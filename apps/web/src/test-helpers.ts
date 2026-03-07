import type {
  SavedActionSummary,
  SavedActionDetail,
  ActionExtractionResponse,
  ActionListResponse,
  ExtractedAction,
  SourceSummary,
  SourceDetail,
  SourceListResponse,
  NoticeFeedResponse,
  PersonalizedNoticeDetail,
  PersonalizedNoticeSummary,
} from './lib/types';
import type { UserProfile } from './lib/profile';

// --- Profiles ---

export const EMPTY_PROFILE: UserProfile = {
  department: null,
  year: null,
  status: null,
  interestKeywords: [],
  preferredBoards: [],
};

export const FULL_PROFILE: UserProfile = {
  department: '컴퓨터공학과',
  year: 3,
  status: '재학생',
  interestKeywords: ['장학금'],
  preferredBoards: ['학사'],
};

// --- Action Summaries ---

export function makeActionSummary(overrides: Partial<SavedActionSummary> = {}): SavedActionSummary {
  return {
    id: 'act-1',
    title: '장학금 신청',
    actionSummary: 'TRINITY에서 장학금을 신청하세요.',
    dueAtIso: '2026-03-15T00:00:00',
    dueAtLabel: '3월 15일까지',
    eligibility: '재학생',
    sourceCategory: 'NOTICE',
    sourceTitle: '장학 안내',
    confidenceScore: 0.85,
    createdAt: '2026-03-01T10:00:00',
    status: 'pending',
    ...overrides,
  };
}

// --- Action Details ---

export function makeActionDetail(overrides: Partial<SavedActionDetail> = {}): SavedActionDetail {
  return {
    id: 'act-1',
    title: '장학금 신청',
    actionSummary: 'TRINITY에서 장학금을 신청하세요.',
    dueAtIso: '2026-03-15T00:00:00',
    dueAtLabel: '3월 15일까지',
    eligibility: '재학생',
    structuredEligibility: null,
    requiredItems: ['신청서'],
    systemHint: 'TRINITY',
    inferred: false,
    confidenceScore: 0.85,
    createdAt: '2026-03-01T10:00:00',
    source: { id: 'src-1', title: '장학 안내', sourceCategory: 'NOTICE', createdAt: '2026-03-01T10:00:00' },
    evidence: [{ fieldName: 'dueAtIso', snippet: '3월 15일까지', confidence: 0.9 }],
    overrides: [],
    additionalDates: [],
    status: 'pending',
    ...overrides,
  };
}

// --- Source Summaries ---

export function makeSourceSummary(overrides: Partial<SourceSummary> = {}): SourceSummary {
  return {
    id: 'src-1',
    title: '장학 안내',
    sourceCategory: 'NOTICE',
    sourceUrl: null,
    createdAt: '2026-03-01T10:00:00',
    actionCount: 2,
    ...overrides,
  };
}

// --- Source Details ---

export function makeSourceDetail(overrides: Partial<SourceDetail> = {}): SourceDetail {
  return {
    id: 'src-1',
    title: '장학 안내',
    sourceCategory: 'NOTICE',
    sourceUrl: null,
    createdAt: '2026-03-01T10:00:00',
    actions: [makeActionSummary()],
    ...overrides,
  };
}

// --- List Responses ---

export function makeActionListResponse(
  actions: SavedActionSummary[],
  hasNext = false,
): ActionListResponse {
  return {
    actions,
    currentPage: 0,
    pageSize: 20,
    totalElements: actions.length,
    totalPages: 1,
    hasNext,
  };
}

export function makeSourceListResponse(
  sources: SourceSummary[],
  hasNext = false,
): SourceListResponse {
  return {
    sources,
    currentPage: 0,
    pageSize: 20,
    totalElements: sources.length,
    totalPages: 1,
    hasNext,
  };
}

// --- Extraction Response ---

export function makeExtractedAction(overrides: Partial<ExtractedAction> = {}): ExtractedAction {
  return {
    id: 'ea-1',
    sourceId: 'src-1',
    title: '장학금 신청',
    actionSummary: 'TRINITY에서 장학금을 신청하세요.',
    dueAtIso: '2026-03-15T00:00:00',
    dueAtLabel: '3월 15일까지',
    additionalDates: [],
    eligibility: '재학생',
    structuredEligibility: null,
    requiredItems: ['신청서'],
    systemHint: 'TRINITY',
    sourceCategory: 'NOTICE',
    evidence: [{ fieldName: 'dueAtIso', snippet: '3월 15일까지', confidence: 0.9 }],
    inferred: false,
    confidenceScore: 0.85,
    createdAt: '2026-03-01T10:00:00',
    ...overrides,
  };
}

export function makeActionExtractionResponse(
  actions: ExtractedAction[] = [makeExtractedAction()],
  duplicate = false,
): ActionExtractionResponse {
  return { actions, duplicate };
}

// --- Notice Feed ---

export function makeNoticeSummary(overrides: Partial<PersonalizedNoticeSummary> = {}): PersonalizedNoticeSummary {
  return {
    id: 'notice-1',
    title: '학생증 신청 안내',
    publishedAt: '2026-02-27T00:00:00+09:00',
    sourceUrl: 'https://example.com/notices/1',
    boardLabel: '학사',
    importanceReasons: ['신입생 공지', '학생증 관련'],
    actionability: 'action_required',
    dueHint: { dueAtIso: '2026-03-05T23:59:59+09:00', label: '3월 5일까지' },
    relevanceScore: 105,
    ...overrides,
  };
}

export function makeNoticeDetail(overrides: Partial<PersonalizedNoticeDetail> = {}): PersonalizedNoticeDetail {
  return {
    ...makeNoticeSummary(),
    body: '정제된 원문',
    attachments: [{ name: '학생증 발급 신청서.hwp', url: 'https://example.com/download/1' }],
    actionBlocks: [{
      title: '학생증 신청',
      summary: 'TRINITY에서 동의 후 신청',
      dueAtIso: null,
      dueAtLabel: null,
      requiredItems: ['증명사진'],
      systemHint: 'TRINITY',
      evidence: [{ fieldName: 'summary', snippet: '학생증을 신청해 주시기 바랍니다.', confidence: 0.9 }],
      confidenceScore: 0.91,
    }],
    ...overrides,
  };
}

export function makeNoticeFeedResponse(
  notices: PersonalizedNoticeSummary[] = [makeNoticeSummary()],
  hasNext = false,
): NoticeFeedResponse {
  const availableBoards = Array.from(new Set(
    notices
      .map((notice) => notice.boardLabel)
      .filter((label): label is string => label !== null),
  ));
  return {
    notices,
    currentPage: 0,
    pageSize: 20,
    totalElements: notices.length,
    totalPages: 1,
    hasNext,
    availableBoards,
  };
}
