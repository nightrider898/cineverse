package com.cineverse.service;

import com.cineverse.config.AppProperties;
import com.cineverse.dto.BookingDtos.PriceBreakdown;
import com.cineverse.entity.Booking;
import com.cineverse.entity.BookingSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The only place prices are computed. Every input comes from the database (seat price snapshots,
 * food menu prices, coupon rules); nothing the client sends is trusted.
 *
 * <pre>
 * gross       = tickets + food
 * fee         = min(perTicket * tickets, feeCap)
 * tax         = fee * taxRateOnFee            (GST on the convenience fee; configurable)
 * discount    = coupon(gross), capped at gross
 * grandTotal  = gross + fee + tax - discount
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class PricingService {
    private final AppProperties props;
    private final CouponService couponService;

    public void reprice(Booking booking) {
        BigDecimal tickets = booking.getSeats().stream().map(BookingSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(booking.getItems().stream().map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal food = booking.getFoodOrder() == null ? BigDecimal.ZERO : booking.getFoodOrder().getSubtotal();
        BigDecimal gross = tickets.add(food);

        AppProperties.Pricing p = props.pricing();
        BigDecimal fee = p.convenienceFeePerTicket().multiply(BigDecimal.valueOf(booking.ticketCount()));
        if (p.convenienceFeeMax() != null && fee.compareTo(p.convenienceFeeMax()) > 0) {
            fee = p.convenienceFeeMax();
        }
        BigDecimal tax = fee.multiply(p.taxRateOnFee()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = booking.getCoupon() == null ? BigDecimal.ZERO : couponService.computeDiscount(booking.getCoupon(), gross);

        booking.setTicketSubtotal(scale(tickets));
        booking.setFoodSubtotal(scale(food));
        booking.setConvenienceFee(scale(fee));
        booking.setTax(tax);
        booking.setDiscount(scale(discount));
        booking.setGrandTotal(scale(gross.add(fee).add(tax).subtract(discount).max(BigDecimal.ZERO)));
    }

    public PriceBreakdown breakdown(Booking b) {
        return new PriceBreakdown(b.getTicketSubtotal(), b.getFoodSubtotal(), b.getConvenienceFee(), b.getTax(),
                b.getDiscount(), b.getGrandTotal(), props.pricing().currency());
    }

    public static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
