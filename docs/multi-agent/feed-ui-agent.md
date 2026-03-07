# Feed UI Agent

## Owns
- `apps/web/src/components/PersonalizedFeedView.tsx`
- `apps/web/src/components/NoticeDetailContent.tsx`
- `apps/web/src/App.tsx`
- related files under `apps/web/src/styles/`
- rendering tests for feed/detail UI

## Focus
- feed layout
- detail layout
- board chips
- badges
- scroll behavior
- readability polish

## Must not edit
- backend services/controllers
- DTOs
- `apps/web/src/lib/api.ts`
- `apps/web/src/lib/types.ts`
- `apps/web/src/lib/router.ts` unless explicitly assigned by Coordinator
- Flyway migrations

## Acceptance expectations
- no API contract drift
- UI stays explainable
- tests cover structure and visible behavior, not styling internals only
