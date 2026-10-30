package com.cineverse.admin;

import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.repository.CinemaRepository;
import com.cineverse.security.SecurityUtils;
import com.cineverse.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Backend authorization for the admin area. ADMIN can do everything; PARTNER is limited to the cinemas
 * linked to its partner account. The frontend route guard is only UX — this is the real boundary.
 */
@Component
@RequiredArgsConstructor
public class AdminAccess {
    private final CinemaRepository cinemaRepository;

    public UserPrincipal requireAdmin() {
        UserPrincipal u = SecurityUtils.requireUser();
        if (!u.isAdmin()) throw new ApiException(ErrorCode.FORBIDDEN);
        return u;
    }

    public void requireCinemaAccess(Long cinemaId) {
        UserPrincipal u = SecurityUtils.requireUser();
        if (u.isAdmin()) return;
        if (u.hasRole("PARTNER") && cinemaRepository.cinemaIdsForPartnerUser(u.id()).contains(cinemaId)) return;
        throw new ApiException(ErrorCode.FORBIDDEN, "You can only manage your own cinemas");
    }

    /** null = unrestricted (admin); otherwise the partner's cinema ids. */
    public List<Long> scopedCinemaIds() {
        UserPrincipal u = SecurityUtils.requireUser();
        return u.isAdmin() ? null : cinemaRepository.cinemaIdsForPartnerUser(u.id());
    }
}
