import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useActionDetail } from './useActionDetail';
import { makeActionDetail } from '../test-helpers';

vi.mock('./api', () => ({
  fetchActionDetail: vi.fn(),
}));

import { fetchActionDetail } from './api';

const mockFetchDetail = vi.mocked(fetchActionDetail);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('useActionDetail', () => {
  it('starts with no selection', () => {
    const onActionSelect = vi.fn();
    const { result } = renderHook(() =>
      useActionDetail({ initialActionId: null, onActionSelect }),
    );
    expect(result.current.selectedId).toBeNull();
    expect(result.current.detail).toBeNull();
    expect(result.current.detailError).toBeNull();
  });

  it('sets selectedId from initialActionId on mount', () => {
    const onActionSelect = vi.fn();
    const { result } = renderHook(() =>
      useActionDetail({ initialActionId: 'a1', onActionSelect }),
    );
    expect(result.current.selectedId).toBe('a1');
    // No fetch on mount when initialActionId matches initial selectedId
    expect(mockFetchDetail).not.toHaveBeenCalled();
  });

  it('fetches detail when initialActionId changes', async () => {
    const detail = makeActionDetail({ id: 'a2' });
    mockFetchDetail.mockResolvedValue(detail);
    const onActionSelect = vi.fn();
    let actionId: string | null = null;
    const { result, rerender } = renderHook(() =>
      useActionDetail({ initialActionId: actionId, onActionSelect }),
    );

    actionId = 'a2';
    rerender();

    await waitFor(() => {
      expect(result.current.selectedId).toBe('a2');
      expect(result.current.detail).toEqual(detail);
    });
  });

  it('fetches detail on handleSelect', async () => {
    const detail = makeActionDetail({ id: 'a2' });
    mockFetchDetail.mockResolvedValue(detail);
    const onActionSelect = vi.fn();
    const { result } = renderHook(() =>
      useActionDetail({ initialActionId: null, onActionSelect }),
    );

    act(() => { result.current.handleSelect('a2'); });

    expect(onActionSelect).toHaveBeenCalledWith('a2');
    expect(result.current.selectedId).toBe('a2');

    await waitFor(() => {
      expect(result.current.detail).toEqual(detail);
    });
  });

  it('sets detailError on fetch failure', async () => {
    mockFetchDetail.mockRejectedValue(new Error('상세 오류'));
    const onActionSelect = vi.fn();
    const { result } = renderHook(() =>
      useActionDetail({ initialActionId: null, onActionSelect }),
    );

    act(() => { result.current.handleSelect('a3'); });

    await waitFor(() => {
      expect(result.current.detailError).toBe('상세 오류');
    });
    // detail should remain null
    expect(result.current.detail).toBeNull();
  });

  it('clears selection', () => {
    const onActionSelect = vi.fn();
    const { result } = renderHook(() =>
      useActionDetail({ initialActionId: 'a1', onActionSelect }),
    );

    act(() => { result.current.clearSelection(); });

    expect(result.current.selectedId).toBeNull();
    expect(result.current.detail).toBeNull();
    expect(result.current.detailError).toBeNull();
    expect(onActionSelect).toHaveBeenCalledWith(null);
  });

  it('clears previous error on new select', async () => {
    mockFetchDetail
      .mockRejectedValueOnce(new Error('오류'))
      .mockResolvedValueOnce(makeActionDetail({ id: 'a2' }));
    const onActionSelect = vi.fn();
    const { result } = renderHook(() =>
      useActionDetail({ initialActionId: null, onActionSelect }),
    );

    act(() => { result.current.handleSelect('a1'); });
    await waitFor(() => { expect(result.current.detailError).toBe('오류'); });

    act(() => { result.current.handleSelect('a2'); });
    // Error should be cleared immediately
    expect(result.current.detailError).toBeNull();
  });

  it('updates detail via setDetail', async () => {
    const detail = makeActionDetail({ id: 'a1', title: '원래' });
    mockFetchDetail.mockResolvedValue(detail);
    const onActionSelect = vi.fn();
    const { result } = renderHook(() =>
      useActionDetail({ initialActionId: null, onActionSelect }),
    );

    act(() => { result.current.handleSelect('a1'); });
    await waitFor(() => { expect(result.current.detail).toEqual(detail); });

    const updated = makeActionDetail({ id: 'a1', title: '수정됨' });
    act(() => { result.current.setDetail(updated); });
    expect(result.current.detail?.title).toBe('수정됨');
  });
});
