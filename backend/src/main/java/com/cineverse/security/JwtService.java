package com.cineverse.security;

import com.cineverse.config.AppProperties;
import com.cineverse.entity.Role;
import com.cineverse.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JwtService {
    private final SecretKey key;
    private final AppProperties.Jwt config;

    public JwtService(AppProperties props) {
        this.config = props.jwt();
        String secret = config.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(config.issuer())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("roles", user.getRoles().stream().map(Role::getName).sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(config.accessExpiration())))
                .signWith(key)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return config.accessExpiration().toSeconds();
    }

    public Optional<UserPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(config.issuer()).build()
                    .parseSignedClaims(token).getPayload();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            return Optional.of(new UserPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("name", String.class),
                    roles == null ? new HashSet<>() : roles.stream().map(String::valueOf).collect(Collectors.toSet())));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
