package com.cineverse.controller;

import com.cineverse.common.ApiResponse;
import com.cineverse.dto.AuthDtos.*;
import com.cineverse.dto.BookingDtos.RefundResponse;
import com.cineverse.dto.PaymentDtos.PaymentStatusResponse;
import com.cineverse.booking.BookingMapper;
import com.cineverse.payment.PaymentService;
import com.cineverse.repository.PaymentRepository;
import com.cineverse.repository.RefundRepository;
import com.cineverse.security.SecurityUtils;
import com.cineverse.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentService paymentService;
    private final BookingMapper bookingMapper;

    @GetMapping
    public ApiResponse<UserResponse> me() {
        return ApiResponse.ok(userService.me(SecurityUtils.requireUser().id()));
    }

    @PutMapping
    public ApiResponse<UserResponse> update(@Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(userService.updateProfile(SecurityUtils.requireUser().id(), req), "Profile updated");
    }

    @PutMapping("/city")
    public ApiResponse<UserResponse> city(@RequestBody Map<String, Long> body) {
        return ApiResponse.ok(userService.setPreferredCity(SecurityUtils.requireUser().id(), body.get("cityId")));
    }

    @GetMapping("/payments")
    @Transactional(readOnly = true)
    public ApiResponse<List<PaymentStatusResponse>> payments() {
        Long userId = SecurityUtils.requireUser().id();
        return ApiResponse.ok(paymentRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream().map(paymentService::toStatus).toList());
    }

    @GetMapping("/refunds")
    @Transactional(readOnly = true)
    public ApiResponse<List<RefundResponse>> refunds() {
        return ApiResponse.ok(refundRepository.findForUser(SecurityUtils.requireUser().id()).stream().map(bookingMapper::refund).toList());
    }
}
