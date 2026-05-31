# ADR 0001: Start with a modular monolith

## Decision

Deploy one Spring Boot application with explicit internal modules. Use
PostgreSQL, MinIO, and Keycloak as external infrastructure services.

## Reason

The private pilot needs low operational cost and rapid iteration. Module
boundaries preserve future extraction options for high-load exam delivery,
submission ingestion, AI workers, and crawler jobs.
