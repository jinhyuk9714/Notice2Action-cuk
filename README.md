# Notice2Action

> 가톨릭대학교 성심교정 학생을 위한 개인화 공지 피드 웹 서비스

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)

Notice2Action는 성심교정 대표 공지를 자동 수집하고, 로컬 프로필을 바탕으로 **내게 중요한 공지**를 먼저 보여주는 서비스입니다. 핵심은 공지를 많이 보여주는 것이 아니라, 놓치면 안 되는 공지를 빠르게 걸러 주고 상세 화면에서 **왜 중요한지**, **지금 무엇을 해야 하는지**, **근거가 무엇인지**를 함께 보여주는 것입니다.

---

## 현재 제품이 해결하는 문제

학생은 학교 공지를 읽을 수는 있어도, 실제로는 아래를 자주 놓칩니다.

| 놓치기 쉬운 것 | 실제 질문 |
|---|---|
| 내가 봐야 하는 공지인지 | 이 공지가 내 학과, 학년, 재학 상태와 관련 있나? |
| 지금 행동이 필요한지 | 그냥 안내인지, 실제 신청이나 제출이 필요한지? |
| 언제까지 해야 하는지 | 마감이 있는지, 얼마나 급한지? |
| 어디서 해야 하는지 | TRINITY, 학사포털, 공유대학, 외부 사이트 중 어디서 처리하나? |
| 왜 이렇게 판단했는지 | 시스템이 임의로 추론한 건 아닌가? |

현재 메인 흐름은 아래와 같습니다.

```text
성심교정 대표 공지 자동 수집
        ↓
프로필 기반 관련도 계산
        ↓
개인화 공지 피드 (#/feed)
        ↓
상세 화면에서 중요 이유 + 행동 블록 + 행동 블록별 근거 확인
        ↓
저장 / 숨김 / 나중에 다시 확인
```

---

## 메인 기능

### 1. 개인화 공지 피드
- 성심교정 대표 공지를 자동 수집합니다.
- 로컬 프로필(학과, 학년, 재학 상태, 관심 키워드, 선호 게시판)을 기준으로 관련도 점수를 계산합니다.
- 피드 카드에는 제목, 게시일, 중요 이유, 행동 필요 여부, 마감 힌트를 표시합니다.
- 게시판 칩은 수동 필터이고, 선호 게시판은 기본 피드 우선순위에만 반영됩니다.
- 기본 진입점은 `#/feed` 입니다.

### 2. 공지 상세 화면
- 상세 화면은 아래 순서로 정보를 보여줍니다.
  - 왜 중요한지
  - 행동 블록
  - 행동 블록별 근거
  - 원문 본문
  - 첨부파일
- 행동 블록은 deterministic 규칙 기반으로 생성합니다.
- 정보성 공지는 행동 블록 대신 `행동 없음`으로 표시합니다.
- 긴 본문은 기본적으로 접어서 보여주고, 표-heavy 구간은 표마다 따로 펼쳐 볼 수 있습니다.

### 3. 저장 / 숨김
- 공지는 로컬 상태로 저장하거나 숨길 수 있습니다.
- 저장한 공지는 `#/saved` 에서 다시 볼 수 있습니다.
- 숨긴 공지는 피드에서 제외되지만 복구할 수 있습니다.

### 4. 프로필 기반 필터링
- 프로필은 로컬 저장소에 유지됩니다.
- 학과, 학년, 재학 상태, 관심 키워드, 선호 게시판이 중요 이유 계산에 반영됩니다.
- 메인 UI에서는 프로필 설정이 부가 기능이 아니라 피드 품질의 핵심 입력입니다.

---

## 메인 API

모든 경로의 prefix는 `/api/v1` 입니다.

### Personalized notice feed

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/notices/feed` | 개인화 공지 피드 조회 |
| `GET` | `/notices/{id}` | 공지 상세 조회 |

피드 응답은 다음 정보를 포함합니다.
- `title`
- `publishedAt`
- `boardLabel`
- `importanceReasons[]`
- `actionability`
- `dueHint`
- `relevanceScore`

상세 응답은 다음 정보를 추가로 포함합니다.
- `body`
- `attachments[]`
- `actionBlocks[]`
- `actionBlocks[]` 내부의 `evidence[]`

### Secondary / legacy APIs

아래 경로는 계속 유지되지만 현재 메인 제품 진입점은 아닙니다.

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/extractions/actions` | 텍스트/URL 기반 수동 추출 |
| `POST` | `/extractions/pdf` | PDF 수동 추출 |
| `POST` | `/extractions/email` | 이메일 수동 추출 |
| `POST` | `/extractions/screenshot` | 스크린샷 OCR 추출 |
| `GET` | `/actions` | legacy action inbox |
| `GET` | `/actions/{id}` | legacy action detail |
| `GET` | `/actions/calendar.ics` | legacy calendar export |
| `GET` | `/sources` | 수집/추출 소스 조회 |
| `GET` | `/sources/{id}` | 소스 상세 조회 |
| `GET` | `/health` | 헬스 체크 |

---

## 수동 추출 기능의 현재 위치

텍스트, URL, PDF, 스크린샷, 이메일 입력 기반 추출 기능은 삭제하지 않았습니다. 다만 지금의 메인 제품 설명에서는 **보조 기능**입니다.

이 기능들은 아래처럼 쓰는 것이 맞습니다.
- 디버그용 추출 확인
- 품질 비교
- 레거시 inbox 흐름 유지
- 추출 엔진 회귀 테스트

즉, 공개 문서의 기본 사용자 시나리오는 `manual extraction` 이 아니라 `personalized notice feed` 입니다.

---

## 기술 스택

| 계층 | 기술 |
|---|---|
| Frontend | React 19 + Vite 7 + TypeScript 5.9 |
| Backend | Spring Boot 3.5 + Java 21 + JPA + Flyway |
| Database | PostgreSQL 16 |
| Parsing | Jsoup, 규칙 기반 extractor |
| OCR / PDF | Tesseract, Apache PDFBox |
| Test | JUnit 5, AssertJ, Vitest, Testing Library |

---

## 로컬 실행

### Prerequisites
- Java 21
- Node.js 18+
- npm
- Docker
- Tesseract (스크린샷 OCR 기능을 실제로 사용할 때만 필요)

### 1. Database

```bash
docker compose up -d db
```

### 2. Backend

```bash
cd apps/api
./gradlew bootRun
```

기본 API 서버: `http://localhost:8080`

### 3. Frontend

```bash
cd apps/web
npm install
npm run dev
```

개발 서버: `http://localhost:5173`

현재 메인 진입점:
- `http://localhost:5173/#/feed`
- `http://localhost:5173/#/saved`
- `http://localhost:5173/#/profile`

보조 경로:
- `http://localhost:5173/#/extract`
- `http://localhost:5173/#/inbox`
- `http://localhost:5173/#/sources`

---

## 환경 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SERVER_PORT` | `8080` | API 서버 포트 |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/campus_action_inbox` | DB 접속 URL |
| `DATABASE_USERNAME` | `campus` | DB 사용자 |
| `DATABASE_PASSWORD` | `campus` | DB 비밀번호 |
| `TESSDATA_PATH` | `/opt/homebrew/share/tessdata` | Tesseract 데이터 경로 |

---

## 테스트

### Backend

```bash
cd apps/api
./gradlew test
```

### Frontend

```bash
cd apps/web
npm run test
npm run typecheck
npm run build
```

---

## 현재 상태와 후속 과제

현재 `main` 기준으로 제품의 중심은 이미 개인화 공지 피드입니다. 남아 있는 후속 과제는 아래처럼 품질 보정 성격이 강합니다.

- 게시판 확장 여부 판단
- 실제 공지 샘플 기준 relevance/priority 미세 조정

레거시 수동 추출 기능은 계속 유지하되, 메인 제품 포지션은 바꾸지 않습니다.
