package com.ledgerline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_ledger_accounts")
public class SystemLedgerAccount {
    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 80)
    private String description;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    protected SystemLedgerAccount() {}

    public SystemLedgerAccount(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public void applyDelta(long delta) {
        if ((delta > 0 && balanceMinor > Long.MAX_VALUE - delta)
                || (delta < 0 && balanceMinor < Long.MIN_VALUE - delta)) {
            throw new IllegalArgumentException("System ledger balance overflow");
        }
        balanceMinor += delta;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
    public long getBalanceMinor() { return balanceMinor; }
}
