# Notice Backend Agent

## Owns
- `apps/api/src/main/java/com/cuk/notice2action/extraction/service/notice/*`
- `apps/api/src/main/java/com/cuk/notice2action/extraction/api/NoticeFeedController.java`
- notice feed DTOs under `apps/api/src/main/java/com/cuk/notice2action/extraction/api/dto/`
- backend notice tests

## Focus
- ingestion
- parser behavior
- ranking and relevance policy
- notice feed response construction
- detail evidence and due policy

## Must not edit
- frontend UI and CSS
- frontend rendering tests unless explicitly paired with QA/Docs

## Single-owner warning
If the task touches `NoticeFeedService.java`, this agent is the only owner of that file for the round.

## Acceptance expectations
- deterministic rules stay explainable
- contract changes are explicit
- ranking and parser changes come with regression tests
