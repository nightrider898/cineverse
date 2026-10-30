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
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Stripe PaymentIntents over the REST API (no SDK). The backend creates the intent with the secret key;
 * the browser only receives the intent's client_secret and the publishable key and confirms with Stripe.js.
 * Final state is taken from signature-verified webhooks (payment_intent.succeeded / payment_failed / canceled).
 */
@Slf4j
@Component
public class StripePaymentProvider implements PaymentProvider {
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300;

    private final AppProperties.Payments.Stripe config;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public StripePaymentProvider(AppProperties props, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.config = props.payments().stripe();
        this.objectMapper = objectMapper;
        this.http = builder.clone().baseUrl(config.apiBase() == null ? "https://api.stripe.com" : config.apiBase()).build();
    }

    @Override public PaymentProviderType type() { return PaymentProviderType.STRIPE; }

    @Override public boolean isEnabled() { return config.configured(); }

    @Override public Set<PaymentMethod> supportedMethods() { return EnumSet.of(PaymentMethod.CARD); }

    @Override
    public OrderResult createOrder(Payment payment, Booking booking) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(PaymentProvider.toMinorUnits(payment.getAmount())));
        form.add("currency", payment.getCurrency().toLowerCase());
        form.add("payment_method_types[]", "card");
        form.add("description", "CineVerse booking " + booking.getReference());
        form.add("metadata[paymentId]", payment.getPublicId().toString());
        form.add("metadata[bookingReference]", booking.getReference());
        JsonNode intent = post("/v1/payment_intents", form, "cv-pi-" + payment.getPublicId());
        return new OrderResult(intent.path("id").asText(),
                new PaymentAction("STRIPE_CLIENT_SECRET", intent.path("client_secret").asText(), config.publishableKey(),
                        intent.path("id").asText(), null, null, null),
                Map.of("status", intent.path("status").asText()));
    }

    @Override
    public StatusResult fetchStatus(Payment payment) {
        JsonNode intent = get("/v1/payment_intents/" + payment.getProviderOrderId());
        return new StatusResult(map(intent.path("status").asText()), intent.path("latest_charge").asText(null),
                intent.path("last_payment_error").path("message").asText(null));
    }

    @Override
    public RefundResult refund(Payment payment, Refund refund) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", payment.getProviderOrderId());
        form.add("amount", String.valueOf(PaymentProvider.toMinorUnits(refund.getAmount())));
        form.add("metadata[refundId]", String.valueOf(refund.getId()));
        JsonNode r = post("/v1/refunds", form, "cv-rf-" + refund.getId());
        return new RefundResult(mapRefund(r.path("status").asText()), r.path("id").asText(), r.path("failure_reason").asText(null));
    }

    @Override
    public RefundResult refundStatus(Payment payment, Refund refund) {
        JsonNode r = get("/v1/refunds/" + refund.getProviderRefundId());
        return new RefundResult(mapRefund(r.path("status").asText()), r.path("id").asText(), r.path("failure_reason").asText(null));
    }

    /** Implements Stripe's documented scheme: HMAC-SHA256(secret, timestamp + "." + body) compared to each v1 signature. */
    @Override
    public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
        String header = headers.get("stripe-signature");
        if (header == null || config.webhookSecret() == null || config.webhookSecret().isBlank()) {
            throw new ApiException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        String timestamp = null;
        boolean valid = false;
        for (String part : header.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals("t")) timestamp = kv[1].trim();
        }
        if (timestamp == null) throw new ApiException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        String expected = HmacUtil.hmacSha256Hex(config.webhookSecret(), timestamp + "." + rawBody);
        for (String part : header.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals("v1") && HmacUtil.safeEquals(expected, kv[1].trim())) valid = true;
        }
        if (!valid || Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp)) > SIGNATURE_TOLERANCE_SECONDS) {
            throw new ApiException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        try {
            JsonNode event = objectMapper.readTree(rawBody);
            String type = event.path("type").asText();
            JsonNode obj = event.path("data").path("object");
            return switch (type) {
                case "payment_intent.succeeded" -> new WebhookEvent(event.path("id").asText(), type, obj.path("id").asText(),
                        obj.path("latest_charge").asText(null), Outcome.SUCCESS, null, false, null);
                case "payment_intent.payment_failed" -> new WebhookEvent(event.path("id").asText(), type, obj.path("id").asText(),
                        null, Outcome.FAILED, obj.path("last_payment_error").path("message").asText("Card payment failed"), false, null);
                case "payment_intent.canceled" -> new WebhookEvent(event.path("id").asText(), type, obj.path("id").asText(),
                        null, Outcome.CANCELLED, "Payment cancelled", false, null);
                case "refund.updated", "charge.refund.updated", "refund.created" -> new WebhookEvent(event.path("id").asText(), type,
                        obj.path("payment_intent").asText(null), null, mapRefund(obj.path("status").asText()), obj.path("failure_reason").asText(null), true, obj.path("id").asText());
                default -> new WebhookEvent(event.path("id").asText(), type, null, null, Outcome.PENDING, null, false, null);
            };
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Malformed Stripe webhook");
        }
    }

    private static Outcome map(String status) {
        return switch (status) {
            case "succeeded" -> Outcome.SUCCESS;
            case "canceled" -> Outcome.CANCELLED;
            case "requires_payment_method" -> Outcome.PENDING; // may be a decline the customer can retry
            default -> Outcome.PENDING;
        };
    }

    private static Outcome mapRefund(String status) {
        return switch (status) {
            case "succeeded" -> Outcome.SUCCESS;
            case "failed", "canceled" -> Outcome.FAILED;
            default -> Outcome.PENDING;
        };
    }

    private JsonNode post(String path, MultiValueMap<String, String> form, String idempotencyKey) {
        try {
            String body = http.post().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.secretKey())
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientResponseException e) {
            log.warn("stripe_error path={} status={}", path, e.getStatusCode());
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "Card payments are temporarily unavailable");
        } catch (Exception e) {
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "Card payments are temporarily unavailable");
        }
    }

    private JsonNode get(String path) {
        try {
            String body = http.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + config.secretKey())
                    .retrieve().body(String.class);
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, "Could not reach Stripe");
        }
    }
}
