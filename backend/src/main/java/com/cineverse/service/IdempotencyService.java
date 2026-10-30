package com.cineverse.service;

import com.cineverse.entity.IdempotencyRecord;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import com.cineverse.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Implements the Idempotency-Key contract for critical POSTs (booking creation, payment initiation,
 * cancellation/refund). The first request with a key runs; replays with the same key and body get the
 * stored response; a concurrent replay gets 409; the same key with a different body gets 422.
 */
@Slf4j
@Service
public class IdempotencyService {
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate newTx;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper, PlatformTransactionManager txm) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.newTx = new TransactionTemplate(txm);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(String scope, Long userId, String key, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            return action.get();
        }
        if (key.length() > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Idempotency-Key is too long");
        }
        String hash = hash(request);

        IdempotencyRecord existing = claim(scope, userId, key, hash);
        if (existing != null) {
            if (!existing.getRequestHash().equals(hash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            if (COMPLETED.equals(existing.getStatus())) {
                log.info("idempotent replay scope={} key={}", scope, key);
                return read(existing.getResponseBody(), responseType);
            }
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        try {
            T result = action.get();
            newTx.executeWithoutResult(s -> repository.find(scope, userId, key).ifPresent(r -> {
                r.setStatus(COMPLETED);
                r.setResponseStatus(200);
                r.setResponseBody(write(result));
                r.setCompletedAt(Instant.now());
            }));
            return result;
        } catch (RuntimeException ex) {
            // Failed attempts are not cached: the client may safely retry with the same key.
            newTx.executeWithoutResult(s -> repository.find(scope, userId, key).ifPresent(repository::delete));
            throw ex;
        }
    }

    /** Returns null when this call claimed the key, otherwise the pre-existing record. */
    private IdempotencyRecord claim(String scope, Long userId, String key, String hash) {
        try {
            return newTx.execute(s -> {
                var found = repository.find(scope, userId, key);
                if (found.isPresent()) {
                    return found.get();
                }
                IdempotencyRecord r = new IdempotencyRecord();
                r.setScope(scope);
                r.setUserId(userId);
                r.setKey(key);
                r.setRequestHash(hash);
                r.setStatus(IN_PROGRESS);
                repository.saveAndFlush(r);
                return null;
            });
        } catch (DataIntegrityViolationException race) {
            // Another request inserted the same key between our read and insert.
            return newTx.execute(s -> repository.find(scope, userId, key).orElseThrow(() -> new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT)));
        }
    }

    private String hash(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(write(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise idempotent payload", e);
        }
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read stored idempotent response", e);
        }
    }
}
