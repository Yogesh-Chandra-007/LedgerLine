package com.ledgerline.security;

import com.ledgerline.service.AuditEventService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final AuditEventService auditEvents;
    public SecurityAuditHandlers(AuditEventService auditEvents) { this.auditEvents = auditEvents; }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException, ServletException {
        record(null, "AUTHENTICATION_REJECTED", request);
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = auth == null ? null : auth.getName();
        record(actor, "AUTHORIZATION_REJECTED", request);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
    }

    private void record(String actor, String action, HttpServletRequest request) {
        try {
            auditEvents.recordRejection(actor, action, request.getMethod(),
                    request.getRequestURI(), action, null, null, null);
        } catch (RuntimeException ignored) {
            // Preserve the security response even if the audit store is unavailable.
        }
    }
}
