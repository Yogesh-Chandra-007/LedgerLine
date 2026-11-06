package com.ledgerline.domain;

public enum UserRole {
    ADMIN,
    ACCOUNT_HOLDER,
    AUDITOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
