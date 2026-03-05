import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  requestActionExtraction,
  requestPdfExtraction,
  requestScreenshotExtraction,
  requestEmailExtraction,
  fetchNoticeFeed,
  fetchNoticeDetail,
  fetchActionList,
  fetchAllMatchingActions,
  deleteAction,
  fetchActionDetail,
  updateAction,
  revertActionField,
  fetchSourceList,
  fetchSourceDetail,
} from './api';
import type { ActionExtractionRequest } from './types';
import {
  makeActionExtractionResponse,
  makeActionDetail,
  makeActionListResponse,
  makeNoticeDetail,
  makeNoticeFeedResponse,
  makeNoticeSummary,
  makeActionSummary,
  makeSourceDetail,
  makeSourceListResponse,
  makeSourceSummary,
} from '../test-helpers';

function mockOkResponse(body: unknown): Response {
  return {
    ok: true,
    json: vi.fn().mockResolvedValue(body),
    text: vi.fn().mockResolvedValue(JSON.stringify(body)),
  } as unknown as Response;
}

function mockErrorResponse(body: string): Response {
  return {
    ok: false,
    text: vi.fn().mockResolvedValue(body),
  } as unknown as Response;
}

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn());
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// --- Error parsing (existing) ---

describe('api error parsing', () => {
  it('uses backend message and details from ApiErrorResponse', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockErrorResponse(JSON.stringify({
        code: 'bad_request',
        message: '요청 값이 올바르지 않습니다.',
        details: ['dueDateFrom: 잘못된 형식'],
      })),
    );

    await expect(fetchActionList('due', 0, { dueDateFrom: 'invalid' }))
      .rejects.toThrow('요청 값이 올바르지 않습니다. (dueDateFrom: 잘못된 형식)');
  });

  it('falls back to plain body text when response is not JSON', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse('직접 전달된 오류 메시지'));

    await expect(fetchActionList())
      .rejects.toThrow('직접 전달된 오류 메시지');
  });

  it('uses function fallback message when body is empty', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse(''));

    await expect(fetchSourceList())
      .rejects.toThrow('소스 목록을 불러오지 못했습니다');
  });
});

// --- requestActionExtraction ---

describe('requestActionExtraction', () => {
  it('sends POST with JSON body and returns parsed response', async () => {
    const payload: ActionExtractionRequest = {
      sourceText: '장학금 안내',
      sourceUrl: null,
      sourceTitle: null,
      sourceCategory: 'NOTICE',
      focusProfile: [],
    };
    const responseData = makeActionExtractionResponse();
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(responseData));

    const result = await requestActionExtraction(payload);

    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/extractions/actions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    expect(result.actions).toHaveLength(1);
    expect(result.duplicate).toBe(false);
  });

  it('throws on error response', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse(''));

    await expect(requestActionExtraction({
      sourceText: 'x',
      sourceUrl: null,
      sourceTitle: null,
      sourceCategory: 'NOTICE',
      focusProfile: [],
    })).rejects.toThrow('액션 추출 요청에 실패했습니다');
  });
});

// --- requestPdfExtraction ---

describe('requestPdfExtraction', () => {
  it('sends FormData with file and sourceTitle', async () => {
    const file = new File(['pdf-content'], 'test.pdf', { type: 'application/pdf' });
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeActionExtractionResponse()));

    await requestPdfExtraction(file, '공지 제목');

    const call = vi.mocked(fetch).mock.calls[0];
    expect(call[0]).toBe('/api/v1/extractions/pdf');
    const body = call[1]?.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('sourceTitle')).toBe('공지 제목');
  });

  it('omits sourceTitle when null', async () => {
    const file = new File(['pdf'], 'test.pdf');
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeActionExtractionResponse()));

    await requestPdfExtraction(file, null);

    const body = vi.mocked(fetch).mock.calls[0][1]?.body as FormData;
    expect(body.has('sourceTitle')).toBe(false);
  });

  it('throws on error response', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse(''));

    await expect(requestPdfExtraction(new File([''], 'f.pdf'), null))
      .rejects.toThrow('PDF 추출 요청에 실패했습니다');
  });
});

// --- requestScreenshotExtraction ---

describe('requestScreenshotExtraction', () => {
  it('sends FormData with file and returns parsed response', async () => {
    const file = new File(['img'], 'shot.png', { type: 'image/png' });
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeActionExtractionResponse()));

    const result = await requestScreenshotExtraction(file, '캡쳐');

    const call = vi.mocked(fetch).mock.calls[0];
    expect(call[0]).toBe('/api/v1/extractions/screenshot');
    expect(result.actions).toHaveLength(1);
  });

  it('throws on error response', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse(''));

    await expect(requestScreenshotExtraction(new File([''], 'f.png'), null))
      .rejects.toThrow('스크린샷 추출 요청에 실패했습니다');
  });
});

// --- requestEmailExtraction ---

describe('requestEmailExtraction', () => {
  it('sends POST JSON with emailBody and subject', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeActionExtractionResponse()));

    await requestEmailExtraction('본문 내용', '제목');

    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/extractions/email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ emailBody: '본문 내용', subject: '제목', senderAddress: null }),
    });
  });

  it('throws on error response', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse(''));

    await expect(requestEmailExtraction('body', null))
      .rejects.toThrow('이메일 추출 요청에 실패했습니다');
  });
});

// --- fetchActionList ---

describe('fetchActionList', () => {
  it('sends GET with default params', async () => {
    const response = makeActionListResponse([makeActionSummary()]);
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(response));

    const result = await fetchActionList();

    const url = vi.mocked(fetch).mock.calls[0][0] as string;
    expect(url).toContain('sort=recent');
    expect(url).toContain('page=0');
    expect(url).toContain('size=20');
    expect(result.actions).toHaveLength(1);
  });

  it('includes all search params when provided', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeActionListResponse([])));

    await fetchActionList('due', 2, {
      q: '장학',
      category: 'NOTICE',
      dueDateFrom: '2026-03-01',
      dueDateTo: '2026-03-31',
    });

    const url = vi.mocked(fetch).mock.calls[0][0] as string;
    expect(url).toContain('sort=due');
    expect(url).toContain('page=2');
    expect(url).toContain('category=NOTICE');
    expect(url).toContain('dueDateFrom=2026-03-01');
    expect(url).toContain('dueDateTo=2026-03-31');
  });

  it('omits empty search params', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeActionListResponse([])));

    await fetchActionList('recent', 0, { q: '' });

    const url = vi.mocked(fetch).mock.calls[0][0] as string;
    expect(url).not.toContain('q=');
    expect(url).not.toContain('category=');
  });

  it('throws on error response', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse(''));

    await expect(fetchActionList())
      .rejects.toThrow('액션 목록을 불러오지 못했습니다');
  });
});

describe('notice feed api', () => {
  it('requests personalized notice feed with repeated keyword params', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeNoticeFeedResponse([makeNoticeSummary()])));

    const result = await fetchNoticeFeed({
      department: '컴퓨터공학과',
      year: 1,
      status: '신입생',
      interestKeywords: ['학생증', '장학금'],
    });

    const url = vi.mocked(fetch).mock.calls[0][0] as string;
    expect(url).toContain('/api/v1/notices/feed?');
    expect(url).toContain('department=');
    expect(url).toContain('year=1');
    expect(url).toContain('status=');
    expect(url).toContain('keyword=');
    expect(result.notices[0].title).toBe('학생증 신청 안내');
  });

  it('requests personalized notice detail', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeNoticeDetail()));

    const result = await fetchNoticeDetail('notice-1', {
      department: null,
      year: null,
      status: null,
      interestKeywords: [],
    });

    const url = vi.mocked(fetch).mock.calls[0][0] as string;
    expect(url).toContain('/api/v1/notices/notice-1');
    expect(result.body).toBe('정제된 원문');
  });
});

// --- fetchAllMatchingActions ---

describe('fetchAllMatchingActions', () => {
  it('returns actions from single page', async () => {
    vi.mocked(fetch).mockResolvedValue(
      mockOkResponse(makeActionListResponse([makeActionSummary({ id: 'a1' })], false)),
    );

    const result = await fetchAllMatchingActions('recent');

    expect(result).toHaveLength(1);
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
  });

  it('accumulates actions across multiple pages', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(mockOkResponse(makeActionListResponse([makeActionSummary({ id: 'a1' })], true)))
      .mockResolvedValueOnce(mockOkResponse(makeActionListResponse([makeActionSummary({ id: 'a2' })], false)));

    const result = await fetchAllMatchingActions('due');

    expect(result).toHaveLength(2);
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2);
  });

  it('returns empty array when no actions exist', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeActionListResponse([], false)));

    const result = await fetchAllMatchingActions('recent');

    expect(result).toHaveLength(0);
  });
});

// --- deleteAction ---

describe('deleteAction', () => {
  it('sends DELETE to correct URL', async () => {
    vi.mocked(fetch).mockResolvedValue({ ok: true } as Response);

    await deleteAction('act-123');

    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/actions/act-123', {
      method: 'DELETE',
    });
  });

  it('throws on error response', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse(''));

    await expect(deleteAction('act-1'))
      .rejects.toThrow('액션 삭제에 실패했습니다');
  });
});

// --- fetchActionDetail ---

describe('fetchActionDetail', () => {
  it('sends GET and returns parsed detail', async () => {
    const detail = makeActionDetail({ id: 'act-42' });
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(detail));

    const result = await fetchActionDetail('act-42');

    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/actions/act-42');
    expect(result.id).toBe('act-42');
  });

  it('throws on error response', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse(''));

    await expect(fetchActionDetail('act-1'))
      .rejects.toThrow('액션 상세 정보를 불러오지 못했습니다');
  });
});

// --- updateAction ---

describe('updateAction', () => {
  it('sends PATCH with JSON body and returns parsed detail', async () => {
    const detail = makeActionDetail({ id: 'act-1', title: '수정됨' });
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(detail));

    const result = await updateAction('act-1', { title: '수정됨' });

    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/actions/act-1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: '수정됨' }),
    });
    expect(result.title).toBe('수정됨');
  });

  it('throws on error response', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse(''));

    await expect(updateAction('act-1', { title: 'x' }))
      .rejects.toThrow('액션 수정에 실패했습니다');
  });
});

// --- revertActionField ---

describe('revertActionField', () => {
  it('delegates to updateAction with revertFields', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeActionDetail()));

    await revertActionField('act-1', 'title');

    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/actions/act-1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ revertFields: ['title'] }),
    });
  });

  it('returns the updated detail', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeActionDetail({ id: 'act-1' })));

    const result = await revertActionField('act-1', 'title');

    expect(result.id).toBe('act-1');
  });
});

// --- fetchSourceList ---

describe('fetchSourceList', () => {
  it('sends GET with default page', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeSourceListResponse([makeSourceSummary()])));

    const result = await fetchSourceList();

    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/sources?page=0&size=20');
    expect(result.sources).toHaveLength(1);
  });

  it('uses custom page number', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeSourceListResponse([])));

    await fetchSourceList(3);

    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/sources?page=3&size=20');
  });
});

// --- fetchSourceDetail ---

describe('fetchSourceDetail', () => {
  it('sends GET and returns parsed detail', async () => {
    vi.mocked(fetch).mockResolvedValue(mockOkResponse(makeSourceDetail({ id: 'src-42' })));

    const result = await fetchSourceDetail('src-42');

    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/sources/src-42');
    expect(result.id).toBe('src-42');
  });

  it('throws on error response', async () => {
    vi.mocked(fetch).mockResolvedValue(mockErrorResponse(''));

    await expect(fetchSourceDetail('src-1'))
      .rejects.toThrow('소스 상세 정보를 불러오지 못했습니다');
  });
});
