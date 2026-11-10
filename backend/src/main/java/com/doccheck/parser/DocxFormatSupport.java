package com.doccheck.parser;

import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** DocxParser 的纯格式换算工具，便于单测和复用。 */
public final class DocxFormatSupport {

    private DocxFormatSupport() {
    }

    public static <T> T first(List<T> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /** XMLBeans 的 union 类型 getter 常返回 Object，统一转 double。 */
    public static double asDouble(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return new BigDecimal(value.toString()).doubleValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double twipsToPt(java.math.BigInteger value) {
        return value == null ? 0 : value.doubleValue() / 20.0;
    }

    public static double twipsToPt(int value) {
        return value / 20.0;
    }

    /** 兼容 CTSignedTwipsMeasure / CTTwipsMeasure 等返回 Object 的字段。 */
    public static double twipsToPt(Object value) {
        return asDouble(value) / 20.0;
    }

    /** half-point（w:sz / w:position）转 pt。 */
    public static double halfPointsToPt(Object value) {
        return asDouble(value) / 2.0;
    }

    public static double emuToPt(long value) {
        return value / 12700.0;
    }

    /**
     * XmlBeans 对 STHexColor 的 getFill() 返回 byte[]，直接 String.valueOf 会得到 [B@xxx。
     * 统一转成大写十六进制颜色；auto/空值返回 null。
     */
    public static String colorHex(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) {
            return bytes.length == 0 ? null : HexFormat.of().withUpperCase().formatHex(bytes);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "auto".equalsIgnoreCase(text)) return null;
        return text.toUpperCase(Locale.ROOT);
    }

    public static boolean hasCjkPunctuation(String text) {
        String punctuation = "，。；：！？、（）《》“”‘’【】…—";
        for (int i = 0; i < text.length(); i++) {
            if (punctuation.indexOf(text.charAt(i)) >= 0) return true;
        }
        return false;
    }

    public static boolean isFullWidth(int cp) {
        return Character.isIdeographic(cp)
                || (cp >= 0x3000 && cp <= 0x303F)
                || (cp >= 0xFF00 && cp <= 0xFFEF);
    }
}
