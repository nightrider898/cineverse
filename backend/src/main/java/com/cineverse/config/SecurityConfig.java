package com.cineverse.config;

import com.cineverse.security.JwtAuthenticationFilter;
import com.cineverse.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final RestAuthenticationEntryPoint entryPoint;
    private final AppProperties props;

    private static final String[] PUBLIC_GET = {
            "/api/v1/movies/**", "/api/v1/cities/**", "/api/v1/cinemas/**", "/api/v1/shows/**",
            "/api/v1/events/**", "/api/v1/sports/**", "/api/v1/plays/**", "/api/v1/activities/**", "/api/v1/venues/**",
            "/api/v1/food/**", "/api/v1/offers/**", "/api/v1/search/**", "/api/v1/reviews/movie/**",
            "/api/v1/payments/methods", "/api/v1/meta/**"
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless bearer-token API: no session, no CSRF token needed for Authorization-header requests.
            // Cookie-authenticated endpoints (refresh/logout) additionally require a custom header (see AuthController).
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .contentTypeOptions(c -> {})
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .permissionsPolicyHeader(p -> p.policy("camera=(self), geolocation=(self), microphone=()")))
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(entryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/change-password").authenticated()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/payments/webhook/**").permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "PARTNER")
                .requestMatchers("/api/v1/staff/**", "/api/v1/tickets/validate").hasAnyRole("VENUE_STAFF", "ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Prevent Boot from also registering the JWT filter as a plain servlet filter. */
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = props.cors() == null || props.cors().allowedOrigins() == null
                ? List.of(props.frontendUrl()) : props.cors().allowedOrigins();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id", "X-Requested-With"));
        cfg.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
