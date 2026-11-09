package com.ledgerline.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private BankAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_account_code")
    private SystemLedgerAccount systemAccount;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(LedgerTransaction transaction, BankAccount account, long amountMinor) {
        this(transaction, account, null, amountMinor);
    }

    public LedgerEntry(LedgerTransaction transaction, SystemLedgerAccount systemAccount, long amountMinor) {
        this(transaction, null, systemAccount, amountMinor);
    }

    private LedgerEntry(LedgerTransaction transaction, BankAccount account,
                        SystemLedgerAccount systemAccount, long amountMinor) {
        if (amountMinor == 0 || (account == null) == (systemAccount == null)) {
            throw new IllegalArgumentException("A nonzero ledger entry must target exactly one ledger account");
        }
        this.transaction = transaction;
        this.account = account;
        this.systemAccount = systemAccount;
        this.amountMinor = amountMinor;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public LedgerTransaction getTransaction() { return transaction; }
    public BankAccount getAccount() { return account; }
    public SystemLedgerAccount getSystemAccount() { return systemAccount; }
    public long getAmountMinor() { return amountMinor; }
    public Instant getCreatedAt() { return createdAt; }
}
