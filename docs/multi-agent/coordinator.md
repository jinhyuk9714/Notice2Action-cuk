# Coordinator

## Responsibility
- Break work into lanes
- Approve file ownership
- Decide whether a task crosses contracts, DTOs, or migrations
- Collect handoffs
- Run final integration and verification
- Merge and push

## Decision rules
### Parallelize only when
- domains are independent
- no single-owner file is shared
- acceptance criteria can be checked per lane

### Do not parallelize when
- `NoticeFeedService.java` changes with frontend contract files
- a Flyway migration is involved
- a task changes DTO shape and UI rendering together
- ranking policy and parsing policy are being reworked at the same time

## Before work starts
Coordinator must freeze:
- goal
- in-scope / out-of-scope
- owner per lane
- forbidden files per lane
- final acceptance criteria

## Before merge
Coordinator must have:
- handoff from every active lane
- test output from every active lane
- one final integrated verification pass
