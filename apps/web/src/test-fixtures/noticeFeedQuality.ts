import type { PersonalizedNoticeDetail, PersonalizedNoticeSummary } from '../lib/types';

export const QUALITY_ACTION_NOTICE: PersonalizedNoticeSummary = {
  id: '269011',
  title: '[학사지원팀] 2026-1학기 수강과목 취소 기간 안내',
  publishedAt: '2026-03-03T00:00:00+09:00',
  sourceUrl: 'https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=269011&srCategoryId=21',
  importanceReasons: ['행동 필요 공지', '이번 주 공지'],
  actionability: 'action_required',
  dueHint: { dueAtIso: '2026-03-25T17:00:00+09:00', label: '3. 25. (수) 17:00' },
  relevanceScore: 17,
};

export const QUALITY_INFORMATIONAL_NOTICE: PersonalizedNoticeSummary = {
  id: '268838',
  title: '[학사지원팀] 2026-1학기 강의시간표 등 변경사항 안내(전공강좌) / 일별 업데이트(2026.03.05.) / 폐강 포함',
  publishedAt: '2026-02-25T00:00:00+09:00',
  sourceUrl: 'https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=268838&srCategoryId=21',
  importanceReasons: ['최근 공지'],
  actionability: 'informational',
  dueHint: null,
  relevanceScore: 5,
};

export const QUALITY_INFORMATIONAL_DETAIL: PersonalizedNoticeDetail = {
  ...QUALITY_INFORMATIONAL_NOTICE,
  body: '2026-1학기 강의시간표 등 수업 관련 변경사항을 안내하오니 확인하시기 바랍니다.',
  attachments: [],
  actionBlocks: [],
};
