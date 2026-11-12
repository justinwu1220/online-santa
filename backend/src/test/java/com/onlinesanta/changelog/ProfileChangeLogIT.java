package com.onlinesanta.changelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinesanta.common.dto.FieldChange;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.user.UserRole;
import com.onlinesanta.user.dto.UserProfileUpdateRequest;

/**
 * 個資異動紀錄的寫入。
 *
 * <p>重點不只是「有寫」，還包括「沒改的時候不要寫」——假異動會讓整份紀錄失去可信度。
 *
 * <p>注意這裡一律用「前後筆數相減」而不是先清空：這張表有資料庫層的 append-only
 * trigger，DELETE 會直接被擋下來（那正是它該有的行為）。
 */
@DisplayName("個資異動紀錄")
class ProfileChangeLogIT extends ApiIntegrationTest {

    private static final String DONOR = "changelog-donor@example.com";

    @Autowired ProfileChangeLogRepository logs;
    @Autowired ProfileChangeLogService changeLog;
    @Autowired UserRepository users;

    private UUID donorId() {
        return users.findByEmailIgnoreCase(DONOR).orElseThrow().getId();
    }

    /** 新的在前。 */
    private List<ProfileChangeLog> logsOf(UUID userId) {
        return logs.findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(
                ProfileChangeTargetType.USER, userId);
    }

    private void patchProfile(String displayName, String name, String phone) throws Exception {
        mvc.perform(as(withBody(patch("/api/me/profile"),
                        new UserProfileUpdateRequest(displayName, name, phone)), DONOR))
                .andExpect(status().isOk());
    }

    /** 先登入建立帳號，再把個人檔案設成已知狀態，回傳此刻的紀錄筆數。 */
    private int givenProfile(String displayName, String name, String phone) throws Exception {
        mvc.perform(as(get("/api/me"), DONOR)).andExpect(status().isOk());
        patchProfile(displayName, name, phone);
        return logsOf(donorId()).size();
    }

    @Test
    @DisplayName("認領者改個人檔案：留下誰、什麼時候、哪個欄位從什麼改成什麼")
    void recordsDonorProfileEdit() throws Exception {
        int before = givenProfile("小明", null, null);
        UUID donor = donorId();

        patchProfile("小明", "王小明", "0912-345-678");

        List<ProfileChangeLog> recorded = logsOf(donor);
        assertThat(recorded).hasSize(before + 1);

        ProfileChangeLog log = recorded.getFirst();
        assertThat(log.getSource()).isEqualTo(ProfileChangeSource.SELF_SERVICE);
        assertThat(log.getActorUserId()).isEqualTo(donor);
        assertThat(log.getActorEmail()).isEqualTo(DONOR);
        assertThat(log.getActorRole()).isEqualTo(UserRole.DONOR);
        assertThat(log.getCreatedAt()).isNotNull();

        assertThat(changeLog.read(log))
                .extracting(FieldChange::field)
                .containsExactlyInAnyOrder("name", "phone");
    }

    @Test
    @DisplayName("內容完全相同的重送不留紀錄——假異動會讓整份紀錄失去可信度")
    void noOpWritesNothing() throws Exception {
        int before = givenProfile("小明", "王小明", "0912-345-678");

        patchProfile("小明", "王小明", "0912-345-678");

        assertThat(logsOf(donorId())).hasSize(before);
    }

    @Test
    @DisplayName("空字串與 null 之間互換不算異動")
    void blankToNullWritesNothing() throws Exception {
        int before = givenProfile("小明", null, null);

        patchProfile("小明", "", "");

        assertThat(logsOf(donorId())).hasSize(before);
    }

    @Test
    @DisplayName("清空欄位要記錄：從有電話改成沒電話正是爭議會問的事")
    void clearingIsRecorded() throws Exception {
        int before = givenProfile("小明", "王小明", "0912-345-678");

        patchProfile("小明", "王小明", "");

        List<ProfileChangeLog> recorded = logsOf(donorId());
        assertThat(recorded).hasSize(before + 1);
        assertThat(changeLog.read(recorded.getFirst()))
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.field()).isEqualTo("phone");
                    assertThat(change.before()).isEqualTo("0912-345-678");
                    assertThat(change.after()).isEmpty();
                });
    }

}
