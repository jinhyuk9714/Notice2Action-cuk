# API

Spring Boot API for Notice2Action CUK.

## Why this structure
- feature-first package layout
- record-based request/response DTOs
- service layer for extraction logic
- Flyway for schema changes
- PostgreSQL as the default local database

## Run
```bash
docker compose up -d db
cd apps/api
gradle bootRun
```
