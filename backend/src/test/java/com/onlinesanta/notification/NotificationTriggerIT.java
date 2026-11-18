package com.onlinesanta.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.transaction.TestTransaction;

import com.onlinesanta.admin.dto.ReviewDecisionRequest;
import com.onlinesanta.admin.dto.ReviewReasonRequest;
import com.onlinesanta.attachment.AttachmentPurpose;
import com.onlinesanta.attachment.dto.UploadUrlRequest;
import com.onlinesanta.support.ShipRequests;
import com.onlinesanta.admin.dto.WishFreezeRequest;
import com.onlinesanta.admin.dto.WishContentEditRequest;
import com.onlinesanta.event.ClaimCreatedEvent;
import com.onlinesanta.event.OrganizationSubmittedEvent;
import com.onlinesanta.event.OrganizationUnlockRejectedEvent;
import com.onlinesanta.event.WishUnlockDecidedEvent;
import com.onlinesanta.event.WishModeratedByAdminEvent;
import com.onlinesanta.event.WishFrozenEvent;
import com.onlinesanta.event.UserSuspensionEvent;
import com.onlinesanta.event.OrganizationSuspensionEvent;
import com.onlinesanta.event.OrgSuspensionClaimEvent;
import com.onlinesanta.event.ClaimReleasedByAdminEvent;
import com.onlinesanta.event.ClaimRecalledEvent;
import com.onlinesanta.event.AffectedDonorNoticeEvent;
import com.onlinesanta.event.FeedbackPhotoConfirmedEvent;
import com.onlinesanta.event.OrganizationReviewedEvent;
import com.onlinesanta.message.dto.SendMessageRequest;
import com.onlinesanta.organization.Organization;
import com.onlinesanta.organization.OrganizationRepository;
import com.onlinesanta.support.ApiIntegrationTest;
import com.onlinesanta.support.InMemoryObjectStorage;
import com.onlinesanta.support.RecordingMailSender;
import com.onlinesanta.support.TestJwtSupport;
import com.onlinesanta.storage.ObjectStorage;
import com.onlinesanta.storage.StorageBucket;
import com.onlinesanta.user.User;
import com.onlinesanta.user.UserRepository;
import com.onlinesanta.wish.AgeRange;
import com.onlinesanta.wish.PriceRange;
import com.onlinesanta.wish.Wish;
import com.onlinesanta.wish.WishCategory;
import com.onlinesanta.wish.WishRepository;

/**
 * Email 通知的各種觸發時機。
 *
 * <p>分成三類驗證，各自對應不同的可測性考量：
 *
 * <ul>
 *   <li><b>事件是否正確發布</b>——用 {@link ApplicationEvents} 直接斷言，不依賴交易
 *       真的 commit（{@code ApiIntegrationTest} 整個測試方法包在一個交易裡，方法結束
 *       會自動回滾，{@code @TransactionalEventListener(AFTER_COMMIT)} 因此完全不會
 *       觸發——事件有沒有「發布」跟它「最終有沒有被消費」是兩件事，這裡只驗前者）。
 *   <li><b>監聽器組出的信件內容是否正確</b>——直接呼叫監聽器方法（不經過真正的
 *       AFTER_COMMIT 派送），因為這裡要驗的是「給定一個事件，組出的收件人與內文對不
 *       對」，不是交易時機本身。
 *   <li><b>AFTER_COMMIT 語意本身</b>（交易 commit 才寄信、回滾不寄信）——用
 *       {@link TestTransaction} 強制真正 commit 一次，驗證機制本身，測完手動清掉
 *       建立的資料，不依賴 {@code ApiIntegrationTest} 的自動回滾。
 * </ul>
 */
@RecordApplicationEvents
@DisplayName("Email 通知的觸發時機")
class NotificationTriggerIT extends ApiIntegrationTest {

    private static final String ORG_NAME = "通知測試機構";
    private static final String ORG_USER = "notify-org@example.org";
    private static final String OTHER_ORG_USER = "notify-org-2@example.org";
    private static final String DONOR = "notify-donor@example.com";
    private static final String ADMIN = "platform-admin@example.com";
    private static final String JPEG = "image/jpeg";

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    UserRepository users;

    @Autowired
    WishRepository wishes;

    @Autowired
    ObjectStorage storage;

    @Autowired
    JavaMailSender mailSender;

    @Autowired
    ApplicationEvents events;

    @Autowired
    ClaimCreatedNotificationListener claimCreatedListener;

    @Autowired
    OrganizationReviewedNotificationListener organizationReviewedListener;

    @Autowired
    FeedbackPhotoConfirmedNotificationListener feedbackPhotoListener;

    private RecordingMailSender fakeMailSender;
    private Organization organization;

    @BeforeEach
    void setUp() {
        fakeMailSender = (RecordingMailSender) mailSender;
        fakeMailSender.reset();

        organization = approvedOrganization("通知測試機構", "org-contact@example.org", ORG_USER);
        users.save(User.newDonor(DONOR, "熱心民眾"));
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * 只有真的用 {@link TestTransaction} commit 過的測試才需要呼叫這個方法清理——
     * 一般測試的資料本來就會隨 {@code @Transactional} 測試基底自動回滾，不需要、
     * 也不能在這裡順便 commit 一次清理，否則反而讓「一般測試」的資料也被留下來。
     *
     * <p>清理本身也得明確 commit：呼叫時當下的交易是 {@link TestTransaction#start()}
     * 開的新交易，若不主動 flagForCommit，一樣會被測試框架在方法結束時整個回滾，
     * 清理等於沒做。
     */
    private void commitCleanupOfSetUpData() {
        TestTransaction.start();
        jdbc.update("""
                DELETE FROM messages WHERE claim_id IN (
                    SELECT c.id FROM claims c
                    JOIN wishes w ON w.id = c.wish_id
                    JOIN organizations o ON o.id = w.organization_id
                    WHERE o.name = ?)
                """, ORG_NAME);
        jdbc.update("""
                DELETE FROM claim_events WHERE claim_id IN (
                    SELECT c.id FROM claims c
                    JOIN wishes w ON w.id = c.wish_id
                    JOIN organizations o ON o.id = w.organization_id
                    WHERE o.name = ?)
                """, ORG_NAME);
        jdbc.update("""
                DELETE FROM claims WHERE wish_id IN (
                    SELECT id FROM wishes WHERE organization_id = (
                        SELECT id FROM organizations WHERE name = ?))
                """, ORG_NAME);
        jdbc.update("""
                DELETE FROM wishes WHERE organization_id = (
                    SELECT id FROM organizations WHERE name = ?)
                """, ORG_NAME);
        jdbc.update("DELETE FROM users WHERE email IN (?, ?)", ORG_USER, DONOR);
        jdbc.update("DELETE FROM organizations WHERE name = ?", ORG_NAME);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // 留一個 active 交易給測試框架收尾（它會 rollback 這個空交易，無妨）
        TestTransaction.start();
    }

    private Organization approvedOrganization(String name, String contactEmail, String memberEmail) {
        Organization org = Organization.register(name, "王承辦", contactEmail, null, null, null);
        org.approve(null, "測試資料");
        organizations.save(org);

        User member = User.newDonor(memberEmail, memberEmail);
        member.joinOrganization(org.getId());
        users.save(member);
        return org;
    }

    private UUID publishedWish(String title) {
        Wish wish = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                title, "描述", WishCategory.ART, PriceRange.UNDER_500);
        wish.publish();
        return wishes.save(wish).getId();
    }

    private UUID claimAs(UUID wishId, String donorEmail) throws Exception {
        String body = mvc.perform(as(post("/api/wishes/{id}/claim", wishId), donorEmail))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private void awaitMailCount(int expected) {
        long deadline = System.currentTimeMillis() + 3000;
        while (fakeMailSender.sent().size() < expected && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ================================================================ 事件確實被發布

    @Test
    @DisplayName("認領成立會發布 ClaimCreatedEvent")
    void claimCreationPublishesEvent() throws Exception {
        UUID wishId = publishedWish("會被認領");
        UUID claimId = claimAs(wishId, DONOR);

        assertThat(events.stream(ClaimCreatedEvent.class))
                .singleElement()
                .satisfies(e -> assertThat(e.claimId()).isEqualTo(claimId));
    }

    @Test
    @DisplayName("核准與駁回都會發布 OrganizationReviewedEvent，approved 欄位正確")
    void organizationReviewPublishesEvent() throws Exception {
        Organization pending = Organization.register(
                "待審機構", "王承辦", "pending@example.org", null, null, null);
        organizations.save(pending);
        givenConfirmedOrganizationDocument(pending.getId());
        givenConfirmedOrganizationPhoto(pending.getId());

        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/approve", pending.getId()),
                        new ReviewDecisionRequest("證件齊全")), ADMIN))
                .andExpect(status().isOk());

        Organization pending2 = Organization.register(
                "待審機構二", "王承辦", "pending2@example.org", null, null, null);
        organizations.save(pending2);
        mvc.perform(as(withBody(post("/api/admin/organizations/{id}/reject", pending2.getId()),
                        new ReviewReasonRequest("請補立案證明")), ADMIN))
                .andExpect(status().isOk());

        assertThat(events.stream(OrganizationReviewedEvent.class))
                .anySatisfy(e -> {
                    assertThat(e.organizationId()).isEqualTo(pending.getId());
                    assertThat(e.approved()).isTrue();
                })
                .anySatisfy(e -> {
                    assertThat(e.organizationId()).isEqualTo(pending2.getId());
                    assertThat(e.approved()).isFalse();
                });
    }

    @Test
    @DisplayName("回饋照片確認上傳會發布 FeedbackPhotoConfirmedEvent，其他附件用途不會")
    void feedbackPhotoConfirmationPublishesEvent() throws Exception {
        // 願望示意圖是另一種用途，不該觸發回饋照片的通知事件
        Wish draft = Wish.draft(organization, "小星", AgeRange.AGE_7_9, "畫畫",
                "有示意圖的願望", "描述", WishCategory.ART, PriceRange.UNDER_500);
        UUID draftWishId = wishes.save(draft).getId();
        uploadAndConfirm(AttachmentPurpose.WISH_IMAGE, draftWishId, ORG_USER);
        assertThat(events.stream(FeedbackPhotoConfirmedEvent.class)).isEmpty();

        UUID wishId = publishedWish("回饋照片");
        UUID claimId = claimAs(wishId, DONOR);
        uploadAndConfirm(AttachmentPurpose.SHIPPING_PROOF, claimId, DONOR);
        mvc.perform(as(withBody(post("/api/claims/{id}/ship", claimId),
                ShipRequests.of("郵局", "R123")), DONOR)).andExpect(status().isOk());

        uploadAndConfirm(AttachmentPurpose.ORG_FEEDBACK, claimId, ORG_USER);

        assertThat(events.stream(FeedbackPhotoConfirmedEvent.class))
                .singleElement()
                .satisfies(e -> assertThat(e.claimId()).isEqualTo(claimId));
    }

    private UUID uploadAndConfirm(AttachmentPurpose purpose, UUID targetId, String userEmail) throws Exception {
        var request = new UploadUrlRequest(purpose, targetId, JPEG, 1_000);
        String body = mvc.perform(as(withBody(post("/api/uploads/signed-url"), request), userEmail))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = json.readTree(body);
        UUID attachmentId = UUID.fromString(node.get("attachmentId").asText());
        String uploadUrl = node.get("uploadUrl").asText();
        String objectName = uploadUrl.substring(
                uploadUrl.indexOf(purpose.prefix()), uploadUrl.indexOf('?'));

        ((InMemoryObjectStorage) storage).simulateUpload(purpose.bucket(), objectName, JPEG, 1_000);

        mvc.perform(as(post("/api/attachments/{id}/confirm", attachmentId), userEmail))
                .andExpect(status().isOk());
        return attachmentId;
    }

    // ================================================================ 監聽器組出的信件內容

    @Test
    @DisplayName("認領成立只寄一封：認領者收到確認信，機構不再收信")
    void claimCreatedSendsOnlyTheDonorConfirmation() throws Exception {
        UUID wishId = publishedWish("內容檢查的願望");
        UUID claimId = claimAs(wishId, DONOR);

        claimCreatedListener.onClaimCreated(new ClaimCreatedEvent(claimId));
        awaitMailCount(1);
        // 多等一下，確認沒有第二封（原本會寄給機構的那封已停寄）
        Thread.sleep(300);

        assertThat(fakeMailSender.sent()).singleElement().satisfies((SimpleMailMessage mail) ->
                assertThat(mail.getTo()).containsExactly(DONOR));
    }

    /**
     * 認領者在按下認領之後、期限提醒信之前，原本收不到任何訊息——而他此刻剛承諾了
     * 要買禮物並在期限前寄出。這封信是他唯一能回頭翻的憑據，所以期限與認領頁連結
     * 都必須在內文裡。
     */
    @Test
    @DisplayName("認領者的確認信含機構名稱、寄送期限與認領頁連結")
    void claimCreatedNotifiesDonor() throws Exception {
        UUID claimId = claimAs(publishedWish("確認信檢查"), DONOR);

        claimCreatedListener.onClaimCreated(new ClaimCreatedEvent(claimId));
        awaitMailCount(1);

        assertThat(fakeMailSender.sentMails()).singleElement().satisfies(sent -> {
            SimpleMailMessage mail = sent.message();
            assertThat(mail.getTo()).containsExactly(DONOR);
            assertThat(mail.getSubject()).startsWith("【線上聖誕老人 2026】").contains("您已認領").contains("確認信檢查");
            assertThat(mail.getText())
                    .contains(ORG_NAME)
                    .contains("前寄出禮物").containsPattern("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}（臺灣時間）")
                    .contains("/me/claims/" + claimId);
            // HTML 版同樣帶著連結，且是 multipart/alternative
            assertThat(sent.html()).contains("/me/claims/" + claimId).contains(ORG_NAME);
        });
    }

    @Test
    @DisplayName("機構審核通過與駁回的信件內容不同，駁回會附上理由")
    void organizationReviewedNotificationContent() throws Exception {
        organization.reject(null, "請補立案證明文件");
        organizations.save(organization);

        organizationReviewedListener.onOrganizationReviewed(
                new OrganizationReviewedEvent(organization.getId(), false));
        awaitMailCount(1);

        assertThat(fakeMailSender.sent()).singleElement().satisfies((SimpleMailMessage mail) -> {
            assertThat(mail.getTo()).containsExactly("org-contact@example.org");
            assertThat(mail.getSubject()).contains("需要補件");
            assertThat(mail.getText()).contains("請補立案證明文件").contains("/org/settings");
        });
    }

    @Test
    @DisplayName("機構審核通過的通知信寄給 contactEmail，內文含核准訊息與登入連結")
    void organizationApprovedNotificationContent() throws Exception {
        organizationReviewedListener.onOrganizationReviewed(
                new OrganizationReviewedEvent(organization.getId(), true));
        awaitMailCount(1);

        assertThat(fakeMailSender.sent()).singleElement().satisfies((SimpleMailMessage mail) -> {
            assertThat(mail.getTo()).containsExactly("org-contact@example.org");
            assertThat(mail.getSubject()).contains("機構申請已通過");
            assertThat(mail.getText()).contains("審核").contains("/org");
        });
    }

    @Test
    @DisplayName("回饋照片通知寄給捐贈者，內文連到我的認領詳情頁")
    void feedbackPhotoNotificationContent() throws Exception {
        UUID claimId = claimAs(publishedWish("回饋內容檢查"), DONOR);

        feedbackPhotoListener.onFeedbackPhotoConfirmed(new FeedbackPhotoConfirmedEvent(claimId));
        awaitMailCount(1);

        assertThat(fakeMailSender.sent()).singleElement().satisfies(mail -> {
            assertThat(mail.getTo()).containsExactly(DONOR);
            assertThat(mail.getText()).contains("/me/claims/" + claimId);
        });
    }

    // ================================================================ 其餘各封通知都能組出信件
    //
    // 欄位名稱與樣板對不上時，寄信會記一行 ERROR 然後「不寄」——使用者那邊只是沒收到信，
    // 沒有任何錯誤畫面。所以每個分支都要有一個「真的收到信」的測試。

    @Autowired
    SuspensionNotificationListener suspensionListener;

    @Autowired
    WishFrozenNotificationListener wishFrozenListener;

    @Autowired
    ClaimRecalledNotificationListener claimRecalledListener;

    private void expectMail(int count, String to, String subjectPart, String textPart) {
        awaitMailCount(count);
        assertThat(fakeMailSender.sent()).hasSize(count);
        SimpleMailMessage mail = fakeMailSender.sent().get(count - 1);
        assertThat(mail.getTo()).containsExactly(to);
        assertThat(mail.getSubject()).startsWith("【線上聖誕老人 2026】").contains(subjectPart);
        assertThat(mail.getText()).contains(textPart);
    }

    @Test
    @DisplayName("認領者收到的各封信：收回、暫停、恢復、機構停權連動、個案通知、結束認領")
    void donorFacingNotifications() throws Exception {
        UUID claimId = claimAs(publishedWish("各種通知"), DONOR);
        String claimUrl = "/me/claims/" + claimId;

        claimRecalledListener.onClaimRecalled(new ClaimRecalledEvent(claimId, "認領者聯繫不上"));
        expectMail(1, DONOR, "各種通知", "認領者聯繫不上");

        wishFrozenListener.onWishFrozen(new WishFrozenEvent(claimId, true));
        expectMail(2, DONOR, "各種通知", claimUrl);

        wishFrozenListener.onWishFrozen(new WishFrozenEvent(claimId, false));
        expectMail(3, DONOR, "各種通知", claimUrl);
        // 恢復信帶孩子的願望內容與（未過期的）新期限
        assertThat(fakeMailSender.sent().get(2).getText()).contains("（臺灣時間）");

        suspensionListener.onClaimAffected(new OrgSuspensionClaimEvent(claimId, true));
        expectMail(4, DONOR, "各種通知", ORG_NAME);

        suspensionListener.onClaimAffected(new OrgSuspensionClaimEvent(claimId, false));
        expectMail(5, DONOR, "各種通知", claimUrl);

        suspensionListener.onAffectedDonorNotice(new AffectedDonorNoticeEvent(claimId, "第一行\n第二行"));
        expectMail(6, DONOR, "各種通知", "第一行\n第二行");

        suspensionListener.onClaimReleasedByAdmin(new ClaimReleasedByAdminEvent(claimId, "重複認領"));
        expectMail(7, DONOR, "各種通知", "重複認領");
    }

    @Test
    @DisplayName("機構收到的停權與解除信，以及認領者帳號停權與恢復信")
    void suspensionNotifications() throws Exception {
        suspensionListener.onOrganizationSuspension(
                new OrganizationSuspensionEvent(organization.getId(), true, "文件造假"));
        expectMail(1, "org-contact@example.org", "停權", "文件造假");

        suspensionListener.onOrganizationSuspension(
                new OrganizationSuspensionEvent(organization.getId(), false, null));
        expectMail(2, "org-contact@example.org", "停權", "/org");

        claimAs(publishedWish("帳號停權用"), DONOR);
        UUID donorId = users.findAll().stream()
                .filter(u -> u.getEmail().equals(DONOR)).findFirst().orElseThrow().getId();
        suspensionListener.onUserSuspension(new UserSuspensionEvent(donorId, true, "多次違規"));
        expectMail(3, DONOR, "帳號", "多次違規");

        suspensionListener.onUserSuspension(new UserSuspensionEvent(donorId, false, null));
        expectMail(4, DONOR, "帳號", "我的認領");
    }

    // ================================================================ 機構端通知（4.1、4.5、4.9、4.10）

    @Autowired
    OrganizationNoticeNotificationListener organizationNoticeListener;

    @Test
    @DisplayName("管理員處置願望的四種信：凍結、修改內容、移除照片、直接解除")
    void wishModerationNotifications() throws Exception {
        UUID wishId = publishedWish("被處置的願望");
        String to = "org-contact@example.org";

        organizationNoticeListener.onWishModerated(new WishModeratedByAdminEvent(
                wishId, WishModeratedByAdminEvent.Action.FROZEN, "疑似含真實姓名"));
        expectMail(1, to, "被處置的願望", "疑似含真實姓名");

        organizationNoticeListener.onWishModerated(new WishModeratedByAdminEvent(
                wishId, WishModeratedByAdminEvent.Action.CONTENT_EDITED, "已改寫說明"));
        expectMail(2, to, "被處置的願望", "已改寫說明");

        organizationNoticeListener.onWishModerated(new WishModeratedByAdminEvent(
                wishId, WishModeratedByAdminEvent.Action.PHOTO_REMOVED, "信件含個資"));
        expectMail(3, to, "被處置的願望", "信件含個資");

        // 直接解除：沒有管理員留言也能寄；願望已回牆時信上這樣說
        organizationNoticeListener.onWishModerated(new WishModeratedByAdminEvent(
                wishId, WishModeratedByAdminEvent.Action.UNFROZEN, null));
        expectMail(4, to, "被處置的願望", "已回到願望牆");
    }

    @Test
    @DisplayName("凍結狀態以提交後的實際狀態為準：修改內容的信只在仍凍結時才寫「仍凍結」；解除後仍凍結就不說已回牆")
    void moderationMailsReflectTheActualFrozenState() throws Exception {
        UUID wishId = publishedWish("實際狀態");
        String to = "org-contact@example.org";

        // 沒有凍結時，修改內容的信不能說「目前仍凍結」
        organizationNoticeListener.onWishModerated(new WishModeratedByAdminEvent(
                wishId, WishModeratedByAdminEvent.Action.CONTENT_EDITED, "只改了說明"));
        awaitMailCount(1);
        assertThat(fakeMailSender.sent().get(0).getText()).doesNotContain("仍凍結");

        // 真的凍結之後再寄，就會說仍凍結；此時「解除」信也不能說已回到願望牆
        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", wishId),
                new WishFreezeRequest("先暫時凍結調查")), ADMIN)).andExpect(status().isOk());
        organizationNoticeListener.onWishModerated(new WishModeratedByAdminEvent(
                wishId, WishModeratedByAdminEvent.Action.CONTENT_EDITED, "只改了說明"));
        awaitMailCount(2);
        assertThat(fakeMailSender.sent().get(1).getText()).contains("仍凍結");

        organizationNoticeListener.onWishModerated(new WishModeratedByAdminEvent(
                wishId, WishModeratedByAdminEvent.Action.UNFROZEN, null));
        expectMail(3, to, "實際狀態", "仍暫停公開");
        assertThat(fakeMailSender.sent().get(2).getText()).doesNotContain("已回到願望牆");
    }

    @Test
    @DisplayName("願望解除凍結申請的結果：通過與退回（退回附原因）")
    void unlockDecisionNotifications() throws Exception {
        UUID wishId = publishedWish("申請解除的願望");
        String to = "org-contact@example.org";

        organizationNoticeListener.onUnlockDecided(new WishUnlockDecidedEvent(wishId, true, "謝謝補充"));
        expectMail(1, to, "申請解除的願望", "謝謝補充");

        organizationNoticeListener.onUnlockDecided(new WishUnlockDecidedEvent(wishId, false, "請說明修改了什麼"));
        expectMail(2, to, "申請解除的願望", "請說明修改了什麼");
    }

    @Test
    @DisplayName("解除停權申請被退回；首次申請與重新送審的收件確認")
    void organizationNotices() throws Exception {
        String to = "org-contact@example.org";

        organizationNoticeListener.onOrganizationUnlockRejected(
                new OrganizationUnlockRejectedEvent(organization.getId(), "請補上最新的立案證明"));
        expectMail(1, to, "停權", "請補上最新的立案證明");

        organizationNoticeListener.onOrganizationSubmitted(
                new OrganizationSubmittedEvent(organization.getId(), false));
        expectMail(2, to, "申請", ORG_NAME);
        assertThat(fakeMailSender.sent().get(1).getText()).contains(to);

        organizationNoticeListener.onOrganizationSubmitted(
                new OrganizationSubmittedEvent(organization.getId(), true));
        expectMail(3, to, "重新送審", ORG_NAME);
    }

    @Test
    @DisplayName("管理員凍結、解除凍結、修改內容會發布 WishModeratedByAdminEvent，動作與原因正確")
    void adminWishActionsPublishModerationEvents() throws Exception {
        UUID wishId = publishedWish("處置事件");

        mvc.perform(as(withBody(post("/api/admin/wishes/{id}/freeze", wishId),
                new WishFreezeRequest("疑似含真實姓名")), ADMIN)).andExpect(status().isOk());
        mvc.perform(as(post("/api/admin/wishes/{id}/unfreeze", wishId), ADMIN)).andExpect(status().isOk());

        assertThat(events.stream(WishModeratedByAdminEvent.class).toList())
                .extracting(WishModeratedByAdminEvent::action, WishModeratedByAdminEvent::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(WishModeratedByAdminEvent.Action.FROZEN, "疑似含真實姓名"),
                        org.assertj.core.groups.Tuple.tuple(WishModeratedByAdminEvent.Action.UNFROZEN, null));
    }

    @Test
    @DisplayName("被退件的機構補件後重新送審，會發布 OrganizationSubmittedEvent（resubmission＝true）")
    void resubmissionPublishesSubmittedEvent() throws Exception {
        organization.reject(null, "請補立案證明文件");
        organizations.save(organization);

        mvc.perform(as(post("/api/organizations/me/resubmit"), ORG_USER)).andExpect(status().isOk());

        assertThat(events.stream(OrganizationSubmittedEvent.class).toList())
                .singleElement().satisfies(e -> {
                    assertThat(e.organizationId()).isEqualTo(organization.getId());
                    assertThat(e.resubmission()).isTrue();
                });
    }

    @Test
    @DisplayName("只屏蔽信件照片（沒改任何欄位）算「移除照片」：發布事件，並留站內訊息")
    void maskOnlyCountsAsPhotoRemoved() throws Exception {
        UUID wishId = publishedWish("只屏蔽照片");

        mvc.perform(as(withBody(patch("/api/admin/wishes/{id}/content", wishId),
                new WishContentEditRequest(null, null, null, null, "信件含個資", false, true)), ADMIN))
                .andExpect(status().isOk());

        assertThat(events.stream(WishModeratedByAdminEvent.class).toList())
                .singleElement().satisfies(e -> {
                    assertThat(e.action()).isEqualTo(WishModeratedByAdminEvent.Action.PHOTO_REMOVED);
                    assertThat(e.reason()).isEqualTo("信件含個資");
                });
    }

    // ================================================================ AFTER_COMMIT 語意本身

    @Test
    @DisplayName("交易真正 commit 之後，通知信才會被寄出")
    void notificationIsSentOnlyAfterCommit() throws Exception {
        UUID wishId = publishedWish("commit 後才寄信");
        claimAs(wishId, DONOR);

        // 認領已經送出，但這個測試方法目前還在同一個交易裡，尚未 commit
        assertThat(fakeMailSender.sent()).isEmpty();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        try {
            // 認領成立只寄認領者的確認信（給機構的那封已停寄）
            awaitMailCount(1);
            Thread.sleep(300);
            assertThat(fakeMailSender.sent()).singleElement().satisfies((SimpleMailMessage mail) ->
                    assertThat(mail.getTo()).containsExactly(DONOR));
        } finally {
            // 這次真正 commit 過，資料不會隨測試自動回滾，明確清掉並真正 commit 這次清理。
            //
            // 放在 finally：斷言一旦失敗就跳過清理的話，留下的機構會讓之後每個跑 setUp()
            // 的測試都撞上 uq_organizations_name，連別的測試類別（ClaimReleaseIT）都會
            // 看到多出來的認領——一個失敗瞬間放大成一整串，真正的原因反而被淹沒。
            commitCleanupOfSetUpData();
        }
    }

    @Test
    @DisplayName("交易回滾時，不會寄出通知信")
    void notificationIsNotSentWhenTransactionRollsBack() throws Exception {
        UUID wishId = publishedWish("回滾不寄信");
        claimAs(wishId, DONOR);

        // 明確標記回滾（而非 flagForCommit），驗證回滾路徑上 AFTER_COMMIT 監聽器
        // 完全不會被觸發
        TestTransaction.flagForRollback();
        TestTransaction.end();

        // 給非同步執行緒一點時間，確認「真的沒有」而不是「還沒輪到」
        Thread.sleep(300);
        assertThat(fakeMailSender.sent()).isEmpty();

        TestTransaction.start();
    }
}
