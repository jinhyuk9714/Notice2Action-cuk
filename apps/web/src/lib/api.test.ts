import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchActionList, fetchSourceList } from './api';

function mockErrorResponse(body: string): Response {
  return {
    ok: false,
    text: vi.fn().mockResolvedValue(body),
  } as unknown as Response;
}

describe('api error parsing', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('uses backend message and details from ApiErrorResponse', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(
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
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(mockErrorResponse('직접 전달된 오류 메시지'));

    await expect(fetchActionList())
      .rejects.toThrow('직접 전달된 오류 메시지');
  });

  it('uses function fallback message when body is empty', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(mockErrorResponse(''));

    await expect(fetchSourceList())
      .rejects.toThrow('소스 목록을 불러오지 못했습니다');
  });
});
