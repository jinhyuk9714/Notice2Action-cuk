# Notice2Action CUK - Spring + React starter

가톨릭대학교 성심교정 학생이 공지, 강의계획서, PDF, 이메일, 스크린샷을 넣으면  
**"내가 지금 해야 할 일"**만 추출하는 프로젝트용 스타터입니다.

이번 버전은 **React + TypeScript + Vite + Spring Boot + PostgreSQL** 기준으로 맞춰져 있습니다.
동아리 코드리뷰와 협업을 고려해서, 익숙한 구조와 보수적인 기본값에 집중했습니다.

## Included
- `apps/web`: React 19 + Vite + TypeScript strict
- `apps/api`: Spring Boot 3 + Java 21 + Validation + JPA + Flyway
- `docker-compose.yml`: local PostgreSQL
- `.claude`: Claude Code hooks / skills / agents / project memory
- `PROJECT_PLAN.md`: 면접용 제품/개발 계획
- `CLAUDE.md`: Claude가 항상 참고할 프로젝트 메모

## Product framing
이 프로젝트의 핵심은 "요약"이 아니라 **action extraction**입니다.

입력:
- 공지 URL
- 공지 텍스트
- PDF
- 스크린샷
- 이메일 내용

출력:
- 해야 할 일
- 마감일
- 준비물 / 제출물
- 대상 여부 / 자격 조건
- 관련 시스템 힌트(TRINITY, 사이버캠퍼스 등)
- 원문 근거 snippet

## Local run
### 1) DB
```bash
docker compose up -d db
```

### 2) API
이 스타터는 **Gradle build files**를 포함합니다.
오프라인 환경에서 생성되어 **Gradle wrapper 파일은 포함하지 않았습니다**.

아래 둘 중 하나로 실행하면 됩니다.

```bash
cd apps/api
gradle bootRun
```

또는 IntelliJ에서 Gradle project로 열고 wrapper를 생성한 뒤:

```bash
./gradlew bootRun
```

### 3) Web
```bash
cd apps/web
npm install
npm run dev
```

기본 프론트 개발 서버는 `http://localhost:5173`
API는 `http://localhost:8080`
이며 Vite proxy가 `/api`를 백엔드로 전달합니다.

## Suggested dev order
1. 텍스트 붙여넣기 → 액션 추출 API
2. 액션 리스트 화면
3. 액션 상세 화면 + evidence snippet
4. URL/PDF ingestion
5. 사용자 프로필 기반 relevance

## Collaboration notes
- 프론트: runtime-friendly strict TypeScript
- 백엔드: record DTO + validation + 서비스 계층 분리
- DB: Flyway migration first
- AI: deterministic extraction first, LLM fallback later
- 신뢰: every extracted field should be explainable

## Claude Code plugins to install
프로젝트 스코프로 설치 권장:
- `feature-dev@claude-plugins-official`
- `typescript-lsp@claude-plugins-official`
- `jdtls-lsp@claude-plugins-official`
- `security-guidance@claude-plugins-official`

`frontend-design@claude-plugins-official`는 기본적으로 꺼두었습니다.
MVP 단계에서는 화려함보다 정보 구조와 신뢰가 더 중요하기 때문입니다.
