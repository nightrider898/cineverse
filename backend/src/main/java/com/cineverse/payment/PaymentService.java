package com.cineverse.payment;

import com.cineverse.booking.BookingLifecycleService;
import com.cineverse.booking.BookingPolicy;
import com.cineverse.dto.PaymentDtos.*;
import com.cineverse.entity.Booking;
import com.cineverse.entity.Payment;
import com.cineverse.entity.PaymentTransaction;
import com.cineverse.entity.enums.*;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.notification.NotificationService;
import com.cineverse.payment.PaymentProvider.Outcome;
import com.cineverse.payment.provider.RazorpayPaymentProvider;
import com.cineverse.repository.BookingRepository;
import com.cineverse.repository.PaymentRepository;
import com.cineverse.repository.PaymentTransactionRepository;
import com.cineverse.repository.WebhookEventRepository;
import com.cineverse.seat.SeatLockService;
import com.cineverse.service.AuditService;
import com.cineverse.service.CouponService;
import com.cineverse.service.PricingService;
import com.cineverse.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Payment orchestration. Payment state and booking state are separate state machines:
 * a payment reaching SUCCESS triggers booking confirmation, which can still fail (seats gone) and
 * then produces an automatic refund. Every path into {@link #applyOutcome} is idempotent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final BookingRepository bookingRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRouter router;
    private final RazorpayPaymentProvider razorpay;
    private final BookingLifecycleService lifecycle;
    private final com.cineverse.booking.BookingEmails bookingEmails;
    private final BookingPolicy policy;
    private final SeatLockService seatLockService;
    private final PricingService pricingService;
    private final CouponService couponService;
    private final RefundService refundService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AppProperties props;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PaymentMethodsResponse methods(Long bookingId, Long userId) {
        boolean payAtVenue = false;
        if (bookingId != null) {
            Booking b = bookingRepository.findById(bookingId).filter(x -> x.getUser().getId().equals(userId)).orElse(null);
            payAtVenue = b != null && policy.payAtVenueAllowed(b);
        }
        var stripe = props.payments().stripe();
        return new PaymentMethodsResponse(router.options(payAtVenue), stripe.configured() ? stripe.publishableKey() : null, router.isSandbox());
    }

    @Transactional
    public PaymentInitResponse initiate(Long userId, InitiatePaymentRequest req) {
        Instant now = Instant.now(clock);
        Booking b = bookingRepository.findForUpdate(req.bookingId()).orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND));
        if (!b.getUser().getId().equals(userId)) throw new ApiException(ErrorCode.BOOKING_NOT_FOUND);
        if (b.getStatus() == BookingStatus.CONFIRMED) throw new ApiException(ErrorCode.BOOKING_STATE_INVALID, "This booking is already paid");
        if (!b.isOpen()) throw new ApiException(ErrorCode.BOOKING_EXPIRED);
        if (b.isPayAtVenue()) throw new ApiException(ErrorCode.BOOKING_STATE_INVALID, "This booking is reserved for payment at the venue");
        if (b.getExpiresAt() != null && !b.getExpiresAt().isAfter(now)) throw new ApiException(ErrorCode.BOOKING_EXPIRED);
        if (b.getBookingType() == BookingType.MOVIE && !seatLockService.isUsable(b.getSeatLockId())) {
            throw new ApiException(ErrorCode.SEAT_LOCK_EXPIRED);
        }

        // Server-side price is final: recompute and re-check the coupon against the chosen method.
        pricingService.reprice(b);
        if (b.getCoupon() != null) couponService.validate(b.getCoupon(), b, req.method());
        b.setPaymentMethod(req.method());

        // Supersede any unfinished attempt on this booking (e.g. customer switched from card to UPI).
        paymentRepository.findByBookingIdOrderByCreatedAtDesc(b.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.INITIATED || p.getStatus() == PaymentStatus.PENDING)
                .forEach(p -> transition(p, PaymentStatus.CANCELLED, "SUPERSEDED", "Replaced by a new payment attempt", null));

        Payment p = new Payment();
        p.setPublicId(UUID.randomUUID());
        p.setBooking(b);
        p.setUserId(userId);
        p.setMethod(req.method());
        p.setAmount(b.getGrandTotal());
        p.setStatus(PaymentStatus.INITIATED);

        if (req.method() == PaymentMethod.PAY_AT_VENUE) {
            return reserveForVenue(b, p);
        }

        PaymentProvider provider = router.resolve(req.method());
        p.setProvider(provider.type());
        paymentRepository.save(p);
        PaymentProvider.OrderResult order = provider.createOrder(p, b);
        p.setProviderOrderId(order.providerOrderId());
        transition(p, PaymentStatus.PENDING, "ORDER_CREATED", null, order.raw());
        b.setStatus(BookingStatus.PAYMENT_PENDING);
        auditService.record(userId, "PAYMENT_CREATED", "PAYMENT", p.getPublicId(),
                Map.of("bookingId", b.getId(), "provider", p.getProvider().name(), "method", p.getMethod().name(), "amount", p.getAmount()));
        log.info("payment_created paymentId={} bookingId={} provider={} amount={}", p.getPublicId(), b.getId(), p.getProvider(), p.getAmount());
        return new PaymentInitResponse(p.getPublicId(), b.getId(), p.getStatus().name(), p.getProvider().name(), p.getMethod().name(),
                p.getAmount(), p.getCurrency(), order.action());
    }

    private PaymentInitResponse reserveForVenue(Booking b, Payment p) {
        if (!policy.payAtVenueAllowed(b)) throw new ApiException(ErrorCode.PAY_AT_VENUE_NOT_ALLOWED);
        Instant holdUntil = policy.payAtVenueHoldUntil(b);
        if (b.getBookingType() == BookingType.MOVIE) {
            seatLockService.convertToVenueHold(b.getSeatLockId(), b.getUser().getId(), holdUntil);
        }
        p.setProvider(PaymentProviderType.VENUE);
        p.setProviderOrderId("VENUE-" + b.getReference());
        paymentRepository.save(p);
        transition(p, PaymentStatus.PENDING, "VENUE_RESERVATION", null, Map.of("holdUntil", holdUntil.toString()));
        b.setPayAtVenue(true);
        b.setStatus(BookingStatus.PAYMENT_PENDING);
        b.setExpiresAt(holdUntil);
        notificationService.notify(b.getUser().getId(), NotificationType.BOOKING_CONFIRMED, "Reserved: pay at the counter",
                "Show reference " + b.getReference() + " at the box office before " + holdUntil + " to collect your tickets.", "/bookings/" + b.getId());
        auditService.record(b.getUser().getId(), "PAY_AT_VENUE_RESERVED", "BOOKING", b.getId(), Map.of("holdUntil", holdUntil.toString()));
        return new PaymentInitResponse(p.getPublicId(), b.getId(), p.getStatus().name(), "VENUE", PaymentMethod.PAY_AT_VENUE.name(),
                p.getAmount(), p.getCurrency(), new PaymentAction("PAY_AT_VENUE", null, null, b.getReference(), null, null, null));
    }

    /**
     * Single idempotent entry point for payment results (webhooks, signature-verified client callbacks,
     * status polling, counter staff, mock simulator).
     */
    @Transactional
    public void applyOutcome(Long paymentId, Outcome outcome, String providerPaymentId, String failureReason, String source) {
        if (outcome == Outcome.PENDING) return;
        Payment p = paymentRepository.findForUpdate(paymentId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (p.getStatus() == PaymentStatus.SUCCESS || p.getStatus() == PaymentStatus.REFUNDED || p.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            log.info("payment_outcome_ignored paymentId={} status={} outcome={} source={} (already settled)", p.getPublicId(), p.getStatus(), outcome, source);
            return;
        }
        Booking b = bookingRepository.findForUpdate(p.getBooking().getId()).orElseThrow();

        if (outcome != Outcome.SUCCESS) {
            if (p.getStatus().isTerminal()) return;
            transition(p, outcome == Outcome.CANCELLED ? PaymentStatus.CANCELLED : PaymentStatus.FAILED, source, failureReason, null);
            p.setFailureReason(failureReason);
            if (b.getStatus() == BookingStatus.PAYMENT_PENDING && !b.isPayAtVenue()) {
                b.setStatus(BookingStatus.PENDING); // customer may retry with another method while the hold lasts
            }
            notificationService.notify(b.getUser().getId(), NotificationType.PAYMENT_FAILED, "Payment " + (outcome == Outcome.CANCELLED ? "cancelled" : "failed"),
                    "Your payment for " + b.getReference() + " did not go through. No money was taken for this attempt.", "/checkout/" + b.getId());
            auditService.record(b.getUser().getId(), "PAYMENT_FAILED", "PAYMENT", p.getPublicId(), Map.of("source", source, "outcome", outcome.name()));
            log.info("payment_failed paymentId={} bookingId={} outcome={} source={}", p.getPublicId(), b.getId(), outcome, source);
            return;
        }

        if (providerPaymentId != null) p.setProviderPaymentId(providerPaymentId);
        p.setCompletedAt(Instant.now(clock));
        p.setFailureReason(null);
        transition(p, PaymentStatus.SUCCESS, source, null, null);
        auditService.record(b.getUser().getId(), "PAYMENT_SUCCESS", "PAYMENT", p.getPublicId(),
                Map.of("bookingId", b.getId(), "amount", p.getAmount(), "source", source));
        log.info("payment_success paymentId={} bookingId={} amount={} source={}", p.getPublicId(), b.getId(), p.getAmount(), source);

        if (b.getStatus() == BookingStatus.CONFIRMED) {
            // A second, different payment captured for an already-confirmed booking: give the money back.
            refundService.request(b, p, p.getAmount(), "Duplicate payment for an already confirmed booking");
            return;
        }
        boolean confirmed = (b.isOpen() || b.getStatus() == BookingStatus.EXPIRED || b.getStatus() == BookingStatus.CANCELLED)
                && lifecycle.confirm(b);
        if (confirmed) {
            notificationService.notify(b.getUser().getId(), NotificationType.PAYMENT_SUCCESS, "Payment received",
                    "₹" + p.getAmount().toPlainString() + " paid for " + b.getReference() + ".", "/bookings/" + b.getId());
            if (!"DEMO_SEED".equals(source)) {
                bookingEmails.sendConfirmation(b, p); // one email: order id, payment id, transaction id, QR ticket
            }
        } else {
            b.setStatus(BookingStatus.REFUND_PENDING);
            refundService.request(b, p, p.getAmount(), "Seats were no longer available when the payment completed");
            notificationService.notify(b.getUser().getId(), NotificationType.PAYMENT_FAILED, "Booking could not be completed",
                    "Your payment arrived after the seats were released. A full refund has been initiated.", "/bookings/" + b.getId());
        }
    }

    /** Verifies the gateway signature, de-duplicates by event id, then applies the result. */
    @Transactional
    public String handleWebhook(PaymentProviderType type, String rawBody, Map<String, String> headers) {
        PaymentProvider provider = router.provider(type);
        PaymentProvider.WebhookEvent ev = provider.parseWebhook(rawBody, headers);
        if (webhookEventRepository.claim(type.name(), ev.eventId(), ev.eventType()) == 0) {
            log.info("webhook_duplicate provider={} eventId={}", type, ev.eventId());
            return "DUPLICATE";
        }
        String outcome;
        if (ev.refundEvent()) {
            if (ev.providerRefundId() != null) refundService.applyProviderUpdate(ev.providerRefundId(), ev.outcome(), ev.failureReason());
            outcome = "REFUND_" + ev.outcome();
        } else if (ev.providerOrderId() == null) {
            outcome = "IGNORED";
        } else {
            Optional<Payment> payment = paymentRepository.findByProviderAndProviderOrderId(type, ev.providerOrderId());
            if (payment.isEmpty()) {
                log.warn("webhook_unknown_order provider={} orderId={}", type, ev.providerOrderId());
                outcome = "UNKNOWN_ORDER";
            } else {
                recordTransaction(payment.get(), "WEBHOOK", ev.eventType(), ev.providerPaymentId());
                applyOutcome(payment.get().getId(), ev.outcome(), ev.providerPaymentId(), ev.failureReason(), "WEBHOOK_" + type);
                outcome = ev.outcome().name();
            }
        }
        webhookEventRepository.markProcessed(type.name(), ev.eventId(), outcome);
        log.info("webhook_processed provider={} eventId={} type={} outcome={}", type, ev.eventId(), ev.eventType(), outcome);
        return outcome;
    }

    @Transactional
    public PaymentStatusResponse verifyRazorpay(Long userId, RazorpayVerifyRequest req) {
        Payment p = owned(userId, req.paymentId());
        if (p.getProvider() != PaymentProviderType.RAZORPAY || !req.razorpayOrderId().equals(p.getProviderOrderId())
                || !razorpay.verifyCheckoutSignature(req.razorpayOrderId(), req.razorpayPaymentId(), req.razorpaySignature())) {
            auditService.record(userId, "PAYMENT_SIGNATURE_REJECTED", "PAYMENT", p.getPublicId(), null);
            throw new ApiException(ErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
        applyOutcome(p.getId(), Outcome.SUCCESS, req.razorpayPaymentId(), null, "RAZORPAY_CHECKOUT_SIGNATURE");
        return toStatus(paymentRepository.findById(p.getId()).orElseThrow());
    }

    /**
     * Status for the client. When the outcome is still unknown (e.g. the browser lost the gateway response),
     * asks the gateway directly instead of assuming failure, so customers are not pushed into paying twice.
     */
    @Transactional
    public PaymentStatusResponse status(Long userId, UUID publicId) {
        Payment p = owned(userId, publicId);
        if (!p.getStatus().isTerminal() && p.getProvider() != PaymentProviderType.VENUE && p.getProviderOrderId() != null) {
            try {
                PaymentProvider.StatusResult s = router.provider(p.getProvider()).fetchStatus(p);
                recordTransaction(p, "STATUS_CHECK", s.outcome().name(), s.providerPaymentId());
                applyOutcome(p.getId(), s.outcome(), s.providerPaymentId(), s.failureReason(), "STATUS_CHECK");
            } catch (ApiException e) {
                log.info("payment_status_check_unavailable paymentId={} reason={}", publicId, e.getMessage());
            }
        }
        return toStatus(paymentRepository.findById(p.getId()).orElseThrow());
    }

    /** Venue staff settles a pay-at-venue reservation. */
    @Transactional
    public PaymentStatusResponse counterSettle(String reference, boolean paid, Long staffId) {
        Booking b = bookingRepository.findByReference(reference.trim().toUpperCase()).orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND));
        if (!b.isPayAtVenue() || b.getStatus() != BookingStatus.PAYMENT_PENDING) {
            throw new ApiException(ErrorCode.BOOKING_STATE_INVALID, "No pending counter payment for this booking");
        }
        Payment p = paymentRepository.findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(b.getId(), EnumSet.of(PaymentStatus.PENDING, PaymentStatus.INITIATED))
                .filter(x -> x.getProvider() == PaymentProviderType.VENUE)
                .orElseThrow(() -> new ApiException(ErrorCode.BOOKING_STATE_INVALID, "No pending counter payment for this booking"));
        if (paid) {
            applyOutcome(p.getId(), Outcome.SUCCESS, "COUNTER-" + staffId, null, "COUNTER");
        } else {
            applyOutcome(p.getId(), Outcome.CANCELLED, null, "Cancelled at counter", "COUNTER");
            Booking locked = bookingRepository.findForUpdate(b.getId()).orElseThrow();
            lifecycle.abandon(locked, BookingStatus.CANCELLED, "Cancelled at the counter");
        }
        auditService.record(staffId, paid ? "COUNTER_PAYMENT_RECEIVED" : "COUNTER_RESERVATION_CANCELLED", "BOOKING", b.getId(), null);
        return toStatus(paymentRepository.findById(p.getId()).orElseThrow());
    }

    public PaymentStatusResponse toStatus(Payment p) {
        Booking b = p.getBooking();
        return new PaymentStatusResponse(p.getPublicId(), b.getId(), b.getReference(), p.getStatus().name(), b.getStatus().name(),
                p.getFailureReason(), p.getProvider().name(), p.getMethod().name(), p.getAmount(), p.getUpdatedAt());
    }

    Payment owned(Long userId, UUID publicId) {
        Payment p = paymentRepository.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (!p.getUserId().equals(userId)) throw ApiException.notFound("Payment");
        return p;
    }

    private void transition(Payment p, PaymentStatus to, String type, String note, Map<String, Object> payload) {
        PaymentStatus from = p.getStatus();
        p.setStatus(to);
        paymentRepository.save(p);
        PaymentTransaction t = new PaymentTransaction();
        t.setPaymentId(p.getId());
        t.setType(type.length() > 30 ? type.substring(0, 30) : type);
        t.setFromStatus(from == null ? null : from.name());
        t.setToStatus(to.name());
        t.setProviderRef(p.getProviderPaymentId() != null ? p.getProviderPaymentId() : p.getProviderOrderId());
        Map<String, Object> data = new HashMap<>();
        if (payload != null) data.putAll(payload);
        if (note != null) data.put("note", note);
        t.setPayload(data.isEmpty() ? null : data);
        transactionRepository.save(t);
    }

    private void recordTransaction(Payment p, String type, String detail, String providerRef) {
        PaymentTransaction t = new PaymentTransaction();
        t.setPaymentId(p.getId());
        t.setType(type);
        t.setFromStatus(p.getStatus().name());
        t.setToStatus(p.getStatus().name());
        t.setProviderRef(providerRef);
        t.setPayload(detail == null ? null : Map.of("detail", detail));
        transactionRepository.save(t);
    }
}
