# Deployment guide

LedgerLine can be packaged as one container: the React/Vite production build is copied into Spring Boot's static resources, so the browser UI and `/api` routes share one origin.

## Build and run the container

From the repository root:

```bash
docker build -t ledgerline:local .
```

The image listens on `PORT` (default 8080), runs as a non-root user, and exposes `GET /api/health` as its health check.

The app needs a PostgreSQL database and two separate database roles:

- `ledgerline_migrator`: owns schema objects and is used only by Flyway.
- `ledgerline_app`: runtime-only permissions used by Spring Data/JPA.

Configure these environment variables in the hosting provider:

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL for the runtime database connection |
| `DB_USERNAME` | Runtime role, normally `ledgerline_app` |
| `DB_PASSWORD` | Runtime role password |
| `MIGRATION_DB_URL` | JDBC URL used by Flyway |
| `MIGRATION_DB_USERNAME` | Migration owner, normally `ledgerline_migrator` |
| `MIGRATION_DB_PASSWORD` | Migration owner password |
| `BOOTSTRAP_ADMIN_USERNAME` | Optional one-time administrator username |
| `BOOTSTRAP_ADMIN_PASSWORD` | Optional one-time bootstrap password; remove after first successful startup |
| `PORT` | Hosting platform's HTTP port; defaults to 8080 |

The V3 migration grants the runtime role only the permissions the application needs. Do not run the app with the migration-owner or PostgreSQL superuser credentials.

## Railway deployment outline

1. Create a PostgreSQL service and a separate application service from this GitHub repository.
2. Provision the two database roles using the PostgreSQL superuser during initial database setup.
3. Set the variables above on the application service. Use the provider's private/internal database connection URL for both JDBC URLs where supported.
4. Configure the service health check path as `/api/health`.
5. Deploy the container and wait for Flyway to finish before testing sign-in.
6. Create the first admin using the bootstrap variables, then remove the bootstrap password.
7. Verify the live frontend, backend health, all three roles, deposits, withdrawals, transfers, statements, and rejected-attempt audit events from a private browser window.

Do not put database passwords, admin passwords, or API credentials in the repository or build arguments. Store them only in the hosting provider's secret/environment-variable manager.

## Production-readiness boundary

A successful image build is not a successful deployment. Before claiming a live deployment, verify the provider's deployment status, health check, database migrations, HTTPS URL, and role-based end-to-end flows. This guide does not claim that a deployment has already been created.
