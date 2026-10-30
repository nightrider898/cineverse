package com.cineverse;

import com.cineverse.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthTest extends IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void registerLoginRefreshRotateLogout() throws Exception {
        String email = "new-" + UUID.randomUUID().toString().substring(0, 8) + "@test.cineverse.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New User\",\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.data.user.roles[0]").value("USER"));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Dup\",\"email\":\"" + email.toUpperCase() + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-pass1\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Secret123\"}")).andExpect(status().isOk()).andReturn();
        JsonNode data = json.readTree(login.getResponse().getContentAsString()).path("data");
        String access = data.path("accessToken").asText();
        String refresh1 = data.path("refreshToken").asText();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer tampered." + access)).andExpect(status().isUnauthorized());

        // cookie-based refresh requires the anti-CSRF header
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("cv_refresh", refresh1))).andExpect(status().isForbidden());
        MvcResult r2 = mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("cv_refresh", refresh1)).header("X-Requested-With", "CineVerse"))
                .andExpect(status().isOk()).andReturn();
        String refresh2 = json.readTree(r2.getResponse().getContentAsString()).path("data").path("refreshToken").asText();
        assertThat(refresh2).isNotEqualTo(refresh1);

        // replaying the rotated token is detected and kills the whole token family
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh2 + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refresh2 + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void validationErrorsAreStructured() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.email").exists())
                .andExpect(jsonPath("$.error.fields.password").exists());
    }
}
