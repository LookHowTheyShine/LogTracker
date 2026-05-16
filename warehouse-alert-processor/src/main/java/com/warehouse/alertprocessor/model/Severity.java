package com.warehouse.alertprocessor.model;

public enum Severity {
    CRITICAL, HIGH, MEDIUM, LOW, UNKNOWN;

    public static Severity from(String value) {
        if (value == null) return UNKNOWN;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
