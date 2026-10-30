package com.cineverse.admin;

import com.cineverse.admin.AdminDtos.*;
import com.cineverse.booking.CancellationService;
import com.cineverse.common.ApiResponse;
import com.cineverse.common.PageResponse;
import com.cineverse.dto.BookingDtos.BookingResponse;
import com.cineverse.dto.BookingDtos.RefundResponse;
import com.cineverse.dto.EngagementDtos.ReviewResponse;
import com.cineverse.dto.PaymentDtos.PaymentAdminResponse;
import com.cineverse.entity.enums.ReviewStatus;
import com.cineverse.service.ReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Admin: operations & analytics")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminOpsController {
    private final AdminOpsService ops;
    private final AnalyticsService analytics;
    private final ReviewService reviewService;
    private final CancellationService cancellationService;
    private final AdminAccess access;

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        access.requireAdmin();
        return ApiResponse.ok(analytics.dashboard());
    }

    @GetMapping("/analytics")
    public ApiResponse<AnalyticsResponse> analytics(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        access.requireAdmin();
        return ApiResponse.ok(analytics.analytics(from, to));
    }

    @GetMapping("/food")
    public ApiResponse<List<AdminFoodResponse>> food() {
        return ApiResponse.ok(ops.food());
    }

    @PostMapping("/food")
    public ApiResponse<AdminFoodResponse> createFood(@Valid @RequestBody FoodUpsertRequest req) {
        return ApiResponse.ok(ops.upsertFood(null, req), "Food item created");
    }

    @PutMapping("/food/{id}")
    public ApiResponse<AdminFoodResponse> updateFood(@PathVariable Long id, @Valid @RequestBody FoodUpsertRequest req) {
        return ApiResponse.ok(ops.upsertFood(id, req), "Food item updated");
    }

    @GetMapping("/coupons")
    public ApiResponse<List<AdminCouponResponse>> coupons() {
        return ApiResponse.ok(ops.coupons());
    }

    @PostMapping("/coupons")
    public ApiResponse<AdminCouponResponse> createCoupon(@Valid @RequestBody CouponUpsertRequest req) {
        return ApiResponse.ok(ops.upsertCoupon(null, req), "Coupon created");
    }

    @PutMapping("/coupons/{id}")
    public ApiResponse<AdminCouponResponse> updateCoupon(@PathVariable Long id, @Valid @RequestBody CouponUpsertRequest req) {
        return ApiResponse.ok(ops.upsertCoupon(id, req), "Coupon updated");
    }

    @GetMapping("/offers")
    public ApiResponse<List<AdminOfferResponse>> offers() {
        return ApiResponse.ok(ops.offers());
    }

    @PostMapping("/offers")
    public ApiResponse<AdminOfferResponse> createOffer(@Valid @RequestBody OfferUpsertRequest req) {
        return ApiResponse.ok(ops.upsertOffer(null, req), "Offer created");
    }

    @PutMapping("/offers/{id}")
    public ApiResponse<AdminOfferResponse> updateOffer(@PathVariable Long id, @Valid @RequestBody OfferUpsertRequest req) {
        return ApiResponse.ok(ops.upsertOffer(id, req), "Offer updated");
    }

    @DeleteMapping("/offers/{id}")
    public ApiResponse<Void> deleteOffer(@PathVariable Long id) {
        ops.deleteOffer(id);
        return ApiResponse.ok(null, "Offer deleted");
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> users(@RequestParam(required = false) String q, @RequestParam(required = false) String role,
                                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.of(ops.users(q, role, page, size)));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<AdminUserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) {
        return ApiResponse.ok(ops.updateUser(id, req), "User updated");
    }

    @GetMapping("/bookings")
    public ApiResponse<PageResponse<AdminBookingRow>> bookings(@RequestParam(required = false) String status, @RequestParam(required = false) String q,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.of(ops.bookings(status, q, date, page, size)));
    }

    @PostMapping("/bookings/{id}/cancel")
    public ApiResponse<BookingResponse> cancelBooking(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        var admin = access.requireAdmin();
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(cancellationService.cancel(admin.id(), id, reason == null ? "Cancelled by CineVerse support" : reason, true));
    }

    @GetMapping("/payments")
    public ApiResponse<PageResponse<PaymentAdminResponse>> payments(@RequestParam(required = false) String status,
                                                                    @RequestParam(required = false) String provider,
                                                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.of(ops.payments(status, provider, page, size)));
    }

    @GetMapping("/refunds")
    public ApiResponse<PageResponse<RefundResponse>> refunds(@RequestParam(required = false) String status,
                                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(PageResponse.of(ops.refunds(status, page, size)));
    }

    @PostMapping("/refunds/{id}/retry")
    public ApiResponse<RefundResponse> retryRefund(@PathVariable Long id) {
        return ApiResponse.ok(ops.retryRefund(id), "Refund queued for retry");
    }

    @PostMapping("/refunds/{id}/complete")
    public ApiResponse<RefundResponse> completeRefund(@PathVariable Long id) {
        return ApiResponse.ok(ops.completeRefund(id), "Refund marked as completed");
    }

    @GetMapping("/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> pendingReviews(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        access.requireAdmin();
        return ApiResponse.ok(PageResponse.of(reviewService.pending(page, size)));
    }

    @PostMapping("/reviews/{id}/{decision}")
    public ApiResponse<ReviewResponse> moderate(@PathVariable Long id, @PathVariable String decision) {
        var admin = access.requireAdmin();
        ReviewStatus status = "approve".equalsIgnoreCase(decision) ? ReviewStatus.APPROVED : ReviewStatus.REJECTED;
        return ApiResponse.ok(reviewService.moderate(id, status, admin.id()));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AuditLogResponse>> audit(@RequestParam(required = false) String action, @RequestParam(required = false) String resource,
                                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return ApiResponse.ok(PageResponse.of(ops.audit(action, resource, page, size)));
    }
}
