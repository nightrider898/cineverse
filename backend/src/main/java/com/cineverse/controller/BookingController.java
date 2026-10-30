package com.cineverse.controller;

import com.cineverse.booking.BookingService;
import com.cineverse.booking.CancellationService;
import com.cineverse.booking.EventBookingService;
import com.cineverse.common.ApiResponse;
import com.cineverse.common.PageResponse;
import com.cineverse.dto.BookingDtos.*;
import com.cineverse.security.SecurityUtils;
import com.cineverse.security.UserPrincipal;
import com.cineverse.service.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Bookings")
@RestController
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;
    private final EventBookingService eventBookingService;
    private final CancellationService cancellationService;
    private final IdempotencyService idempotency;

    @Operation(summary = "Create a movie booking from an active seat lock (supports Idempotency-Key)")
    @PostMapping("/api/v1/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingResponse> create(@Valid @RequestBody CreateBookingRequest req,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        Long userId = SecurityUtils.requireUser().id();
        return ApiResponse.ok(idempotency.execute("booking.create", userId, key, req, BookingResponse.class,
                () -> bookingService.createFromLock(userId, req)));
    }

    @Operation(summary = "Book event / sports / play / activity tickets (quantity inventory)")
    @PostMapping("/api/v1/bookings/event")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingResponse> createEvent(@Valid @RequestBody CreateEventBookingRequest req,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        Long userId = SecurityUtils.requireUser().id();
        return ApiResponse.ok(idempotency.execute("booking.createEvent", userId, key, req, BookingResponse.class,
                () -> eventBookingService.create(userId, req)));
    }

    @GetMapping("/api/v1/bookings")
    public ApiResponse<PageResponse<BookingResponse>> list(@RequestParam(defaultValue = "upcoming") String tab,
                                                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(bookingService.listForUser(SecurityUtils.requireUser().id(), tab, page, size)));
    }

    @GetMapping("/api/v1/bookings/{id}")
    public ApiResponse<BookingResponse> get(@PathVariable Long id) {
        UserPrincipal u = SecurityUtils.requireUser();
        return ApiResponse.ok(bookingService.get(u.id(), id, u.isAdmin() || u.hasRole("VENUE_STAFF")));
    }

    @PutMapping("/api/v1/bookings/{id}/food")
    public ApiResponse<BookingResponse> food(@PathVariable Long id, @Valid @RequestBody UpdateFoodRequest req) {
        return ApiResponse.ok(bookingService.updateFood(SecurityUtils.requireUser().id(), id, req));
    }

    @Operation(summary = "Apply a coupon; the backend validates every rule and recomputes the price")
    @PostMapping("/api/v1/coupons/apply")
    public ApiResponse<BookingResponse> applyCoupon(@Valid @RequestBody CouponApplyRequest req) {
        return ApiResponse.ok(bookingService.applyCoupon(SecurityUtils.requireUser().id(), req), "Coupon applied");
    }

    @DeleteMapping("/api/v1/bookings/{id}/coupon")
    public ApiResponse<BookingResponse> removeCoupon(@PathVariable Long id) {
        return ApiResponse.ok(bookingService.removeCoupon(SecurityUtils.requireUser().id(), id));
    }

    @Operation(summary = "Cancel a booking; creates a refund request when eligible (supports Idempotency-Key)")
    @PostMapping("/api/v1/bookings/{id}/cancel")
    public ApiResponse<BookingResponse> cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest req,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        Long userId = SecurityUtils.requireUser().id();
        String reason = req == null ? null : req.reason();
        return ApiResponse.ok(idempotency.execute("booking.cancel", userId, key, Map.of("bookingId", id, "reason", String.valueOf(reason)),
                BookingResponse.class, () -> cancellationService.cancel(userId, id, reason, false)), "Booking cancelled");
    }
}
