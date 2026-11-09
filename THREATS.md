# LedgerLine threat model

Scope: the LedgerLine demo API, React frontend, PostgreSQL schema, authentication, authorization, and money-movement workflows. Use synthetic data only. This document records design controls and required verification; it is not a penetration-test report or security certification.

| Threat | Impact | Current mitigation | Verification still required |
|---|---|---|---|
| Broken object-level authorization / IDOR | Reading or debiting another user's account | Service-layer ownership checks for account operations | HTTP tests with two users for every endpoint |
| Privilege escalation | Account holder gains admin access | Server-assigned roles; admin endpoint rejects ADMIN creation; no public registration | Test crafted role values and unauthenticated provisioning |
| Duplicate request / retry | Double credit or debit | Unique idempotency key plus request fingerprint | Concurrent duplicate-key test on PostgreSQL |
| Concurrent withdrawals | Negative balance or overspending | Pessimistic account row lock and same-transaction balance update | Multi-connection PostgreSQL stress test |
| Transfer deadlock | Failed or stuck transfers | Both account rows locked in deterministic ID order | Concurrent opposite-direction transfer test |
| Ledger tampering | Erased or altered financial history | Append-only PostgreSQL triggers; compensating-entry design | Verify trigger permissions and runtime role in PostgreSQL |
| Money creation/destruction | Ledger imbalance | Two signed postings per transaction; service verifies sum equals zero | DB-level invariant/reconciliation check |
| Numeric precision errors | Incorrect amounts | Integer minor units; no floating point in API | Boundary and overflow tests |
| Credential theft | Account takeover | BCrypt password hashing; stateless Basic auth; frontend memory-only credentials | HTTPS deployment, XSS review, rate limiting |
| Database privilege abuse | Unauthorized schema or ledger changes | Environment-based credentials and Flyway migration boundary | Least-privilege PostgreSQL role audit |
| Secret exposure | Database/API compromise | Environment variables and ignored local env files | Secret scanning and deployment configuration review |

## Trust boundaries

- Browser input and client-supplied account IDs are untrusted.
- API authentication establishes identity; service-layer ownership checks authorize access to individual accounts.
- PostgreSQL is the consistency boundary for account locks, transaction rows, ledger entries, and append-only triggers.
- Deployment secrets must be stored in the hosting provider, not in source control.

## Residual risks

This demo does not implement public registration, password reset, rate limiting, a complete auditor interface, a production session/token lifecycle, monitoring, or disaster recovery. PostgreSQL-specific migrations and concurrency behavior must be tested against a disposable PostgreSQL database. Do not use real funds or real financial data.
