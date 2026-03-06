# Notice Feed Quality Audit (2026-03-06)

## Scope
- Board: Catholic University Songsim main notice board (`srCategoryId=21`)
- Evaluation date: `2026-03-06`
- Evaluation set: 30 recent notices (`action_required` 15 / `informational` 15)
- Profile mode: unset (`department/year/status/keywords = null/[]`)

## Acceptance Snapshot
- Actionability mismatches: `0/30`
- Future due mismatches: `0/4`
- Parser contamination on checked real HTML fixtures: `0`
- Informational notices showing due/action blocks in user-facing detail: `0` after gating fix

## Fixed In This Sprint
1. Real Catholic University HTML parser now survives image-only and table-heavy notices without falling back to `document.body().text()`.
2. Informational notices no longer surface due hints or action blocks just because the body contains dates.
3. Image-only notices with attached form files can now be classified as `action_required` when the title/body strongly imply a student task.
4. `YYYY.MM.DD HH:mm ~ M.DD HH:mm` ranges now resolve to the range end for due fallback.
5. Lecture/schedule ranges such as `강의일정 : 3월 16일 ~ 6월 5일` are no longer treated as deadlines.

## Remaining Risks
1. Multi-step actionable notices still produce generic block titles such as `[신청]`, `[수강]` in some cases.
2. Image-only actionable notices still have weak summaries without OCR or better title-to-action derivation.
3. Relative due labels such as `3일 이내` are acceptable for feed urgency, but not ideal as final user-facing wording.

## Expected vs Actual
| ID | Title | Expected | Actual | Expected Due | Actual Due | Note |
| --- | --- | --- | --- | --- | --- | --- |
| 269154 | 2026학년도 1학기 강의변경(폐강 등) 10차 안내(교양) | informational | informational | null | null | image-only; detail hides action blocks |
| 269125 | 2026학년도 1학기 강의변경(폐강 등) 9차 안내(교양) | informational | informational | null | null | image-only; detail hides action blocks |
| 269089 | [학사지원팀] 2026-1학기 개강미사 및 수업운영 안내 | informational | informational | null | null | detail hides action blocks |
| 268989 | [2~4학년] 2026학년도 1학기 부전공(2차) 신청/변경 안내 | action_required | action_required | null | null | image-only |
| 268838 | [학사지원팀] 2026-1학기 강의시간표 등 변경사항 안내(전공강좌) / 일별 업데이트(2026.03.05.) / 폐강 포함 | informational | informational | null | null | detail hides action blocks |
| 269093 | 2026학년도 1학기 강의변경 8차 안내(교양) | informational | informational | null | null | image-only; detail hides action blocks |
| 269011 | [학사지원팀] 2026-1학기 수강과목 취소 기간 안내 | action_required | action_required | 3. 25. (수) 17:00 | 3. 25. (수) 17:00 | future due visible |
| 269059 | 2026학년도 1학기 강의변경 7차 안내(교양) | informational | informational | null | null | image-only; detail hides action blocks |
| 269046 | 2026학년도 1학기 강의변경 6차 안내(교양) | informational | informational | null | null | image-only; detail hides action blocks |
| 269021 | 2026학년도 1학기 강의변경 5차 안내(교양) | informational | informational | null | null | image-only; detail hides action blocks |
| 269005 | 2026학년도 1학기 강의변경(폐강 등) 4차 안내(교양) | informational | informational | null | null | image-only; detail hides action blocks |
| 268935 | [학사지원팀] 2026학년도 1학기 성의교정 '의과학 오디세이' 교과목 수강 안내 | action_required | action_required | null | null | - |
| 268869 | [학사지원팀] 2026학년도 1학기 학기 중 취업학생 출결 사항 안내 | action_required | action_required | null | null | image-only |
| 268851 | [학사지원팀] 2026학년도 1학기 공결 신청 변경 안내 | action_required | action_required | 3일 이내 | 3일 이내 | future due visible |
| 268888 | 2026학년도 1학기 강의변경 3차 안내(교양) | informational | informational | null | null | image-only; detail hides action blocks |
| 268871 | 2026학년도 1학기 강의변경 2차 안내(교양) | informational | informational | null | null | image-only; detail hides action blocks |
| 268768 | 2026학년도 신·편입생(등록완료자) 학번조회 안내 | action_required | action_required | null | null | - |
| 268767 | 2026학년도 신입생 입학미사 안내 | informational | informational | null | null | detail hides action blocks |
| 268680 | [학사지원팀] 「취소-시간차 수강신청제｣ 재안내 (여석생성시각 11시 ~ 11시 10분 사이) | action_required | action_required | null | null | image-only |
| 268679 | [학사지원팀] 2026학년도 학점이월제도 안내 | informational | informational | null | null | detail hides action blocks |
| 268630 | 2026년 가을(2025학년도 후기) 조기졸업 신청 안내 | action_required | action_required | null | null | image-only |
| 268629 | [학사지원팀] 2025학년도 후기(2026년 8월) 졸업대상자 예비 졸업사정 일정 안내 | informational | informational | null | null | image-only; detail hides action blocks |
| 268584 | [학사지원팀] 2026학년도 편입생 학점인정 및 수강신청 안내 | action_required | action_required | null | null | - |
| 268547 | 2026학년도 신입생 수강신청 안내 | action_required | action_required | ~ 3/9 | ~ 3/9 | future due visible |
| 268396 | [학사지원팀] 2026년 2월 졸업대상자 융복합트랙 이수여부 확인 기간 알림 | action_required | action_required | null | null | image-only |
| 268391 | [학사지원팀] 2026-1학기 제한인원 상향 요청과목 수요조사 안내 | action_required | action_required | null | null | - |
| 268255 | [학사지원팀] 2026-1학기 강의시간표 등 변경사항 안내(전공강좌) / 일별 업데이트(2026.02.25.) | informational | informational | null | null | detail hides action blocks |
| 268242 | [학부대학운영팀] 2026학년도 1학기 군 복무 학점(군 e-러닝) 수강신청 안내 | action_required | action_required | null | null | - |
| 268226 | [학부대학] 2026학년도 1학기 <I-DESIGN> 수강신청 관련 안내 | action_required | action_required | ~ 3/9 | ~ 3/9 | future due visible |
| 268212 | [학부대학] 2026학년도 1학기 「Self-making Project Portfolio」 교과목 참여 학생 모집 안내 | action_required | action_required | null | null | - |
