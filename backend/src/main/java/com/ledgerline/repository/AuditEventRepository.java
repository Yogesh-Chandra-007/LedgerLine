package com.ledgerline.repository;

import java.util.List;
import java.util.UUID;
import com.ledgerline.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findTop200ByOrderByOccurredAtDesc();
    List<AuditEvent> findAllByResourceIdAndOutcomeOrderByOccurredAtAsc(
            String resourceId, String outcome);
}
