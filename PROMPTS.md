# AI assistance and developer decision log

This file records AI-assisted work and material engineering decisions for LedgerLine. AI-generated code is not proof of correctness. The developer remains responsible for reviewing, running, understanding, and presenting the implementation honestly.

## Tools used

ChatGPT assisted with repository inspection, Spring Boot implementation, React UI work, database design, adversarial test design, documentation, and GitHub operations. The Snovasys portal links to a separate BalanceVault source repository. That repository was treated only as problem context; its implementation was not copied into LedgerLine. LedgerLine is an independent Java/Spring Boot implementation.

## Dated work and corrections

### 6 November 2026 — Authentication and role foundation

- **Prompt/task:** Establish a secure Spring Boot foundation with database-backed authentication and role-based access.
- **AI output:** Proposed Java/Spring Boot dependencies, a user table, BCrypt password hashing, ADMIN/ACCOUNT_HOLDER/AUDITOR roles, and route-level authorization.
- **What needed correction:** An early Java 25 suggestion did not match the project's Java 21 environment. A governance starter dependency could not be resolved, and the initial Flyway dependency configuration needed correction.
- **Developer correction:** Kept Java 21, removed the unresolved starter, corrected dependency management, and used environment-based DB credentials. The identity and RBAC foundation was committed separately.
- **Evidence:** [Day 1 security foundation](https://github.com/Yogesh-Chandra-007/LedgerLine/commit/c90a6185438df456060c6e4ab82f75d380511fcc).

### 9 November 2026 — Core banking operations and double-entry model

- **Prompt/task:** Implement accounts, deposits, withdrawals, transfers, and a ledger suitable for financial-integrity testing.
- **AI output:** Added account and transaction entities, a system cash-clearing counter-account, service-layer balance changes, transaction endpoints, and a React dashboard.
- **What needed correction:** A customer-only posting is not double-entry accounting. A deposit or withdrawal needs an equal-and-opposite system counter-posting. The dashboard also initially used floating-point amount conversion and an over-escaped decimal validation pattern.
- **Developer correction:** Required two signed postings per transaction, represented amounts as integer minor units, replaced decimal parsing with BigInt conversion, corrected the validation pattern, and added a transaction result-balance snapshot for reliable idempotent replay.
- **Evidence:** [Banking foundation and dashboard](https://github.com/Yogesh-Chandra-007/LedgerLine/commit/49c06af3b1d288112b4f0e4ab3d589a1b0c8236c).

### 9 November 2026 — Concurrency and idempotency hardening

- **Prompt/task:** Make retries and simultaneous money movements safe.
- **AI output:** Added pessimistic account row locks, deterministic lock ordering for transfers, a unique idempotency key, and a SHA-256 request fingerprint.
- **What needed correction:** Locking only the account row does not serialize the same idempotency key when two requests target different accounts. A uniqueness constraint prevents a second commit but can leave the losing request as an unhandled persistence error. Returning the current account balance on replay also does not reproduce the original response.
- **Developer correction:** Added a transaction-scoped PostgreSQL advisory lock keyed by the idempotency key, acquired before account locks, and persisted the original result account/balance snapshot. Added PostgreSQL tests for cross-account duplicate keys, concurrent withdrawals, and opposite-direction transfers.
- **Evidence:** [Concurrency and database security implementation](https://github.com/Yogesh-Chandra-007/LedgerLine/commit/488152fd0007c87f359ad20bc12cdf1f713353c8).

### 9 November 2026 — PostgreSQL schema and database-level integrity

- **Prompt/task:** Verify the actual PostgreSQL schema and enforce financial invariants below the service layer.
- **AI output:** Added Flyway migrations for audit events, deferred ledger checks, balance reconciliation, and separate migration/runtime database roles.
- **What needed correction:** PostgreSQL schema validation exposed Hibernate mapping mismatches: the original currency column used CHAR(3) while Hibernate expected VARCHAR(3), and the request fingerprint used CHAR(64) while Hibernate expected VARCHAR(64). An early PostgreSQL test also incorrectly expected both postings to belong to the customer account; the counter-posting belongs to the system clearing account.
- **Developer correction:** Added forward-only V4/V5 migrations to normalize the two columns without rewriting earlier migrations. Corrected the test to assert one customer posting and two postings across the whole transaction. Expanded deferred database triggers to validate operation-specific posting shape, source ownership, same-currency transfers, active account status, result snapshots, safe numeric bounds, and account/ledger reconciliation.
- **Evidence:** [Currency mapping correction](https://github.com/Yogesh-Chandra-007/LedgerLine/commit/c217307346c389c19cb92b240be248d2e99d2990) and [database-level integrity implementation](https://github.com/Yogesh-Chandra-007/LedgerLine/commit/cab368fbf671dff316a495c526d3371bdd0b4010).

### 9 November 2026 — Audit trail, statements, and role workspaces

- **Prompt/task:** Complete the auditor/admin workflows and make refused operations visible without changing balances.
- **AI output:** Added an append-only audit-event model, audit hooks for successful and rejected requests, admin provisioning and overview endpoints, read-only auditor endpoints, and role-specific React workspaces.
- **What needed correction:** Recording only successful ledger entries omits failed attempts. Writing a rejection inside the failed business transaction would roll back the audit event too. Statements also lacked the initiating identity and running balance.
- **Developer correction:** Recorded successful audit events in the business transaction and refusals in a separate REQUIRES_NEW transaction after rollback. Captured request metadata and attempted amount when parseable, associated refused requests with the target account, and merged them into statements without changing the running balance. Added HTTP tests for refused withdrawals and invalid amounts.
- **Evidence:** [Audit and statement implementation](https://github.com/Yogesh-Chandra-007/LedgerLine/commit/cab368fbf671dff316a495c526d3371bdd0b4010).

### 9 November 2026 — Single-container packaging

- **Prompt/task:** Prepare one deployable artifact so the UI and API can share an origin.
- **AI output:** Added a multi-stage Dockerfile that builds the React app, packages its static files into Spring Boot, and runs the jar in a non-root Java 21 runtime image.
- **What needed correction:** The initial container draft assumed a package-lock file that the repository does not contain. That would make `npm ci` fail.
- **Developer correction:** Changed the Docker build and CI install step to `npm install`, added a Docker build job, made the server honor the platform `PORT`, and documented separate migration/runtime database credentials and the health endpoint.
- **Evidence:** [Container packaging and deployment guide](https://github.com/Yogesh-Chandra-007/LedgerLine/commit/a83b9e17a2ba4fa93f3df5f1efa2e0be5d2cfe93).

## Verification record

- GitHub Actions run [#42570259363](https://github.com/Yogesh-Chandra-007/LedgerLine/actions/runs/42570259363) passed backend H2/PostgreSQL tests and the frontend production build for commit `0c198ad49ccb8579dac182645843cfd0a3e3a0bc`.
- GitHub Actions run [#42570536320](https://github.com/Yogesh-Chandra-007/LedgerLine/actions/runs/42570536320) passed both jobs for commit `c1be61010de77774cbadd3df555198579c6c2a8d`.
- GitHub Actions run [#42570700162](https://github.com/Yogesh-Chandra-007/LedgerLine/actions/runs/42570700162) passed backend H2/PostgreSQL tests and frontend build for commit `cab368fbf671dff316a495c526d3371bdd0b4010`.
- GitHub Actions run [#42570866389](https://github.com/Yogesh-Chandra-007/LedgerLine/actions/runs/42570866389) passed all three jobs for commit `a83b9e17a2ba4fa93f3df5f1efa2e0be5d2cfe93`: backend H2/PostgreSQL tests, frontend production build, and production container build.
- The latest successful backend run reported 35 tests: 17 banking workflow tests, 1 health test, 10 PostgreSQL integrity tests, and 7 role-based access tests; all had zero failures, errors, or skips.
- The PostgreSQL CI service is ephemeral. This is real PostgreSQL integration evidence, not evidence of a persistent production deployment.
- No live deployment or independent penetration test is claimed.

## Ongoing decision record

For each material AI-assisted change, add the task/prompt, proposed output, what was wrong or incomplete, the developer's correction, the exact test command, the actual result, and the relevant commit link. Never fabricate manual work, test results, or commit history.
