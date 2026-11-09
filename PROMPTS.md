# AI assistance and developer decision log

This file records AI-assisted work and material decisions for LedgerLine. AI-generated code is not proof of correctness; the developer remains responsible for review, execution, and understanding.

## Tools used

ChatGPT assisted with planning, repository inspection, Java/Spring Boot implementation, API design, tests, documentation, and GitHub operations. The challenge portal links to a public BalanceVault repository. It was inspected to understand the broader financial-ledger problem, but its implementation was not copied into LedgerLine. LedgerLine remains an independent Java/Spring Boot implementation.

## Earlier corrections

- A Java 25 suggestion was corrected to Java 21 to match the development environment.
- An unresolvable governance starter dependency was removed.
- Flyway dependency management was corrected.
- A local PostgreSQL authentication mismatch was addressed by resetting the local application user's password and using environment variables.

## 6 November 2026 — Identity and RBAC foundation

- **Task:** Establish Spring Boot, PostgreSQL/Flyway, database-backed authentication, and role-based access.
- **Implementation:** User identity schema, BCrypt password hashing, ADMIN/ACCOUNT_HOLDER/AUDITOR roles, stateless Spring Security, one-time environment-configured admin bootstrap, and H2 tests.
- **Verification:** GitHub source inspection only at commit time. CI/local execution was still pending.

## 9 November 2026 — Banking operations and React dashboard

- **Task:** Catch up on the unfinished banking workflows and create a usable demo UI.
- **Implementation added:** Customer account entity and schema, internal cash-clearing ledger account, immutable transaction/entry model, two-sided postings, account ownership checks, integer minor-unit amounts, pessimistic account locking, idempotency keys with request fingerprints, deposit/withdrawal/transfer endpoints, account statements, admin provisioning of non-admin users, and React dashboard flows.
- **Design correction:** A one-sided ledger entry is not double-entry accounting. Deposit and withdrawal operations were revised to post a customer-side entry and an equal/opposite system cash-clearing entry. Every transaction is checked to sum to zero before entries are saved.
- **Verification status:** Source files and GitHub commit contents are reviewable. Maven tests, PostgreSQL migrations/triggers, frontend build, and live end-to-end flows have not yet been executed in this session. Do not claim they pass until run.
- **Known risks:** Concurrent reuse of one idempotency key across different account lock sets needs a dedicated PostgreSQL test and graceful conflict handling. A reconciliation job and complete admin/auditor UI are not yet implemented.

## Ongoing decision record

For each material AI-assisted change, record the task, proposed change, developer review/correction, exact test command, and actual result. Never fabricate test results, commits, or manual work.
