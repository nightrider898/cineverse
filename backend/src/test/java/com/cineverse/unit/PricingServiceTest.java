package com.cineverse.unit;

import com.cineverse.config.AppProperties;
import com.cineverse.entity.*;
import com.cineverse.entity.enums.BookingType;
import com.cineverse.entity.enums.DiscountType;
import com.cineverse.service.CouponService;
import com.cineverse.service.PricingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit test of the price engine (no Spring, no DB). */
class PricingServiceTest {
    private final AppProperties props = new AppProperties("CineVerse", "http://localhost:3000", "Asia/Kolkata", null, null, null, null, null, null,
            new AppProperties.Pricing(new BigDecimal("25"), new BigDecimal("200"), new BigDecimal("0.18"), "INR"),
            null, null, null, null, null, null, null, null);
    private final CouponService coupons = new CouponService(null, null, null, java.time.Clock.systemUTC());
    private final PricingService pricing = new PricingService(props, coupons);

    @Test
    void computesTotalsFromServerSidePrices() {
        Booking b = booking(300, 300);
        FoodOrder food = new FoodOrder();
        food.setSubtotal(new BigDecimal("250"));
        b.setFoodOrder(food);
        pricing.reprice(b);
        assertThat(b.getTicketSubtotal()).isEqualByComparingTo("600");
        assertThat(b.getFoodSubtotal()).isEqualByComparingTo("250");
        assertThat(b.getConvenienceFee()).isEqualByComparingTo("50");
        assertThat(b.getTax()).isEqualByComparingTo("9.00");
        assertThat(b.getGrandTotal()).isEqualByComparingTo("909.00");
    }

    @Test
    void percentCouponIsCappedAndNeverExceedsOrder() {
        Booking b = booking(500, 500);
        Coupon c = new Coupon();
        c.setDiscountType(DiscountType.PERCENT);
        c.setDiscountValue(new BigDecimal("10"));
        c.setMaxDiscount(new BigDecimal("50"));
        b.setCoupon(c);
        pricing.reprice(b);
        assertThat(b.getDiscount()).isEqualByComparingTo("50");

        Coupon flat = new Coupon();
        flat.setDiscountType(DiscountType.FLAT);
        flat.setDiscountValue(new BigDecimal("5000"));
        assertThat(coupons.computeDiscount(flat, new BigDecimal("400"))).isEqualByComparingTo("400");
    }

    @Test
    void convenienceFeeIsCapped() {
        Booking b = booking(100, 100, 100, 100, 100, 100, 100, 100, 100, 100);
        pricing.reprice(b);
        assertThat(b.getConvenienceFee()).isEqualByComparingTo("200");
    }

    private static Booking booking(int... seatPrices) {
        Booking b = new Booking();
        b.setBookingType(BookingType.MOVIE);
        for (int p : seatPrices) {
            BookingSeat s = new BookingSeat();
            s.setPrice(BigDecimal.valueOf(p));
            b.getSeats().add(s);
        }
        return b;
    }
}
