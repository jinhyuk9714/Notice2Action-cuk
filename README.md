# Notice2Action

> 가톨릭대학교 성심교정 학생을 위한 **공지 → 행동 추출** 웹 서비스

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)

학교 공지사항을 요약하는 것이 아니라, **"지금 내가 해야 할 일(Action)"** 만 구조화해서 보여줍니다.

---

## 해결하는 문제

학생이 공지를 읽더라도 실제로 놓치는 것들이 있습니다:

| 놓치는 것 | 예시 |
|-----------|------|
| **무엇**을 해야 하는지 | 신청, 제출, 등록, 참석 |
| **언제까지** 해야 하는지 | 마감일시 |
| **무엇을 준비**해야 하는지 | 성적증명서, 자기소개서 등 |
| **내가 대상**인지 | 2학년 이상, 졸업예정자 등 |
| **어디서** 해야 하는지 | TRINITY, 장학포털, LMS 등 |

```
[입력]                        [추출 엔진]                        [출력]

텍스트 붙여넣기  ──┐                                          ┌── 액션 카드 목록
공지 URL         ──┤                                          ├── 마감일 · 시스템 · 준비물
PDF 업로드       ──┼──→  Heuristic 추출 (규칙 기반)  ────────→├── 자격 요건
스크린샷 OCR     ──┤     + SHA-256 중복 감지                  ├── 근거 snippet + confidence
이메일 본문      ──┘     + confidence scoring                 ├── DB 저장 → 인박스
                                                              └── 캘린더(.ics) · CSV 내보내기
```

---

## 추출 데모

**입력** — 장학금 공지:
```
[2026학년도 1학기 교내장학금 신청 안내]

1. 신청 대상: 2학년 이상 재학생 (평균평점 3.5 이상)
2. 신청 기간: 2026.3.1 ~ 2026.3.15 18:00
3. 신청 방법: 장학포털에서 온라인 신청
4. 제출 서류: 성적증명서, 자기소개서, 통장사본
```

**추출 결과**:

| 필드 | 값 |
|------|-----|
| 액션 요약 | `[신청] 2026-03-15 18:00까지 장학포털에서 준비물: 성적증명서, 자기소개서, 통장사본.` |
| 마감일 | `2026-03-15T18:00:00+09:00` |
| 시스템 힌트 | 장학포털 |
| 준비물 | 성적증명서, 자기소개서, 통장사본 |
| 자격 요건 | 2학년 이상 재학생 (평균평점 3.5 이상) |
| 근거 | 각 필드별 원문 snippet + confidence 점수 |

---

## 주요 기능

### 입력 (5종)

| 소스 | 방식 | 기술 |
|------|------|------|
| 텍스트 | 직접 붙여넣기 | — |
| URL | 공지 URL 입력 → HTML 자동 추출 | Jsoup |
| PDF | 파일 업로드 (최대 10MB) | Apache PDFBox |
| 스크린샷 | 이미지 업로드 → OCR | Tesseract (Tess4J) |
| 이메일 | 본문 + 제목 + 발신자 입력 | — |

### 추출 엔진 (Heuristic, 규칙 기반)

| 항목 | 규칙 수 | 예시 |
|------|---------|------|
| 날짜 패턴 | 7개 | 한글 전체(`2026년 3월 12일 18시`), ISO(`2026.03.12`), 연도 생략, 오전/오후 등 |
| 시스템 힌트 | 17개 | TRINITY, 장학포털, LMS, 종정넷, 사이버캠퍼스 등 |
| 준비물 키워드 | 22개 | 성적증명서, 자기소개서, 통장사본, 여권사본 등 |
| 액션 동사 | 15개 | 신청, 제출, 완료, 등록, 참석, 납부 등 |
| 자격 시그널 | 14개 | 대상, 졸업예정자, 재학생, 학년, 전공 등 |
| 복수 액션 | 최대 5개 | 동사 앵커링 기반 문장 분할 |

### 신뢰 구축
- 모든 추출 필드에 **원문 근거 snippet** + **confidence 점수** (0.0~1.0) 제공
- 마감 키워드 근접 시 confidence 부스팅
- SHA-256 content hash로 **중복 소스 감지**

### 인박스 관리
- **검색** — 제목 + 요약 전문 검색
- **필터** — 소스 카테고리(공지/이메일/PDF/스크린샷), 마감일 범위
- **정렬** — 최신순 / 마감 임박순 (NULLS LAST)
- **편집/삭제** — 액션 필드 수정 (PATCH) + 삭제
- **페이지네이션** — 무한 스크롤 방식

### 부가 기능
- **다크모드** — Light / Dark / System 전환 (localStorage 저장)
- **캘린더 내보내기** — 마감일 있는 액션을 `.ics` 파일로 다운로드
- **CSV 내보내기** — 전체 액션 목록을 CSV로 다운로드
- **리마인더** — D-7, D-3, D-1, D-Day 알림 (Notification API)
- **프로필 설정** — 학과/학년/재학상태 기반 관련도 필터링
- **소스 히스토리** — 원문 소스별 추출 이력 조회
- **URL 라우팅** — 해시 기반 (`#/inbox/{id}`, `#/sources/{id}`)

---

## 기술 스택

| 계층 | 기술 |
|------|------|
| Frontend | React 19 + Vite 7 + TypeScript 5.9 (strict) |
| Backend | Spring Boot 3.5 + Java 21 + JPA + Flyway |
| Database | PostgreSQL 16 (Docker) / H2 (테스트) |
| PDF 추출 | Apache PDFBox 3.0 |
| OCR | Tesseract (Tess4J 5.13) |
| HTML 파싱 | Jsoup 1.18 |
| 빌드 | Gradle 9.3 (API) + npm (Web) |
| 테스트 | JUnit 5 + AssertJ (API) · Vitest + Testing Library (Web) |

---

## 프로젝트 구조

```
Notice2Action/
├── apps/
│   ├── api/                                    # Spring Boot 백엔드
│   │   └── src/main/java/.../extraction/
│   │       ├── api/                            #   REST 컨트롤러
│   │       │   └── dto/                        #   Request/Response DTO (records)
│   │       ├── service/                        #   추출 엔진, 영속화, URL·PDF·OCR·iCal
│   │       ├── domain/                         #   SourceCategory enum
│   │       └── persistence/                    #   JPA 엔티티 + 리포지토리
│   │
│   └── web/                                    # React 프론트엔드
│       └── src/
│           ├── components/                     #   12개 컴포넌트
│           │   ├── InboxView.tsx               #     검색/필터/정렬/인박스
│           │   ├── ActionCard.tsx              #     액션 카드
│           │   ├── ActionDetailPanel.tsx        #     상세 + 근거
│           │   ├── SourceIngestionForm.tsx      #     입력 폼 (5종)
│           │   ├── SourceListView.tsx           #     소스 히스토리
│           │   ├── ProfileSettings.tsx          #     프로필 설정
│           │   ├── ThemeToggle.tsx              #     다크모드 토글
│           │   ├── FileDropZone.tsx             #     드래그앤드롭 업로드
│           │   ├── SkeletonCard.tsx             #     로딩 스켈레톤
│           │   └── ErrorBoundary.tsx            #     에러 바운더리
│           └── lib/                            #   유틸리티
│               ├── api.ts                      #     fetch 래퍼 (타입 가드 검증)
│               ├── types.ts                    #     타입 정의 + runtime 타입 가드
│               ├── router.ts                   #     해시 라우팅
│               ├── theme.ts                    #     테마 관리
│               ├── profile.ts                  #     프로필 저장/로드
│               ├── reminder.ts                 #     리마인더 관리
│               ├── relevance.ts                #     관련도 계산
│               ├── csv.ts                      #     CSV 생성
│               └── dateRange.ts                #     날짜 범위 파싱
│
├── docker-compose.yml                          # PostgreSQL 16
├── CLAUDE.md                                   # Claude Code 프로젝트 메모리
└── PROJECT_PLAN.md                             # 제품/개발 계획
```

---

## API 엔드포인트

모든 경로의 prefix: `/api/v1`

### 추출

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/extractions/actions` | 텍스트/URL → 액션 추출 + DB 저장 |
| `POST` | `/extractions/pdf` | PDF 파일 업로드 → 액션 추출 |
| `POST` | `/extractions/email` | 이메일 본문 → 액션 추출 |
| `POST` | `/extractions/screenshot` | 스크린샷 OCR → 액션 추출 |

### 액션

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/actions` | 목록 조회 (`sort`, `q`, `category`, `dueDateFrom`, `dueDateTo`, `page`, `size`) |
| `GET` | `/actions/{id}` | 상세 조회 (근거 snippet 포함) |
| `PATCH` | `/actions/{id}` | 액션 필드 수정 |
| `DELETE` | `/actions/{id}` | 액션 삭제 |
| `GET` | `/actions/calendar.ics` | 전체 액션 캘린더 내보내기 |
| `GET` | `/actions/{id}/calendar.ics` | 단일 액션 캘린더 내보내기 |

### 소스

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/sources` | 소스 목록 조회 (페이지네이션) |
| `GET` | `/sources/{id}` | 소스 상세 + 추출된 액션 목록 |

### 기타

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/health` | 헬스 체크 |

---

## 로컬 실행

### Prerequisites

- **Java 21** (Gradle toolchain이 자동 다운로드)
- **Node.js 18+** / npm
- **Docker** (PostgreSQL용)
- **Tesseract** (스크린샷 OCR 사용 시)
  - macOS: `brew install tesseract tesseract-lang`
  - 기본 tessdata 경로: `/opt/homebrew/share/tessdata`

### 1. Database

```bash
docker compose up -d db
```

### 2. Backend

```bash
cd apps/api
./gradlew bootRun
```

API 서버: `http://localhost:8080`

### 3. Frontend

```bash
cd apps/web
npm install
npm run dev
```

개발 서버: `http://localhost:5173` (Vite proxy → `/api` → `localhost:8080`)

---

## 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SERVER_PORT` | `8080` | API 서버 포트 |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/campus_action_inbox` | DB 접속 URL |
| `DATABASE_USERNAME` | `campus` | DB 사용자 |
| `DATABASE_PASSWORD` | `campus` | DB 비밀번호 |
| `TESSDATA_PATH` | `/opt/homebrew/share/tessdata` | Tesseract 언어 데이터 경로 |

---

## 테스트

```bash
# Backend — 15 테스트 클래스, ~179 테스트
cd apps/api && ./gradlew test

# Frontend — 17 테스트 파일, ~192 테스트
cd apps/web && npx vitest run

# Frontend — 타입 체크
cd apps/web && npm run typecheck
```

### 백엔드 테스트 범위
추출 엔진, confidence 점수, 날짜 파싱, 중복 감지, 검색/필터, 액션 수정,
PDF 추출, 스크린샷 OCR, URL 페칭, 이메일 추출, 캘린더 생성, 소스 히스토리,
에러 핸들링, 프로필 정렬, 쿼리 파라미터 검증

### 프론트엔드 테스트 범위
컴포넌트 렌더링, API 클라이언트, runtime 타입 가드, CSV 생성, 날짜 범위,
D-Day 계산, 라벨 포맷, 프로필 관리, 리마인더, 해시 라우팅, 테마 전환

---

## 설계 원칙

1. **요약보다 행동** — 원문 소개가 아니라 "해야 할 일"이 우선
2. **근거 없는 추론 금지** — 날짜·대상·제출물은 원문에 근거가 있어야 함
3. **Deterministic first** — 규칙 기반 우선, LLM은 불확실할 때만 fallback
4. **Trust-building UI** — 추출 결과와 근거 snippet을 함께 표시

---

## 로드맵

| 단계 | 기능 | 상태 |
|------|------|------|
| **V1** | 텍스트/URL 입력, 액션 추출, 인박스, 상세+근거 | ✅ |
| **V1.5** | 추출 개선 (날짜 7패턴, 시스템 17개, 준비물 22개, 복수 액션) | ✅ |
| **V2** | PDF 업로드, 스크린샷 OCR, 프로필 필터링, 마감 정렬 | ✅ |
| **V3** | 키보드 접근성, 신뢰도 점수 시각화, 에러 복구 UI | ✅ |
| **V4** | 이메일 추출, 캘린더 export, 소스 히스토리, 안정성 개선 | ✅ |
| **V5** | 다크모드, URL 라우팅, 날짜 필터, CSV 내보내기, 리마인더 | ✅ |
| V-next | LLM fallback, 팀 모드, 캘린더 연동, 알림 push | 예정 |

---

## License

MIT
