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
import com.cineverse.payment.HmacUtil;
import com.cineverse.payment.PaymentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Razorpay Orders API. Covers cards, UPI (including Google Pay / PhonePe apps via UPI intent and collect),
 * net banking and wallets through Razorpay Checkout. The frontend opens Checkout with the public key id and
 * order id, then posts the handler response to /payments/razorpay/verify, where the signature is checked:
 * HMAC_SHA256(order_id + "|" + payment_id, key_secret).
 */
@Slf4j
@Component
public class RazorpayPaymentProvider implements PaymentProvider {
    private final AppProperties.Payments.Razorpay config;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public RazorpayPaymentProvider(AppProperties props, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.config = props.payments().razorpay();
        this.objectMapper = objectMapper;
        this.http = builder.clone().baseUrl(config.apiBase() == null ? "https://api.razorpay.com" : config.apiBase()).build();
    }

    @Override public PaymentProviderType type() { return PaymentProviderType.RAZORPAY; }

    @Override public boolean isEnabled() { return config.configured(); }

    @Override
    public Set<PaymentMethod> supportedMethods() {
        return EnumSet.of(PaymentMethod.CARD, PaymentMethod.UPI, PaymentMethod.GPAY, PaymentMethod.PHONEPE, PaymentMethod.NETBANKING, PaymentMethod.WALLET);
    }

    @Override
    public OrderResult createOrder(Payment payment, Booking booking) {
        Map<String, Object> body = Map.of(
                "amount", PaymentProvider.toMinorUnits(payment.getAmount()),
                "currency", payment.getCurrency(),
                "receipt", booking.getReference(),
                "notes", Map.of("paymentId", payment.getPublicId().toString(), "bookingReference", booking.getReference()));
        JsonNode order = call(() -> http.post().uri("/v1/orders").header(HttpHeaders.AUTHORIZATION, basicAuth())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));
        String orderId = order.path("id").asText();
        return new OrderResult(orderId, new PaymentAction("RAZORPAY_CHECKOUT", null, null, orderId, config.keyId(), null, null),
                Map.of("status", order.path("status").asText()));
    }

    /** True when the Checkout handler's signature proves the payment belongs to our order. */
    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        return HmacUtil.safeEquals(HmacUtil.hmacSha256Hex(config.keySecret(), orderId + "|" + paymentId), signature);
    }

    @Override
    public StatusResult fetchStatus(Payment payment) {
        JsonNode payments = call(() -> http.get().uri("/v1/orders/{id}/payments", payment.getProviderOrderId())
                .header(HttpHeaders.AUTHORIZATION, basicAuth()).retrieve().body(String.class));
        boolean anyFailed = false;
        String failure = null;
        for (JsonNode p : payments.path("items")) {
            String status = p.path("status").asText();
            if ("captured".equals(status)) return new StatusResult(Outcome.SUCCESS, p.path("id").asText(), null);
            if ("failed".equals(status)) { anyFailed = true; failure = p.path("error_description").asText(null); }
        }
        // A failed attempt does not end the order: the customer may retry within Checkout.
        return new StatusResult(Outcome.PENDING, null, anyFailed ? failure : null);
    }

    @Override
    public RefundResult refund(Payment payment, Refund refund) {
        Map<String, Object> body = Map.of("amount", PaymentProvider.toMinorUnits(refund.getAmount()),
                "receipt", "refund-" + refund.getId(), "notes", Map.of("bookingReference", refund.getBooking().getReference()));
        JsonNode r = call(() -> http.post().uri("/v1/payments/{id}/refund", payment.getProviderPaymentId())
                .header(HttpHeaders.AUTHORIZATION, basicAuth()).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(String.class));
        return new RefundResult(mapRefund(r.path("status").asText()), r.path("id").asText(), null);
    }

    @Override
    public RefundResult refundStatus(Payment payment, Refund refund) {
        JsonNode r = call(() -> http.get().uri("/v1/refunds/{id}", refund.getProviderRefundId())
                .header(HttpHeaders.AUTHORIZATION, basicAuth()).retrieve().body(String.class));
        return new RefundResult(mapRefund(r.path("status").asText()), r.path("id").asText(), null);
    }

    /** X-Razorpay-Signature = HMAC_SHA256(raw body, webhook secret). Event id comes from X-Razorpay-Event-Id. */
    @Override
    public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
        String secret = config.webhookSecret();
        if (secret == null || secret.isBlank() || !HmacUtil.safeEquals(HmacUtil.hmacSha256Hex(secret, rawBody), headers.get("x-razorpay-signature"))) {
            throw new ApiException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        try {
            JsonNode n = objectMapper.readTree(rawBody);
            String type = n.path("event").asText();
            String eventId = headers.getOrDefault("x-razorpay-event-id", HmacUtil.sha256Hex(rawBody));
            JsonNode payment = n.path("payload").path("payment").path("entity");
            return switch (type) {
                case "payment.captured", "order.paid" -> new WebhookEvent(eventId, type, payment.path("order_id").asText(),
                        payment.path("id").asText(), Outcome.SUCCESS, null, false, null);
                case "payment.failed" -> new WebhookEvent(eventId, type, payment.path("order_id").asText(), payment.path("id").asText(),
                        Outcome.PENDING, payment.path("error_description").asText(null), false, null);
                case "refund.processed", "refund.failed" -> {
                    JsonNode refund = n.path("payload").path("refund").path("entity");
                    yield new WebhookEvent(eventId, type, null, refund.path("payment_id").asText(),
                            "refund.processed".equals(type) ? Outcome.SUCCESS : Outcome.FAILED, null, true, refund.path("id").asText());
                }
                default -> new WebhookEvent(eventId, type, null, null, Outcome.PENDING, null, false, null);
            };
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Malformed Razorpay webhook");
        }
    }

    private static Outcome mapRefund(String status) {
        return switch (status) {
            case "processed" -> Outcome.SUCCESS;
            case "failed" -> Outcome.FAILED;
            default -> Outcome.PENDING;
        };
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString((config.keyId() + ":" + config.keySecret()).getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode call(java.util.function.Supplier<String> request) {
        try {
            return objectMapper.readTree(request.get());
        } catch (Exception e) {
            log.warn("razorpay_error {}", e.getMessage());
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "Razorpay is temporarily unavailable");
        }
    }
}
