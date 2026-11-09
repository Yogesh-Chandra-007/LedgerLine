# Deployment guide

LedgerLine is packaged as one Docker container: the React production build is copied into Spring Boot static resources, so the UI and `/api` routes share one origin.

## Recommended demo hosting: Render + Neon

The repository includes a Render Blueprint at `render.yaml`. It configures a free Docker web service in Singapore, deploys from `main`, and checks `/api/health`.

**Important limitations:** Render's free web service sleeps after 15 minutes without traffic and can take about a minute to wake. Neon Free suspends database compute after 5 minutes idle and has monthly compute/network quotas. This setup is suitable for a challenge demo, not production banking. Review the providers' current limits before creating resources.

### 1. Create the Neon PostgreSQL database

1. Create a Neon account and a new PostgreSQL project.
2. Save the connection details securely. Use the Neon connection string and add `sslmode=require`; the JDBC URL must start with `jdbc:postgresql://`.
3. In the Neon SQL Editor, create the separate runtime role (choose a strong password):

```sql
CREATE ROLE ledgerline_app LOGIN PASSWORD 'REPLACE_WITH_A_STRONG_UNIQUE_PASSWORD';
GRANT CONNECT ON DATABASE neondb TO ledgerline_app;
GRANT USAGE ON SCHEMA public TO ledgerline_app;
```

Use the Neon project's default owner role as the Flyway migration identity. The V3 migration grants `ledgerline_app` the minimum runtime table/column permissions. Do not use the owner credentials as the application's runtime identity.

If your Neon database name is not `neondb`, substitute its actual name in the GRANT statement.

### 2. Create the Render service

1. Sign in to Render and connect the GitHub account that can access this private repository.
2. Choose **New → Blueprint** and select `Yogesh-Chandra-007/LedgerLine`, branch `main`.
3. Render reads `render.yaml`. Confirm the service is named `ledgerline`, uses the Docker runtime and the Free plan. Do not select a paid plan unless you explicitly choose to.
4. Supply the prompted environment variables as secrets/values:

| Variable | Value |
|---|---|
| `DB_URL` | Neon JDBC URL, e.g. `jdbc:postgresql://HOST/DB?sslmode=require` |
| `DB_USERNAME` | `ledgerline_app` |
| `DB_PASSWORD` | Runtime role password created in step 1 |
| `MIGRATION_DB_URL` | Same Neon JDBC URL |
| `MIGRATION_DB_USERNAME` | Neon project owner role username |
| `MIGRATION_DB_PASSWORD` | Neon project owner role password |
| `BOOTSTRAP_ADMIN_USERNAME` | A unique administrator username |
| `BOOTSTRAP_ADMIN_PASSWORD` | A strong, unique one-time password |

Do not paste credentials into GitHub, chat, source files, or Docker build arguments. Enter them only in Render's environment-variable/secret interface.

### 3. Deploy and verify

1. Start the Blueprint deploy and watch the build/runtime logs.
2. Confirm Flyway completes all migrations and the service health check passes.
3. Open the generated HTTPS `onrender.com` URL. Confirm the React app loads and `/api/health` responds successfully.
4. Sign in with the bootstrap administrator, create one ACCOUNT_HOLDER and one AUDITOR, then remove `BOOTSTRAP_ADMIN_PASSWORD` from Render and redeploy/restart.
5. Test the account-holder flow: create account, deposit, withdraw, transfer, replay an idempotency key, and inspect the statement.
6. Test the auditor's read-only views and verify an account holder cannot access another user's account.
7. Record the live URL and the exact date/time of verification in the challenge submission. Do not claim tests you did not perform.

## Container details

Build locally from the repository root:

```bash
docker build -t ledgerline:local .
```

The image listens on `PORT` (default 8080), runs as a non-root user, and exposes `GET /api/health` for the platform health check.

## Production-readiness boundary

A successful image build is not a successful deployment. Before claiming a live deployment, verify the provider's deployment status, health check, database migrations, HTTPS URL, and role-based end-to-end flows. Free-tier hosting is for demonstration only; it does not provide the availability, backups, or operational guarantees required for a real financial service.
