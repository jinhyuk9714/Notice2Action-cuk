# Feed Direct-Use Validation Log

## Purpose
- 1주 동안 실제 사용 기준으로 개인화 피드 정렬 품질과 수집 신뢰도를 검증한다.
- 새 기능 추가 없이, 반복되는 실패 패턴이 있는지 기록한다.
- 같은 유형의 실패가 2회 이상 반복될 때만 코드 보정으로 승격한다.

## References
- 사용자 시나리오 기준: [feed-manual-smoke-checklist.md](./feed-manual-smoke-checklist.md)
- 운영 상태 기준: [../ops/production-smoke-checklist.md](../ops/production-smoke-checklist.md)

## Validation Window
- 기간: 7일
- 기본 저장소: `Notice2Action-clean`
- 기본 화면: `#/feed`, `#/saved`, `#/profile`
- 일일 최소 확인 순서:
  1. `/actuator/health`
  2. `/api/v1/notices/feed?page=0&size=10`
  3. `#/feed` 상위 10개
  4. 필요 시 `#/saved`, `#/profile`

## Representative Profiles
1. `컴퓨터정보공학부 / 1학년 / 신입생 / 선호 게시판 [학사, 일반] / 키워드 [학생증, 학번, I-DESIGN]`
2. `컴퓨터정보공학부 / 3학년 / 재학생 / 선호 게시판 [학사, 취창업] / 키워드 [수강신청, 취업]`
3. `학과 미지정 / 4학년 / 졸업예정자 / 선호 게시판 [학사, 장학] / 키워드 [졸업, 예비 졸업사정, 트랙]`
4. `컴퓨터정보공학부 / 2학년 / 재학생 / 선호 게시판 [학사, 장학] / 키워드 [장학금, 국가장학금]`
5. `학과 미지정 / 3학년 / 재학생 / 선호 게시판 [학사] / 키워드 [부전공, 복수전공]`

## Daily Acceptance Checks
- 상위 10개 중 명백한 오탐이 1개 이하인가
- `다른 대상 공지`가 same-profile matched actionable notice보다 위에 오지 않는가
- keyword-only informational notice가 matched actionable notice를 앞지르지 않는가
- preferred board positive notice가 같은 tier의 비선호 게시판 notice보다 앞서거나 최소 동일 tier인가
- 피드가 비었을 때 사용자가 `공지가 없음`과 `동기화 실패/지연`을 구분할 수 있는가

## Escalation Rules
- 같은 유형의 ranking 실패가 2회 이상 반복되면 `NoticeFeedService` 미세 보정 대상으로 승격한다.
- `다른 대상 공지`가 반복적으로 matched actionable notice를 앞서면 ranking blocker로 기록한다.
- keyword-only informational notice가 반복적으로 상단에 뜨면 keyword weighting 조정 후보로 승격한다.
- sync 상태 문구가 실제 상태와 다르면 운영 가시성 버그로 기록한다.
- 이번 주간에는 게시판 확장, parser/actionability 변경, 새 기능 추가를 하지 않는다.

## Daily Log Template

```md
## Day 1

### System Check
- actuator:
  - status:
  - syncState:
  - noticeCount:
- feed API:
  - count:
  - availableBoards:
- sync strip matches reality:
  - [ ] yes
  - [ ] no
- notes:
  - ...

### Profile 1
- profile:
  - 컴퓨터정보공학부 / 1학년 / 신입생 / [학사, 일반] / [학생증, 학번, I-DESIGN]
- top 3 notices:
  1.
  2.
  3.
- findings:
  - [ ] 명백한 오탐 없음
  - [ ] 다른 대상 공지 과상승 없음
  - [ ] keyword reason 납득 가능
  - [ ] preferred board reason 납득 가능
  - [ ] 정보성 공지 과상승 없음
- notes:
  - ...

### Profile 2
- profile:
  - 컴퓨터정보공학부 / 3학년 / 재학생 / [학사, 취창업] / [수강신청, 취업]
- top 3 notices:
  1.
  2.
  3.
- findings:
  - [ ] 명백한 오탐 없음
  - [ ] 다른 대상 공지 과상승 없음
  - [ ] keyword reason 납득 가능
  - [ ] preferred board reason 납득 가능
  - [ ] 정보성 공지 과상승 없음
- notes:
  - ...

### Profile 3
- profile:
  - 학과 미지정 / 4학년 / 졸업예정자 / [학사, 장학] / [졸업, 예비 졸업사정, 트랙]
- top 3 notices:
  1.
  2.
  3.
- findings:
  - [ ] 명백한 오탐 없음
  - [ ] 다른 대상 공지 과상승 없음
  - [ ] keyword reason 납득 가능
  - [ ] preferred board reason 납득 가능
  - [ ] 정보성 공지 과상승 없음
- notes:
  - ...

### Profile 4
- profile:
  - 컴퓨터정보공학부 / 2학년 / 재학생 / [학사, 장학] / [장학금, 국가장학금]
- top 3 notices:
  1.
  2.
  3.
- findings:
  - [ ] 명백한 오탐 없음
  - [ ] 다른 대상 공지 과상승 없음
  - [ ] keyword reason 납득 가능
  - [ ] preferred board reason 납득 가능
  - [ ] 정보성 공지 과상승 없음
- notes:
  - ...

### Profile 5
- profile:
  - 학과 미지정 / 3학년 / 재학생 / [학사] / [부전공, 복수전공]
- top 3 notices:
  1.
  2.
  3.
- findings:
  - [ ] 명백한 오탐 없음
  - [ ] 다른 대상 공지 과상승 없음
  - [ ] keyword reason 납득 가능
  - [ ] preferred board reason 납득 가능
  - [ ] 정보성 공지 과상승 없음
- notes:
  - ...

### Repeated Patterns
- repeated ranking failure:
  - [ ] no
  - [ ] yes
- repeated sync mismatch:
  - [ ] no
  - [ ] yes
- candidate follow-up:
  - none / NoticeFeedService / sync-status wording / UI polish
```

## Weekly Decision
- 7일 기록 후 아래 둘 중 하나로 결론낸다.
  1. 반복 실패 패턴 없음 -> 다음 우선순위를 `배포/운영 검증`으로 넘긴다.
  2. 반복 실패 패턴 있음 -> `NoticeFeedService` 미세 보정 라운드로 승격한다.
