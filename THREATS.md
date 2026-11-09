# LedgerLine threat model

Scope: the LedgerLine demo API, React frontend, PostgreSQL schema, authentication, authorization, and money-movement workflows. Use synthetic data only. This is a threat model plus implemented controls and automated verification; it is not a penetration-test report or security certification.

| Threat | Impact | Current mitigation | Evidence / remaining verification |
|---|---|---|---|
| Broken object-level authorization / IDOR | Reading or debiting another user's account | Service-layer ownership checks on account reads/debits; transfer source ownership is checked | H2 service tests and role tests; add more HTTP-level two-user coverage |
| Privilege escalation | Account holder gains admin access | Server-assigned roles; admin endpoint only creates ACCOUNT_HOLDER/AUDITOR; no public registration | H2 RBAC tests; additional malformed-role cases remain useful |
| Duplicate request / retry | Double credit or debit | Unique idempotency key, SHA-256 fingerprint, PostgreSQL transaction-scoped advisory lock, original response snapshot | H2 retry tests and PostgreSQL concurrent duplicate-key integration test |
| Concurrent withdrawals | Negative balance or overspending | Pessimistic account row lock; account balance and ledger entries commit atomically | H2 concurrency test and PostgreSQL integration test |
| Transfer deadlock | Failed or stuck transfers | Both account rows locked in deterministic account-ID order | Opposite-direction transfer stress test remains to be added |
| Ledger tampering | Erased or altered financial history | PostgreSQL append-only triggers; runtime role has no UPDATE/DELETE on ledger tables | PostgreSQL integration test checks runtime permissions and owner-level trigger rejection |
| Audit tampering | Erased/refined refusal evidence | Audit events are append-only; runtime role has no UPDATE/DELETE | PostgreSQL integration test checks owner-level trigger rejection |
| Money creation/destruction | Ledger imbalance or forged balance | Deferred PostgreSQL constraint triggers validate operation-specific posting shape, exactly two postings with zero sum, debit ownership, same-currency transfers, and customer/system balance reconciliation | PostgreSQL integration tests include unbalanced transaction, direct balance tampering, and unbacked account insert |
| Numeric precision errors | Incorrect minor-unit amount | Integer minor units, BigInt-based frontend decimal parsing, safe-integer amount limit | H2 boundary tests; broader fuzz/property testing remains useful |
| Unauthorized account mutation | Ownership/currency changed by compromised app DB credentials | Runtime role can update only balance columns; Hibernate dynamic updates limit normal balance writes | PostgreSQL integration test checks ownership/currency updates are denied |
| Credential theft | Account takeover | BCrypt password hashing, stateless Basic auth, memory-only frontend credentials | HTTPS deployment, XSS review, rate limiting, MFA and session redesign remain |
| Database privilege abuse | Unauthorized schema or ledger changes | Separate migration owner and runtime role; minimal grants | CI provisions both roles and tests runtime privilege boundaries; production role audit remains |
| Secret exposure | Database/API compromise | Environment variables and ignored local env files | Secret scanning and deployment configuration review remain |

## Trust boundaries

- Browser input and client-supplied account IDs are untrusted.
- API authentication establishes identity; service-layer ownership checks authorize individual account operations.
- PostgreSQL is the consistency boundary for row locks, transaction rows, ledger postings, reconciliation triggers, and append-only records.
- The runtime DB role is intentionally not the migration/schema owner.
- Migration credentials can alter schema and triggers; protect them separately from runtime credentials.
- Deployment secrets belong in the hosting provider's secret store, never in source control.

## Residual risks

This demo does not implement public registration, password reset, MFA, rate limiting, a production session/token lifecycle, monitoring, disaster recovery, or independent penetration testing. Broader HTTP-level authorization coverage, deployment, and live demo verification remain. Do not use real funds or real financial data.
