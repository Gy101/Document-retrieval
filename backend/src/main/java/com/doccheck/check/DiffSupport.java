package com.doccheck.check;

import com.doccheck.domain.model.FormatDiff;

/** 策略生成差异的公共工厂，统一字段格式，减少各策略重复代码。 */
public final class DiffSupport {

    private DiffSupport() {
    }

    public static FormatDiff of(String diffType, String severity, String elementType, String anchorPath,
                                String location, String templateFormat, String actualFormat,
                                String contentSnippet, String suggestion) {
        return FormatDiff.builder()
                .diffType(diffType)
                .severity(severity)
                .elementType(elementType)
                .anchorPath(anchorPath)
                .location(location)
                .templateFormat(nullToDash(templateFormat))
                .actualFormat(nullToDash(actualFormat))
                .contentSnippet(abbreviate(contentSnippet, 200))
                .suggestion(suggestion)
                .build();
    }

    public static String anchorOfParagraph(int index) {
        return "body.p[" + index + "]";
    }

    public static String anchorOfTable(int index) {
        return "body.table[" + index + "]";
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "未设置" : value;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "…";
    }
}
