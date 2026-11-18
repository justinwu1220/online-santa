package com.onlinesanta.notification;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 通知信樣板的載入處。啟動時一次讀完 {@link MailTemplateId} 列的所有樣板並檢查格式，
 * 缺檔或缺 {@code <template>} 區塊就讓應用程式<strong>啟動失敗</strong>——
 * 樣板壞了要在部署時就發現，不要等到第一個使用者觸發那封信。
 */
@Component
public class MailTemplates {

    private final Map<MailTemplateId, MailTemplate> templates = new EnumMap<>(MailTemplateId.class);

    public MailTemplates() {
        for (MailTemplateId id : MailTemplateId.values()) {
            templates.put(id, MailTemplate.parse(id.name(), read(id)));
        }
    }

    public MailTemplate get(MailTemplateId id) {
        return templates.get(id);
    }

    public MailTemplate.Rendered render(MailTemplateId id, Map<String, ?> values) {
        return templates.get(id).render(values);
    }

    private static String read(MailTemplateId id) {
        try (InputStream in = MailTemplates.class.getClassLoader().getResourceAsStream(id.resourcePath())) {
            if (in == null) {
                throw new IllegalStateException("找不到通知信樣板檔：" + id.resourcePath()
                        + "（原檔名 " + id.sourceName() + "）");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
