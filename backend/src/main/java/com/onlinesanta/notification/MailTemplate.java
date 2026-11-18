package com.onlinesanta.notification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一份通知信樣板（主旨 + HTML 內文），可以填入欄位值產生要寄的信。
 *
 * <p>樣板格式沿用設計端交付的原檔：
 * <ul>
 *   <li>{@code <template id="email-subject">} 主旨、{@code <template id="email-body">} 內文；</li>
 *   <li>{@code {{欄位}}} 代換成值；內文會做 HTML 跳脫，換行轉成 {@code <br>}；主旨不跳脫、換行壓成空白；</li>
 *   <li>{@code data-if="欄位"}（只出現在 {@code <p>}、{@code <span>}）：欄位是 null、空白或 false
 *       時整個元素連同內容拿掉，否則只移除這個屬性。</li>
 * </ul>
 *
 * <p>缺欄位（樣板用到、呼叫端沒給）一律丟 {@link IllegalArgumentException}：這是程式寫錯，
 * 不是資料問題，寧可在測試與啟動檢查就爆出來，也不要寄出一封還留著 {@code {{...}}} 的信。
 * 欄位值給 null 視為空字串（由呼叫端負責決定要不要放替代文字）。
 */
public final class MailTemplate {

    /** 渲染結果。{@code html} 是完整文件，{@code text} 是同內容的純文字版。 */
    public record Rendered(String subject, String html, String text) {
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");
    private static final Pattern SUBJECT = Pattern.compile(
            "<template id=\"email-subject\">(.*?)</template>", Pattern.DOTALL);
    private static final Pattern BODY = Pattern.compile(
            "<template id=\"email-body\">(.*?)</template>", Pattern.DOTALL);
    private static final Pattern DATA_IF_OPEN = Pattern.compile(
            "<(p|span)\\b[^>]*?\\sdata-if=\"(\\w+)\"[^>]*>");
    private static final Pattern DATA_IF_ATTR = Pattern.compile("\\sdata-if=\"\\w+\"");

    private final String name;
    private final String subjectTemplate;
    private final String bodyTemplate;

    private MailTemplate(String name, String subjectTemplate, String bodyTemplate) {
        this.name = name;
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = bodyTemplate;
    }

    /** 從樣板原檔（完整 HTML 文字）取出主旨與內文。缺任一個就丟例外。 */
    public static MailTemplate parse(String name, String source) {
        Matcher s = SUBJECT.matcher(source);
        Matcher b = BODY.matcher(source);
        if (!s.find()) {
            throw new IllegalStateException("樣板 " + name + " 缺少 <template id=\"email-subject\">");
        }
        if (!b.find()) {
            throw new IllegalStateException("樣板 " + name + " 缺少 <template id=\"email-body\">");
        }
        MailTemplate template = new MailTemplate(name, s.group(1).strip(), b.group(1).strip());
        template.checkWellFormed();
        return template;
    }

    public String name() {
        return name;
    }

    /** 樣板用到的所有欄位名稱（含 data-if 條件欄位），供測試與啟動檢查使用。 */
    public Set<String> fields() {
        Set<String> fields = new LinkedHashSet<>();
        for (String t : List.of(subjectTemplate, bodyTemplate)) {
            Matcher m = PLACEHOLDER.matcher(t);
            while (m.find()) {
                fields.add(m.group(1));
            }
        }
        Matcher m = DATA_IF_OPEN.matcher(bodyTemplate);
        while (m.find()) {
            fields.add(m.group(2));
        }
        return fields;
    }

    public Rendered render(Map<String, ?> values) {
        String subject = substitute(subjectTemplate, values, false)
                .replaceAll("\\s*[\\r\\n]+\\s*", " ").strip();
        String body = substitute(applyConditions(bodyTemplate, values), values, true);
        String html = "<!doctype html>\n<html lang=\"zh-Hant-TW\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + escape(subject) + "</title></head><body>\n"
                + body + "\n</body></html>";
        return new Rendered(subject, html, MailText.fromHtml(body));
    }

    // ── 條件段落 ────────────────────────────────────────────────

    private String applyConditions(String html, Map<String, ?> values) {
        StringBuilder out = new StringBuilder();
        int pos = 0;
        Matcher open = DATA_IF_OPEN.matcher(html);
        while (open.find(pos)) {
            out.append(html, pos, open.start());
            String tag = open.group(1);
            String field = open.group(2);
            int end = elementEnd(html, tag, open.end());
            if (end < 0) {
                throw new IllegalStateException("樣板 " + name + " 的 <" + tag + " data-if=\"" + field
                        + "\"> 找不到結尾標籤");
            }
            if (!values.containsKey(field)) {
                throw new IllegalArgumentException("樣板 " + name + " 需要欄位 " + field);
            }
            if (truthy(values.get(field))) {
                String element = html.substring(open.start(), end);
                out.append(DATA_IF_ATTR.matcher(element).replaceFirst(""));
            }
            pos = end;
        }
        out.append(html, pos, html.length());
        return out.toString();
    }

    /** 從開頭標籤之後往下找同名元素的結尾（處理同名標籤巢狀），回傳結尾標籤之後的位置。 */
    private static int elementEnd(String html, String tag, int from) {
        Pattern p = Pattern.compile("<(/?)" + tag + "\\b[^>]*>");
        Matcher m = p.matcher(html);
        int depth = 1;
        int pos = from;
        while (m.find(pos)) {
            depth += m.group(1).isEmpty() ? 1 : -1;
            pos = m.end();
            if (depth == 0) {
                return pos;
            }
        }
        return -1;
    }

    private static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return !v.toString().isBlank();
    }

    // ── 欄位代換 ────────────────────────────────────────────────

    private String substitute(String template, Map<String, ?> values, boolean html) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        int pos = 0;
        while (m.find()) {
            String field = m.group(1);
            if (!values.containsKey(field)) {
                throw new IllegalArgumentException("樣板 " + name + " 需要欄位 " + field);
            }
            Object v = values.get(field);
            String value = v == null ? "" : v.toString();
            out.append(template, pos, m.start());
            if (!html) {
                out.append(value);
            } else if (insideTag(template, m.start())) {
                out.append(escape(value));                 // 屬性值（如 href）：只跳脫，不轉 <br>
            } else {
                out.append(escape(value).replaceAll("\\r?\\n", "<br>"));
            }
            pos = m.end();
        }
        out.append(template, pos, template.length());
        return out.toString();
    }

    private static boolean insideTag(String s, int index) {
        return s.lastIndexOf('<', index) > s.lastIndexOf('>', index);
    }

    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private void checkWellFormed() {
        // data-if 元素必須成對；只在載入時檢查一次，避免寄信當下才發現樣板壞了
        Matcher open = DATA_IF_OPEN.matcher(bodyTemplate);
        List<String> problems = new ArrayList<>();
        while (open.find()) {
            if (elementEnd(bodyTemplate, open.group(1), open.end()) < 0) {
                problems.add(open.group(2));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("樣板 " + name + " 的條件段落沒有結尾標籤：" + problems);
        }
    }
}
