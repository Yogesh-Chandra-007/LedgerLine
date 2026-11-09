package com.ledgerline.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionType type;
    @Column(name = "created_by", nullable = false, length = 80)
    private String createdBy;
    @Column(name = "result_account_id")
    private UUID resultAccountId;
    @Column(name = "result_balance_minor")
    private Long resultBalanceMinor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerTransaction() {}
    public LedgerTransaction(String idempotencyKey, String requestFingerprint, TransactionType type, String createdBy) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.type = type;
        this.createdBy = createdBy;
    }
    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    public void setResultSnapshot(UUID accountId, long balanceMinor) { this.resultAccountId = accountId; this.resultBalanceMinor = balanceMinor; }
    public UUID getResultAccountId() { return resultAccountId; }
    public Long getResultBalanceMinor() { return resultBalanceMinor; }
    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public TransactionType getType() { return type; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
