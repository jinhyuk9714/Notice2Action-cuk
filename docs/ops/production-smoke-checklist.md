# Production Smoke Checklist

## 서버 기동 직후
1. `/actuator/health` 확인
2. `/api/v1/notices/feed?page=0&size=5` 확인
3. 프론트 `#/feed` 열기
4. 보조 확인으로 `#/saved`, `#/profile` 열기

## 상태별 확인 포인트
### 1. bootstrapping
- 피드 상단: `공지 동기화 중...`
- `count === 0`이어도 `중요 공지가 없습니다.`가 보이면 안 됨
- actuator health: `UNKNOWN`

### 2. healthy
- 피드 상단: `마지막 동기화 ...`
- notices가 정상 노출
- actuator health: `UP`

### 3. stale
- 피드 상단: `동기화 지연 · 마지막 동기화 ...`
- 기존 notices는 보여도 됨
- actuator health: `OUT_OF_SERVICE`

### 4. failed
- `count === 0`
  - 피드 상단: `공지 동기화 실패 · 잠시 후 다시 시도해 주세요`
  - generic empty state 대신 실패 문구가 보여야 함
- `count > 0`
  - 피드 상단: `동기화 실패 · 마지막 동기화 ...`
- actuator health: `DOWN`

## 정상/실패 구분 요약
- 피드가 비었을 때 먼저 `/actuator/health`와 `syncStatus.state`를 같이 확인합니다.
- empty + `healthy`면 실제로 공지가 없는 상태일 수 있습니다.
- empty + `bootstrapping/failed`면 수집 상태 문제로 판단합니다.
