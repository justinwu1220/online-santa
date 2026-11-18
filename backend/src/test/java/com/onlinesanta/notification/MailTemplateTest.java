package com.onlinesanta.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import com.onlinesanta.support.RecordingMailSender;

/** 樣板載入、填值、條件段落、跳脫、純文字版與 MIME 寄送。 */
class MailTemplateTest {

    private static final MailTemplates TEMPLATES = new MailTemplates();

    /** 每個欄位都填上看得出來源的值；data-if 欄位也是有值（保留該段）。 */
    private static Map<String, Object> sampleValues(MailTemplate template) {
        Map<String, Object> values = new HashMap<>();
        for (String field : template.fields()) {
            values.put(field, field.endsWith("Url") ? "https://example.org/" + field : "值<" + field + ">");
        }
        return values;
    }

    @ParameterizedTest
    @EnumSource(MailTemplateId.class)
    @DisplayName("每一份樣板都能載入，並能用完整欄位渲染出完整的信")
    void everyTemplateRenders(MailTemplateId id) {
        MailTemplate template = TEMPLATES.get(id);
        MailTemplate.Rendered mail = template.render(sampleValues(template));

        assertThat(mail.subject()).startsWith("【線上聖誕老人 2026】").doesNotContain("{{").doesNotContain("\n");
        assertThat(mail.html()).doesNotContain("{{").doesNotContain("data-if");
        assertThat(mail.text()).doesNotContain("{{")
                .doesNotContainPattern("</?(p|br|div|strong|a|span|td|tr|table)\b").contains("Best wishes,")
                .contains("線上聖誕老人團隊");
        assertThat(mail.html()).contains("Best wishes,<br>線上聖誕老人團隊");
        // 值裡的角括號不能原樣進 HTML（已跳脫），純文字版則是原樣
        assertThat(mail.html()).doesNotContain("值<");
    }

    @Test
    @DisplayName("data-if：空白、null、false 時整段拿掉；有值時保留並移除屬性")
    void conditionalParagraph() {
        MailTemplate t = TEMPLATES.get(MailTemplateId.ORG_APPROVED);
        Map<String, Object> v = new HashMap<>();
        v.put("organizationName", "星星小學");
        v.put("orgUrl", "https://example.org/org");

        v.put("reviewNote", "謝謝您");
        String with = t.render(v).html();
        assertThat(with).contains("團隊給貴單位的留言").contains("謝謝您").doesNotContain("data-if");

        for (Object empty : new Object[] {null, "", "   ", false}) {
            v.put("reviewNote", empty);
            MailTemplate.Rendered without = t.render(v);
            assertThat(without.html()).doesNotContain("團隊給貴單位的留言");
            assertThat(without.text()).doesNotContain("團隊給貴單位的留言");
            // 整段拿掉後其餘內容完整
            assertThat(without.html()).contains("星星小學").contains("https://example.org/org");
        }
    }

    @Test
    @DisplayName("跳脫：HTML 版跳脫特殊字元並把換行轉成 <br>；純文字版還原；主旨不跳脫且壓成一行")
    void escapingAndNewlines() {
        MailTemplate t = TEMPLATES.get(MailTemplateId.TEAM_CASE_NOTICE);
        Map<String, Object> v = sampleValues(t);
        v.put("adminMessage", "第一行 <b>&\"'\n第二行");
        MailTemplate.Rendered mail = t.render(v);

        assertThat(mail.html()).contains("第一行 &lt;b&gt;&amp;&quot;&#39;<br>第二行").doesNotContain("<b>&");
        assertThat(mail.text()).contains("第一行 <b>&\"'\n第二行");

        MailTemplate s = TEMPLATES.get(MailTemplateId.ADMIN_DIGEST);
        Map<String, Object> sv = sampleValues(s);
        sv.put("orgCount", "2\n3");
        assertThat(s.render(sv).subject()).contains("機構 2 3 件").doesNotContain("\n");
    }

    @Test
    @DisplayName("純文字版：連結寫成「文字：網址」，mailto 只留信箱，表格每列一行")
    void plainTextLinks() {
        MailTemplate t = TEMPLATES.get(MailTemplateId.CLAIM_CONFIRMED);
        Map<String, Object> v = sampleValues(t);
        v.put("claimUrl", "https://example.org/me/claims/1?a=1&b=2");
        MailTemplate.Rendered mail = t.render(v);
        assertThat(mail.html()).contains("https://example.org/me/claims/1?a=1&amp;b=2");
        assertThat(mail.text()).contains("https://example.org/me/claims/1?a=1&b=2");

        MailTemplate r = TEMPLATES.get(MailTemplateId.CLAIM_RECALLED);
        MailTemplate.Rendered recalled = r.render(sampleValues(r));
        assertThat(recalled.html()).contains("mailto:onlinesanta2026@gmail.com");
        assertThat(recalled.text()).contains("onlinesanta2026@gmail.com").doesNotContain("mailto:");

        MailTemplate d = TEMPLATES.get(MailTemplateId.ADMIN_DIGEST);
        String text = d.render(sampleValues(d)).text();
        assertThat(text).contains("機構參與申請：https://example.org/orgQueueUrl");
        // 表格每一列自成一行，不黏在一起
        assertThat(text.lines().filter(l -> l.contains("機構參與申請")).count()).isEqualTo(1);
        assertThat(text.lines().filter(l -> l.contains("機構參與申請") && l.contains("願望送審"))).isEmpty();
    }

    @Test
    @DisplayName("恢復寄送信：期限有效時只寫日期；期限已過時不寫日期，改顯示請到認領頁確認")
    void resumeTemplatesHandleAPassedDeadline() {
        for (MailTemplateId id : new MailTemplateId[] {
                MailTemplateId.WISH_RESUMED, MailTemplateId.ORG_REINSTATED_CLAIM_RESUMED}) {
            MailTemplate t = TEMPLATES.get(id);

            Map<String, Object> valid = sampleValues(t);
            valid.put("shipDeadline", "2026/12/25 18:00");
            valid.put("deadlinePassed", false);
            MailTemplate.Rendered ok = t.render(valid);
            assertThat(ok.text()).contains("2026/12/25 18:00").doesNotContain("已經過了");

            Map<String, Object> passed = sampleValues(t);
            passed.put("shipDeadline", null);
            passed.put("deadlinePassed", true);
            MailTemplate.Rendered late = t.render(passed);
            assertThat(late.text()).doesNotContain("新的最晚寄出時間").contains("已經過了")
                    .contains("onlinesanta2026@gmail.com");
            assertThat(late.html()).doesNotContain("data-if");
        }
    }

    @Test
    @DisplayName("缺欄位是程式錯誤：丟例外，不會寄出殘留 {{ }} 的信")
    void missingFieldFails() {
        MailTemplate t = TEMPLATES.get(MailTemplateId.CLAIM_CONFIRMED);
        Map<String, Object> v = sampleValues(t);
        v.remove("claimUrl");
        assertThatThrownBy(() -> t.render(v)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimUrl");
    }

    @Test
    @DisplayName("樣板缺 <template> 區塊時載入就失敗（啟動檢查的依據）")
    void malformedTemplateRejected() {
        assertThatThrownBy(() -> MailTemplate.parse("x", "<html></html>"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("email-subject");
        assertThatThrownBy(() -> MailTemplate.parse("x",
                "<template id=\"email-subject\">s</template>"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("email-body");
    }

    @Test
    @DisplayName("sendTemplate 寄出 multipart/alternative：純文字在前、HTML 在後，主旨帶前綴")
    void sendsMultipartAlternative() throws Exception {
        RecordingMailSender sender = new RecordingMailSender();
        NotificationProperties props = new NotificationProperties("smtp.example.org", 587, "u", "p",
                "from@example.org", "https://example.org", 0, "[測試站]");
        NotificationService service = new NotificationService(providerOf(sender), props);

        MailTemplate t = TEMPLATES.get(MailTemplateId.CLAIM_CONFIRMED);
        service.sendTemplateNow("donor@example.org", MailTemplateId.CLAIM_CONFIRMED, sampleValues(t));

        assertThat(sender.sentMails()).hasSize(1);
        RecordingMailSender.SentMail mail = sender.sentMails().get(0);
        assertThat(mail.message().getTo()).containsExactly("donor@example.org");
        assertThat(mail.message().getSubject()).startsWith("[測試站] 【線上聖誕老人 2026】");
        assertThat(mail.message().getText()).contains("Best wishes,");
        assertThat(mail.html()).contains("<strong").contains("Best wishes,<br>線上聖誕老人團隊");
        assertThat(mail.mime().getContentType()).startsWith("multipart/");
        assertThat(mail.mime().getContent()).isInstanceOf(jakarta.mail.Multipart.class);
    }

    @Test
    @DisplayName("樣板欄位缺漏時 sendTemplate 不寄也不丟例外（best-effort）")
    void sendTemplateSwallowsRenderErrors() {
        RecordingMailSender sender = new RecordingMailSender();
        NotificationProperties props = new NotificationProperties("smtp.example.org", 587, "u", "p",
                "from@example.org", "https://example.org");
        NotificationService service = new NotificationService(providerOf(sender), props);
        service.sendTemplateNow("a@example.org", MailTemplateId.CLAIM_CONFIRMED, Map.of());
        assertThat(sender.sentMails()).isEmpty();
    }

    private static ObjectProvider<JavaMailSender> providerOf(JavaMailSender sender) {
        return new ObjectProvider<>() {
            @Override
            public JavaMailSender getObject() {
                return sender;
            }

            @Override
            public JavaMailSender getObject(Object... args) {
                return sender;
            }

            @Override
            public JavaMailSender getIfAvailable() {
                return sender;
            }

            @Override
            public JavaMailSender getIfUnique() {
                return sender;
            }
        };
    }
}
