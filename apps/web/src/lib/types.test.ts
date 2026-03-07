import { describe, it, expect } from 'vitest';
import {
  parseNoticeFeedResponse,
  parsePersonalizedNoticeDetail,
  parseActionExtractionResponse,
  parseActionListResponse,
  parseSavedActionDetail,
  parseSourceListResponse,
  parseSourceDetail,
  SourceCategorySchema,
} from './types';

const VALID_EVIDENCE = { fieldName: '마감일', snippet: '2026-03-15까지', confidence: 0.9 };

const VALID_EXTRACTED_ACTION = {
  id: null,
  sourceId: null,
  title: '장학금 신청',
  actionSummary: '교내 장학금 신청',
  dueAtIso: '2026-03-15',
  dueAtLabel: '3월 15일',
  additionalDates: [{ isoAt: '2026-03-12T10:00:00+09:00', label: '3월 12일 설명회' }],
  eligibility: '재학생',
  structuredEligibility: {
    universal: false,
    statuses: ['재학생'],
    excludedStatuses: [],
    years: [3],
    department: '컴퓨터정보공학',
  },
  requiredItems: ['성적증명서'],
  systemHint: 'TRINITY',
  sourceCategory: 'NOTICE',
  evidence: [VALID_EVIDENCE],
  inferred: false,
  confidenceScore: 0.9,
  createdAt: null,
};

const VALID_SUMMARY = {
  id: '123',
  title: '장학금 신청',
  actionSummary: '교내 장학금 신청',
  dueAtIso: null,
  dueAtLabel: null,
  eligibility: null,
  sourceCategory: null,
  sourceTitle: null,
  confidenceScore: 0.9,
  createdAt: '2026-03-01T00:00:00Z',
};

const VALID_DETAIL = {
  id: '123',
  title: '장학금 신청',
  actionSummary: '교내 장학금 신청',
  dueAtIso: null,
  dueAtLabel: null,
  eligibility: null,
  structuredEligibility: null,
  requiredItems: [],
  systemHint: null,
  inferred: false,
  confidenceScore: 0.9,
  createdAt: '2026-03-01T00:00:00Z',
  source: null,
  evidence: [VALID_EVIDENCE],
  overrides: [],
  additionalDates: [],
};

describe('SourceCategorySchema', () => {
  it('accepts valid categories', () => {
    expect(SourceCategorySchema.parse('NOTICE')).toBe('NOTICE');
    expect(SourceCategorySchema.parse('PDF')).toBe('PDF');
  });

  it('rejects invalid category', () => {
    expect(() => SourceCategorySchema.parse('INVALID')).toThrow();
  });
});

describe('parseActionExtractionResponse', () => {
  it('throws for null', () => {
    expect(() => parseActionExtractionResponse(null)).toThrow();
  });

  it('throws for non-object', () => {
    expect(() => parseActionExtractionResponse('string')).toThrow();
    expect(() => parseActionExtractionResponse(42)).toThrow();
  });

  it('throws for object without actions', () => {
    expect(() => parseActionExtractionResponse({})).toThrow();
  });

  it('throws for missing duplicate field', () => {
    expect(() => parseActionExtractionResponse({ actions: [] })).toThrow();
  });

  it('parses empty actions array with duplicate', () => {
    const result = parseActionExtractionResponse({ actions: [], duplicate: false });
    expect(result.actions).toHaveLength(0);
    expect(result.duplicate).toBe(false);
  });

  it('parses valid action', () => {
    const result = parseActionExtractionResponse({ actions: [VALID_EXTRACTED_ACTION], duplicate: false });
    expect(result.actions).toHaveLength(1);
    expect(result.actions[0].title).toBe('장학금 신청');
    expect(result.actions[0].additionalDates).toHaveLength(1);
    expect(result.actions[0].structuredEligibility?.department).toBe('컴퓨터정보공학');
  });

  it('throws when action missing title', () => {
    const { title: _, ...noTitle } = VALID_EXTRACTED_ACTION;
    expect(() => parseActionExtractionResponse({ actions: [noTitle], duplicate: false })).toThrow();
  });

  it('throws when action missing actionSummary', () => {
    const { actionSummary: _, ...noSummary } = VALID_EXTRACTED_ACTION;
    expect(() => parseActionExtractionResponse({ actions: [noSummary], duplicate: false })).toThrow();
  });

  it('throws when action has invalid evidence', () => {
    const action = { ...VALID_EXTRACTED_ACTION, evidence: [{ fieldName: 'f' }] };
    expect(() => parseActionExtractionResponse({ actions: [action], duplicate: false })).toThrow();
  });

  it('throws when action has non-boolean inferred', () => {
    const action = { ...VALID_EXTRACTED_ACTION, inferred: 'yes' };
    expect(() => parseActionExtractionResponse({ actions: [action], duplicate: false })).toThrow();
  });

  it('throws when evidence snippet missing confidence', () => {
    const action = { ...VALID_EXTRACTED_ACTION, evidence: [{ fieldName: 'f', snippet: 's' }] };
    expect(() => parseActionExtractionResponse({ actions: [action], duplicate: false })).toThrow();
  });

  it('throws when requiredItems contains non-string', () => {
    const action = { ...VALID_EXTRACTED_ACTION, requiredItems: [123] };
    expect(() => parseActionExtractionResponse({ actions: [action], duplicate: false })).toThrow();
  });
});

describe('parseActionListResponse', () => {
  it('throws for null', () => {
    expect(() => parseActionListResponse(null)).toThrow();
  });

  it('parses empty actions array', () => {
    const result = parseActionListResponse({
      actions: [], currentPage: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false,
    });
    expect(result.actions).toHaveLength(0);
    expect(result.hasNext).toBe(false);
  });

  it('parses valid summary', () => {
    const result = parseActionListResponse({
      actions: [VALID_SUMMARY], currentPage: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false,
    });
    expect(result.actions).toHaveLength(1);
  });

  it('throws when summary missing id', () => {
    const { id: _, ...noId } = VALID_SUMMARY;
    expect(() => parseActionListResponse({
      actions: [noId], currentPage: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false,
    })).toThrow();
  });

  it('throws when summary missing title', () => {
    const { title: _, ...noTitle } = VALID_SUMMARY;
    expect(() => parseActionListResponse({
      actions: [noTitle], currentPage: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false,
    })).toThrow();
  });

  it('throws when summary missing createdAt', () => {
    const { createdAt: _, ...noCreatedAt } = VALID_SUMMARY;
    expect(() => parseActionListResponse({
      actions: [noCreatedAt], currentPage: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false,
    })).toThrow();
  });

  it('throws when missing pagination fields', () => {
    expect(() => parseActionListResponse({ actions: [VALID_SUMMARY] })).toThrow();
  });

  it('throws when pageSize is missing', () => {
    expect(() => parseActionListResponse({
      actions: [VALID_SUMMARY], currentPage: 0, totalElements: 1, totalPages: 1, hasNext: false,
    })).toThrow();
  });

  it('throws when totalElements is missing', () => {
    expect(() => parseActionListResponse({
      actions: [VALID_SUMMARY], currentPage: 0, pageSize: 20, totalPages: 1, hasNext: false,
    })).toThrow();
  });
});

describe('parseSavedActionDetail', () => {
  it('throws for null', () => {
    expect(() => parseSavedActionDetail(null)).toThrow();
  });

  it('parses valid detail', () => {
    const result = parseSavedActionDetail(VALID_DETAIL);
    expect(result.id).toBe('123');
    expect(result.evidence).toHaveLength(1);
    expect(result.additionalDates).toEqual([]);
  });

  it('parses detail with structured eligibility and additional dates', () => {
    const result = parseSavedActionDetail({
      ...VALID_DETAIL,
      structuredEligibility: {
        universal: false,
        statuses: ['재학생'],
        excludedStatuses: [],
        years: [3],
        department: '컴퓨터정보공학',
      },
      additionalDates: [{ isoAt: '2026-03-12T10:00:00+09:00', label: '3월 12일 설명회' }],
    });

    expect(result.structuredEligibility?.department).toBe('컴퓨터정보공학');
    expect(result.additionalDates[0]?.label).toBe('3월 12일 설명회');
  });

  it('throws when missing evidence array', () => {
    const { evidence: _, ...noEvidence } = VALID_DETAIL;
    expect(() => parseSavedActionDetail(noEvidence)).toThrow();
  });

  it('throws when evidence has invalid snippet', () => {
    const detail = { ...VALID_DETAIL, evidence: [{ fieldName: 'f' }] };
    expect(() => parseSavedActionDetail(detail)).toThrow();
  });

  it('throws when missing id', () => {
    const { id: _, ...noId } = VALID_DETAIL;
    expect(() => parseSavedActionDetail(noId)).toThrow();
  });

  it('throws when missing actionSummary', () => {
    const { actionSummary: _, ...noSummary } = VALID_DETAIL;
    expect(() => parseSavedActionDetail(noSummary)).toThrow();
  });

  it('parses detail with empty overrides', () => {
    const result = parseSavedActionDetail({ ...VALID_DETAIL, overrides: [] });
    expect(result.overrides).toHaveLength(0);
  });

  it('parses detail with overrides', () => {
    const result = parseSavedActionDetail({
      ...VALID_DETAIL,
      overrides: [{ fieldName: 'title', machineValue: '원래 제목' }],
    });
    expect(result.overrides).toHaveLength(1);
    expect(result.overrides[0].fieldName).toBe('title');
    expect(result.overrides[0].machineValue).toBe('원래 제목');
  });

  it('defaults overrides to empty array when missing', () => {
    const { overrides: _, ...noOverrides } = VALID_DETAIL;
    const result = parseSavedActionDetail(noOverrides);
    expect(result.overrides).toHaveLength(0);
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

const VALID_NOTICE_SUMMARY = {
  id: 'notice-1',
  title: '학생증 신청 안내',
  publishedAt: '2026-02-27T00:00:00+09:00',
  sourceUrl: 'https://example.com/notices/1',
  boardLabel: '장학',
  importanceReasons: ['신입생 공지', '학생증 관련'],
  actionability: 'action_required',
  dueHint: { dueAtIso: '2026-03-05T23:59:59+09:00', label: '3월 5일까지' },
  relevanceScore: 105,
};

const VALID_NOTICE_DETAIL = {
  ...VALID_NOTICE_SUMMARY,
  body: '정제된 원문',
  attachments: [{ name: '학생증 발급 신청서.hwp', url: 'https://example.com/download/1' }],
  actionBlocks: [{
    title: '학생증 신청',
    summary: 'TRINITY에서 동의 후 신청',
    dueAtIso: null,
    dueAtLabel: null,
    requiredItems: ['증명사진'],
    systemHint: 'TRINITY',
    evidence: [VALID_EVIDENCE],
    confidenceScore: 0.91,
  }],
};

describe('parseSourceListResponse', () => {
  it('throws for null', () => {
    expect(() => parseSourceListResponse(null)).toThrow();
  });

  it('parses valid response', () => {
    const result = parseSourceListResponse({
      sources: [VALID_SOURCE_SUMMARY],
      currentPage: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false,
    });
    expect(result.sources).toHaveLength(1);
  });

  it('parses empty sources', () => {
    const result = parseSourceListResponse({
      sources: [],
      currentPage: 0, pageSize: 20, totalElements: 0, totalPages: 0, hasNext: false,
    });
    expect(result.sources).toHaveLength(0);
  });

  it('throws when missing pagination fields', () => {
    expect(() => parseSourceListResponse({ sources: [VALID_SOURCE_SUMMARY] })).toThrow();
  });

  it('throws when source pageSize is missing', () => {
    expect(() => parseSourceListResponse({
      sources: [VALID_SOURCE_SUMMARY], currentPage: 0, totalElements: 1, totalPages: 1, hasNext: false,
    })).toThrow();
  });

  it('throws when source totalElements is missing', () => {
    expect(() => parseSourceListResponse({
      sources: [VALID_SOURCE_SUMMARY], currentPage: 0, pageSize: 20, totalPages: 1, hasNext: false,
    })).toThrow();
  });

  it('throws when source missing id', () => {
    const { id: _, ...noId } = VALID_SOURCE_SUMMARY;
    expect(() => parseSourceListResponse({
      sources: [noId], currentPage: 0, pageSize: 20, totalElements: 1, totalPages: 1, hasNext: false,
    })).toThrow();
  });
});

describe('parseSourceDetail', () => {
  it('throws for null', () => {
    expect(() => parseSourceDetail(null)).toThrow();
  });

  it('parses valid detail', () => {
    const result = parseSourceDetail({
      id: 'src-1',
      title: '공결 신청 안내',
      sourceCategory: 'NOTICE',
      sourceUrl: null,
      createdAt: '2026-03-01T00:00:00Z',
      actions: [VALID_SUMMARY],
    });
    expect(result.id).toBe('src-1');
    expect(result.actions).toHaveLength(1);
  });

  it('parses detail with empty actions', () => {
    const result = parseSourceDetail({
      id: 'src-1',
      title: null,
      sourceCategory: 'PDF',
      sourceUrl: null,
      createdAt: '2026-03-01T00:00:00Z',
      actions: [],
    });
    expect(result.actions).toHaveLength(0);
  });

  it('throws when missing sourceCategory', () => {
    expect(() => parseSourceDetail({
      id: 'src-1',
      title: '테스트',
      createdAt: '2026-03-01T00:00:00Z',
      actions: [],
    })).toThrow();
  });
});

describe('parseNoticeFeedResponse', () => {
  it('parses valid personalized notice feed', () => {
    const result = parseNoticeFeedResponse({
      notices: [VALID_NOTICE_SUMMARY],
      currentPage: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    });

    expect(result.notices[0].title).toBe('학생증 신청 안내');
    expect(result.notices[0].boardLabel).toBe('장학');
    expect(result.notices[0].dueHint?.label).toBe('3월 5일까지');
  });

  it('throws when importanceReasons is missing', () => {
    const { importanceReasons: _, ...broken } = VALID_NOTICE_SUMMARY;
    expect(() => parseNoticeFeedResponse({
      notices: [broken],
      currentPage: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    })).toThrow();
  });
});

describe('parsePersonalizedNoticeDetail', () => {
  it('parses valid personalized notice detail', () => {
    const result = parsePersonalizedNoticeDetail(VALID_NOTICE_DETAIL);
    expect(result.boardLabel).toBe('장학');
    expect(result.attachments).toHaveLength(1);
    expect(result.actionBlocks).toHaveLength(1);
  });

  it('throws when body is missing', () => {
    const { body: _, ...broken } = VALID_NOTICE_DETAIL;
    expect(() => parsePersonalizedNoticeDetail(broken)).toThrow();
  });
});
