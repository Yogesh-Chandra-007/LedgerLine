package com.ledgerline.service;

import com.ledgerline.domain.AuditEvent;
import com.ledgerline.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventService {
    private final AuditEventRepository events;

    public AuditEventService(AuditEventRepository events) {
        this.events = events;
    }

    // Called inside the business transaction: the audit success record commits or rolls back with it.
    public void recordSuccess(String actor, String action, String resourceType, String resourceId,
                              Long amountMinor, String details) {
        events.save(new AuditEvent(actor, action, resourceType, resourceId,
                "SUCCESS", amountMinor, null, null, null, details));
    }

    // Rejections are recorded in an independent transaction after the business transaction rolls back.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejection(String actor, String action, String method, String path,
                                String reason, Long amountMinor) {
        events.save(new AuditEvent(actor, action, null, null, "REJECTED",
                amountMinor, safe(reason, 500), safe(method, 10), safe(path, 255), null));
    }

    private String safe(String value, int max) {
        if (value == null) return null;
        String clean = value.replaceAll("[\\r\\n\\t]", " ");
        return clean.length() <= max ? clean : clean.substring(0, max);
    }
}
