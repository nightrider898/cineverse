package com.cineverse;

import com.cineverse.entity.User;
import com.cineverse.support.Fixtures;
import com.cineverse.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec §101: admin creates movie, cinema, screen, seats and show; a customer can immediately find and lock it. */
class AdminFlowTest extends IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void adminCreatesCatalogAndCustomerBooksIt() throws Exception {
        String admin = login(fixtures.user("ADMIN"));
        String customer = login(fixtures.user("USER"));

        JsonNode movie = call("POST", "/api/v1/admin/movies", admin, """
                {"title":"Test Horizon","synopsis":"A test film.","durationMin":120,"certificate":"UA","releaseDate":"2026-01-01",
                 "formats":["2D","IMAX"],"status":"NOW_SHOWING","featured":false,"active":true,"languages":["Telugu","English"],
                 "genres":["Drama","Sci-Fi"],"directors":["Test Director"],"cast":[{"name":"Test Actor","character":"Hero"}]}""", 201);
        long movieId = movie.path("id").asLong();

        JsonNode cities = call("GET", "/api/v1/cities", null, null, 200);
        long hyderabad = 0;
        for (JsonNode c : cities) if (c.path("slug").asText().equals("hyderabad")) hyderabad = c.path("id").asLong();

        JsonNode cinema = call("POST", "/api/v1/admin/cinemas", admin, """
                {"name":"Test Multiplex","cityId":%d,"locality":"Test Nagar","address":"1 Test Road","latitude":17.4,"longitude":78.4,
                 "facilities":["PARKING"],"allowPayAtVenue":true,"cancellationAllowed":true,"active":true}""".formatted(hyderabad), 201);
        long cinemaId = cinema.path("id").asLong();

        JsonNode screen = call("POST", "/api/v1/admin/screens", admin,
                "{\"cinemaId\":%d,\"name\":\"Audi 1\",\"screenType\":\"IMAX\",\"formats\":[\"2D\",\"IMAX\"],\"active\":true}".formatted(cinemaId), 201);
        long screenId = screen.path("id").asLong();

        JsonNode layout = call("PUT", "/api/v1/admin/screens/" + screenId + "/layout", admin, """
                {"rows":[{"label":"A","seatType":"VIP","seatCount":6,"offset":2,"aisleAfter":[3],"disabledSeats":[]},
                         {"label":"B","seatType":"GOLD","seatCount":10,"offset":0,"aisleAfter":[5],"disabledSeats":[10]},
                         {"label":"C","seatType":"SILVER","seatCount":10,"offset":0,"aisleAfter":[5],"disabledSeats":[],"seatTypeOverrides":{"1":"GOLD"}}]}""", 200);
        assertThat(layout.path("capacity").asInt()).isEqualTo(25);
        assertThat(layout.path("layout").get(1).path("disabledSeats").get(0).asInt()).isEqualTo(10);

        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(3);
        JsonNode show = call("POST", "/api/v1/admin/shows", admin, """
                {"movieId":%d,"screenId":%d,"date":"%s","startTime":"18:30","format":"IMAX","language":"Telugu","basePrice":300}"""
                .formatted(movieId, screenId, date), 201);
        long showId = show.path("id").asLong();
        assertThat(show.path("total").asLong()).isEqualTo(25);

        // overlapping show on the same screen is rejected
        call("POST", "/api/v1/admin/shows", admin, """
                {"movieId":%d,"screenId":%d,"date":"%s","startTime":"19:00","format":"IMAX","language":"Telugu","basePrice":300}"""
                .formatted(movieId, screenId, date), 409);

        // customer discovers it and books
        JsonNode showtimes = call("GET", "/api/v1/movies/" + movieId + "/showtimes?city=hyderabad&date=" + date, null, null, 200);
        assertThat(showtimes.path("cinemas").get(0).path("cinema").path("name").asText()).isEqualTo("Test Multiplex");
        JsonNode seatMap = call("GET", "/api/v1/shows/" + showId, customer, null, 200);
        assertThat(seatMap.path("pricing").toString()).contains("VIP").contains("620"); // 300 + 320 VIP delta
        JsonNode lock = call("POST", "/api/v1/shows/" + showId + "/seat-lock", customer, "{\"seatIds\":[\"A1\",\"A2\"]}", 200);
        assertThat(lock.path("ticketSubtotal").decimalValue()).isEqualByComparingTo("1240");
        call("POST", "/api/v1/shows/" + showId + "/seat-lock", customer, "{\"seatIds\":[\"B10\"]}", 409); // disabled seat

        // customers cannot reach admin APIs
        call("POST", "/api/v1/admin/movies", customer, "{}", 403);
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
        assertThat(call("GET", "/api/v1/admin/dashboard", admin, null, 200).path("activeMovies").asLong()).isGreaterThan(0);
    }

    @Test
    void partnerIsScopedToOwnCinemas() throws Exception {
        String partner = login(fixtures.user("PARTNER"));
        call("POST", "/api/v1/admin/movies", partner, """
                {"title":"Nope","synopsis":"x","durationMin":100,"certificate":"U","releaseDate":"2026-01-01","formats":["2D"],
                 "status":"NOW_SHOWING","featured":false,"active":true,"languages":["Hindi"],"genres":["Drama"]}""", 403);
        call("GET", "/api/v1/admin/screens?cinemaId=1", partner, null, 403);
    }

    String login(User u) throws Exception {
        var res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + Fixtures.PASSWORD + "\"}")).andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    JsonNode call(String method, String url, String token, String body, int expected) throws Exception {
        var req = request(org.springframework.http.HttpMethod.valueOf(method), url);
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        var res = mvc.perform(req).andExpect(status().is(expected)).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).path("data");
    }
}
