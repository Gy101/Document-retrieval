package com.doccheck.domain.enums;

public enum TemplateScope {
    PUBLIC,
    PERSONAL;

    public static boolean isValid(String scope) {
        return PUBLIC.name().equalsIgnoreCase(scope) || PERSONAL.name().equalsIgnoreCase(scope);
    }
}
