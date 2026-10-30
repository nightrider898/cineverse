package com.cineverse.booking;

import com.cineverse.dto.BookingDtos.*;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.entity.enums.BookingType;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.repository.*;
import com.cineverse.service.AuditService;
import com.cineverse.service.CouponService;
import com.cineverse.service.PricingService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.*;

/** Movie booking lifecycle up to payment: create from a seat lock, food, coupons, reads. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {
    public static final Set<BookingStatus> UPCOMING = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.PAYMENT_PENDING);
    public static final Set<BookingStatus> CANCELLED = EnumSet.of(BookingStatus.CANCELLED, BookingStatus.REFUND_PENDING, BookingStatus.REFUNDED, BookingStatus.EXPIRED);

    private final BookingRepository bookingRepository;
    private final SeatLockRepository seatLockRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final FoodItemRepository foodItemRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final CouponService couponService;
    private final BookingMapper mapper;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public BookingResponse createFromLock(Long userId, CreateBookingRequest req) {
        Instant now = Instant.now(clock);
        SeatLock lock = seatLockRepository.findForUpdate(req.lockId()).orElseThrow(() -> new ApiException(ErrorCode.SEAT_LOCK_EXPIRED));
        if (!lock.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This seat selection belongs to another customer");
        }
        if (!lock.isUsable(now)) {
            throw new ApiException(ErrorCode.SEAT_LOCK_EXPIRED);
        }

        Optional<Booking> existing = bookingRepository.findBySeatLockId(lock.getId());
        if (existing.isPresent()) {
            Booking b = existing.get();
            if (!b.isOpen()) throw new ApiException(ErrorCode.BOOKING_STATE_INVALID);
            replaceFood(b, req.food());
            reprice(b);
            return mapper.toResponse(b, false);
        }

        Show show = showRepository.findById(lock.getShowId()).orElseThrow(() -> new ApiException(ErrorCode.SHOW_NOT_FOUND));
        List<ShowSeat> seats = showSeatRepository.lockByLockId(lock.getId());
        if (seats.isEmpty() || seats.size() != lock.labels().size()) {
            throw new ApiException(ErrorCode.SEAT_LOCK_EXPIRED);
        }

        Booking b = new Booking();
        b.setReference(nextReference());
        b.setUser(userRepository.getReferenceById(userId));
        b.setBookingType(BookingType.MOVIE);
        b.setShow(show);
        b.setSeatLockId(lock.getId());
        b.setStatus(BookingStatus.PENDING);
        b.setExpiresAt(lock.getExpiresAt());
        for (ShowSeat s : seats) {
            BookingSeat bs = new BookingSeat();
            bs.setBooking(b);
            bs.setShowSeatId(s.getId());
            bs.setSeatLabel(s.getLabel());
            bs.setSeatType(s.getSeatTypeCode());
            bs.setPrice(s.getPrice());
            b.getSeats().add(bs);
        }
        replaceFood(b, req.food());
        pricingService.reprice(b);
        bookingRepository.save(b);
        auditService.record(userId, "USER_CREATED_BOOKING", "BOOKING", b.getId(),
                Map.of("reference", b.getReference(), "showId", show.getId(), "seats", lock.getSeatLabels()));
        log.info("booking_created bookingId={} ref={} userId={} total={}", b.getId(), b.getReference(), userId, b.getGrandTotal());
        return mapper.toResponse(b, false);
    }

    @Transactional
    public BookingResponse updateFood(Long userId, Long bookingId, UpdateFoodRequest req) {
        Booking b = openOwned(userId, bookingId);
        if (b.getBookingType() != BookingType.MOVIE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Food can only be added to movie bookings");
        }
        replaceFood(b, req.food());
        reprice(b);
        return mapper.toResponse(b, false);
    }

    @Transactional
    public BookingResponse applyCoupon(Long userId, CouponApplyRequest req) {
        Booking b = openOwned(userId, req.bookingId());
        Coupon coupon = couponService.findActive(req.couponCode());
        pricingService.reprice(b); // make sure subtotals are current before checking minimum order
        couponService.validate(coupon, b, b.getPaymentMethod());
        b.setCoupon(coupon);
        b.setCouponCode(coupon.getCode().toUpperCase());
        pricingService.reprice(b);
        auditService.record(userId, "COUPON_APPLIED", "BOOKING", b.getId(), Map.of("coupon", coupon.getCode(), "discount", b.getDiscount()));
        return mapper.toResponse(b, false);
    }

    @Transactional
    public BookingResponse removeCoupon(Long userId, Long bookingId) {
        Booking b = openOwned(userId, bookingId);
        b.setCoupon(null);
        b.setCouponCode(null);
        pricingService.reprice(b);
        return mapper.toResponse(b, false);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long userId, Long bookingId, boolean isStaff) {
        Booking b = bookingRepository.findById(bookingId).orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND));
        if (!isStaff && !b.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.BOOKING_NOT_FOUND);
        }
        return mapper.toResponse(b, true);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> listForUser(Long userId, String tab, int page, int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 50));
        Instant now = Instant.now(clock);
        Page<Booking> result = switch (tab == null ? "upcoming" : tab.toLowerCase()) {
            case "completed" -> bookingRepository.findPast(userId, EnumSet.of(BookingStatus.CONFIRMED), now, pr);
            case "cancelled" -> bookingRepository.findByUserIdAndStatusInOrderByUpdatedAtDesc(userId, CANCELLED, pr);
            default -> bookingRepository.findUpcoming(userId, UPCOMING, now, pr);
        };
        return result.map(b -> mapper.toResponse(b, false));
    }

    /**
     * Re-prices and re-validates the coupon. A coupon that no longer qualifies (e.g. food removed and the
     * minimum is no longer met) is dropped rather than failing the request.
     */
    void reprice(Booking b) {
        pricingService.reprice(b);
        if (b.getCoupon() != null) {
            try {
                couponService.validate(b.getCoupon(), b, null);
            } catch (ApiException e) {
                b.setCoupon(null);
                b.setCouponCode(null);
                pricingService.reprice(b);
            }
        }
    }

    private void replaceFood(Booking b, List<FoodLine> lines) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        if (lines != null) {
            for (FoodLine l : lines) quantities.merge(l.foodItemId(), l.quantity(), Integer::sum);
        }
        if (quantities.isEmpty()) {
            b.setFoodOrder(null);
            return;
        }
        Long cinemaId = b.getShow().getCinema().getId();
        Map<Long, FoodItem> menu = new HashMap<>();
        foodItemRepository.findAllById(quantities.keySet()).forEach(f -> menu.put(f.getId(), f));
        FoodOrder order = b.getFoodOrder();
        if (order == null) {
            order = new FoodOrder();
            order.setBooking(b);
            b.setFoodOrder(order);
        }
        order.getItems().clear();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var e : quantities.entrySet()) {
            FoodItem f = menu.get(e.getKey());
            if (f == null || !f.isAvailable() || (f.getCinemaId() != null && !f.getCinemaId().equals(cinemaId))) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "A selected food item is not available at this cinema");
            }
            if (e.getValue() > 20) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Maximum 20 of each item");
            FoodOrderItem item = new FoodOrderItem();
            item.setFoodOrder(order);
            item.setFoodItemId(f.getId());
            item.setItemName(f.getName());
            item.setUnitPrice(f.getPrice());
            item.setQuantity(e.getValue());
            order.getItems().add(item);
            subtotal = subtotal.add(f.getPrice().multiply(BigDecimal.valueOf(e.getValue())));
        }
        order.setSubtotal(subtotal);
    }

    Booking openOwned(Long userId, Long bookingId) {
        Booking b = bookingRepository.findForUpdate(bookingId).orElseThrow(() -> new ApiException(ErrorCode.BOOKING_NOT_FOUND));
        if (!b.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.BOOKING_NOT_FOUND);
        }
        if (!b.isOpen()) {
            throw new ApiException(b.getStatus() == BookingStatus.EXPIRED ? ErrorCode.BOOKING_EXPIRED : ErrorCode.BOOKING_STATE_INVALID);
        }
        if (b.getExpiresAt() != null && !b.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new ApiException(ErrorCode.BOOKING_EXPIRED);
        }
        return b;
    }

    public String nextReference() {
        long seq = ((Number) entityManager.createNativeQuery("select nextval('booking_reference_seq')").getSingleResult()).longValue();
        return "CV-%d-%06d".formatted(Year.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).getValue(), seq);
    }
}
