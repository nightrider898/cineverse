package com.cineverse.security;

import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static Optional<UserPrincipal> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static UserPrincipal requireUser() {
        return currentUser().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    public static Long currentUserIdOrNull() {
        return currentUser().map(UserPrincipal::id).orElse(null);
    }
}
