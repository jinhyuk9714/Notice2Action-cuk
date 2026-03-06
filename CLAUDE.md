# Notice2Action CUK - Project memory

## Goal
가톨릭대학교 성심교정 학생이 **내게 중요한 공지**를 먼저 보고,
상세 화면에서 **왜 중요한지**와 **지금 해야 할 일**을 근거와 함께 확인할 수 있는 웹 서비스.

## Why this project exists
학생은 학교 공지를 읽을 수는 있어도,
실제로는 아래를 자주 놓칩니다.

- 이 공지가 나와 관련 있는지
- 지금 행동이 필요한지
- 언제까지 해야 하는지
- 어디서 해야 하는지
- 왜 그렇게 판단했는지

현재 제품의 중심은 `개인화 공지 피드`이며,
행동 추출은 그 피드의 상세 해석 엔진 역할을 합니다.

## Product principles
1. **피드 우선**
   - 메인 진입점은 수동 입력이 아니라 개인화 공지 피드다.
2. **요약보다 행동 해석**
   - 상세에서는 원문 설명보다 실제 행동 여부와 근거가 우선이다.
3. **근거 없는 추론 금지**
   - 날짜, 대상, 시스템, 준비물, actionability는 근거가 있어야 한다.
4. **Deterministic first**
   - 날짜, 키워드, 시스템명, 대상, 준비물 추출은 규칙 기반 우선.
   - 불확실할 때만 LLM fallback을 고려한다.
5. **Trust-building UI**
   - 추출 결과와 함께 중요 이유, 행동 블록, 행동 블록별 근거를 같이 보여준다.
6. **Strict typing**
   - TypeScript strict
   - Java DTO/record/validation
   - API contract drift 최소화

## Core domain objects
- `NoticeSource`
- `PersonalizedNotice`
- `ExtractedAction`
- `EvidenceSnippet`
- `Reminder`
- `UserProfile`

## Scope
### V1
- representative notice auto-collection
- personalized feed
- notice detail with action-block evidence
- saved / hidden notices
- local profile filters

### Secondary / legacy
- raw text extraction
- public notice URL extraction
- PDF ingestion
- screenshot OCR
- email extraction
- legacy action inbox

### Later
- board expansion
- reminder / export polish
- team mode for 동아리/팀플 공지

## Engineering standards
### Frontend
- Vite + React + TS strict
- main UX is `#/feed`, `#/saved`, `#/profile`
- legacy routes stay available, but are not the primary product path
- loading/error/empty states should stay obvious

### Backend
- Spring Boot feature packages
- controller -> service -> persistence
- request/response contracts as records
- validation annotations on inputs
- deterministic extraction stays first-class even if LLM helpers exist later

## Workflow notes
- 실개발 기준 저장소는 clean clone을 사용한다.
- 기존 dirty local clone은 아카이브로 유지하고 참고용으로만 본다.
- salvage가 필요하면 clean clone에서 새 `codex/*` 브랜치로 다시 구현한다.

## Codex workflow
- Prefer minimal, typed changes
- Do not introduce hidden magic
- When editing backend, preserve package boundaries
- When editing frontend, keep UX obvious and explainable
- If extracting new fields, add evidence strategy first
