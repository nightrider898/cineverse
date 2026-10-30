package com.cineverse.entity.enums;

public enum PaymentStatus {
    INITIATED, PENDING, AUTHORIZED, SUCCESS, FAILED, CANCELLED, REFUNDED, PARTIALLY_REFUNDED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED || this == REFUNDED || this == PARTIALLY_REFUNDED;
    }
}
