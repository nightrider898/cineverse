package com.cineverse;

import com.cineverse.entity.Show;
import com.cineverse.entity.User;
import com.cineverse.support.Fixtures;
import com.cineverse.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec §96, over real HTTP (MockMvc) and PostgreSQL: login -> city -> movie -> show -> seats -> lock ->
 * food -> coupon -> checkout -> mock payment -> confirmed -> QR -> My Bookings.
 */
class BookingFlowE2ETest extends IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void fullBookingJourney() throws Exception {
        Show show = fixtures.showAt(Duration.ofHours(30), 24);
        User user = fixtures.user("USER");
        String token = login(user.getEmail());

        // browse: city list and the movie's showtimes in the show's city on the show's date
        JsonNode cities = apiGet("/api/v1/cities", null);
        assertThat(cities.size()).isGreaterThanOrEqualTo(9);
        String city = show.getCinema().getCity().getSlug();
        LocalDate date = LocalDate.ofInstant(show.getStartTime(), ZoneId.of("Asia/Kolkata"));
        JsonNode showtimes = apiGet("/api/v1/movies/" + show.getMovie().getId() + "/showtimes?city=" + city + "&date=" + date, null);
        assertThat(showtimes.path("cinemas").toString()).contains("\"showId\":" + show.getId());

        // seat map comes from the backend
        JsonNode seatMap = apiGet("/api/v1/shows/" + show.getId(), token);
        assertThat(seatMap.path("layout").path("seats").size()).isGreaterThan(100);

        // lock seats
        JsonNode lock = apiPost("/api/v1/shows/" + show.getId() + "/seat-lock", "{\"seatIds\":[\"E5\",\"E6\"]}", token, null, 200);
        assertThat(lock.path("seatIds").size()).isEqualTo(2);
        String lockId = lock.path("lockId").asText();

        // create booking with food (prices come from the DB, not the client)
        JsonNode booking = apiPost("/api/v1/bookings", "{\"lockId\":\"" + lockId + "\",\"food\":[{\"foodItemId\":1,\"quantity\":1}]}", token, "idem-book-1", 201);
        long bookingId = booking.path("id").asLong();
        assertThat(booking.path("price").path("foodSubtotal").decimalValue()).isEqualByComparingTo("250");
        // Idempotent replay returns the same booking
        JsonNode replay = apiPost("/api/v1/bookings", "{\"lockId\":\"" + lockId + "\",\"food\":[{\"foodItemId\":1,\"quantity\":1}]}", token, "idem-book-1", 201);
        assertThat(replay.path("id").asLong()).isEqualTo(bookingId);

        // coupon: first booking gets WELCOME100 (10%, max 100)
        JsonNode withCoupon = apiPost("/api/v1/coupons/apply", "{\"couponCode\":\"welcome100\",\"bookingId\":" + bookingId + "}", token, null, 200);
        BigDecimal discount = withCoupon.path("price").path("discount").decimalValue();
        assertThat(discount).isGreaterThan(BigDecimal.ZERO).isLessThanOrEqualTo(new BigDecimal("100"));
        BigDecimal total = withCoupon.path("price").path("grandTotal").decimalValue();
        BigDecimal expected = withCoupon.path("price").path("ticketSubtotal").decimalValue()
                .add(withCoupon.path("price").path("foodSubtotal").decimalValue())
                .add(withCoupon.path("price").path("convenienceFee").decimalValue())
                .add(withCoupon.path("price").path("tax").decimalValue()).subtract(discount);
        assertThat(total).isEqualByComparingTo(expected);

        // an expired coupon is rejected with a business error code
        mvc.perform(auth(post("/api/v1/coupons/apply"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"couponCode\":\"EXPIRED10\",\"bookingId\":" + bookingId + "}")).andExpect(status().isUnprocessableEntity());

        // pay with the mock gateway
        JsonNode init = apiPost("/api/v1/payments/initiate", "{\"bookingId\":" + bookingId + ",\"method\":\"UPI\"}", token, "idem-pay-1", 200);
        assertThat(init.path("amount").decimalValue()).isEqualByComparingTo(total);
        assertThat(init.path("action").path("type").asText()).isEqualTo("MOCK_SIMULATOR");
        String paymentId = init.path("paymentId").asText();
        JsonNode sim = apiPost("/api/v1/dev/payments/" + paymentId + "/simulate?deliveries=2", "{\"outcome\":\"SUCCESS\"}", token, null, 200);
        assertThat(sim.path("deliveries").get(0).asText()).isEqualTo("SUCCESS");
        assertThat(sim.path("deliveries").get(1).asText()).isEqualTo("DUPLICATE");

        // confirmed with QR ticket
        JsonNode confirmed = apiGet("/api/v1/bookings/" + bookingId, token);
        assertThat(confirmed.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.path("reference").asText()).matches("CV-\\d{4}-\\d{6}");
        assertThat(confirmed.path("ticket").path("qrImage").asText()).startsWith("data:image/png;base64,");
        String qr = confirmed.path("ticket").path("qrToken").asText();
        assertThat(qr).startsWith("CV1.").doesNotContain(paymentId);

        // appears under My Bookings -> Upcoming
        JsonNode upcoming = apiGet("/api/v1/bookings?tab=upcoming", token);
        assertThat(upcoming.path("content").toString()).contains("\"id\":" + bookingId);

        // seats now show as BOOKED for everybody
        JsonNode after = apiGet("/api/v1/shows/" + show.getId() + "/seats", null);
        assertThat(after.path("seats").toString()).contains("{\"label\":\"E5\",\"status\":\"BOOKED\"");

        // notifications were created
        assertThat(apiGet("/api/v1/notifications/unread-count", token).path("unread").asLong()).isGreaterThanOrEqualTo(2);

        // staff scans the QR once; the second scan is rejected
        User staff = fixtures.user("VENUE_STAFF");
        String staffToken = login(staff.getEmail());
        clock.advance(Duration.ofHours(28)); // within the entry window
        JsonNode scan1 = apiPost("/api/v1/tickets/validate", "{\"qrToken\":\"" + qr + "\"}", staffToken, null, 200);
        JsonNode scan2 = apiPost("/api/v1/tickets/validate", "{\"qrToken\":\"" + qr + "\"}", staffToken, null, 200);
        assertThat(scan1.path("result").asText()).isEqualTo("VALID");
        assertThat(scan2.path("result").asText()).isEqualTo("ALREADY_USED");
        // customers cannot use the staff endpoint
        mvc.perform(auth(post("/api/v1/tickets/validate"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"qrToken\":\"" + qr + "\"}")).andExpect(status().isForbidden());
    }

    @Test
    void clientCannotLockSeatsWithoutLoggingIn() throws Exception {
        Show show = fixtures.show(Duration.ofHours(40));
        mvc.perform(post("/api/v1/shows/" + show.getId() + "/seat-lock").contentType(MediaType.APPLICATION_JSON)
                .content("{\"seatIds\":[\"A1\"]}")).andExpect(status().isUnauthorized());
    }

    String login(String email) throws Exception {
        var res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + Fixtures.PASSWORD + "\"}")).andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    JsonNode apiGet(String url, String token) throws Exception {
        var res = mvc.perform(auth(get(url), token))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).path("data");
    }

    JsonNode apiPost(String url, String body, String token, String idemKey, int expected) throws Exception {
        MockHttpServletRequestBuilder req = auth(post(url), token)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (idemKey != null) req.header("Idempotency-Key", idemKey);
        var res = mvc.perform(req).andExpect(status().is(expected)).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).path("data");
    }

    static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }
}