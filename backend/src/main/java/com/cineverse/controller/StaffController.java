package com.cineverse.controller;

import com.cineverse.booking.BookingMapper;
import com.cineverse.booking.TicketService;
import com.cineverse.common.ApiResponse;
import com.cineverse.dto.BookingDtos.BookingResponse;
import com.cineverse.dto.PaymentDtos.PaymentStatusResponse;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.payment.PaymentService;
import com.cineverse.repository.BookingRepository;
import com.cineverse.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Venue staff")
@RestController
@RequiredArgsConstructor
public class StaffController {
    private final TicketService ticketService;
    private final PaymentService paymentService;
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;

    public record ValidateRequest(@NotBlank String qrToken) {}

    public record CounterRequest(@NotBlank @Pattern(regexp = "PAID|CANCELLED") String action) {}

    @Operation(summary = "Scan a QR ticket at the gate. Each ticket admits once.")
    @PostMapping("/api/v1/tickets/validate")
    public ApiResponse<TicketService.ValidationResult> validate(@Valid @RequestBody ValidateRequest req) {
        return ApiResponse.ok(ticketService.validate(req.qrToken(), SecurityUtils.requireUser().id()));
    }

    @GetMapping("/api/v1/staff/bookings/{reference}")
    @Transactional(readOnly = true)
    public ApiResponse<BookingResponse> lookup(@PathVariable String reference) {
        return ApiResponse.ok(bookingRepository.findByReference(reference.trim().toUpperCase())
                .map(b -> bookingMapper.toResponse(b, false)).orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND)));
    }

    @Operation(summary = "Settle a pay-at-venue reservation at the counter")
    @PostMapping("/api/v1/staff/bookings/{reference}/counter-payment")
    public ApiResponse<PaymentStatusResponse> counter(@PathVariable String reference, @Valid @RequestBody CounterRequest req) {
        return ApiResponse.ok(paymentService.counterSettle(reference, "PAID".equals(req.action()), SecurityUtils.requireUser().id()));
    }
}
