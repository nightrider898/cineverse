package com.cineverse.security;

import com.cineverse.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cineverse.common.ApiResponse;
import com.cineverse.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window, per-client-IP rate limiter. Auth endpoints get a much tighter budget to slow
 * credential stuffing. Single-node only; swap the counter map for Redis when scaling out.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
    private final AppProperties.RateLimit config;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties props, ObjectMapper objectMapper) {
        this.config = props.rateLimit();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.enabled() || !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().startsWith("/api/v1/payments/webhook");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean auth = request.getRequestURI().startsWith("/api/v1/auth/") && "POST".equals(request.getMethod());
        int limit = auth ? config.authPerMinute() : config.generalPerMinute();
        String key = (auth ? "auth:" : "api:") + request.getRemoteAddr() + ":" + (System.currentTimeMillis() / 60_000);
        int count = counters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));
        if (count > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiResponse.error(ErrorCode.RATE_LIMITED.name(), ErrorCode.RATE_LIMITED.defaultMessage()));
            return;
        }
        chain.doFilter(request, response);
    }

    @Scheduled(fixedDelay = 60_000)
    void evictOldWindows() {
        long current = System.currentTimeMillis() / 60_000;
        counters.keySet().removeIf(k -> Long.parseLong(k.substring(k.lastIndexOf(':') + 1)) < current);
    }
}
