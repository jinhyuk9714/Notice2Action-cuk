---
name: spring-boot-guidelines
description: Use for Spring Boot backend changes in this project.
---

# Spring Boot backend rules

## Layering
- Controller: HTTP boundary only
- Service: business/extraction logic
- Persistence: repository/entity only
- DTOs: request/response contracts, not domain dumping grounds

## Java style
- Prefer records for DTOs.
- Use bean validation annotations at the edge.
- Keep methods small and explicit.
- Avoid untyped `Map<String, Object>` when a record/class is possible.

## Data
- Migrations go in Flyway first.
- Prefer additive schema changes.
- Keep enum values stable once they become part of an API contract.
