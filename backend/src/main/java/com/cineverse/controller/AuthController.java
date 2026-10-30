package com.cineverse.controller;

import com.cineverse.common.ApiResponse;
import com.cineverse.config.AppProperties;
import com.cineverse.dto.AuthDtos.*;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.security.SecurityUtils;
import com.cineverse.service.AuthService;
import com.cineverse.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    public static final String REFRESH_COOKIE = "cv_refresh";
    private static final String CSRF_HEADER = "X-Requested-With";

    private final AuthService authService;
    private final UserService userService;
    private final AppProperties props;

    @Operation(summary = "Create an account")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http, HttpServletResponse res) {
        var issued = authService.register(req, http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
        setRefreshCookie(res, issued.refreshToken(), issued.refreshTtl());
        return ApiResponse.ok(issued.response(), "Welcome to CineVerse");
    }

    @Operation(summary = "Log in with email and password")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http, HttpServletResponse res) {
        var issued = authService.login(req, http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
        setRefreshCookie(res, issued.refreshToken(), issued.refreshTtl());
        return ApiResponse.ok(issued.response());
    }

    @Operation(summary = "Rotate the refresh token (HttpOnly cookie or body) and get a new access token")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody(required = false) RefreshRequest body,
                                             @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
                                             HttpServletRequest http, HttpServletResponse res) {
        String token = resolveRefreshToken(body, cookie, http);
        try {
            var issued = authService.refresh(token, http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
            setRefreshCookie(res, issued.refreshToken(), issued.refreshTtl());
            return ApiResponse.ok(issued.response());
        } catch (ApiException e) {
            clearRefreshCookie(res);
            throw e;
        }
    }

    @Operation(summary = "Revoke the refresh token")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshRequest body,
                                    @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
                                    HttpServletRequest http, HttpServletResponse res) {
        authService.logout(body != null && body.refreshToken() != null ? body.refreshToken() : cookie);
        clearRefreshCookie(res);
        return ApiResponse.ok(null, "Logged out");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ApiResponse.ok(null, "If an account exists for this email, a reset link has been sent");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ApiResponse.ok(null, "Password updated. Please log in.");
    }

    @PostMapping("/change-password")
    public ApiResponse<AuthResponse> change(@Valid @RequestBody ChangePasswordRequest req, HttpServletRequest http, HttpServletResponse res) {
        var issued = authService.changePassword(SecurityUtils.requireUser().id(), req, http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
        setRefreshCookie(res, issued.refreshToken(), issued.refreshTtl());
        return ApiResponse.ok(issued.response(), "Password changed");
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.ok(userService.me(SecurityUtils.requireUser().id()));
    }

    /**
     * Cookie-based refresh must come from our own frontend: the custom header forces a CORS preflight,
     * which cross-site forms cannot trigger. Body-based refresh (mobile/Postman) does not need it.
     */
    private String resolveRefreshToken(RefreshRequest body, String cookie, HttpServletRequest http) {
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) return body.refreshToken();
        if (cookie != null && http.getHeader(CSRF_HEADER) == null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Missing " + CSRF_HEADER + " header");
        }
        return cookie;
    }

    private void setRefreshCookie(HttpServletResponse res, String token, Duration ttl) {
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, token).httpOnly(true)
                .secure(props.cookies().secure()).sameSite(props.cookies().sameSite()).path("/api/v1/auth").maxAge(ttl).build().toString());
    }

    private void clearRefreshCookie(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true)
                .secure(props.cookies().secure()).sameSite(props.cookies().sameSite()).path("/api/v1/auth").maxAge(0).build().toString());
    }
}
