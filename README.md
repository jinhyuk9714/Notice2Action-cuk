# Notice2Action

> 가톨릭대학교 성심교정 학생을 위한 **공지 → 행동 추출** 웹 서비스

학교 공지사항을 요약하는 것이 아니라, **"지금 내가 해야 할 일(Action)"** 만 구조화해서 보여줍니다.

## 해결하는 문제

학생이 공지를 읽더라도 실제로 놓치는 것들이 있습니다:

- **무엇**을 해야 하는지 (신청, 제출, 등록, 참석)
- **언제까지** 해야 하는지 (마감일시)
- **무엇을 준비**해야 하는지 (성적증명서, 자기소개서 등)
- **내가 대상**인지 (2학년 이상, 졸업예정자 등)
- **어디서** 해야 하는지 (TRINITY, 장학포털, LMS 등)

```
[입력]                     [추출 엔진]                    [출력]

텍스트 붙여넣기  ──┐                                  ┌── 액션 카드 목록
                   ├──→  Heuristic 추출 (규칙 기반)  ──├── 마감일 · 시스템 · 준비물
공지 URL 붙여넣기 ─┘                                  ├── 자격 요건
                                                      ├── 근거 snippet (신뢰 구축)
                                                      └── DB 저장 → 인박스 조회
```

## 추출 예시

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
| 자격 요건 | 신청 대상: 2학년 이상 재학생 (평균평점 3.5 이상) |
| 근거 | 각 필드별 원문 snippet + confidence 점수 |

## 주요 기능

### 입력
- **텍스트 붙여넣기** — 공지 원문을 직접 입력
- **URL 붙여넣기** — 공지 URL에서 HTML 자동 추출

### 추출 엔진 (Heuristic, 규칙 기반)

| 항목 | 스펙 |
|------|------|
| 날짜 패턴 | 7개 — 한글 전체(`2026년 3월 12일 18시`), ISO(`2026.03.12`), 연도 생략, 오전/오후, 범위 끝 등 |
| 시스템 힌트 | 17개 — TRINITY, 장학포털, LMS, 종정넷, 사이버캠퍼스 등 |
| 준비물 키워드 | 22개 — 성적증명서, 자기소개서, 통장사본, 여권사본 등 |
| 액션 동사 | 15개 — 신청, 제출, 완료, 등록, 참석, 납부 등 |
| 자격 시그널 | 14개 — 대상, 졸업예정자, 재학생, 학년, 전공 등 |
| 복수 액션 | 최대 5개 — 동사 앵커링 기반 문장 분할 |

### 신뢰 구축
- 모든 추출 필드에 **원문 근거 snippet** + **confidence 점수** 제공
- 마감 키워드 근접 시 confidence 부스팅

### 저장 및 조회
- PostgreSQL DB에 액션 영속화
- 인박스 목록 조회 + 상세 조회 (근거 포함)

## 기술 스택

| 계층 | 기술 |
|------|------|
| Frontend | React 19 + Vite + TypeScript strict |
| Backend | Spring Boot 3 + Java 21 + JPA + Flyway |
| Database | PostgreSQL (Docker) / H2 (테스트) |
| 빌드 | Gradle (API) + npm (Web) |

## 프로젝트 구조

```
Notice2Action/
├── apps/
│   ├── api/                              # Spring Boot 백엔드
│   │   └── src/main/java/.../extraction/
│   │       ├── api/                      #   REST 컨트롤러 + DTO
│   │       ├── service/                  #   추출 엔진 + 영속화 + URL 페처
│   │       ├── domain/                   #   SourceCategory enum
│   │       └── persistence/              #   JPA 엔티티 + 리포지토리
│   │
│   └── web/                              # React 프론트엔드
│       └── src/
│           ├── components/               #   SourceIngestionForm, InboxView,
│           │                             #   ActionCard, ActionDetailPanel
│           └── lib/                      #   API 클라이언트 + 타입 정의
│
├── docker-compose.yml                    # PostgreSQL
├── CLAUDE.md                             # Claude Code 프로젝트 메모
└── PROJECT_PLAN.md                       # 제품/개발 계획
```

## API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/v1/health` | 헬스 체크 |
| `POST` | `/api/v1/extractions/actions` | 텍스트/URL → 액션 추출 + DB 저장 |
| `GET` | `/api/v1/actions` | 저장된 액션 목록 조회 |
| `GET` | `/api/v1/actions/{id}` | 액션 상세 조회 (근거 포함) |

## 로컬 실행

### 1. PostgreSQL
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
개발 서버: `http://localhost:5173` (Vite proxy가 `/api`를 백엔드로 전달)

## 테스트

```bash
# Backend — 41개 테스트 (추출 29 + 영속화 4 + URL 2 + 기타)
cd apps/api && ./gradlew test

# Frontend — 타입 체크 + 빌드
cd apps/web && npm run typecheck && npm run build
```

## 로드맵

| 단계 | 기능 | 상태 |
|------|------|------|
| **V1** | 텍스트/URL 입력, 액션 추출, 인박스, 상세+근거 | 완료 |
| **V1 추출 개선** | 날짜 7패턴, 시스템 17개, 준비물 22개, 복수 액션 | 완료 |
| V1.5 | PDF 업로드, 스크린샷 OCR, 프로필 필터링, 마감 정렬+리마인더 | 예정 |
| V2 | 이메일 포워딩, 캘린더 export, 히스토리, 팀 모드 | 예정 |

## 설계 원칙

1. **요약보다 행동** — 원문 소개가 아니라 "해야 할 일"이 우선
2. **근거 없는 추론 금지** — 날짜·대상·제출물은 원문에 근거가 있어야 함
3. **Deterministic first** — 규칙 기반 우선, LLM은 불확실할 때만 fallback
4. **Trust-building UI** — 추출 결과와 근거 snippet을 함께 표시
