# Day 1 — Initial data model

## Scope

Day 1 establishes the identity and authorization data needed before financial account operations are added. It deliberately does not create account or transaction tables; those belong to Day 2, when the ledger domain and its invariants are introduced together.

## app_users

| Column | Type | Rule |
|---|---|---|
| id | UUID | Primary key |
| username | VARCHAR(80) | Required, unique |
| password_hash | VARCHAR(100) | Required; BCrypt hash only |
| role | VARCHAR(30) | ADMIN, ACCOUNT_HOLDER, or AUDITOR |
| enabled | BOOLEAN | Required; defaults true |
| created_at | TIMESTAMPTZ | Required, set on insert |

A database check constraint limits roles to the three supported values. A role index supports role-based administrative queries.

## Design decisions

- Passwords are never stored in plaintext.
- User identity is represented by an immutable UUID primary key.
- A user has one explicit role in this initial version. Multi-role membership can be introduced later if the challenge requires it.
- No public registration endpoint exists. User provisioning must be an administrative operation; public self-registration would create an unnecessary privilege-escalation surface.
- Financial account tables are intentionally deferred to Day 2 so account ownership and ledger relationships are designed together.
