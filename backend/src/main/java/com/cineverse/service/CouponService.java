package com.cineverse.service;

import com.cineverse.entity.Booking;
import com.cineverse.entity.Coupon;
import com.cineverse.entity.CouponUsage;
import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.entity.enums.BookingType;
import com.cineverse.entity.enums.DiscountType;
import com.cineverse.entity.enums.PaymentMethod;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.repository.BookingRepository;
import com.cineverse.repository.CouponRepository;
import com.cineverse.repository.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CouponService {
    private static final Set<BookingStatus> COMPLETED_BOOKINGS = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.REFUND_PENDING, BookingStatus.REFUNDED);
    private static final Set<PaymentMethod> UPI_FAMILY = EnumSet.of(PaymentMethod.UPI, PaymentMethod.GPAY, PaymentMethod.PHONEPE);

    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    public Coupon findActive(String code) {
        return couponRepository.findByCode(code.trim())
                .orElseThrow(() -> new ApiException(ErrorCode.COUPON_INVALID, "Coupon code " + code.trim().toUpperCase() + " does not exist"));
    }

    /**
     * Validates every coupon condition against the booking as stored on the server.
     * {@code method} is null while browsing checkout; payment-method rules are enforced again at payment time.
     */
    public void validate(Coupon c, Booking booking, PaymentMethod method) {
        Instant now = Instant.now(clock);
        Long userId = booking.getUser().getId();
        if (!c.isActive()) {
            throw new ApiException(ErrorCode.COUPON_INVALID, "This coupon is no longer active");
        }
        if (now.isBefore(c.getValidFrom()) || !now.isBefore(c.getValidTo())) {
            throw new ApiException(ErrorCode.COUPON_EXPIRED);
        }
        if (c.getUsageLimit() != null && c.getTimesUsed() >= c.getUsageLimit()) {
            throw new ApiException(ErrorCode.COUPON_INVALID, "This coupon has been fully redeemed");
        }
        if (usageRepository.countByCouponIdAndUserId(c.getId(), userId) >= c.getPerUserLimit()) {
            throw new ApiException(ErrorCode.COUPON_INVALID, "You have already used this coupon the maximum number of times");
        }
        if (c.isFirstBookingOnly() && bookingRepository.existsByUserIdAndStatusIn(userId, COMPLETED_BOOKINGS)) {
            throw new ApiException(ErrorCode.COUPON_INVALID, "This coupon is only valid on your first booking");
        }
        if (c.getMovieId() != null && (booking.getBookingType() != BookingType.MOVIE || !c.getMovieId().equals(booking.getShow().getMovie().getId()))) {
            throw new ApiException(ErrorCode.COUPON_INVALID, "This coupon is not valid for this movie");
        }
        if (c.getCinemaId() != null && (booking.getBookingType() != BookingType.MOVIE || !c.getCinemaId().equals(booking.getShow().getCinema().getId()))) {
            throw new ApiException(ErrorCode.COUPON_INVALID, "This coupon is not valid at this cinema");
        }
        if (c.getCityId() != null && !c.getCityId().equals(cityOf(booking))) {
            throw new ApiException(ErrorCode.COUPON_INVALID, "This coupon is not valid in this city");
        }
        BigDecimal gross = booking.getTicketSubtotal().add(booking.getFoodSubtotal());
        if (gross.compareTo(c.getMinOrderAmount()) < 0) {
            throw new ApiException(ErrorCode.COUPON_INVALID, "Minimum order of ₹" + c.getMinOrderAmount().stripTrailingZeros().toPlainString() + " required");
        }
        if (method != null && c.getPaymentMethod() != null && !methodMatches(c.getPaymentMethod(), method)) {
            throw new ApiException(ErrorCode.COUPON_INVALID, "Coupon " + c.getCode() + " is valid only with " + c.getPaymentMethod() + " payments");
        }
    }

    public BigDecimal computeDiscount(Coupon c, BigDecimal gross) {
        BigDecimal discount = c.getDiscountType() == DiscountType.PERCENT
                ? gross.multiply(c.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : c.getDiscountValue();
        if (c.getMaxDiscount() != null) discount = discount.min(c.getMaxDiscount());
        return discount.min(gross).max(BigDecimal.ZERO);
    }

    /** Records a redemption when the booking is confirmed. Payment already succeeded, so this never fails the booking. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUsage(Booking booking) {
        if (booking.getCoupon() == null || usageRepository.existsByBookingId(booking.getId())) return;
        Coupon c = couponRepository.findForUpdate(booking.getCoupon().getId()).orElseThrow();
        c.setTimesUsed(c.getTimesUsed() + 1);
        CouponUsage u = new CouponUsage();
        u.setCouponId(c.getId());
        u.setUserId(booking.getUser().getId());
        u.setBookingId(booking.getId());
        u.setDiscount(booking.getDiscount());
        usageRepository.save(u);
    }

    public static boolean methodMatches(String required, PaymentMethod actual) {
        if ("UPI".equalsIgnoreCase(required)) return UPI_FAMILY.contains(actual);
        return required.equalsIgnoreCase(actual.name());
    }

    private Long cityOf(Booking b) {
        return b.getBookingType() == BookingType.MOVIE ? b.getShow().getCinema().getCity().getId()
                : b.getEventSession().getEvent().getCity().getId();
    }
}
