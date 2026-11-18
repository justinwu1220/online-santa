package com.onlinesanta.notification;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把已填值的 HTML 內文轉成純文字版（multipart 的 text/plain 部分）。
 *
 * <p>只處理樣板用到的那一小撮標籤：段落、換行、連結、表格。連結寫成「文字：網址」，
 * 純文字客戶端也點得到；其餘標籤直接丟掉，字元實體還原。
 */
final class MailText {

    private static final Pattern LINK = Pattern.compile(
            "<a\\b[^>]*?href=\"([^\"]*)\"[^>]*>(.*?)</a>", Pattern.DOTALL);

    private MailText() {
    }

    static String fromHtml(String html) {
        String s = html.replaceAll("(?s)<style.*?</style>", "");
        s = LINK.matcher(s).replaceAll(r -> {
            String label = stripTags(r.group(2)).strip();
            String url = unescape(r.group(1));
            if (url.startsWith("mailto:")) {
                url = url.substring("mailto:".length());
            }
            // 文字就是網址本身（例如 mailto 的信箱）時不重複
            String out = label.equals(url) || label.isEmpty() ? url : label + "：" + url;
            return Matcher.quoteReplacement(out);
        });
        s = s.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|tr|thead|tbody|table)>", "\n\n")
                .replaceAll("(?i)</(td|th)>", "　");
        s = unescape(stripTags(s));
        s = s.replaceAll("[ \\t　]+\\n", "\n")        // 行尾空白
                .replaceAll("\\n[ \\t]+", "\n")           // 行首縮排（樣板排版用）
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
        return s + "\n";
    }

    private static String stripTags(String s) {
        return s.replaceAll("(?s)<[^>]+>", "");
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&");
    }
}
