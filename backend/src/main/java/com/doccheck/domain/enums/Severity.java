package com.doccheck.domain.enums;

public final class Severity {

    public static final String HIGH = "high";
    public static final String MIDDLE = "middle";
    public static final String LOW = "low";

    private Severity() {
    }

    /**
     * 兼容模型可能返回的中文/大小写值，统一为 high/middle/low。
     */
    public static String normalize(String value) {
        if (value == null) {
            return MIDDLE;
        }
        String v = value.trim().toLowerCase();
        return switch (v) {
            case "high", "高", "严重", "critical" -> HIGH;
            case "low", "低", "轻微", "minor" -> LOW;
            default -> MIDDLE;
        };
    }

    public static int rank(String severity) {
        return switch (normalize(severity)) {
            case HIGH -> 0;
            case MIDDLE -> 1;
            default -> 2;
        };
    }
}
