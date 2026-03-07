# Notice2Action Multi-Agent Pack

## Purpose
Use multiple agents only when work can be split by domain without shared ownership conflicts.

This project uses:
- `1 Coordinator`
- `3 Execution Agents`

More than three execution lanes increases merge and policy conflicts faster than it increases delivery speed.

## Roles
| Role | Primary scope | Must not own |
| --- | --- | --- |
| Coordinator | task split, ownership approval, contract decisions, final integration | none |
| Feed UI Agent | feed/detail UI, CSS, layout, component rendering tests | backend, DTO/API shape, migration |
| Notice Backend Agent | ingestion, parser, ranking, notice feed API, backend notice tests | frontend UI/CSS |
| QA/Docs Agent | fixtures, regression tests, docs, smoke notes | product logic files alone |

## Single-owner files
These files are never edited by two agents in the same round:
- `apps/api/src/main/java/com/cuk/notice2action/extraction/service/notice/NoticeFeedService.java`
- `apps/web/src/lib/types.ts`
- `apps/web/src/lib/api.ts`
- `apps/web/src/lib/router.ts`
- `apps/api/src/main/resources/db/migration/*`

If a task needs one of these plus other domains, the Coordinator assigns one agent as the only owner for that round.

## Branch and Worktree Rules
- Real work starts from the clean clone, never from an archived dirty clone.
- Branch format: `codex/<lane>-<task>`
- One agent = one branch = one worktree
- Recommended worktree root: `~/.config/superpowers/worktrees/Notice2Action/<branch>`
- QA/Docs starts only after a production diff exists.

## Execution Order
1. Coordinator writes the task brief.
2. One execution agent owns each lane.
3. Each agent implements only inside its ownership boundary.
4. Each agent hands off with the required checklist.
5. Coordinator integrates, verifies, and pushes.

## Typical splits
### UI-only work
- Feed UI Agent implements
- QA/Docs Agent adds regression coverage and docs
- Notice Backend Agent does not participate

### Ranking or ingestion work
- Notice Backend Agent owns the logic
- QA/Docs Agent owns fixtures and regression tests
- Feed UI Agent does not participate unless rendering changes are explicitly required

### Contract-crossing work
If a task touches `NoticeFeedService.java` plus DTOs and frontend contract files, do not parallelize it. Coordinator assigns a single owner.

## Required documents
- `coordinator.md`
- `feed-ui-agent.md`
- `notice-backend-agent.md`
- `qa-docs-agent.md`
- `task-brief-template.md`
- `handoff-checklist.md`
