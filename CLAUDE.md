# Notice2Action CUK - Project memory

## Goal
가톨릭대학교 성심교정 학생이 공지 / PDF / 이메일 / 스크린샷에서
**내가 지금 해야 할 일(Action)**만 구조화해서 볼 수 있는 웹 서비스.

## Why this project exists
학생이 학교 정보를 "읽는 것"은 가능하지만,
실제로는 아래를 놓치는 경우가 많다.

- 무엇을 해야 하는지
- 언제까지 해야 하는지
- 무엇을 준비해야 하는지
- 내가 대상인지
- 어느 시스템(TRINITY, 사이버캠퍼스, 외부 사이트)에서 해야 하는지

이 프로젝트는 요약보다 **행동 추출**에 집중한다.

## Product principles
1. **요약보다 행동**
   - 원문 소개보다 "지금 해야 할 일"이 우선이다.
2. **근거 없는 추론 금지**
   - 날짜/대상/제출물/신청 경로는 근거가 있어야 한다.
3. **Deterministic first**
   - 날짜, 키워드, 시스템명, 준비물 추출은 규칙 기반 우선.
   - 불확실할 때만 LLM fallback.
4. **Trust-building UI**
   - 추출 결과만 보여주지 말고 근거 snippet도 같이 보여준다.
5. **Popular-stack friendly**
   - React/Vite 프론트, Spring Boot 백엔드, Gradle/JPA/Flyway 기본.
6. **Strict typing**
   - TypeScript strict
   - Java DTO/record/validation
   - API contract drift 최소화

## Core domain objects
- `NoticeSource`
- `ActionExtractionRequest`
- `ExtractedAction`
- `EvidenceSnippet`
- `Reminder`
- `UserProfile`

## MVP scope
### V1
- raw text paste
- public notice URL paste
- action extraction
- action inbox list
- action detail with evidence

### V1.5
- PDF ingestion
- screenshot OCR
- profile relevance
- due-date sorting and reminder

### V2
- email forwarding
- calendar export
- saved sources / history
- team mode for 동아리/팀플 공지

## Engineering standards
### Frontend
- Vite + React + TS strict
- state is local until real complexity appears
- fetch layer typed explicitly
- make loading/error/empty states obvious

### Backend
- Spring Boot feature packages
- controller -> service -> persistence
- request/response contracts as records
- validation annotations on inputs
- Flyway migrations before JPA usage grows

## Claude workflow
- Prefer minimal, typed changes
- Do not introduce hidden magic
- When editing backend, preserve package boundaries
- When editing frontend, keep UX obvious and explainable
- If extracting new fields, add evidence strategy first
