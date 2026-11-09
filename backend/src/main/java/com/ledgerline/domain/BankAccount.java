package com.ledgerline.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "bank_accounts")
public class BankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "account_number", nullable = false, unique = true, length = 24)
    private String accountNumber;
    @Column(name = "owner_username", nullable = false, length = 80)
    private String ownerUsername;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BankAccount() {}
    public BankAccount(String accountNumber, String ownerUsername, String currency) {
        this.accountNumber = accountNumber;
        this.ownerUsername = ownerUsername;
        this.currency = currency;
        this.balanceMinor = 0L;
        this.status = AccountStatus.ACTIVE;
    }
    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
    public void credit(long amountMinor) {
        if (amountMinor <= 0 || balanceMinor > Long.MAX_VALUE - amountMinor)
            throw new IllegalArgumentException("Invalid credit amount");
        balanceMinor += amountMinor;
    }
    public void debit(long amountMinor) {
        if (amountMinor <= 0 || balanceMinor < amountMinor)
            throw new IllegalArgumentException("Insufficient funds");
        balanceMinor -= amountMinor;
    }
    public UUID getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getCurrency() { return currency; }
    public long getBalanceMinor() { return balanceMinor; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
