# Notice2Action

`Notice2Action`은 가톨릭대학교 성심교정 학생을 위한 **개인화 공지 피드 + 행동 추출 서비스**입니다. 학교 공지를 많이 보여주는 데서 끝내지 않고, 학생 프로필에 맞는 공지를 먼저 올리고 공지 안에서 지금 해야 할 행동과 근거를 함께 보여주는 흐름을 목표로 합니다.

## 문제의식

학교 공지는 게시판과 첨부파일 안에 중요한 일정, 신청 조건, 준비물이 흩어져 있습니다. 학생 입장에서는 "나에게 해당되는지", "언제까지 무엇을 해야 하는지", "근거가 어디인지"가 더 중요합니다. Notice2Action은 공지를 개인화 피드로 정렬하고, 상세 화면에서 action block과 evidence snippet을 확인하도록 구성했습니다.

## 제품 흐름

```text
성심교정 공지 수집
  -> 프로필 기반 관련도 계산
  -> 개인화 공지 피드 (#/feed)
  -> 공지 상세에서 중요 이유, 행동 블록, 근거 확인
  -> 저장, 숨김, 다시 보기
```

수동 추출 기능은 보조 도구로 유지합니다.

```text
텍스트 / 공지 URL / PDF / 스크린샷 / 이메일
  -> 규칙 기반 액션 추출
  -> legacy inbox / source history / calendar export
```

## 주요 화면

- `#/feed`: 학과, 학년, 신분, 관심 키워드, 선호 게시판을 기준으로 정렬한 개인화 공지 피드
- `#/profile`: 피드 정렬과 중요 이유 계산에 쓰는 프로필 설정
- `#/saved`: 저장한 공지 확인
- `#/extract`: 텍스트, URL, PDF, 스크린샷, 이메일 기반 수동 추출
- `#/inbox`: 저장된 액션 검색, 필터, 상태 토글, 수정, 캘린더 export
- `#/sources`: 추출/수집 소스 이력 조회

## 주요 기능

개인화 피드:

- 성심교정 공지 자동 수집
- 프로필 기반 relevance 계산
- 공지별 importance reason 표시
- `action_required` / `informational` 분류
- 공지 상세 본문, 첨부파일, 행동 블록, evidence snippet 제공

수동 추출:

- 텍스트 붙여넣기
- 공지 URL 입력
- PDF 업로드
- 스크린샷 OCR
- 이메일 본문 추출

legacy inbox:

- 제목/요약 검색
- 카테고리, 날짜 범위, 상태 필터
- `pending` / `completed` 상태 토글
- 액션 상세 편집과 revert
- `.ics` calendar export
- CSV export
- 브라우저 Notification 기반 reminder

## 추출 원칙

- 요약보다 행동을 우선합니다.
- 근거 없는 추론을 피합니다.
- deterministic first 방식을 사용합니다.
- trust-building UI를 위해 evidence snippet을 결과와 함께 보여줍니다.

현재 추출기는 날짜, 시스템 힌트, 준비물, 자격 조건, 상태를 규칙 기반으로 추출하고 결과를 evidence와 함께 저장합니다. `LlmActionEnhancementService`는 LLM 기반 보강 경로로 분리되어 있습니다.

## API 개요

모든 API prefix는 `/api/v1`입니다.

Personalized notice feed:

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/notices/feed` | 개인화 공지 피드 조회 |
| `GET` | `/notices/{id}` | 공지 상세 조회 |

Manual extraction / legacy inbox:

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/extractions/actions` | 텍스트/URL 기반 수동 추출 |
| `POST` | `/extractions/pdf` | PDF 추출 |
| `POST` | `/extractions/email` | 이메일 추출 |
| `POST` | `/extractions/screenshot` | 스크린샷 OCR 추출 |
| `GET` | `/actions` | 액션 목록 조회 |
| `GET` | `/actions/{id}` | 액션 상세 조회 |
| `PATCH` | `/actions/{id}` | 액션 수정 |
| `DELETE` | `/actions/{id}` | 액션 삭제 |
| `GET` | `/actions/calendar.ics` | 액션 캘린더 export |
| `GET` | `/actions/{id}/calendar.ics` | 단일 액션 calendar export |
| `GET` | `/sources` | 소스 목록 조회 |
| `GET` | `/sources/{id}` | 소스 상세 조회 |

Health:

- `GET /api/v1/health`
- `GET /actuator/health`

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Frontend | React 19, Vite 7, TypeScript, Zod |
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA, Flyway |
| Database | PostgreSQL 16 |
| Parsing | Jsoup, PDFBox, Tess4J |
| Test | JUnit 5, AssertJ, Vitest, Testing Library |

## 프로젝트 구조

```text
Notice2Action/
├── apps/
│   ├── api/   # Spring Boot API
│   └── web/   # React + Vite frontend
├── docker-compose.yml
├── PROJECT_PLAN.md
└── README.md
```

세부 경로:

- `apps/api/src/main/java/com/cuk/notice2action/extraction/api`: REST controller와 DTO
- `apps/api/src/main/java/com/cuk/notice2action/extraction/service`: 추출, 영속화, 캘린더, URL/PDF/OCR 처리
- `apps/api/src/main/java/com/cuk/notice2action/extraction/persistence`: entity와 repository
- `apps/web/src/components`: 피드, 저장 공지, 프로필, 인박스, 상세 패널
- `apps/web/src/lib`: API client, type, router, relevance, reminder, CSV helper

## 로컬 실행

요구 사항:

- Java 21
- Node.js 18+
- npm
- Docker
- Tesseract optional: 스크린샷 OCR을 실제로 확인할 때만 필요

macOS OCR 의존성 예시:

```bash
brew install tesseract tesseract-lang
```

Database:

```bash
docker compose up -d db
```

Backend:

```bash
cd apps/api
./gradlew bootRun
```

기본 주소는 `http://localhost:8080`입니다.

Frontend:

```bash
cd apps/web
npm install
npm run dev
```

기본 주소는 `http://localhost:5173`입니다. Vite dev server는 `/api` 요청을 `http://localhost:8080`으로 프록시합니다.

## 검증

Backend:

```bash
cd apps/api
./gradlew test
```

Frontend:

```bash
cd apps/web
npm test
npm run typecheck
npm run build
```

## 운영 메모

- 공지 피드 수집은 백엔드 기동 후 자동으로 한 번 실행될 수 있습니다.
- 일부 학교 공지는 본문이 비어 있거나 HTML 구조가 깨져 있어 skip warning이 찍힐 수 있습니다.
- 서버 상태 확인은 `/actuator/health` 기준으로 보는 편이 안전합니다.

## 라이선스

MIT
