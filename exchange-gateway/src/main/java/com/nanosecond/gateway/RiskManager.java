package com.nanosecond.gateway;

public class RiskManager {
    private static final long MAX_QTY = 1000;
    private static final double MAX_NOTIONAL = 1_000_000.0;

    public enum ValidationResult {
        VALID,
        INVALID_PRICE,
        INVALID_QTY,
        FAT_FINGER_QTY,
        MAX_NOTIONAL_EXCEEDED
    }

    public ValidationResult validate(double price, long qty) {
        if (price <= 0) {
            return ValidationResult.INVALID_PRICE;
        }
        if (qty <= 0) {
            return ValidationResult.INVALID_QTY;
        }
        if (qty > MAX_QTY) {
            return ValidationResult.FAT_FINGER_QTY;
        }
        if (price * qty > MAX_NOTIONAL) {
            return ValidationResult.MAX_NOTIONAL_EXCEEDED;
        }
        return ValidationResult.VALID;
    }
}
