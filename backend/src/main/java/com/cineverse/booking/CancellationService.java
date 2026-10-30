package com.cineverse.booking;

import com.cineverse.config.AppProperties;
import com.cineverse.dto.BookingDtos.BookingResponse;
import com.cineverse.entity.Booking;
import com.cineverse.entity.Payment;
import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.entity.enums.NotificationType;
import com.cineverse.entity.enums.PaymentStatus;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.notification.NotificationService;
import com.cineverse.payment.RefundService;
import com.cineverse.repository.BookingRepository;
import com.cineverse.repository.PaymentRepository;
import com.cineverse.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Customer cancellation. Policy (cut-off before showtime, refund percentage, whether the convenience fee
 * is refunded) is configuration, see {@code cineverse.cancellation.*}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationService {
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingLifecycleService lifecycle;
    private final BookingPolicy policy;
    private final RefundService refundService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final BookingMapper mapper;
    private final AppProperties props;
    private final Clock clock;

    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId, String reason, boolean actingAsAdmin) {
        Booking b = bookingRepository.findForUpdate(bookingId).orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND));
        if (!actingAsAdmin && !b.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.BOOKING_NOT_FOUND);
        }
        String why = reason == null || reason.isBlank() ? "Cancelled by customer" : reason.trim();

        if (b.isOpen()) {
            // Nothing captured yet (checkout abandoned or unpaid venue reservation): just release inventory.
            lifecycle.abandon(b, BookingStatus.CANCELLED, why);
            auditService.record(userId, "BOOKING_CANCELLED", "BOOKING", b.getId(), Map.of("paid", false));
            return mapper.toResponse(b, false);
        }
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.BOOKING_STATE_INVALID, "Only confirmed bookings can be cancelled");
        }
        Instant now = Instant.now(clock);
        if (!actingAsAdmin) {
            if (!policy.venueAllowsCancellation(b)) {
                throw new ApiException(ErrorCode.CANCELLATION_NOT_ALLOWED, "This venue does not allow cancellations");
            }
            if (!now.isBefore(policy.cancellationDeadline(b))) {
                throw new ApiException(ErrorCode.CANCELLATION_NOT_ALLOWED,
                        "Cancellations close " + props.cancellation().cutoffMinutesBeforeShow() + " minutes before the start time");
            }
        }

        lifecycle.releaseConfirmedInventory(b);
        b.setCancelledAt(now);
        b.setCancelReason(why);

        Payment payment = paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(b.getId(), PaymentStatus.SUCCESS).orElse(null);
        String refundNote;
        if (payment != null && refundableAmount(b).signum() > 0) {
            b.setStatus(BookingStatus.REFUND_PENDING);
            var refund = refundService.request(b, payment, refundableAmount(b), why);
            refundNote = "A refund of ₹" + refund.getAmount().toPlainString() + " has been initiated.";
        } else {
            b.setStatus(BookingStatus.CANCELLED);
            refundNote = "No payment was captured for this booking.";
        }
        notificationService.notify(b.getUser().getId(), NotificationType.CANCELLATION, "Booking " + b.getReference() + " cancelled",
                refundNote, "/bookings/" + b.getId());
        notificationService.emailAfterCommit(b.getUser().getEmail(), "Booking " + b.getReference() + " cancelled", "booking-cancelled",
                Map.of("name", b.getUser().getFullName(), "reference", b.getReference(), "refundNote", refundNote));
        auditService.record(userId, actingAsAdmin ? "ADMIN_CANCELLED_BOOKING" : "BOOKING_CANCELLED", "BOOKING", b.getId(),
                Map.of("reference", b.getReference(), "refund", payment != null));
        log.info("booking_cancelled bookingId={} status={}", b.getId(), b.getStatus());
        return mapper.toResponse(b, false);
    }

    BigDecimal refundableAmount(Booking b) {
        BigDecimal base = b.getGrandTotal();
        if (!props.cancellation().refundConvenienceFee()) {
            base = base.subtract(b.getConvenienceFee()).subtract(b.getTax());
        }
        return base.multiply(BigDecimal.valueOf(props.cancellation().refundPercent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
    }
}
