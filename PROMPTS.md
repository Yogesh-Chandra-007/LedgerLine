# AI Assistance and Developer Decisions

This file records AI assistance, material corrections, and developer verification for LedgerLine. It is intended to make the development process transparent.

## AI tools used

ChatGPT has been used for project planning, Java/Spring Boot configuration guidance, PostgreSQL setup and troubleshooting, React/Vite setup, debugging, Git/GitHub workflow guidance, and implementation assistance. The developer remains responsible for reviewing, implementing, testing, and understanding the project.

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
- **Result:** Reported corrected; migration behavior itself was not claimed as verified at that time.

### PostgreSQL authentication
- **AI suggestion:** Initial database connection configuration did not resolve the local authentication failure.
- **What was incorrect, incomplete, or risky:** The application database user's password/configuration did not match the local PostgreSQL credentials.
- **Developer decision / correction:** The application user's password was reset and the backend was configured to receive the password through an environment variable.
- **How it was tested:** The developer reported that the backend setup and database connection were verified locally.
- **Result:** Reported resolved.

## Day 1 — GitHub foundation and security implementation (6 November 2026)

- **Prompt / task:** Follow the agreed six-day roadmap. Day 1 is foundation and security: project setup, initial identity schema, authentication, and role-based access control.
- **AI suggestion:** Build the Java 21 Spring Boot API foundation, PostgreSQL/Flyway user schema, database-backed authentication, role authorization, isolated tests, and React/Vite health-check interface.
- **What was incomplete or risky:** The first GitHub commit only established documentation and a basic app scaffold. It did not satisfy the agreed Day 1 authentication and RBAC target. This was identified and corrected by expanding the implementation.
- **Developer decision / correction:** Keep the repository private; do not commit secrets; do not add public self-registration or a hard-coded production administrator; use environment-provided one-time bootstrap credentials; use a test-only H2 profile.
- **Implementation added:** PostgreSQL app_users migration, UserAccount and UserRole domain types, repository, database-backed UserDetailsService, BCrypt password encoder, stateless Spring Security configuration, role-protected route probes, administrator bootstrap, role-based tests, and data/security model documentation.
- **How it was tested:** GitHub file/commit verification only. Maven tests, PostgreSQL migration, and frontend build have not yet been executed in this session. GitHub Actions is configured to run the backend tests and frontend build after push.
- **Result:** Day 1 source implementation committed; runtime/CI verification pending. Do not mark Day 1 verified until the workflow passes and the application is exercised.

## Verification boundary

The developer reported completing local Java 21/Maven, Spring Boot, PostgreSQL, React/Vite, and health-endpoint setup before this GitHub scaffold was generated. That report is separate from verification of the newly committed repository source.

The following are not yet implemented: account management and ownership checks, deposits, withdrawals, transfers, double-entry ledger enforcement, money-conservation checks, overdraft prevention under concurrency, idempotency, append-only audit logging, statements, full frontend/API integration, and deployment. These are assigned to Days 2–5.

## Ongoing decision log

Add an entry whenever AI guidance materially affects implementation. Record mistakes and corrections plainly; do not describe a feature as passing unless a test was actually run.

### YYYY-MM-DD — Short task title
- **Prompt / task:**
- **AI suggestion:**
- **What was incorrect, incomplete, or risky:**
- **Developer decision / correction:**
- **How it was tested:**
- **Result:** Pass / Fail / Not yet tested
