package com.onlinesanta.closing.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.springframework.stereotype.Component;

/**
 * 收據三聯單範本（三頁：白、黃、紅）。
 *
 * <p>這份 PDF 不是使用者提供的原檔，而是由原檔用 {@code tools/BuildReceiptTemplate} 產生：範本上所有文字
 * 統一改用全字庫正楷體 TW-Kai（原檔的標楷體只是子集，也不能散布），固定標籤以真正的文字寫回原位置；
 * 預印的舊編號、舊許可文號與期間兩行、數量欄的「份」已經移除，由每張收據自己的內容取代。
 * 產生步驟與來源見 {@code closing/README.md}。
 *
 * <p>執行期只需要把它當底圖：每張收據再疊上自己的內容（{@link ReceiptPdfRenderer}）。
 */
@Component
public class ReceiptTemplate {

    private static final String RESOURCE = "closing/receipt-template.pdf";

    private final byte[] pdf;

    public ReceiptTemplate() {
        try (InputStream in = ReceiptTemplate.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("找不到收據範本：" + RESOURCE);
            }
            this.pdf = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("讀取收據範本失敗", e);
        }
    }

    /** 範本的位元組；每次渲染各自載入一份（PDDocument 不是執行緒安全的）。 */
    public byte[] pdf() {
        return pdf;
    }
}
