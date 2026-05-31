# ClearLeaf

ClearLeaf is a local-first examination preparation platform. Phase 1 targets
invited CBSE/NCERT Grade 5 and 6 students practicing Math and Science.

## Local development

Prerequisites: Docker Desktop and Docker Compose.

```bash
cp .env.example .env
docker compose up --build
```

Back up and restore PostgreSQL:

```bash
./scripts/backup-local.sh
./scripts/restore-local.sh backups/postgres-YYYYMMDD-HHMMSS.sql
```

Services:

- Frontend: http://localhost:3000
- API health: http://localhost:8081/api/health
- Keycloak: http://localhost:8080
- Mailpit approval-email inbox: http://localhost:8025
- PostgreSQL: localhost:5432
- MinIO console: http://localhost:9001

## Signup approval

Open http://localhost:3000/account to sign in with email and password or to
submit a signup request. By default, emails are captured locally at
http://localhost:8025 and are not delivered to external email addresses.

Configuration:

```text
USER_CREATION_APPROVAL_REQUIRED=Y
APP_SIGNUP_APPROVAL_EMAIL_TO=admin@clearleaf.local
SPRING_MAIL_HOST=mailpit
SPRING_MAIL_PORT=1025
```

Set `USER_CREATION_APPROVAL_REQUIRED=N` before rebuilding and restarting the
stack to create student accounts immediately without email approval.

To deliver real email, replace the `SPRING_MAIL_*` values with your SMTP
provider settings and set `APP_SIGNUP_APPROVAL_EMAIL_TO` to the approver's real
email address.

## Phase 1 scope

The repository currently provides the runnable foundation, requirements
traceability, taxonomy registry, taxonomy lifecycle API, question model,
workflow validation, and exact-match scoring rules. Continue implementation
against `docs/releases/phase-01.md`.
