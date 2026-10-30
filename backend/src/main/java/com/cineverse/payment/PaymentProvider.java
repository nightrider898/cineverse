package com.cineverse.payment;

import com.cineverse.dto.PaymentDtos.PaymentAction;
import com.cineverse.entity.Booking;
import com.cineverse.entity.Payment;
import com.cineverse.entity.Refund;
import com.cineverse.entity.enums.PaymentMethod;
import com.cineverse.entity.enums.PaymentProviderType;

import java.util.Map;
import java.util.Set;

/**
 * Gateway abstraction. Implementations talk to exactly one gateway and never let secrets leave the backend:
 * {@link OrderResult#action()} carries only what the browser needs (client secret, order id, redirect URL).
 */
public interface PaymentProvider {

    enum Outcome { SUCCESS, FAILED, CANCELLED, PENDING }

    record OrderResult(String providerOrderId, PaymentAction action, Map<String, Object> raw) {}

    record StatusResult(Outcome outcome, String providerPaymentId, String failureReason) {}

    record RefundResult(Outcome outcome, String providerRefundId, String failureReason) {}

    /** A verified, provider-neutral view of an inbound webhook. */
    record WebhookEvent(String eventId, String eventType, String providerOrderId, String providerPaymentId,
                        Outcome outcome, String failureReason, boolean refundEvent, String providerRefundId) {}

    PaymentProviderType type();

    boolean isEnabled();

    Set<PaymentMethod> supportedMethods();

    OrderResult createOrder(Payment payment, Booking booking);

    StatusResult fetchStatus(Payment payment);

    RefundResult refund(Payment payment, Refund refund);

    default RefundResult refundStatus(Payment payment, Refund refund) {
        return new RefundResult(Outcome.PENDING, refund.getProviderRefundId(), null);
    }

    /** Verifies the signature and parses the payload. Throws WEBHOOK_SIGNATURE_INVALID on failure. */
    WebhookEvent parseWebhook(String rawBody, Map<String, String> headers);

    static long toMinorUnits(java.math.BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }
}
