# Notice2Action CUK - Project plan

## 1. Pitch
Notice2Action는 가톨릭대학교 성심교정 대표 공지를 자동 수집하고,
학생 프로필에 맞춰 **내게 중요한 공지 전체 피드**를 먼저 보여주는 서비스입니다.

핵심은 공지를 요약하는 것이 아니라,
- 이 공지가 나와 관련 있는지
- 지금 행동이 필요한지
- 왜 그렇게 판단했는지
를 빠르게 확인할 수 있게 만드는 것입니다.

## 2. Product story
1. 시스템이 성심교정 대표 공지를 자동 수집한다.
2. 로컬 프로필을 기준으로 관련도와 중요 이유를 계산한다.
3. 피드에서 중요한 공지를 먼저 본다.
4. 상세 화면에서 원문, 행동 블록, 근거를 함께 확인한다.
5. 저장하거나 숨겨서 다시 관리한다.

즉, 이 서비스의 메인 경험은 `입력 도구`가 아니라 `개인화 공지 피드`입니다.

## 3. Why this product matters
학생은 보통 공지를 못 읽는 것이 아니라, 공지 속에서 **내가 지금 신경 써야 할 것**을 빠르게 가려내지 못합니다.

이 제품은 그 문제를 다음 방식으로 줄입니다.
- 대표 공지를 자동 수집한다.
- 프로필 기반으로 우선순위를 계산한다.
- 정보성 공지와 행동 필요 공지를 구분한다.
- 상세 화면에서 근거를 같이 보여 신뢰를 만든다.

## 4. Current MVP screens
- Personalized feed
- Notice detail
- Saved notices
- Profile filters

## 5. Supporting capabilities
아래 기능은 아직 유효하지만 현재 메인 UX는 아닙니다.
- legacy manual extraction
- legacy action inbox
- PDF / screenshot / email extraction
- calendar export
- source history

이 기능들은 디버그, 비교, 회귀 테스트, 보조 워크플로 용도로 남아 있습니다.

## 6. Near-term product work
- relevance 품질과 reason 문구 개선
- detail evidence 품질 개선
- table-heavy body 축약
- 게시판 확장 여부 판단
- image-only 공지 처리 품질 보정

## 7. Engineering stance
- Deterministic first
- 근거 없는 추론 금지
- trust-building UI
- strict typing
- frontend/backend contract drift 최소화
