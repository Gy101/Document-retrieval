package com.doccheck.domain.enums;

public enum TaskStatus {
    QUEUED("排队中"),
    RUNNING("处理中"),
    SUCCESS("校验成功"),
    FAILED("校验失败"),
    DEAD("已终止");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static boolean isTerminal(String status) {
        return SUCCESS.name().equals(status) || FAILED.name().equals(status) || DEAD.name().equals(status);
    }

    public static TaskStatus parse(String value) {
        for (TaskStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知任务状态: " + value);
    }
}
