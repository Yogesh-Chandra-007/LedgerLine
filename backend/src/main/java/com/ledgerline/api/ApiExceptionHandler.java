package com.ledgerline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.service.AuditEventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.ContentCachingRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final AuditEventService auditEvents;
    private final ObjectMapper objectMapper;

    public ApiExceptionHandler(AuditEventService auditEvents, ObjectMapper objectMapper) {
        this.auditEvents = auditEvents;
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())
                ? null : auth.getName();
        try {
            auditEvents.recordRejection(actor, action(request), request.getMethod(),
                    request.getRequestURI(), exception.getReason(), amountMinor(request));
        } catch (RuntimeException ignored) {
            // A failed audit write must not replace the original business rejection.
        }
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "detail", exception.getReason() == null ? "Request rejected" : exception.getReason()));
    }

    private Long amountMinor(HttpServletRequest request) {
        if (!(request instanceof ContentCachingRequestWrapper cached)) return null;
        byte[] body = cached.getContentAsByteArray();
        if (body.length == 0) return null;
        try {
            JsonNode node = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode amount = node == null ? null : node.get("amountMinor");
            return amount != null && amount.isIntegralNumber() && amount.canConvertToLong()
                    ? amount.longValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String action(HttpServletRequest request) {
        String value = request.getMethod() + " " + request.getRequestURI();
        return value.length() <= 80 ? value : value.substring(0, 80);
    }
}
