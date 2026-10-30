package com.cineverse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns a request id (honouring a sane inbound X-Request-Id), exposes it on the response
 * and logs one structured line per API request: method, path, status, duration, user.
 * Bodies and headers are never logged, so credentials and card data cannot leak here.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String requestId = inbound != null && SAFE_ID.matcher(inbound).matches() ? inbound : UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        response.setHeader(HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (request.getRequestURI().startsWith("/api/")) {
                log.info("http_request method={} path={} status={} durationMs={} userId={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), ms, request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTR));
            }
            MDC.remove("requestId");
        }
    }
}
