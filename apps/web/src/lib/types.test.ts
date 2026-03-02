import { describe, it, expect } from 'vitest';
import { isActionExtractionResponse, isActionListResponse, isSavedActionDetail, isSourceListResponse, isSourceDetail } from './types';

const VALID_EVIDENCE = { fieldName: '마감일', snippet: '2026-03-15까지', confidence: 0.9 };

const VALID_EXTRACTED_ACTION = {
  title: '장학금 신청',
  actionSummary: '교내 장학금 신청',
  dueAtIso: '2026-03-15',
  dueAtLabel: '3월 15일',
  eligibility: '재학생',
  requiredItems: ['성적증명서'],
  systemHint: 'TRINITY',
  sourceCategory: 'NOTICE',
  evidence: [VALID_EVIDENCE],
  inferred: false,
};

const VALID_SUMMARY = {
  id: '123',
  title: '장학금 신청',
  actionSummary: '교내 장학금 신청',
  createdAt: '2026-03-01T00:00:00Z',
};

const VALID_DETAIL = {
  id: '123',
  title: '장학금 신청',
  actionSummary: '교내 장학금 신청',
  createdAt: '2026-03-01T00:00:00Z',
  evidence: [VALID_EVIDENCE],
};

describe('isActionExtractionResponse', () => {
  it('returns false for null', () => {
    expect(isActionExtractionResponse(null)).toBe(false);
  });

  it('returns false for non-object', () => {
    expect(isActionExtractionResponse('string')).toBe(false);
    expect(isActionExtractionResponse(42)).toBe(false);
  });

  it('returns false for object without actions', () => {
    expect(isActionExtractionResponse({})).toBe(false);
  });

  it('returns false for missing duplicate field', () => {
    expect(isActionExtractionResponse({ actions: [] })).toBe(false);
  });

  it('returns true for empty actions array with duplicate', () => {
    expect(isActionExtractionResponse({ actions: [], duplicate: false })).toBe(true);
  });

  it('returns true for valid action', () => {
    expect(isActionExtractionResponse({ actions: [VALID_EXTRACTED_ACTION], duplicate: false })).toBe(true);
  });

  it('returns false when action missing title', () => {
    const { title: _, ...noTitle } = VALID_EXTRACTED_ACTION;
    expect(isActionExtractionResponse({ actions: [noTitle] })).toBe(false);
  });

  it('returns false when action missing actionSummary', () => {
    const { actionSummary: _, ...noSummary } = VALID_EXTRACTED_ACTION;
    expect(isActionExtractionResponse({ actions: [noSummary] })).toBe(false);
  });

  it('returns false when action has invalid evidence', () => {
    const action = { ...VALID_EXTRACTED_ACTION, evidence: [{ fieldName: 'f' }] };
    expect(isActionExtractionResponse({ actions: [action] })).toBe(false);
  });

  it('returns false when action has non-boolean inferred', () => {
    const action = { ...VALID_EXTRACTED_ACTION, inferred: 'yes' };
    expect(isActionExtractionResponse({ actions: [action] })).toBe(false);
  });

  it('returns false when evidence snippet missing confidence', () => {
    const action = { ...VALID_EXTRACTED_ACTION, evidence: [{ fieldName: 'f', snippet: 's' }] };
    expect(isActionExtractionResponse({ actions: [action] })).toBe(false);
  });

  it('returns false when requiredItems contains non-string', () => {
    const action = { ...VALID_EXTRACTED_ACTION, requiredItems: [123] };
    expect(isActionExtractionResponse({ actions: [action] })).toBe(false);
  });
});

describe('isActionListResponse', () => {
  it('returns false for null', () => {
    expect(isActionListResponse(null)).toBe(false);
  });

  it('returns true for empty actions array', () => {
    expect(isActionListResponse({ actions: [], currentPage: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false })).toBe(true);
  });

  it('returns true for valid summary', () => {
    expect(isActionListResponse({ actions: [VALID_SUMMARY], currentPage: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false })).toBe(true);
  });

  it('returns false when summary missing id', () => {
    const { id: _, ...noId } = VALID_SUMMARY;
    expect(isActionListResponse({ actions: [noId], currentPage: 0, totalPages: 1, hasNext: false })).toBe(false);
  });

  it('returns false when summary missing title', () => {
    const { title: _, ...noTitle } = VALID_SUMMARY;
    expect(isActionListResponse({ actions: [noTitle], currentPage: 0, totalPages: 1, hasNext: false })).toBe(false);
  });

  it('returns false when summary missing createdAt', () => {
    const { createdAt: _, ...noCreatedAt } = VALID_SUMMARY;
    expect(isActionListResponse({ actions: [noCreatedAt], currentPage: 0, totalPages: 1, hasNext: false })).toBe(false);
  });

  it('returns false when missing pagination fields', () => {
    expect(isActionListResponse({ actions: [VALID_SUMMARY] })).toBe(false);
  });

  it('returns false when pageSize is missing', () => {
    expect(isActionListResponse({
      actions: [VALID_SUMMARY], currentPage: 0, totalElements: 1, totalPages: 1, hasNext: false,
    })).toBe(false);
  });

  it('returns false when totalElements is missing', () => {
    expect(isActionListResponse({
      actions: [VALID_SUMMARY], currentPage: 0, pageSize: 20, totalPages: 1, hasNext: false,
    })).toBe(false);
  });
});

describe('isSavedActionDetail', () => {
  it('returns false for null', () => {
    expect(isSavedActionDetail(null)).toBe(false);
  });

  it('returns true for valid detail', () => {
    expect(isSavedActionDetail(VALID_DETAIL)).toBe(true);
  });

  it('returns false when missing evidence array', () => {
    const { evidence: _, ...noEvidence } = VALID_DETAIL;
    expect(isSavedActionDetail(noEvidence)).toBe(false);
  });

  it('returns false when evidence has invalid snippet', () => {
    const detail = { ...VALID_DETAIL, evidence: [{ fieldName: 'f' }] };
    expect(isSavedActionDetail(detail)).toBe(false);
  });

  it('returns false when missing id', () => {
    const { id: _, ...noId } = VALID_DETAIL;
    expect(isSavedActionDetail(noId)).toBe(false);
  });

  it('returns false when missing actionSummary', () => {
    const { actionSummary: _, ...noSummary } = VALID_DETAIL;
    expect(isSavedActionDetail(noSummary)).toBe(false);
  });
});

const VALID_SOURCE_SUMMARY = {
  id: 'src-1',
  title: '공결 신청 안내',
  sourceCategory: 'NOTICE',
  sourceUrl: null,
  createdAt: '2026-03-01T00:00:00Z',
  actionCount: 2,
};

describe('isSourceListResponse', () => {
  it('returns false for null', () => {
    expect(isSourceListResponse(null)).toBe(false);
  });

  it('returns true for valid response', () => {
    expect(isSourceListResponse({
      sources: [VALID_SOURCE_SUMMARY],
      currentPage: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false,
    })).toBe(true);
  });

  it('returns true for empty sources', () => {
    expect(isSourceListResponse({
      sources: [],
      currentPage: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false,
    })).toBe(true);
  });

  it('returns false when missing pagination fields', () => {
    expect(isSourceListResponse({ sources: [VALID_SOURCE_SUMMARY] })).toBe(false);
  });

  it('returns false when source pageSize is missing', () => {
    expect(isSourceListResponse({
      sources: [VALID_SOURCE_SUMMARY], currentPage: 0, totalElements: 1, totalPages: 1, hasNext: false,
    })).toBe(false);
  });

  it('returns false when source totalElements is missing', () => {
    expect(isSourceListResponse({
      sources: [VALID_SOURCE_SUMMARY], currentPage: 0, pageSize: 20, totalPages: 1, hasNext: false,
    })).toBe(false);
  });

  it('returns false when source missing id', () => {
    const { id: _, ...noId } = VALID_SOURCE_SUMMARY;
    expect(isSourceListResponse({
      sources: [noId], currentPage: 0, totalPages: 1, hasNext: false,
    })).toBe(false);
  });
});

describe('isSourceDetail', () => {
  it('returns false for null', () => {
    expect(isSourceDetail(null)).toBe(false);
  });

  it('returns true for valid detail', () => {
    expect(isSourceDetail({
      id: 'src-1',
      title: '공결 신청 안내',
      sourceCategory: 'NOTICE',
      sourceUrl: null,
      createdAt: '2026-03-01T00:00:00Z',
      actions: [VALID_SUMMARY],
    })).toBe(true);
  });

  it('returns true for detail with empty actions', () => {
    expect(isSourceDetail({
      id: 'src-1',
      title: null,
      sourceCategory: 'PDF',
      sourceUrl: null,
      createdAt: '2026-03-01T00:00:00Z',
      actions: [],
    })).toBe(true);
  });

  it('returns false when missing sourceCategory', () => {
    expect(isSourceDetail({
      id: 'src-1',
      title: '테스트',
      createdAt: '2026-03-01T00:00:00Z',
      actions: [],
    })).toBe(false);
  });
});
