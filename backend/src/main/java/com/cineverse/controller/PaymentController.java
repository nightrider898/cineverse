package com.cineverse.controller;

import com.cineverse.common.ApiResponse;
import com.cineverse.dto.PaymentDtos.*;
import com.cineverse.entity.enums.PaymentProviderType;
import com.cineverse.payment.PaymentService;
import com.cineverse.security.SecurityUtils;
import com.cineverse.service.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "Payments")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final IdempotencyService idempotency;

    @GetMapping("/methods")
    public ApiResponse<PaymentMethodsResponse> methods(@RequestParam(required = false) Long bookingId) {
        return ApiResponse.ok(paymentService.methods(bookingId, SecurityUtils.currentUserIdOrNull()));
    }

    @Operation(summary = "Start a payment for a booking. Amount is taken from the server-side price, never from the client.")
    @PostMapping("/initiate")
    public ApiResponse<PaymentInitResponse> initiate(@Valid @RequestBody InitiatePaymentRequest req,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        Long userId = SecurityUtils.requireUser().id();
        return ApiResponse.ok(idempotency.execute("payment.initiate", userId, key, req, PaymentInitResponse.class,
                () -> paymentService.initiate(userId, req)));
    }

    @Operation(summary = "Payment status; checks with the gateway when the outcome is still unknown")
    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentStatusResponse> status(@PathVariable UUID paymentId) {
        return ApiResponse.ok(paymentService.status(SecurityUtils.requireUser().id(), paymentId));
    }

    @Operation(summary = "Razorpay Checkout success handler: verifies the HMAC signature server-side")
    @PostMapping("/razorpay/verify")
    public ApiResponse<PaymentStatusResponse> verifyRazorpay(@Valid @RequestBody RazorpayVerifyRequest req) {
        return ApiResponse.ok(paymentService.verifyRazorpay(SecurityUtils.requireUser().id(), req));
    }

    @PostMapping("/webhook/stripe")
    public ApiResponse<Map<String, String>> stripe(@RequestBody String body, HttpServletRequest req) {
        return webhook(PaymentProviderType.STRIPE, body, req);
    }

    @PostMapping("/webhook/razorpay")
    public ApiResponse<Map<String, String>> razorpay(@RequestBody String body, HttpServletRequest req) {
        return webhook(PaymentProviderType.RAZORPAY, body, req);
    }

    @PostMapping("/webhook/phonepe")
    public ApiResponse<Map<String, String>> phonepe(@RequestBody String body, HttpServletRequest req) {
        return webhook(PaymentProviderType.PHONEPE, body, req);
    }

    @PostMapping("/webhook/mock")
    public ApiResponse<Map<String, String>> mock(@RequestBody String body, HttpServletRequest req) {
        return webhook(PaymentProviderType.MOCK, body, req);
    }

    private ApiResponse<Map<String, String>> webhook(PaymentProviderType type, String body, HttpServletRequest req) {
        Map<String, String> headers = new HashMap<>();
        Collections.list(req.getHeaderNames()).forEach(h -> headers.put(h.toLowerCase(Locale.ROOT), req.getHeader(h)));
        return ApiResponse.ok(Map.of("result", paymentService.handleWebhook(type, body, headers)));
    }
}
