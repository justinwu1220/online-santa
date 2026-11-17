package com.onlinesanta.changelog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinesanta.common.FieldDiff;
import com.onlinesanta.common.dto.FieldChange;

/**
 * 寫入個資異動紀錄。
 *
 * <p>沒有變動就不寫——這是整套機制便宜的關鍵，也讓「登入時順便修補資料」那種每個
 * request 都會跑的路徑只有在真的改動時才留下一筆。
 *
 * <p>用一般的 {@code @Transactional} 併入呼叫端的交易，理由與
 * {@link com.onlinesanta.admin.AdminAuditService} 相同：被 rollback 掉的改動，不該留下
 * 一筆聲稱它發生過的紀錄。
 */
@Service
public class ProfileChangeLogService {

    private final ProfileChangeLogRepository logs;
    private final ObjectMapper json;

    public ProfileChangeLogService(ProfileChangeLogRepository logs, ObjectMapper json) {
        this.logs = logs;
        this.json = json;
    }

    /**
     * 比對前後快照，有變動才寫一筆。
     *
     * @return 實際記錄下來的欄位變更；沒有變動時為空清單
     */
    @Transactional
    public List<FieldChange> record(ProfileChangeTargetType targetType, UUID targetId,
                                    Map<String, String> before, Map<String, String> after,
                                    ProfileChangeActor actor) {
        List<FieldChange> changes = FieldDiff.between(before, after);
        if (changes.isEmpty()) {
            return List.of();
        }
        logs.save(ProfileChangeLog.of(targetType, targetId, write(changes), actor));
        return changes;
    }

    /**
     * 某個對象在某個時間點之後的異動，舊的在前。停權抽屜用。
     *
     * <p>不寫稽核：呼叫端（{@code detailOf}）本來就已經寫了一筆。
     */
    @Transactional(readOnly = true)
    public List<ProfileChangeLog> since(ProfileChangeTargetType targetType, UUID targetId, Instant since) {
        return logs.findByTargetTypeAndTargetIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAscIdAsc(
                targetType, targetId, since);
    }

    /**
     * 把存下來的 JSON 讀回欄位變更。
     *
     * <p>讀取這端刻意寬容：一列壞掉就當作沒有欄位可顯示，不要讓整頁掛掉。寫入端相反
     * （見 {@link #write}）——寫壞了要擋，讀壞了不該連累其他紀錄。
     */
    public List<FieldChange> read(ProfileChangeLog log) {
        try {
            return json.readValue(log.getChanges(), new TypeReference<List<FieldChange>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    /**
     * 序列化失敗就讓整筆改動 rollback。
     *
     * <p>payload 只是一串 String，實務上不會失敗；但萬一失敗，另一個選擇是系統默默
     * 接受了一筆沒有證據的個資異動——那正是這個功能存在的理由。寧可讓使用者重試。
     */
    private String write(List<FieldChange> changes) {
        try {
            return json.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("無法序列化個資異動紀錄", e);
        }
    }
}
