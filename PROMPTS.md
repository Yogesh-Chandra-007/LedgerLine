# AI Assistance and Developer Decisions

This file records AI assistance, material corrections, and developer verification for LedgerLine. It is intended to make the development process transparent.

## AI tools used

ChatGPT has been used for project planning, Java/Spring Boot configuration guidance, PostgreSQL setup and troubleshooting, React/Vite setup, debugging, and Git/GitHub workflow guidance. The developer remains responsible for reviewing, implementing, testing, and understanding the project.

## Recorded AI mistakes and corrections

### Java version configuration
- **AI suggestion:** An initial Maven configuration used Java 25.
- **What was incorrect, incomplete, or risky:** The installed development JDK was Java 21, so the suggested configuration did not match the local environment.
- **Developer decision / correction:** The project configuration was changed to Java 21.
- **How it was tested:** The local Maven build/setup was checked.
- **Result:** Reported corrected; retain build verification with subsequent commits.

### Unresolvable governance starter dependency
- **AI suggestion:** Add a governance starter dependency.
- **What was incorrect, incomplete, or risky:** The dependency could not be resolved from Maven Central and was not required for the current foundation.
- **Developer decision / correction:** The dependency was removed.
- **How it was tested:** Maven setup was checked after the change.
- **Result:** Reported corrected.

### Flyway dependency configuration
- **AI suggestion:** Add Flyway using the initial dependency configuration.
- **What was incorrect, incomplete, or risky:** The configuration caused Maven version-management issues.
- **Developer decision / correction:** The dependency configuration was corrected.
- **How it was tested:** Maven setup was checked.
- **Result:** Reported corrected; migration behavior itself is not claimed as verified.

### PostgreSQL authentication
- **AI suggestion:** Initial database connection configuration did not resolve the local authentication failure.
- **What was incorrect, incomplete, or risky:** The application database user's password/configuration did not match the local PostgreSQL credentials.
- **Developer decision / correction:** The application user's password was reset and the backend was configured to receive the password through an environment variable.
- **How it was tested:** The developer reported that the backend setup and database connection were verified locally.
- **Result:** Reported resolved.

## Verification boundary

The developer reported completing the local Java 21/Maven, Spring Boot, PostgreSQL, React/Vite, and health-endpoint foundation. The repository documentation is not proof that every local setup step is reproducible on another machine.

The following are **not yet implemented or verified**: complete authentication and RBAC, account management, deposits, withdrawals, transfers, double-entry ledger enforcement, money-conservation checks, overdraft prevention under concurrency, idempotency, append-only audit logging, statements, automated transaction/security/concurrency tests, complete frontend/API integration, and deployment.

## Ongoing decision log

Add an entry whenever AI guidance materially affects implementation. Record mistakes and corrections plainly; do not describe a feature as passing unless a test was actually run.

### YYYY-MM-DD — Short task title
- **Prompt / task:**
- **AI suggestion:**
- **What was incorrect, incomplete, or risky:**
- **Developer decision / correction:**
- **How it was tested:**
- **Result:** Pass / Fail / Not yet tested
