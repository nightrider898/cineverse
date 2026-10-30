package com.cineverse.service;

import com.cineverse.config.AppProperties;
import com.cineverse.dto.AuthDtos.*;
import com.cineverse.entity.PasswordResetToken;
import com.cineverse.entity.RefreshToken;
import com.cineverse.entity.User;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.notification.EmailService;
import com.cineverse.notification.NotificationService;
import com.cineverse.repository.*;
import com.cineverse.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    /** Used to keep login timing uniform when the email is unknown. */
    private volatile String dummyHash;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CityRepository cityRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserService userService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AppProperties props;
    private final Clock clock;

    public record IssuedTokens(AuthResponse response, String refreshToken, Duration refreshTtl) {}

    @Transactional
    public IssuedTokens register(RegisterRequest req, String userAgent, String ip) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        User user = new User();
        user.setEmail(req.email().trim().toLowerCase());
        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.getRoles().add(roleRepository.findByName("USER").orElseThrow());
        userRepository.save(user);
        auditService.record(user.getId(), "USER_REGISTERED", "USER", user.getId(), null);
        sendWelcome(user);
        return issue(user, userAgent, ip);
    }

    @Transactional
    public IssuedTokens login(LoginRequest req, String userAgent, String ip) {
        User user = userRepository.findByEmail(req.email().trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(req.password(), dummyHash());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            auditService.record(user.getId(), "LOGIN_FAILED", "USER", user.getId(), null);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        user.setLastLoginAt(Instant.now(clock));
        if (props.email() == null || props.email().loginAlerts()) sendLoginAlert(user, userAgent, ip);
        return issue(user, userAgent, ip);
    }

    /**
     * Rotating refresh tokens: each refresh revokes the presented token and issues a new one.
     * Presenting an already-revoked token is treated as theft and revokes the whole family.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public IssuedTokens refresh(String rawToken, String userAgent, String ip) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        Instant now = Instant.now(clock);
        if (token.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected for user {}", token.getUser().getId());
            refreshTokenRepository.revokeAllForUser(token.getUser().getId(), now);
            auditService.record(token.getUser().getId(), "REFRESH_TOKEN_REUSE", "USER", token.getUser().getId(), null);
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        if (!token.getExpiresAt().isAfter(now) || !token.getUser().isEnabled()) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        IssuedTokens issued = issue(token.getUser(), userAgent, ip);
        token.setRevokedAt(now);
        token.setReplacedBy(TokenHasher.sha256(issued.refreshToken()));
        return issued;
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) t.setRevokedAt(Instant.now(clock));
        });
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        userRepository.findByEmail(req.email().trim()).filter(User::isEnabled).ifPresent(user -> {
            String raw = TokenHasher.newToken(32);
            PasswordResetToken t = new PasswordResetToken();
            t.setUser(user);
            t.setTokenHash(TokenHasher.sha256(raw));
            t.setExpiresAt(Instant.now(clock).plus(Duration.ofMinutes(30)));
            resetTokenRepository.save(t);
            String link = props.frontendUrl() + "/forgot-password?token=" + raw;
            emailService.send(user.getEmail(), "Reset your CineVerse password", "password-reset",
                    Map.of("name", user.getFullName(), "link", link));
            auditService.record(user.getId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(), null);
        });
        // Always succeed so the endpoint cannot be used to discover registered emails.
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken t = resetTokenRepository.findByTokenHash(TokenHasher.sha256(req.token()))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID, "This reset link is invalid or has expired"));
        Instant now = Instant.now(clock);
        if (t.getUsedAt() != null || !t.getExpiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.TOKEN_INVALID, "This reset link is invalid or has expired");
        }
        t.setUsedAt(now);
        User user = t.getUser();
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
        auditService.record(user.getId(), "PASSWORD_RESET", "USER", user.getId(), null);
    }

    @Transactional
    public IssuedTokens changePassword(Long userId, ChangePasswordRequest req, String userAgent, String ip) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        refreshTokenRepository.revokeAllForUser(userId, Instant.now(clock));
        auditService.record(userId, "PASSWORD_CHANGED", "USER", userId, null);
        return issue(user, userAgent, ip);
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** "Thanks for registering" email, sent after the account row commits. */
    private void sendWelcome(User user) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", user.getFullName());
        m.put("fullName", user.getFullName());
        m.put("email", user.getEmail());
        m.put("memberSince", DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(IST).format(Instant.now(clock)));
        m.put("cityName", user.getPreferredCity() != null ? user.getPreferredCity().getName() : "your city");
        String brand = props.brand() != null && props.brand().name() != null ? props.brand().name() : props.appName();
        notificationService.emailAfterCommit(user.getEmail(), "Welcome to " + brand + ", " + firstName(user.getFullName()) + "!", "welcome", m);
    }

    /** Security notice for every successful password sign-in (toggle: cineverse.email.login-alerts). */
    private void sendLoginAlert(User user, String userAgent, String ip) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", user.getFullName());
        m.put("email", user.getEmail());
        m.put("when", DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a 'IST'").withZone(IST).format(Instant.now(clock)));
        m.put("device", com.cineverse.security.UserAgents.describe(userAgent));
        m.put("ip", ip == null || ip.isBlank() ? "Unknown" : ip);
        String brand = props.brand() != null && props.brand().name() != null ? props.brand().name() : props.appName();
        notificationService.emailAfterCommit(user.getEmail(), "New sign-in to your " + brand + " account", "signin-alert", m);
    }

    private static String firstName(String full) {
        String t = full == null ? "" : full.trim();
        return t.isEmpty() ? "there" : t.split("\\s+")[0];
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("cineverse-timing-equaliser");
        }
        return dummyHash;
    }

    private IssuedTokens issue(User user, String userAgent, String ip) {
        String raw = TokenHasher.newToken(48);
        Duration ttl = props.jwt().refreshExpiration();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(TokenHasher.sha256(raw));
        rt.setExpiresAt(Instant.now(clock).plus(ttl));
        rt.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())));
        rt.setIpAddress(ip);
        refreshTokenRepository.save(rt);
        AuthResponse response = new AuthResponse(jwtService.issueAccessToken(user), raw, jwtService.accessTokenTtlSeconds(),
                userService.toResponse(user));
        return new IssuedTokens(response, raw, ttl);
    }
}
