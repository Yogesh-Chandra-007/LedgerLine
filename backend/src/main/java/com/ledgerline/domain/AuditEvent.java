package com.ledgerline.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_username", length = 80)
    private String actorUsername;

    @Column(name = "action_type", nullable = false, length = 80)
    private String actionType;

    @Column(name = "resource_type", length = 40)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(name = "amount_minor")
    private Long amountMinor;

    @Column(length = 500)
    private String reason;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    @Column(length = 1000)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {}

    public AuditEvent(String actorUsername, String actionType, String resourceType, String resourceId,
                      String outcome, Long amountMinor, String reason, String requestMethod,
                      String requestPath, String details) {
        this.actorUsername = actorUsername;
        this.actionType = actionType;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.outcome = outcome;
        this.amountMinor = amountMinor;
        this.reason = reason;
        this.requestMethod = requestMethod;
        this.requestPath = requestPath;
        this.details = details;
    }

    @PrePersist
    void onCreate() { if (occurredAt == null) occurredAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getActorUsername() { return actorUsername; }
    public String getActionType() { return actionType; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getOutcome() { return outcome; }
    public Long getAmountMinor() { return amountMinor; }
    public String getReason() { return reason; }
    public String getRequestMethod() { return requestMethod; }
    public String getRequestPath() { return requestPath; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
}
