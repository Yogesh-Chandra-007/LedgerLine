# LedgerLine security model

## Authentication

- Spring Security performs database-backed HTTP Basic authentication.
- Passwords are stored as BCrypt hashes with work factor 12.
- Requests are stateless; credentials must be sent only over HTTPS outside local development.
- The frontend keeps the credential string in React memory and clears it on sign-out. This is a demo trade-off, not a production token/session architecture.

## Authorization

| Role | Access |
|---|---|
| ADMIN | Admin provisioning endpoints and role status probes |
| ACCOUNT_HOLDER | Own account and transaction operations |
| AUDITOR | Auditor route namespace; read-only workflows are not yet implemented |

The admin provisioning endpoint can create ACCOUNT_HOLDER and AUDITOR users but rejects ADMIN role creation. No public registration endpoint exists.

## Financial-operation controls

- Account ownership is checked in the service layer for every customer account read or debit.
- Amounts are positive integer minor units; floating-point arithmetic is not used.
- Account rows are locked pessimistically before balance mutation. Transfers lock both account rows in deterministic ID order.
- Idempotency keys are unique in the database and are bound to a SHA-256 fingerprint of operation type, account IDs, and amount.
- A retry with the same key and same request returns the existing transaction; reusing a key with different request content is rejected.
- Every posted transaction creates two signed ledger entries whose sum is checked to zero in the service layer.
- Database triggers make transaction and entry rows append-only.

## Known limitations

- A database uniqueness conflict from two simultaneous requests using the same idempotency key on different locked account sets must be handled and tested explicitly; the unique constraint prevents a duplicate commit, but the losing request may currently surface as a generic persistence conflict.
- PostgreSQL row-lock and trigger behavior must be validated against a real PostgreSQL instance. H2 tests do not validate PostgreSQL-specific migration SQL.
- Auditor data scoping, password reset, rate limiting, production-grade session management, deployment hardening, and independent penetration testing remain future work.
