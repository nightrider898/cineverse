package com.cineverse.admin;

import com.cineverse.admin.AdminDtos.*;
import com.cineverse.booking.BookingMapper;
import com.cineverse.config.CacheConfig;
import com.cineverse.dto.BookingDtos.RefundResponse;
import com.cineverse.dto.PaymentDtos.PaymentAdminResponse;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.BookingStatus;
import com.cineverse.entity.enums.PaymentStatus;
import com.cineverse.entity.enums.RefundStatus;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.payment.RefundService;
import com.cineverse.repository.*;
import com.cineverse.service.AuditService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Admin operations: food, coupons, offers, users, bookings, payments, refunds, audit log. */
@Service
@RequiredArgsConstructor
public class AdminOpsService {
    private final FoodItemRepository foodItemRepository;
    private final CouponRepository couponRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefundService refundService;
    private final BookingMapper bookingMapper;
    private final AdminAccess access;
    private final AuditService audit;

    // ---------------------------------------------------------------- food
    @Transactional(readOnly = true)
    public List<AdminFoodResponse> food() {
        return foodItemRepository.findAllByOrderBySortOrderAscNameAsc().stream().map(this::food).toList();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.FOOD_MENU, allEntries = true)
    public AdminFoodResponse upsertFood(Long id, FoodUpsertRequest req) {
        if (req.cinemaId() == null) access.requireAdmin(); else access.requireCinemaAccess(req.cinemaId());
        FoodItem f = id == null ? new FoodItem() : foodItemRepository.findById(id).orElseThrow(() -> ApiException.notFound("Food item"));
        if (id != null && f.getCinemaId() != null) access.requireCinemaAccess(f.getCinemaId());
        f.setCinemaId(req.cinemaId());
        f.setName(req.name().trim());
        f.setDescription(req.description());
        f.setCategory(req.category());
        f.setPrice(req.price());
        f.setImageUrl(req.imageUrl());
        f.setVeg(req.veg());
        f.setAvailable(req.available());
        f.setSortOrder(req.sortOrder());
        foodItemRepository.save(f);
        audit.record(id == null ? "ADMIN_CREATED_FOOD" : "ADMIN_UPDATED_FOOD", "FOOD_ITEM", f.getId(), Map.of("price", f.getPrice()));
        return food(f);
    }

    private AdminFoodResponse food(FoodItem f) {
        return new AdminFoodResponse(f.getId(), f.getCinemaId(), f.getName(), f.getDescription(), f.getCategory(), f.getPrice(),
                f.getImageUrl(), f.isVeg(), f.isAvailable(), f.getSortOrder());
    }

    // ---------------------------------------------------------------- coupons
    @Transactional(readOnly = true)
    public List<AdminCouponResponse> coupons() {
        access.requireAdmin();
        return couponRepository.findAllByOrderByCreatedAtDesc().stream().map(this::coupon).toList();
    }

    @Transactional
    public AdminCouponResponse upsertCoupon(Long id, CouponUpsertRequest req) {
        access.requireAdmin();
        if (!req.validTo().isAfter(req.validFrom())) throw new ApiException(ErrorCode.VALIDATION_FAILED, "validTo must be after validFrom");
        couponRepository.findByCode(req.code()).filter(c -> !c.getId().equals(id)).ifPresent(c -> {
            throw new ApiException(ErrorCode.CONFLICT, "Coupon code already exists");
        });
        Coupon c = id == null ? new Coupon() : couponRepository.findById(id).orElseThrow(() -> ApiException.notFound("Coupon"));
        c.setCode(req.code().toUpperCase());
        c.setDescription(req.description());
        c.setDiscountType(req.discountType());
        c.setDiscountValue(req.discountValue());
        c.setMaxDiscount(req.maxDiscount());
        c.setMinOrderAmount(req.minOrderAmount());
        c.setValidFrom(req.validFrom());
        c.setValidTo(req.validTo());
        c.setMovieId(req.movieId());
        c.setCityId(req.cityId());
        c.setCinemaId(req.cinemaId());
        c.setPaymentMethod(req.paymentMethod() == null || req.paymentMethod().isBlank() ? null : req.paymentMethod());
        c.setFirstBookingOnly(req.firstBookingOnly());
        c.setUsageLimit(req.usageLimit());
        c.setPerUserLimit(req.perUserLimit());
        c.setActive(req.active());
        couponRepository.save(c);
        audit.record(id == null ? "ADMIN_CREATED_COUPON" : "ADMIN_UPDATED_COUPON", "COUPON", c.getId(), Map.of("code", c.getCode(), "active", c.isActive()));
        return coupon(c);
    }

    private AdminCouponResponse coupon(Coupon c) {
        return new AdminCouponResponse(c.getId(), c.getCode(), c.getDescription(), c.getDiscountType().name(), c.getDiscountValue(),
                c.getMaxDiscount(), c.getMinOrderAmount(), c.getValidFrom(), c.getValidTo(), c.getMovieId(), c.getCityId(), c.getCinemaId(),
                c.getPaymentMethod(), c.isFirstBookingOnly(), c.getUsageLimit(), c.getPerUserLimit(), c.getTimesUsed(), c.isActive());
    }

    // ---------------------------------------------------------------- offers
    @Transactional(readOnly = true)
    public List<AdminOfferResponse> offers() {
        return offerRepository.findAllByOrderBySortOrderAsc().stream().map(this::offer).toList();
    }

    @Transactional
    public AdminOfferResponse upsertOffer(Long id, OfferUpsertRequest req) {
        access.requireAdmin();
        Offer o = id == null ? new Offer() : offerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Offer"));
        o.setTitle(req.title());
        o.setDescription(req.description());
        o.setOfferType(req.offerType());
        o.setCouponCode(req.couponCode() == null || req.couponCode().isBlank() ? null : req.couponCode().toUpperCase());
        o.setCityId(req.cityId());
        o.setMovieId(req.movieId());
        o.setImageUrl(req.imageUrl());
        o.setTerms(req.terms());
        o.setValidFrom(req.validFrom());
        o.setValidTo(req.validTo());
        o.setActive(req.active());
        o.setSortOrder(req.sortOrder());
        offerRepository.save(o);
        audit.record(id == null ? "ADMIN_CREATED_OFFER" : "ADMIN_UPDATED_OFFER", "OFFER", o.getId(), null);
        return offer(o);
    }

    @Transactional
    public void deleteOffer(Long id) {
        access.requireAdmin();
        offerRepository.deleteById(id);
        audit.record("ADMIN_DELETED_OFFER", "OFFER", id, null);
    }

    private AdminOfferResponse offer(Offer o) {
        return new AdminOfferResponse(o.getId(), o.getTitle(), o.getDescription(), o.getOfferType(), o.getCouponCode(), o.getCityId(),
                o.getMovieId(), o.getImageUrl(), o.getTerms(), o.getValidFrom(), o.getValidTo(), o.isActive(), o.getSortOrder());
    }

    // ---------------------------------------------------------------- users
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> users(String q, String role, int page, int size) {
        access.requireAdmin();
        return userRepository.search(q == null || q.isBlank() ? null : q.trim(), role == null || role.isBlank() ? null : role,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"))).map(this::user);
    }

    @Transactional
    public AdminUserResponse updateUser(Long id, UserUpdateRequest req) {
        var admin = access.requireAdmin();
        User u = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getId().equals(admin.id()) && (!req.enabled() || !req.roles().contains("ADMIN"))) {
            throw new ApiException(ErrorCode.CONFLICT, "You cannot remove your own admin access");
        }
        u.getRoles().clear();
        u.getRoles().addAll(roleRepository.findByNameIn(req.roles()));
        boolean disabling = u.isEnabled() && !req.enabled();
        u.setEnabled(req.enabled());
        if (disabling) refreshTokenRepository.revokeAllForUser(u.getId(), Instant.now());
        audit.record("ADMIN_UPDATED_USER", "USER", id, Map.of("roles", req.roles(), "enabled", req.enabled()));
        return user(u);
    }

    private AdminUserResponse user(User u) {
        return new AdminUserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                u.getRoles().stream().map(Role::getName).sorted().toList(), u.isEnabled(), u.getCreatedAt(), u.getLastLoginAt());
    }

    // ---------------------------------------------------------------- bookings, payments, refunds
    @Transactional(readOnly = true)
    public Page<AdminBookingRow> bookings(String status, String q, LocalDate date, int page, int size) {
        access.requireAdmin();
        Specification<Booking> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), BookingStatus.valueOf(status)));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("reference")), like), cb.like(cb.lower(root.get("user").get("email")), like)));
            }
            if (date != null) {
                ZoneId zone = ZoneId.of("Asia/Kolkata");
                ps.add(cb.between(root.get("createdAt"), date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return bookingRepository.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(b -> new AdminBookingRow(bookingMapper.toResponse(b, false), b.getUser().getId(), b.getUser().getFullName(), b.getUser().getEmail()));
    }

    @Transactional(readOnly = true)
    public Page<PaymentAdminResponse> payments(String status, String provider, int page, int size) {
        access.requireAdmin();
        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), PaymentStatus.valueOf(status)));
            if (provider != null && !provider.isBlank()) ps.add(cb.equal(root.get("provider"), com.cineverse.entity.enums.PaymentProviderType.valueOf(provider)));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return paymentRepository.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(p -> new PaymentAdminResponse(p.getPublicId(), p.getBooking().getId(), p.getBooking().getReference(), p.getUserId(),
                        p.getProvider().name(), p.getMethod().name(), p.getStatus().name(), p.getAmount(), p.getRefundedAmount(),
                        p.getProviderOrderId(), p.getProviderPaymentId(), p.getFailureReason(), p.getCreatedAt(), p.getCompletedAt()));
    }

    @Transactional(readOnly = true)
    public Page<RefundResponse> refunds(String status, int page, int size) {
        access.requireAdmin();
        return refundRepository.search(status == null || status.isBlank() ? null : RefundStatus.valueOf(status),
                PageRequest.of(page, Math.min(size, 100))).map(bookingMapper::refund);
    }

    public RefundResponse retryRefund(Long id) {
        var admin = access.requireAdmin();
        refundService.retry(id, admin.id());
        return refundById(id);
    }

    public RefundResponse completeRefund(Long id) {
        var admin = access.requireAdmin();
        refundService.markCompletedManually(id, admin.id());
        return refundById(id);
    }

    @Transactional(readOnly = true)
    public RefundResponse refundById(Long id) {
        return refundRepository.findById(id).map(bookingMapper::refund).orElseThrow(() -> ApiException.notFound("Refund"));
    }

    // ---------------------------------------------------------------- audit
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> audit(String action, String resource, int page, int size) {
        access.requireAdmin();
        return auditLogRepository.search(action == null || action.isBlank() ? null : action, resource == null || resource.isBlank() ? null : resource,
                        PageRequest.of(page, Math.min(size, 200)))
                .map(a -> new AuditLogResponse(a.getId(), a.getUserId(), a.getAction(), a.getResource(), a.getResourceId(), a.getIpAddress(),
                        a.getRequestId(), a.getMetadata(), a.getCreatedAt()));
    }
}
