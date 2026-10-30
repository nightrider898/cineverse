package com.cineverse.controller;

import com.cineverse.common.ApiResponse;
import com.cineverse.dto.PaymentDtos.MockOutcomeRequest;
import com.cineverse.entity.Payment;
import com.cineverse.entity.enums.PaymentProviderType;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.payment.PaymentService;
import com.cineverse.payment.provider.MockPaymentProvider;
import com.cineverse.repository.PaymentRepository;
import com.cineverse.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Development-only payment simulator backend. Exists only when cineverse.payments.mock-enabled=true.
 * It plays the role of the gateway: it signs a webhook and delivers it to our own webhook handler,
 * optionally more than once to demonstrate idempotent processing.
 */
@Tag(name = "Dev: payment simulator")
@RestController
@RequestMapping("/api/v1/dev/payments")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cineverse.payments.mock-enabled", havingValue = "true")
public class DevPaymentController {
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final MockPaymentProvider mockProvider;
    private final ObjectMapper objectMapper;

    @GetMapping("/{paymentId}")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> details(@PathVariable UUID paymentId) {
        Payment p = owned(paymentId);
        return ApiResponse.ok(Map.of("paymentId", p.getPublicId(), "amount", p.getAmount(), "currency", p.getCurrency(),
                "method", p.getMethod().name(), "status", p.getStatus().name(), "bookingId", p.getBooking().getId(),
                "bookingReference", p.getBooking().getReference(), "orderId", p.getProviderOrderId()));
    }

    @Operation(summary = "Simulate the gateway result. deliveries>1 re-sends the same webhook event to prove idempotency.")
    @PostMapping("/{paymentId}/simulate")
    public ApiResponse<Map<String, Object>> simulate(@PathVariable UUID paymentId, @Valid @RequestBody MockOutcomeRequest req,
                                                     @RequestParam(defaultValue = "1") int deliveries) throws Exception {
        Payment p = owned(paymentId);
        if (p.getProvider() != PaymentProviderType.MOCK) throw new ApiException(ErrorCode.BAD_REQUEST, "Not a mock payment");
        String body = objectMapper.writeValueAsString(Map.of("eventId", "evt_mock_" + UUID.randomUUID(), "orderId", p.getProviderOrderId(),
                "paymentRef", "mock_pay_" + UUID.randomUUID().toString().substring(0, 12), "outcome", req.outcome()));
        Map<String, String> headers = Map.of(MockPaymentProvider.SIGNATURE_HEADER, mockProvider.signature(body));
        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.max(1, Math.min(deliveries, 5)); i++) {
            results.add(paymentService.handleWebhook(PaymentProviderType.MOCK, body, headers));
        }
        var status = paymentService.status(SecurityUtils.requireUser().id(), paymentId);
        return ApiResponse.ok(Map.of("deliveries", results, "payment", status));
    }

    private Payment owned(UUID paymentId) {
        Payment p = paymentRepository.findByPublicId(paymentId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (!p.getUserId().equals(SecurityUtils.requireUser().id())) throw ApiException.notFound("Payment");
        return p;
    }
}
