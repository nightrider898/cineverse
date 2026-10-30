package com.cineverse.admin;

import com.cineverse.admin.AdminDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Reporting queries run directly in SQL (read-only). "Sold" bookings are those that reached CONFIRMED,
 * including ones later cancelled/refunded; refunds are subtracted separately for net sales.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String SOLD = "('CONFIRMED','REFUND_PENDING','REFUNDED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public DashboardResponse dashboard() {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        Instant dayStart = today.atStartOfDay(IST).toInstant();
        Instant now = Instant.now(clock);
        var p = new MapSqlParameterSource().addValue("dayStart", utc(dayStart)).addValue("now", utc(now))
                .addValue("from", utc(today.minusDays(13).atStartOfDay(IST).toInstant()))
                .addValue("fromDate", today.minusDays(13)).addValue("toDate", today);

        long users = count("select count(*) from users", p);
        long todays = count("select count(*) from bookings where confirmed_at >= :dayStart and status in " + SOLD, p);
        BigDecimal revenue = money("select coalesce(sum(grand_total),0) from bookings where confirmed_at >= :dayStart and status in " + SOLD, p);
        long movies = count("select count(*) from movies where active and status = 'NOW_SHOWING'", p);
        long shows = count("select count(*) from shows where status = 'SCHEDULED' and start_time > :now", p);
        long events = count("select count(distinct e.id) from events e join event_sessions s on s.event_id = e.id where e.status = 'PUBLISHED' and s.start_time > :now", p);
        long refunds = count("select count(*) from refunds where status in ('REQUESTED','PROCESSING','FAILED')", p);
        long reviews = count("select count(*) from reviews where status = 'PENDING'", p);

        List<LocalDate> days = new ArrayList<>();
        for (int i = 13; i >= 0; i--) days.add(today.minusDays(i));
        Map<LocalDate, BigDecimal> rev = daily("""
                select (confirmed_at at time zone 'Asia/Kolkata')::date d, sum(grand_total) v from bookings
                where confirmed_at >= :from and status in %s group by 1""".formatted(SOLD), p);
        Map<LocalDate, BigDecimal> cnt = daily("""
                select (confirmed_at at time zone 'Asia/Kolkata')::date d, count(*) v from bookings
                where confirmed_at >= :from and status in %s group by 1""".formatted(SOLD), p);
        Map<LocalDate, BigDecimal> signups = daily("""
                select (created_at at time zone 'Asia/Kolkata')::date d, count(*) v from users where created_at >= :from group by 1""", p);
        Map<LocalDate, BigDecimal> occ = daily("""
                select s.show_date d, round(100.0 * sum(case when ss.status = 'BOOKED' then 1 else 0 end)
                       / nullif(sum(case when ss.status <> 'BLOCKED' then 1 else 0 end), 0), 1) v
                from shows s join show_seats ss on ss.show_id = s.id
                where s.show_date between :fromDate and :toDate
                  and s.status <> 'CANCELLED' group by s.show_date""", p);
        return new DashboardResponse(users, todays, revenue, movies, shows, events, refunds, reviews,
                series(days, rev), series(days, cnt), series(days, signups), series(days, occ));
    }

    public AnalyticsResponse analytics(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        LocalDate f = from == null ? today.minusDays(29) : from;
        LocalDate t = to == null ? today : to;
        if (t.isBefore(f)) { LocalDate x = f; f = t; t = x; }
        var p = new MapSqlParameterSource().addValue("from", utc(f.atStartOfDay(IST).toInstant()))
                .addValue("to", utc(t.plusDays(1).atStartOfDay(IST).toInstant()))
                .addValue("fromDate", f).addValue("toDate", t);
        String inRange = " b.confirmed_at >= :from and b.confirmed_at < :to and b.status in " + SOLD;

        BigDecimal gross = money("select coalesce(sum(b.grand_total),0) from bookings b where" + inRange, p);
        BigDecimal discounts = money("select coalesce(sum(b.discount),0) from bookings b where" + inRange, p);
        BigDecimal refunded = money("select coalesce(sum(amount),0) from refunds where status = 'COMPLETED' and processed_at >= :from and processed_at < :to", p);
        long bookings = count("select count(*) from bookings b where" + inRange, p);
        long seats = count("select count(*) from booking_seats bs join bookings b on b.id = bs.booking_id where" + inRange, p);
        long items = count("select coalesce(sum(bi.quantity),0) from booking_items bi join bookings b on b.id = bi.booking_id where" + inRange, p);
        BigDecimal ticketRevenue = money("select coalesce(sum(b.ticket_subtotal),0) from bookings b where" + inRange, p);
        long tickets = seats + items;
        BigDecimal avg = tickets == 0 ? BigDecimal.ZERO : ticketRevenue.divide(BigDecimal.valueOf(tickets), 2, RoundingMode.HALF_UP);
        BigDecimal occupancy = money("""
                select coalesce(round(100.0 * sum(case when ss.status = 'BOOKED' then 1 else 0 end)
                       / nullif(sum(case when ss.status <> 'BLOCKED' then 1 else 0 end), 0), 1), 0)
                from shows s join show_seats ss on ss.show_id = s.id
                where s.show_date between :fromDate and :toDate and s.status <> 'CANCELLED'""", p);

        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = f; !d.isAfter(t); d = d.plusDays(1)) days.add(d);
        Map<LocalDate, BigDecimal> rev = daily("select (b.confirmed_at at time zone 'Asia/Kolkata')::date d, sum(b.grand_total) v from bookings b where" + inRange + " group by 1", p);
        Map<LocalDate, BigDecimal> cnt = daily("select (b.confirmed_at at time zone 'Asia/Kolkata')::date d, count(*) v from bookings b where" + inRange + " group by 1", p);

        List<Ranked> movies = ranked("""
                select m.title n, count(bs.id) c, coalesce(sum(bs.price),0) a from bookings b join shows s on s.id = b.show_id
                join movies m on m.id = s.movie_id join booking_seats bs on bs.booking_id = b.id where %s
                group by m.title order by c desc limit 10""".formatted(inRange), p);
        List<Ranked> cinemas = ranked("""
                select c.name n, count(distinct b.id) c, coalesce(sum(b.grand_total),0) a from bookings b join shows s on s.id = b.show_id
                join cinemas c on c.id = s.cinema_id where %s group by c.name order by c desc limit 10""".formatted(inRange), p);
        List<Ranked> showtimes = ranked("""
                select to_char(s.start_time at time zone 'Asia/Kolkata', 'HH12:MI AM') n, count(*) c, coalesce(sum(b.grand_total),0) a
                from bookings b join shows s on s.id = b.show_id where %s group by 1 order by c desc limit 10""".formatted(inRange), p);
        List<Ranked> coupons = ranked("""
                select c.code n, count(u.id) c, coalesce(sum(u.discount),0) a from coupon_usage u join coupons c on c.id = u.coupon_id
                where u.used_at >= :from and u.used_at < :to group by c.code order by c desc""", p);
        List<Ranked> methods = ranked("""
                select p.method n, count(*) c, coalesce(sum(p.amount),0) a from payments p
                where p.status in ('SUCCESS','REFUNDED','PARTIALLY_REFUNDED') and p.completed_at >= :from and p.completed_at < :to
                group by p.method order by c desc""", p);
        List<Ranked> statuses = ranked("""
                select status n, count(*) c, coalesce(sum(grand_total),0) a from bookings where created_at >= :from and created_at < :to
                group by status order by c desc""", p);

        return new AnalyticsResponse(f, t, gross, gross.subtract(refunded), refunded, discounts, bookings, tickets, avg, occupancy,
                series(days, rev), series(days, cnt), movies, cinemas, showtimes, coupons, methods, statuses);
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }

    private long count(String sql, MapSqlParameterSource p) {
        Number n = jdbc.queryForObject(sql, p, Number.class);
        return n == null ? 0 : n.longValue();
    }

    private BigDecimal money(String sql, MapSqlParameterSource p) {
        BigDecimal v = jdbc.queryForObject(sql, p, BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }

    private Map<LocalDate, BigDecimal> daily(String sql, MapSqlParameterSource p) {
        Map<LocalDate, BigDecimal> out = new HashMap<>();
        jdbc.query(sql, p, rs -> {
            out.put(rs.getDate("d").toLocalDate(), rs.getBigDecimal("v"));
        });
        return out;
    }

    private List<Ranked> ranked(String sql, MapSqlParameterSource p) {
        return jdbc.query(sql, p, (rs, i) -> new Ranked(rs.getString("n"), rs.getLong("c"), rs.getBigDecimal("a")));
    }

    private static List<Point> series(List<LocalDate> days, Map<LocalDate, BigDecimal> values) {
        return days.stream().map(d -> new Point(d.toString(), values.getOrDefault(d, BigDecimal.ZERO))).toList();
    }
}
