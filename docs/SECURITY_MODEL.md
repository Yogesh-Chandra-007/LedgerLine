# LedgerLine security model

## Authentication

- Spring Security performs database-backed HTTP Basic authentication.
- Passwords are stored as BCrypt hashes with work factor 12.
- Requests are stateless; credentials must be sent only over HTTPS outside local development.
- The frontend keeps the credential string in React memory and clears it on sign-out. This is a demo trade-off, not a production token/session architecture.

## Authorization

| Role | Access |
|---|---|
| ADMIN | User provisioning, system account register, and audit/transaction oversight |
| ACCOUNT_HOLDER | Own account creation, account listing, statements, deposits, withdrawals, and transfers |
| AUDITOR | Read-only account register, transaction postings, and audit events |

The admin provisioning endpoint can create ACCOUNT_HOLDER and AUDITOR users but rejects ADMIN role creation. No public registration endpoint exists. Account ownership is checked in the service layer; possessing an account UUID is never authorization.

## Financial-operation controls

- Amounts are positive integer minor units; the frontend converts decimal input with BigInt rather than floating-point arithmetic.
- Backend amount limits stay within JavaScript's safe integer range.
- Account rows are locked pessimistically before balance mutation. Transfers lock both account rows in deterministic ID order.
- A transaction-scoped PostgreSQL advisory lock serializes requests sharing an idempotency key before account locks are acquired.
- Idempotency keys are unique and bound to a SHA-256 fingerprint of operation type, account IDs, and amount.
- The original result account and balance snapshot are stored so a retry returns the original transaction response.
- Every posted transaction creates exactly two signed ledger entries. A deferred PostgreSQL constraint trigger enforces count=2 and sum=0 at commit.
- Deferred database triggers reconcile every customer/system account balance with the sum of its ledger postings.
- Transaction, ledger-entry, and audit-event rows are append-only.
- The runtime database role is separate from the migration owner. It cannot update/delete ledger or audit rows and can update only the balance columns of customer/system accounts.

## Audit trail

- Successful account openings, user provisioning, deposits, withdrawals, transfers, and idempotent replays are recorded in the same database transaction as the operation.
- Business rejections are recorded in an independent transaction after the failed operation rolls back.
- Authentication and authorization rejections are also recorded.
- Refused money requests capture the actor, attempted amount when parseable, account reference, request path, and reason.
- Account statements merge posted ledger entries with rejected attempts. Rejected attempts do not change the running balance.

## Known limitations

- PostgreSQL migrations and database-specific constraints are covered by CI integration tests, but production-host behavior and operational monitoring remain unverified.
- Opposite-direction transfer stress tests, broader fuzz/property tests, rate limiting, MFA, password reset, production-grade session lifecycle, deployment hardening, backups, and independent penetration testing remain future work.
- A database superuser or migration owner can alter schema and triggers; migration credentials must be protected separately from the runtime role.
