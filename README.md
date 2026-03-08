# Notice2Action

> 가톨릭대학교 성심교정 학생을 위한 개인화 공지 피드 + 행동 추출 도구

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)

Notice2Action는 학교 공지를 많이 보여주는 서비스가 아니라, 내 프로필에 맞는 공지를 먼저 올리고 그 안에서 지금 해야 할 행동과 근거를 같이 보여주는 서비스입니다.

## 현재 제품 구조

현재 메인 제품 흐름은 개인화 공지 피드입니다.

```text
성심교정 공지 수집
    ↓
프로필 기반 관련도 계산
    ↓
개인화 공지 피드 (#/feed)
    ↓
공지 상세에서 중요 이유 + 행동 블록 + 근거 확인
    ↓
저장 / 숨김 / 다시 보기
```

수동 추출 기능은 없어지지 않았지만 지금은 보조 도구입니다.

```text
텍스트 / 공지 URL / PDF / 스크린샷 / 이메일
    ↓
규칙 기반 액션 추출
    ↓
legacy inbox / source history / calendar export
```

## 사용자가 보는 핵심 화면

### 1. 개인화 공지 피드
- 기본 진입점은 `#/feed`
- 학과, 학년, 신분, 관심 키워드, 선호 게시판을 기준으로 관련도 계산
- 공지 카드에서 중요 이유, 행동 필요 여부, 마감 힌트 확인
- 게시판 칩 필터 지원

### 2. 공지 상세
- 왜 중요한지
- 행동 블록
- 행동 블록별 evidence snippet
- 원문 본문과 첨부파일

### 3. 저장 / 숨김 / 복구
- 저장한 공지는 `#/saved`
- 숨긴 공지는 피드에서 제외되지만 다시 복구 가능
- 저장/숨김 상태는 브라우저 로컬 상태로 유지

### 4. 프로필 설정
- `#/profile`
- 학과, 학년, 재학 상태, 관심 키워드, 선호 게시판 설정
- 피드 정렬과 중요 이유 계산의 기준값

### 5. 수동 추출 / legacy inbox
- `#/extract`: 텍스트, URL, PDF, 스크린샷, 이메일 입력 기반 추출
- `#/inbox`: 저장된 액션 검색, 필터, 상태 토글, 수정, 캘린더 export
- `#/sources`: 추출/수집 소스 이력 조회

## 추출 원칙

- 요약보다 행동
- 근거 없는 추론 금지
- deterministic first
- evidence snippet을 함께 보여주는 trust-building UI

현재 추출기는 날짜, 시스템 힌트, 준비물, 자격 조건, 상태를 규칙 기반으로 뽑고, 결과를 evidence와 함께 저장합니다.

## 주요 기능

### 개인화 피드
- 성심교정 공지 자동 수집
- 프로필 기반 relevance 계산
- 공지별 importance reasons 노출
- action_required / informational 분류
- 공지 상세 + 행동 블록 표시

### 수동 추출
- 텍스트 붙여넣기
- 공지 URL 입력
- PDF 업로드
- 스크린샷 OCR
- 이메일 본문 추출

### legacy inbox
- 제목/요약 검색
- 카테고리, 날짜 범위, 상태 필터
- `pending` / `completed` 상태 토글
- 액션 상세 편집과 revert
- `.ics` calendar export
- CSV export
- 브라우저 Notification 기반 리마인더

## 기술 스택

| 계층 | 기술 |
|---|---|
| Frontend | React 19, Vite 7, TypeScript strict, Zod |
| Backend | Spring Boot 3.5, Java 21, JPA, Flyway |
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
└── README.md
```

조금 더 구체적으로는:

- `apps/api/src/main/java/com/cuk/notice2action/extraction/api`
  REST 컨트롤러
- `apps/api/src/main/java/com/cuk/notice2action/extraction/service`
  추출, 영속화, 캘린더, URL/PDF/OCR 처리
- `apps/api/src/main/java/com/cuk/notice2action/extraction/persistence`
  엔티티와 리포지토리
- `apps/web/src/components`
  피드, 저장 공지, 프로필, 인박스, 상세 패널
- `apps/web/src/lib`
  API 클라이언트, 타입, 라우터, relevance, reminder, csv 등

## API 개요

모든 API prefix는 `/api/v1` 입니다.

### Personalized notice feed

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/notices/feed` | 개인화 공지 피드 조회 |
| `GET` | `/notices/{id}` | 공지 상세 조회 |

주요 query param:
- `department`
- `year`
- `status`
- `board`
- `keyword`
- `preferredBoard`

### Manual extraction / legacy inbox

| Method | Path | 설명 |
|---|---|---|
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

### Health

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/health` | 간단 헬스 체크 |
| `GET` | `/actuator/health` | Spring Boot actuator health |

## 로컬 실행

### Prerequisites
- Java 21
- Node.js 18+
- npm
- Docker
- Tesseract
  스크린샷 OCR을 실제로 확인할 때만 필요

macOS 예시:

```bash
brew install tesseract tesseract-lang
```

### 1. Database

```bash
docker compose up -d db
```

### 2. Backend

```bash
cd apps/api
./gradlew bootRun
```

기본 주소: [http://localhost:8080](http://localhost:8080)

### 3. Frontend

```bash
cd apps/web
npm install
npm run dev
```

기본 주소: [http://localhost:5173](http://localhost:5173)

Vite dev server는 `/api` 요청을 `http://localhost:8080` 으로 프록시합니다.

### 주요 해시 라우트

- `#/feed`
- `#/saved`
- `#/profile`
- `#/extract`
- `#/inbox`
- `#/sources`

## 테스트

### Backend

```bash
cd apps/api
./gradlew test
```

### Frontend

```bash
cd apps/web
npm test
npm run typecheck
npm run build
```

## 개발 메모

- 공지 피드 수집은 백엔드 기동 후 자동으로 한 번 실행될 수 있습니다.
- 일부 학교 공지는 본문이 비어 있거나 HTML 구조가 깨져 있을 수 있어서, 수집 로그에 skip warning이 찍힐 수 있습니다.
- 이 경고는 개별 공지 품질 문제일 수 있으며 서버 전체 장애와는 다를 수 있습니다. 확인은 `/actuator/health` 기준으로 하는 편이 낫습니다.

## 현재 상태

- 기본 제품 진입점은 개인화 공지 피드
- 수동 추출과 legacy inbox는 유지 중
- 웹/API 계약은 `status`, `structuredEligibility`, `additionalDates` 기준으로 정렬됨

## License

MIT
