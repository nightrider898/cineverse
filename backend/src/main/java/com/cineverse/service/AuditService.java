package com.cineverse.service;

import com.cineverse.entity.AuditLog;
import com.cineverse.repository.AuditLogRepository;
import com.cineverse.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Durable audit trail for security- and money-relevant actions. Callers must never put
 * secrets, passwords or card data in {@code metadata}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repository;

    public void record(String action, String resource, Object resourceId, Map<String, Object> metadata) {
        record(SecurityUtils.currentUserIdOrNull(), action, resource, resourceId, metadata);
    }

    public void record(Long userId, String action, String resource, Object resourceId, Map<String, Object> metadata) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setResource(resource);
        entry.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
        entry.setMetadata(metadata);
        entry.setRequestId(MDC.get("requestId"));
        entry.setIpAddress(currentIp());
        repository.save(entry);
        log.info("audit action={} resource={} resourceId={} userId={}", action, resource, resourceId, userId);
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        }
        return null;
    }
}
