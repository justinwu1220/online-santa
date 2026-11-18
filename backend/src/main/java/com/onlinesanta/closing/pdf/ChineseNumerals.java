package com.onlinesanta.closing.pdf;

/** 收據中文大寫金額。 */
public final class ChineseNumerals {

    private static final char[] DIGITS = {'零', '壹', '貳', '參', '肆', '伍', '陸', '柒', '捌', '玖'};

    /** 收據大寫欄的格數：佰拾萬仟佰拾元，共 7 位，最大 9,999,999。 */
    public static final int SLOTS = 7;

    private ChineseNumerals() {
    }

    /**
     * 把金額拆成 {@link #SLOTS} 格的大寫數字，由高位到低位（百萬、十萬、萬、仟、佰、拾、元）。
     * 每一格都填字，高位沒有數字的格填「零」（防止事後在前面加數字），與收據預印的「佰拾萬仟佰拾元整」對應。
     *
     * @throws IllegalArgumentException 金額為負或超過 9,999,999（放不進這張收據）
     */
    public static String[] slots(int amount) {
        if (amount < 0 || amount > 9_999_999) {
            throw new IllegalArgumentException("金額超出收據可填寫的範圍（0 到 9,999,999 元）：" + amount);
        }
        String[] out = new String[SLOTS];
        int rest = amount;
        for (int i = SLOTS - 1; i >= 0; i--) {
            out[i] = String.valueOf(DIGITS[rest % 10]);
            rest /= 10;
        }
        return out;
    }

    /** 大寫金額的完整讀法，例如 1200 → 壹仟貳佰元整；0 → 零元整。供檢查報表與文字摘要用。 */
    public static String spoken(int amount) {
        if (amount < 0 || amount > 9_999_999) {
            throw new IllegalArgumentException("金額超出範圍：" + amount);
        }
        if (amount == 0) {
            return "零元整";
        }
        String[] units = {"", "拾", "佰", "仟"};
        StringBuilder sb = new StringBuilder();
        int wan = amount / 10_000;
        int rest = amount % 10_000;
        if (wan > 0) {
            sb.append(group(wan, units)).append("萬");
        }
        if (rest > 0) {
            if (wan > 0 && rest < 1000) {
                sb.append('零');
            }
            sb.append(group(rest, units));
        }
        return sb.append("元整").toString();
    }

    private static String group(int value, String[] units) {
        StringBuilder sb = new StringBuilder();
        boolean pendingZero = false;
        for (int pos = 3; pos >= 0; pos--) {
            int d = (value / (int) Math.pow(10, pos)) % 10;
            if (d == 0) {
                pendingZero = sb.length() > 0;
            } else {
                if (pendingZero) {
                    sb.append('零');
                    pendingZero = false;
                }
                sb.append(DIGITS[d]).append(units[pos]);
            }
        }
        return sb.toString();
    }
}
