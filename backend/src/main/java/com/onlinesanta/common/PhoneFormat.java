package com.onlinesanta.common;

import java.util.Optional;
import java.util.Set;

/**
 * 電話格式（1018 新版設計稿 SPEC 13.4）。資料庫一律存加了 dash 的格式。
 *
 * <ul>
 *   <li>行動電話：09 開頭 10 碼，{@code 09XX-XXX-XXX}。</li>
 *   <li>市話（選填）：區碼 02 → {@code 02-XXXX-XXXX}；0826、0836 → 4 碼區碼；037、049、082、089 → 3 碼區碼；
 *       其他 03–08 → 2 碼區碼；區碼之後的號碼 6 碼以上時切成「前段-後 4 碼」，例如
 *       {@code 04-2234-5678}、{@code 049-12-3456}、{@code 03-123-4567}。最多 10 碼，不支援分機。</li>
 * </ul>
 *
 * 輸入可以帶空白、dash、括號（貼上「(04) 2234 5678」之類也行），其他字元一律視為不合法。
 */
public final class PhoneFormat {

    private static final Set<String> FOUR_DIGIT_AREA = Set.of("0826", "0836");
    private static final Set<String> THREE_DIGIT_AREA = Set.of("037", "049", "082", "089");

    private PhoneFormat() {
    }

    /** @return 格式化後的行動電話；不合法回 empty。 */
    public static Optional<String> mobile(String input) {
        return digitsOf(input)
                .filter(d -> d.length() == 10 && d.startsWith("09"))
                .map(d -> d.substring(0, 4) + "-" + d.substring(4, 7) + "-" + d.substring(7));
    }

    /** @return 格式化後的市話；不合法回 empty。 */
    public static Optional<String> landline(String input) {
        return digitsOf(input).flatMap(PhoneFormat::formatLandline);
    }

    private static Optional<String> formatLandline(String d) {
        if (d.length() > 10 || d.length() < 8 || d.charAt(0) != '0' || d.charAt(1) < '2' || d.charAt(1) > '8') {
            return Optional.empty();
        }
        int areaLength = areaLength(d);
        String area = d.substring(0, areaLength);
        String number = d.substring(areaLength);
        // 區碼之後 6–8 碼才算完整
        if (number.length() < 6 || number.length() > 8) {
            return Optional.empty();
        }
        String head = number.substring(0, number.length() - 4);
        String tail = number.substring(number.length() - 4);
        return Optional.of(area + "-" + head + "-" + tail);
    }

    private static int areaLength(String digits) {
        if (digits.startsWith("02")) {
            return 2;
        }
        if (digits.length() >= 4 && FOUR_DIGIT_AREA.contains(digits.substring(0, 4))) {
            return 4;
        }
        if (digits.length() >= 3 && THREE_DIGIT_AREA.contains(digits.substring(0, 3))) {
            return 3;
        }
        return 2;
    }

    /** 只留數字；含有數字、空白、dash、括號以外的字元就不合法。 */
    private static Optional<String> digitsOf(String input) {
        if (input == null) {
            return Optional.empty();
        }
        StringBuilder digits = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c) && c < 128) {
                digits.append(c);
            } else if (c != ' ' && c != '-' && c != '(' && c != ')') {
                return Optional.empty();
            }
        }
        return digits.isEmpty() ? Optional.empty() : Optional.of(digits.toString());
    }
}
