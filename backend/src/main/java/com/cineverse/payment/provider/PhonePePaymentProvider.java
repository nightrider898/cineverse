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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * PhonePe Payment Gateway (Standard Checkout, v2 APIs with OAuth client credentials).
 *
 * Endpoint paths and payload shapes follow PhonePe's published Standard Checkout documentation at the time of
 * writing; base URLs are configurable per environment. Before going live, re-check them against PhonePe's
 * current developer docs for your merchant account — nothing here relies on undocumented endpoints.
 */
@Slf4j
@Component
public class PhonePePaymentProvider implements PaymentProvider {
    private final AppProperties.Payments.PhonePe config;
    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    private volatile String accessToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public PhonePePaymentProvider(AppProperties props, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.props = props;
        this.config = props.payments().phonepe();
        this.objectMapper = objectMapper;
        this.http = builder.clone().build();
    }

    private boolean production() {
        return "PRODUCTION".equalsIgnoreCase(config.environment());
    }

    private String authUrl() {
        return production() ? "https://api.phonepe.com/apis/identity-manager/v1/oauth/token"
                : "https://api-preprod.phonepe.com/apis/pg-sandbox/v1/oauth/token";
    }

    private String pgBase() {
        return production() ? "https://api.phonepe.com/apis/pg" : "https://api-preprod.phonepe.com/apis/pg-sandbox";
    }

    @Override public PaymentProviderType type() { return PaymentProviderType.PHONEPE; }

    @Override public boolean isEnabled() { return config.configured(); }

    @Override
    public Set<PaymentMethod> supportedMethods() {
        return EnumSet.of(PaymentMethod.PHONEPE, PaymentMethod.UPI, PaymentMethod.CARD, PaymentMethod.NETBANKING);
    }

    @Override
    public OrderResult createOrder(Payment payment, Booking booking) {
        String merchantOrderId = "CV" + payment.getPublicId().toString().replace("-", "");
        Map<String, Object> body = Map.of(
                "merchantOrderId", merchantOrderId,
                "amount", PaymentProvider.toMinorUnits(payment.getAmount()),
                "expireAfter", 900,
                "metaInfo", Map.of("udf1", booking.getReference()),
                "paymentFlow", Map.of("type", "PG_CHECKOUT", "message", "CineVerse booking " + booking.getReference(),
                        "merchantUrls", Map.of("redirectUrl", props.frontendUrl() + "/payment/" + booking.getId() + "?paymentId=" + payment.getPublicId())));
        JsonNode res = call(() -> http.post().uri(pgBase() + "/checkout/v2/pay").header(HttpHeaders.AUTHORIZATION, "O-Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));
        return new OrderResult(merchantOrderId,
                new PaymentAction("REDIRECT", null, null, merchantOrderId, null, res.path("redirectUrl").asText(), null),
                Map.of("phonepeOrderId", res.path("orderId").asText(""), "state", res.path("state").asText("")));
    }

    @Override
    public StatusResult fetchStatus(Payment payment) {
        JsonNode res = call(() -> http.get().uri(pgBase() + "/checkout/v2/order/{id}/status", payment.getProviderOrderId())
                .header(HttpHeaders.AUTHORIZATION, "O-Bearer " + token()).retrieve().body(String.class));
        return new StatusResult(map(res.path("state").asText()), res.path("orderId").asText(null), res.path("errorCode").asText(null));
    }

    @Override
    public RefundResult refund(Payment payment, Refund refund) {
        Map<String, Object> body = Map.of("merchantRefundId", "CVR" + refund.getId() + "x" + refund.getAttempts(),
                "originalMerchantOrderId", payment.getProviderOrderId(),
                "amount", PaymentProvider.toMinorUnits(refund.getAmount()));
        JsonNode res = call(() -> http.post().uri(pgBase() + "/payments/v2/refund").header(HttpHeaders.AUTHORIZATION, "O-Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));
        return new RefundResult(map(res.path("state").asText()), res.path("refundId").asText(null), null);
    }

    /** PhonePe signs callbacks with Authorization: SHA256(username:password) configured in the merchant dashboard. */
    @Override
    public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
        String expected = HmacUtil.sha256Hex(config.webhookUsername() + ":" + config.webhookPassword());
        if (config.webhookUsername() == null || config.webhookUsername().isBlank() || !HmacUtil.safeEquals(expected, headers.get("authorization"))) {
            throw new ApiException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        try {
            JsonNode n = objectMapper.readTree(rawBody);
            String type = n.path("event").asText();
            JsonNode p = n.path("payload");
            String eventId = type + ":" + p.path("orderId").asText() + ":" + p.path("state").asText();
            boolean refund = type.startsWith("pg.refund");
            return new WebhookEvent(eventId, type, refund ? p.path("originalMerchantOrderId").asText(null) : p.path("merchantOrderId").asText(),
                    p.path("orderId").asText(null), map(p.path("state").asText()), p.path("errorCode").asText(null), refund,
                    refund ? p.path("refundId").asText(null) : null);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Malformed PhonePe webhook");
        }
    }

    private static Outcome map(String state) {
        return switch (state) {
            case "COMPLETED" -> Outcome.SUCCESS;
            case "FAILED" -> Outcome.FAILED;
            default -> Outcome.PENDING;
        };
    }

    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) return accessToken;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.clientId());
        form.add("client_version", config.clientVersion());
        form.add("client_secret", config.clientSecret());
        form.add("grant_type", "client_credentials");
        JsonNode res = call(() -> http.post().uri(authUrl()).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String.class));
        accessToken = res.path("access_token").asText();
        long expiresAt = res.path("expires_at").asLong(0);
        tokenExpiry = expiresAt > 0 ? Instant.ofEpochSecond(expiresAt) : Instant.now().plusSeconds(900);
        return accessToken;
    }

    private JsonNode call(java.util.function.Supplier<String> request) {
        try {
            return objectMapper.readTree(request.get());
        } catch (Exception e) {
            log.warn("phonepe_error {}", e.getMessage());
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "PhonePe is temporarily unavailable");
        }
    }
}
