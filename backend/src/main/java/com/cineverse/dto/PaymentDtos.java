package com.cineverse.dto;

import com.cineverse.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentMethodOption(String method, String label, String description, String provider, boolean available) {}

    public record PaymentMethodsResponse(List<PaymentMethodOption> methods, String stripePublishableKey, boolean sandbox) {}

    public record InitiatePaymentRequest(@NotNull Long bookingId, @NotNull PaymentMethod method) {}

    /**
     * Tells the frontend what to do next. Only public identifiers leave the backend
     * (client secret / order id / publishable key id); secrets never do.
     */
    public record PaymentAction(String type, String clientSecret, String publishableKey, String orderId, String keyId,
                                String redirectUrl, String simulatorUrl) {}

    public record PaymentInitResponse(UUID paymentId, Long bookingId, String status, String provider, String method,
                                      BigDecimal amount, String currency, PaymentAction action) {}

    public record PaymentStatusResponse(UUID paymentId, Long bookingId, String bookingReference, String status,
                                        String bookingStatus, String failureReason, String provider, String method,
                                        BigDecimal amount, Instant updatedAt) {}

    public record RazorpayVerifyRequest(@NotNull UUID paymentId, @NotBlank String razorpayOrderId,
                                        @NotBlank String razorpayPaymentId, @NotBlank String razorpaySignature) {}

    public record MockOutcomeRequest(@NotBlank @Pattern(regexp = "SUCCESS|FAILED|CANCELLED") String outcome) {}

    public record PaymentAdminResponse(UUID paymentId, Long bookingId, String bookingReference, Long userId,
                                       String provider, String method, String status, BigDecimal amount,
                                       BigDecimal refundedAmount, String providerOrderId, String providerPaymentId,
                                       String failureReason, Instant createdAt, Instant completedAt) {}
}
