# Production Readiness

## 필수 환경
- Java 21
- PostgreSQL 16 이상
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `SERVER_PORT`
- OCR 기능이 필요할 때만 `TESSDATA_PATH`

## 배포 전 확인
1. DB 연결이 정상인지 확인
2. Flyway migration이 최신까지 적용됐는지 확인
3. `/actuator/health`가 열려 있는지 확인
4. `/api/v1/notices/feed` 응답에 `syncStatus`가 포함되는지 확인

## 수집 상태 판단 기준
- `healthy`
  - 마지막 동기화가 최근 성공 상태
  - actuator health: `UP`
- `stale`
  - 마지막 성공은 있지만 너무 오래된 상태
  - actuator health: `OUT_OF_SERVICE`
- `failed`
  - 최근 전체 배치가 실패한 상태
  - actuator health: `DOWN`
- `bootstrapping`
  - 첫 기동 직후 아직 성공 이력이 없는 상태
  - actuator health: `UNKNOWN`

## 운영 해석 기준
- `failed` + `noticeCount = 0`
  - 피드가 비어 보일 수 있음
  - 데이터 부재가 아니라 수집 실패일 가능성이 큼
- `failed` + `noticeCount > 0`
  - 이전 수집 데이터는 남아 있음
  - 최신화만 실패한 상태
- `stale`
  - 스케줄러 또는 외부 사이트 응답 지연 가능성 우선 확인

## 참고
- malformed detail 공지 1건을 건너뛰는 partial skip은 전체 배치 실패로 보지 않습니다.
- 운영자는 피드 상단 상태 표시와 `/actuator/health`를 같이 봐야 합니다.
