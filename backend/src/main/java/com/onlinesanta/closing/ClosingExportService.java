package com.onlinesanta.closing;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onlinesanta.common.exception.BusinessRuleException;
import com.onlinesanta.common.exception.ResourceNotFoundException;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;

/**
 * 結案文件匯出：把檔期的有效收據打包成一個壓縮檔。
 *
 * <p><strong>分段推進</strong>：幾百張收據的 PDF 要先補產、再讀回打包，單次請求放不下
 * （Cloud Run 30 秒、Firebase Hosting 轉發約 60 秒）。前端建立匯出後反覆呼叫 {@link #advance}，
 * 每次在時間預算內做一段；進度存在資料庫，請求中斷或換了執行個體都能接續。
 *
 * <p>兩個階段：
 * <ol>
 *   <li><b>補產 PDF</b>：還沒有 PDF（待產生、暫存已過期）的收據逐張產生。本次匯出已經試過仍無法產生的
 *       （缺許可文號、版面放不下）不再重試，最後列在壓縮檔內的說明檔。</li>
 *   <li><b>打包</b>：並行讀回各張收據的紅聯（給捐贈人）與白黃聯（協會留存），依序串流寫進壓縮檔（不整包進記憶體）。</li>
 * </ol>
 *
 * <p>壓縮檔結構：{@code 紅聯（給捐贈人）/編號.pdf}、{@code 白黃聯（協會留存）/編號.pdf}、{@code 00_收據清單.csv}（不含證號）、
 * 有收據沒收進來時的 {@code 00_未收進壓縮檔的收據.txt}。檔名是 UTF-8。
 */
@Service
public class ClosingExportService {

    private static final Logger log = LoggerFactory.getLogger(ClosingExportService.class);

    /** 單次 advance 預設的時間預算：留足餘裕給 Cloud Run 的 30 秒與前端轉發的 60 秒。 */
    public static final Duration DEFAULT_BUDGET = Duration.ofSeconds(15);

    private static final int FETCH_PARALLELISM = 8;
    private static final int FETCH_BATCH = 24;

    private final ClosingExportRepository exports;
    private final DonationReceiptRepository receipts;
    private final DonationReceiptService receiptService;
    private final CampaignService campaigns;
    private final ObjectStorage storage;
    private final ClosingProperties properties;
    private final ReceiptCheckService receiptCheck;
    private final com.onlinesanta.closing.doc.ClosingDataService dataService;
    private final com.onlinesanta.closing.doc.ClosingPackageBuilder packageBuilder;

    public ClosingExportService(ClosingExportRepository exports, DonationReceiptRepository receipts,
                                DonationReceiptService receiptService, CampaignService campaigns,
                                ObjectStorage storage, ClosingProperties properties,
                                ReceiptCheckService receiptCheck,
                                com.onlinesanta.closing.doc.ClosingDataService dataService,
                                com.onlinesanta.closing.doc.ClosingPackageBuilder packageBuilder) {
        this.receiptCheck = receiptCheck;
        this.dataService = dataService;
        this.packageBuilder = packageBuilder;
        this.exports = exports;
        this.receipts = receipts;
        this.receiptService = receiptService;
        this.campaigns = campaigns;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional
    public ClosingExport start(UUID campaignId, UUID adminUserId) {
        campaigns.get(campaignId);
        int total = (int) receipts.countByCampaignIdAndStatus(campaignId, ReceiptStatus.ISSUED);
        if (total == 0) {
            throw new BusinessRuleException("NO_RECEIPTS", "這個檔期還沒有有效的收據，沒有可匯出的內容");
        }
        return exports.save(new ClosingExport(campaignId, ExportKind.RECEIPTS, total, adminUserId));
    }

    /** 建立結案文件包匯出（成果備查與結案備查併報）。 */
    @Transactional
    public ClosingExport startPackage(UUID campaignId, UUID adminUserId) {
        campaigns.get(campaignId);
        int total = (int) receipts.countByCampaignIdAndStatus(campaignId, ReceiptStatus.ISSUED);
        if (total == 0) {
            throw new BusinessRuleException("NO_RECEIPTS", "這個檔期還沒有有效的收據，沒有可匯出的內容");
        }
        return exports.save(new ClosingExport(campaignId, ExportKind.CLOSING_PACKAGE, total, adminUserId));
    }

    @Transactional(readOnly = true)
    public ClosingExport get(UUID id) {
        return exports.findById(id).orElseThrow(() -> ResourceNotFoundException.of("匯出作業", id));
    }

    @Transactional
    public ClosingExport advance(UUID id) {
        return advance(id, DEFAULT_BUDGET);
    }

    /**
     * 推進一段。已經完成、失敗或過期的作業原樣回傳（前端多呼叫一次無害）。
     * 預算用完就存下進度回傳，由下一次呼叫接續。
     */
    @Transactional
    public ClosingExport advance(UUID id, Duration budget) {
        ClosingExport export = exports.findById(id).orElseThrow(() -> ResourceNotFoundException.of("匯出作業", id));
        if (export.getStatus() != ExportStatus.PREPARING) {
            return export;
        }
        Instant deadline = Instant.now().plus(budget);
        try {
            if (!prepare(export, deadline)) {
                return exports.save(export);        // 預算用完，下次接續
            }
            assemble(export);
        } catch (RuntimeException e) {
            log.error("結案文件匯出失敗：exportId={}", id, e);
            export.fail("匯出失敗：" + e.getMessage());
        }
        return exports.save(export);
    }

    /** 補產還沒有 PDF 的收據。回傳 true 代表全部處理完（可以打包）。 */
    private boolean prepare(ClosingExport export, Instant deadline) {
        List<DonationReceipt> todo = receipts.findNeedingPdf(export.getCampaignId()).stream()
                .filter(r -> !attemptedDuring(export, r))
                .toList();
        for (DonationReceipt r : todo) {
            if (!Instant.now().isBefore(deadline)) {
                export.progress(export.getTotal() - remaining(export));
                return false;
            }
            receiptService.generatePdf(r.getId());
        }
        export.progress(export.getTotal() - remaining(export));
        return true;
    }

    /** 這張收據在本次匯出開始之後已經試過產生、仍然不行（版面放不下、缺字、證號解不開等）。 */
    private static boolean attemptedDuring(ClosingExport export, DonationReceipt r) {
        return r.getPdfStatus() == PdfStatus.FAILED
                && r.getUpdatedAt() != null && r.getUpdatedAt().isAfter(export.getCreatedAt());
    }

    private int remaining(ClosingExport export) {
        return (int) receipts.findNeedingPdf(export.getCampaignId()).stream()
                .filter(r -> !attemptedDuring(export, r)).count();
    }

    private void assemble(ClosingExport export) {
        List<DonationReceipt> all = receipts.findAllByCampaignIdOrderByReceiptNoAsc(export.getCampaignId()).stream()
                .filter(r -> r.getStatus() == ReceiptStatus.ISSUED)
                .toList();
        // 雲端檔案可能因為暫存到期被清掉：先在主執行緒補齊，並行讀取的工作執行緒只碰儲存、不碰資料庫
        for (DonationReceipt r : all) {
            if (r.getPdfStatus() == PdfStatus.READY && filesMissing(r)) {
                receiptService.generatePdf(r.getId());
            }
        }
        List<DonationReceipt> included = all.stream().filter(r -> r.getPdfStatus() == PdfStatus.READY).toList();
        List<DonationReceipt> skipped = all.stream().filter(r -> r.getPdfStatus() != PdfStatus.READY).toList();

        String objectName = "closing/exports/" + export.getId() + ".zip";
        long size;
        ExecutorService pool = Executors.newFixedThreadPool(FETCH_PARALLELISM);
        try (CountingOutputStream counting = new CountingOutputStream(
                storage.openWrite(StorageBucket.PRIVATE, objectName, "application/zip"));
             ZipOutputStream zip = new ZipOutputStream(counting, StandardCharsets.UTF_8)) {
            zip.setLevel(Deflater.NO_COMPRESSION);          // PDF 已經壓縮過，再壓只是白花 CPU

            boolean pack = export.getKind() == ExportKind.CLOSING_PACKAGE;
            String receiptsRoot = pack ? "03_收據/" : "";
            String keepRoot = pack ? "05_留存備份/" : "";
            putText(zip, keepRoot + "00_收據檢查.txt", receiptCheck.check(export.getCampaignId()).toText());
            putText(zip, keepRoot + "00_收據清單.csv", "﻿" + csv(all));
            if (!skipped.isEmpty()) {
                putText(zip, keepRoot + "00_未收進壓縮檔的收據.txt", skippedNote(skipped));
            }
            if (pack) {
                var dataset = dataService.load(export.getCampaignId());
                var built = packageBuilder.build(dataset);
                for (var file : built.files()) {
                    putBytes(zip, file.path(), file.bytes());
                }
                putText(zip, keepRoot + "待補資料.md", com.onlinesanta.closing.doc.ClosingPackageBuilder.todoMarkdown(
                        dataset, built.todo()));
            }
            for (int from = 0; from < included.size(); from += FETCH_BATCH) {
                List<DonationReceipt> batch = included.subList(from, Math.min(from + FETCH_BATCH, included.size()));
                List<Future<byte[][]>> futures = new ArrayList<>();
                for (DonationReceipt r : batch) {
                    futures.add(pool.submit(() -> new byte[][] {
                            read(r, PdfKind.RED), read(r, PdfKind.WHITE_YELLOW)}));
                }
                for (int i = 0; i < batch.size(); i++) {
                    byte[][] pair = futures.get(i).get();
                    putBytes(zip, receiptsRoot + "紅聯（給捐贈人）/" + batch.get(i).getReceiptNo() + ".pdf", pair[0]);
                    putBytes(zip, receiptsRoot + "白黃聯（協會留存）/" + batch.get(i).getReceiptNo() + ".pdf", pair[1]);
                }
            }
            zip.finish();
            size = counting.count();
        } catch (IOException e) {
            throw new UncheckedIOException("寫入壓縮檔失敗", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("匯出被中斷", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("讀取收據 PDF 失敗：" + e.getCause().getMessage(), e.getCause());
        } finally {
            pool.shutdownNow();
        }
        export.ready(objectName, size, skipped.size(), Instant.now().plus(Duration.ofDays(properties.retentionDays())));
    }

    private boolean filesMissing(DonationReceipt r) {
        return r.getRedObject() == null || r.getWhiteYellowObject() == null
                || storage.find(StorageBucket.PRIVATE, r.getRedObject()).isEmpty()
                || storage.find(StorageBucket.PRIVATE, r.getWhiteYellowObject()).isEmpty();
    }

    /** 讀回收據 PDF（只讀儲存，可在工作執行緒執行）。 */
    private byte[] read(DonationReceipt r, PdfKind kind) {
        String object = kind == PdfKind.RED ? r.getRedObject() : r.getWhiteYellowObject();
        return (object == null ? java.util.Optional.<byte[]>empty() : storage.read(StorageBucket.PRIVATE, object))
                .orElseThrow(() -> new IllegalStateException("收據 " + r.getReceiptNo() + " 的 PDF 讀不到"));
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws IOException {
        putBytes(zip, name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    /** 收據清單（不含證號）。第一列是欄名，Excel 直接開啟（檔頭有 BOM）。 */
    static String csv(List<DonationReceipt> receipts) {
        StringBuilder sb = new StringBuilder("編號,捐贈者,是否要收據,物資,數量,單位,時價（元）,捐贈日,開立日,機構,PDF 狀態\r\n");
        for (DonationReceipt r : receipts) {
            sb.append(cell(r.getReceiptNo())).append(',')
                    .append(cell(r.getDonorName())).append(',')
                    .append(r.isReceiptRequested() ? "是" : "否").append(',')
                    .append(cell(r.getGiftDescription())).append(',')
                    .append(r.getGiftQuantity()).append(',')
                    .append(cell(r.getGiftUnit())).append(',')
                    .append(r.getGiftValue()).append(',')
                    .append(r.getDonatedOn()).append(',')
                    .append(r.getIssuedOn()).append(',')
                    .append(cell(r.getOrganizationName())).append(',')
                    .append(r.getPdfStatus()).append("\r\n");
        }
        return sb.toString();
    }

    /** CSV 欄位跳脫；開頭是 = + - @ 的文字加單引號，避免 Excel 把它當成公式執行。 */
    private static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String skippedNote(List<DonationReceipt> skipped) {
        StringBuilder sb = new StringBuilder("以下收據的 PDF 無法產生，沒有收進這個壓縮檔：\r\n\r\n");
        for (DonationReceipt r : skipped) {
            sb.append(r.getReceiptNo()).append("：");
            switch (r.getPdfStatus()) {
                case FAILED -> sb.append(r.getPdfError() == null ? "版面放不下" : r.getPdfError());
                default -> sb.append("尚未產生");
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    /** 計算寫出的位元組數（壓縮檔大小）。 */
    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count;

        CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        long count() {
            return count;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
