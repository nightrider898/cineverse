package com.cineverse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String USER_ID_ATTR = "cineverse.userId";
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.parse(header.substring(7)).ifPresent(principal -> {
                var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
                MDC.put("userId", String.valueOf(principal.id()));
                request.setAttribute(USER_ID_ATTR, principal.id());
            });
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }
}
