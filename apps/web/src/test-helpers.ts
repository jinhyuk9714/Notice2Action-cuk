import type { SavedActionSummary, SavedActionDetail, ActionListResponse, SourceSummary, SourceDetail, SourceListResponse } from './lib/types';
import type { UserProfile } from './lib/profile';

// --- Profiles ---

export const EMPTY_PROFILE: UserProfile = { department: null, year: null, status: null };

export const FULL_PROFILE: UserProfile = { department: '컴퓨터공학과', year: 3, status: '재학생' };

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
    requiredItems: ['신청서'],
    systemHint: 'TRINITY',
    inferred: false,
    confidenceScore: 0.85,
    createdAt: '2026-03-01T10:00:00',
    source: { id: 'src-1', title: '장학 안내', sourceCategory: 'NOTICE', createdAt: '2026-03-01T10:00:00' },
    evidence: [{ fieldName: 'dueAtIso', snippet: '3월 15일까지', confidence: 0.9 }],
    overrides: [],
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
