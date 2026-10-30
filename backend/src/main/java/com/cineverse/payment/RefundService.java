package com.cineverse.payment;

import com.cineverse.entity.Booking;
import com.cineverse.entity.Payment;
import com.cineverse.entity.Refund;
import com.cineverse.entity.enums.*;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.notification.NotificationService;
import com.cineverse.repository.RefundRepository;
import com.cineverse.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;

/**
 * Refund workflow: REQUESTED -> PROCESSING -> COMPLETED | FAILED (retried up to 5 times).
 * A refund is only marked COMPLETED when the gateway reports it processed; we never claim instant refunds.
 */
@Slf4j
@Service
public class RefundService {
    private static final int MAX_ATTEMPTS = 5;

    private final RefundRepository refundRepository;
    private final PaymentRouter router;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final Clock clock;
    private final TransactionTemplate newTx;

    public RefundService(RefundRepository refundRepository, PaymentRouter router, NotificationService notificationService,
                         AuditService auditService, Clock clock, PlatformTransactionManager txm) {
        this.refundRepository = refundRepository;
        this.router = router;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.clock = clock;
        this.newTx = new TransactionTemplate(txm);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Refund request(Booking booking, Payment payment, BigDecimal amount, String reason) {
        var existing = refundRepository.findByPaymentId(payment.getId());
        if (existing.isPresent()) return existing.get();
        Refund r = new Refund();
        r.setBooking(booking);
        r.setPayment(payment);
        r.setAmount(amount.min(payment.getAmount()));
        r.setReason(reason);
        r.setStatus(RefundStatus.REQUESTED);
        if (payment.getProvider() == PaymentProviderType.VENUE) {
            r.setStatus(RefundStatus.PROCESSING);
            r.setFailureReason("Paid at the venue: refund is handed over at the box office");
        }
        refundRepository.save(r);
        notificationService.notify(booking.getUser().getId(), NotificationType.REFUND, "Refund initiated for " + booking.getReference(),
                "₹" + r.getAmount().toPlainString() + " will be refunded to your original payment method.", "/bookings/" + booking.getId());
        auditService.record(booking.getUser().getId(), "REFUND_CREATED", "REFUND", r.getId(),
                Map.of("bookingId", booking.getId(), "amount", r.getAmount(), "reason", reason));
        log.info("refund_requested refundId={} bookingId={} amount={}", r.getId(), booking.getId(), r.getAmount());
        return r;
    }

    @Scheduled(fixedDelay = 20_000, initialDelay = 10_000)
    public void processDue() {
        var ids = refundRepository.findProcessableIds(EnumSet.of(RefundStatus.REQUESTED, RefundStatus.FAILED, RefundStatus.PROCESSING),
                MAX_ATTEMPTS, PageRequest.of(0, 50));
        for (Long id : ids) {
            try {
                newTx.executeWithoutResult(s -> process(id));
            } catch (RuntimeException e) {
                log.warn("refund_processing_error refundId={} error={}", id, e.getMessage());
                newTx.executeWithoutResult(s -> refundRepository.findForUpdate(id).ifPresent(r -> {
                    r.setStatus(RefundStatus.FAILED);
                    r.setAttempts(r.getAttempts() + 1);
                    r.setFailureReason(e.getMessage());
                }));
            }
        }
    }

    void process(Long id) {
        Refund r = refundRepository.findForUpdate(id).orElse(null);
        if (r == null || r.getStatus() == RefundStatus.COMPLETED) return;
        Payment payment = r.getPayment();
        if (payment.getProvider() == PaymentProviderType.VENUE) return; // settled manually by staff/admin

        PaymentProvider provider = router.provider(payment.getProvider());
        if (!provider.isEnabled()) {
            r.setStatus(RefundStatus.FAILED);
            r.setAttempts(r.getAttempts() + 1);
            r.setFailureReason(payment.getProvider() + " is not configured on this server");
            return;
        }
        PaymentProvider.RefundResult result;
        if (r.getStatus() == RefundStatus.PROCESSING && r.getProviderRefundId() != null) {
            result = provider.refundStatus(payment, r);
        } else if (r.getStatus() == RefundStatus.PROCESSING) {
            return;
        } else {
            r.setAttempts(r.getAttempts() + 1);
            r.setStatus(RefundStatus.PROCESSING);
            result = provider.refund(payment, r);
        }
        apply(r, result.outcome(), result.providerRefundId(), result.failureReason());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyProviderUpdate(String providerRefundId, PaymentProvider.Outcome outcome, String failureReason) {
        refundRepository.findByProviderRefundId(providerRefundId)
                .flatMap(r -> refundRepository.findForUpdate(r.getId()))
                .ifPresent(r -> apply(r, outcome, providerRefundId, failureReason));
    }

    @Transactional
    public Refund markCompletedManually(Long refundId, Long adminId) {
        Refund r = refundRepository.findForUpdate(refundId).orElseThrow(() -> ApiException.notFound("Refund"));
        if (r.getStatus() == RefundStatus.COMPLETED) return r;
        apply(r, PaymentProvider.Outcome.SUCCESS, r.getProviderRefundId() == null ? "MANUAL-" + r.getId() : r.getProviderRefundId(), null);
        auditService.record(adminId, "REFUND_MARKED_COMPLETED", "REFUND", r.getId(), null);
        return r;
    }

    @Transactional
    public Refund retry(Long refundId, Long adminId) {
        Refund r = refundRepository.findForUpdate(refundId).orElseThrow(() -> ApiException.notFound("Refund"));
        if (r.getStatus() == RefundStatus.COMPLETED) throw new ApiException(ErrorCode.CONFLICT, "Refund already completed");
        r.setStatus(RefundStatus.REQUESTED);
        r.setAttempts(0);
        r.setFailureReason(null);
        auditService.record(adminId, "REFUND_RETRY", "REFUND", r.getId(), null);
        return r;
    }

    private void apply(Refund r, PaymentProvider.Outcome outcome, String providerRefundId, String failureReason) {
        if (providerRefundId != null) r.setProviderRefundId(providerRefundId);
        switch (outcome) {
            case SUCCESS -> complete(r);
            case FAILED, CANCELLED -> {
                r.setStatus(RefundStatus.FAILED);
                r.setFailureReason(failureReason == null ? "Gateway rejected the refund" : failureReason);
                log.warn("refund_failed refundId={} reason={}", r.getId(), r.getFailureReason());
            }
            case PENDING -> r.setStatus(RefundStatus.PROCESSING);
        }
    }

    private void complete(Refund r) {
        Instant now = Instant.now(clock);
        r.setStatus(RefundStatus.COMPLETED);
        r.setProcessedAt(now);
        r.setFailureReason(null);
        Payment p = r.getPayment();
        p.setRefundedAmount(p.getRefundedAmount().add(r.getAmount()));
        p.setStatus(p.getRefundedAmount().compareTo(p.getAmount()) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        Booking b = r.getBooking();
        if (b.getStatus() == BookingStatus.REFUND_PENDING) {
            b.setStatus(BookingStatus.REFUNDED);
        }
        notificationService.notify(b.getUser().getId(), NotificationType.REFUND, "Refund completed for " + b.getReference(),
                "₹" + r.getAmount().toPlainString() + " has been refunded. It may take 5-7 working days to reflect in your account.",
                "/bookings/" + b.getId());
        notificationService.emailAfterCommit(b.getUser().getEmail(), "Refund processed for " + b.getReference(), "refund-update",
                Map.of("name", b.getUser().getFullName(), "amount", r.getAmount().toPlainString(), "reference", b.getReference(), "status", "COMPLETED"));
        auditService.record(null, "REFUND_COMPLETED", "REFUND", r.getId(), Map.of("bookingId", b.getId(), "amount", r.getAmount()));
        log.info("refund_completed refundId={} bookingId={}", r.getId(), b.getId());
    }
}
