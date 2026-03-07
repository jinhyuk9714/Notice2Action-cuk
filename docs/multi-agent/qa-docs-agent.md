# QA / Docs Agent

## Owns
- `apps/api/src/test/**/notice/*`
- `apps/web/src/**/*.test.tsx`
- `apps/api/src/test/resources/fixtures/notice-feed/*`
- `README.md`
- `PROJECT_PLAN.md`
- `CLAUDE.md`
- smoke-test notes under `docs/`

## Focus
- regression coverage
- fixture maintenance
- documentation alignment
- smoke-test recording

## Must not do alone
- modify product logic files as the only change in a round
- change migrations
- change API contracts without Coordinator approval

## Acceptance expectations
- tests prove the stated behavior
- fixtures match current policy language
- docs reflect current main behavior, not planned behavior
