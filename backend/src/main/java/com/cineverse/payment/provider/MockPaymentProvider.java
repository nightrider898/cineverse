package com.cineverse.payment.provider;

import com.cineverse.config.AppProperties;
import com.cineverse.dto.PaymentDtos.PaymentAction;
import com.cineverse.entity.Booking;
import com.cineverse.entity.Payment;
import com.cineverse.entity.Refund;
import com.cineverse.entity.enums.PaymentMethod;
import com.cineverse.entity.enums.PaymentProviderType;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.payment.PaymentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Development gateway. Orders are "paid" from the frontend payment simulator (/dev/payment-simulator),
 * which asks the backend to emit a mock webhook. The webhook takes the same idempotent path as real
 * gateways, so duplicate deliveries can be exercised end to end. Disabled unless PAYMENTS_MOCK_ENABLED.
 */
@Component
@RequiredArgsConstructor
public class MockPaymentProvider implements PaymentProvider {
    public static final String SIGNATURE_HEADER = "x-mock-signature";

    private final AppProperties props;
    private final ObjectMapper objectMapper;

    @Override public PaymentProviderType type() { return PaymentProviderType.MOCK; }

    @Override public boolean isEnabled() { return props.payments().mockEnabled(); }

    @Override
    public Set<PaymentMethod> supportedMethods() {
        return EnumSet.of(PaymentMethod.CARD, PaymentMethod.UPI, PaymentMethod.GPAY, PaymentMethod.PHONEPE, PaymentMethod.NETBANKING, PaymentMethod.WALLET);
    }

    @Override
    public OrderResult createOrder(Payment payment, Booking booking) {
        String orderId = "mock_order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String url = props.frontendUrl() + "/dev/payment-simulator?paymentId=" + payment.getPublicId();
        return new OrderResult(orderId, new PaymentAction("MOCK_SIMULATOR", null, null, orderId, null, null, url), Map.of("orderId", orderId));
    }

    @Override
    public StatusResult fetchStatus(Payment payment) {
        // The mock gateway has no external state: whatever the simulator reported is already applied.
        return new StatusResult(Outcome.PENDING, payment.getProviderPaymentId(), null);
    }

    @Override
    public RefundResult refund(Payment payment, Refund refund) {
        return new RefundResult(Outcome.SUCCESS, "mock_rfnd_" + UUID.randomUUID().toString().substring(0, 12), null);
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
        if (!isEnabled()) throw new ApiException(ErrorCode.NOT_FOUND);
        String expected = signature(rawBody);
        if (!expected.equals(headers.get(SIGNATURE_HEADER))) {
            throw new ApiException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        try {
            JsonNode n = objectMapper.readTree(rawBody);
            Outcome outcome = Outcome.valueOf(n.path("outcome").asText());
            return new WebhookEvent(n.path("eventId").asText(), "mock.payment." + outcome.name().toLowerCase(),
                    n.path("orderId").asText(), n.path("paymentRef").asText(null), outcome,
                    outcome == Outcome.SUCCESS ? null : "Simulated " + outcome.name().toLowerCase(), false, null);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Malformed mock webhook");
        }
    }

    /** HMAC with the JWT secret so the mock webhook is not trivially forgeable even in shared dev environments. */
    public String signature(String rawBody) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(props.jwt().secret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
