# Notice2Action CUK - Project plan

## 1. Pitch
성심교정 학생이 공지/PDF/이메일/스크린샷을 넣으면,
중요한 내용을 읽기 쉽게 요약하는 것이 아니라
**실제로 해야 할 일(action)**만 추출해 주는 서비스.

## 2. Why it is interview-friendly
- 문제를 10초 안에 설명할 수 있음
- 학생 실사용성이 높음
- AI가 왜 필요한지 설명이 쉬움
- 데모가 강함
- React + Spring Boot로 협업 친화적

## 3. Demo story
1. 학교 공지 텍스트 또는 URL 입력
2. 백엔드가 action / due date / system hint / evidence 추출
3. 프론트에서 action inbox에 정리
4. detail card에서 evidence snippet 보여주기
5. "이 서비스는 요약기가 아니라 action inbox"라고 마무리

## 4. MVP screens
- Source ingestion
- Action inbox
- Action detail

## 5. 4-week build plan
### Week 1
- repo setup
- React/Vite strict setup
- Spring Boot API scaffold
- extraction DTO and service
- local postgres and migration

### Week 2
- text ingestion form
- first action extraction heuristic
- inbox list UI
- action detail card

### Week 3
- URL/PDF input
- evidence snippet rendering
- better due date parsing
- system hint extraction

### Week 4
- fallback LLM integration
- screenshot input
- polish demo data
- interview script and README cleanup

## 6. Post-MVP ideas
- user profile filtering
- reminders
- campus system deeplink hints
- email forwarding
- club/team project mode
