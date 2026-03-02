import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { usePagedList, type PageResult } from './usePagedList';

function makePage<T>(items: T[], hasNext = false): PageResult<T> {
  return { items, hasNext };
}

describe('usePagedList', () => {
  it('starts in loading state', () => {
    const fetchPage = vi.fn(() => new Promise<PageResult<string>>(() => {}));
    const { result } = renderHook(() =>
      usePagedList({ fetchPage, deps: [] }),
    );
    expect(result.current.loading).toBe(true);
    expect(result.current.items).toEqual([]);
  });

  it('loads first page and sets items', async () => {
    const fetchPage = vi.fn().mockResolvedValue(makePage(['a', 'b'], false));
    const { result } = renderHook(() =>
      usePagedList({ fetchPage, deps: [] }),
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });
    expect(result.current.items).toEqual(['a', 'b']);
    expect(result.current.hasNext).toBe(false);
  });

  it('shows error on fetch failure', async () => {
    const fetchPage = vi.fn().mockRejectedValue(new Error('네트워크 오류'));
    const { result } = renderHook(() =>
      usePagedList({ fetchPage, deps: [] }),
    );

    await waitFor(() => {
      expect(result.current.error).toBe('네트워크 오류');
    });
    expect(result.current.loading).toBe(false);
  });

  it('retries after error', async () => {
    const fetchPage = vi.fn()
      .mockRejectedValueOnce(new Error('실패'))
      .mockResolvedValueOnce(makePage(['a']));
    const { result } = renderHook(() =>
      usePagedList({ fetchPage, deps: [] }),
    );

    await waitFor(() => { expect(result.current.error).toBe('실패'); });

    act(() => { result.current.retry(); });

    await waitFor(() => {
      expect(result.current.error).toBeNull();
      expect(result.current.items).toEqual(['a']);
    });
  });

  it('loads more pages', async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(makePage(['a'], true))
      .mockResolvedValueOnce(makePage(['b'], false));
    const { result } = renderHook(() =>
      usePagedList({ fetchPage, deps: [] }),
    );

    await waitFor(() => { expect(result.current.hasNext).toBe(true); });

    act(() => { result.current.loadMore(); });

    await waitFor(() => {
      expect(result.current.items).toEqual(['a', 'b']);
      expect(result.current.hasNext).toBe(false);
    });
  });

  it('sets loadMoreError on page 2 failure', async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(makePage(['a'], true))
      .mockRejectedValueOnce(new Error('실패'));
    const { result } = renderHook(() =>
      usePagedList({ fetchPage, deps: [] }),
    );

    await waitFor(() => { expect(result.current.hasNext).toBe(true); });

    act(() => { result.current.loadMore(); });

    await waitFor(() => {
      expect(result.current.loadMoreError).toBe(true);
      expect(result.current.loadingMore).toBe(false);
      expect(result.current.items).toEqual(['a']);
    });
  });

  it('resets on deps change', async () => {
    const fetchPage = vi.fn()
      .mockResolvedValueOnce(makePage(['a', 'b'], true))
      .mockResolvedValueOnce(makePage(['c']));

    let deps = ['dep1'];
    const { result, rerender } = renderHook(() =>
      usePagedList({ fetchPage, deps }),
    );

    await waitFor(() => { expect(result.current.items).toEqual(['a', 'b']); });

    deps = ['dep2'];
    rerender();

    await waitFor(() => {
      expect(result.current.items).toEqual(['c']);
    });
  });

  it('provides setItems for external mutation', async () => {
    const fetchPage = vi.fn().mockResolvedValue(makePage(['a', 'b']));
    const { result } = renderHook(() =>
      usePagedList({ fetchPage, deps: [] }),
    );

    await waitFor(() => { expect(result.current.items).toEqual(['a', 'b']); });

    act(() => { result.current.setItems(['x']); });
    expect(result.current.items).toEqual(['x']);
  });

  it('provides clearError', async () => {
    const fetchPage = vi.fn().mockRejectedValue(new Error('오류'));
    const { result } = renderHook(() =>
      usePagedList({ fetchPage, deps: [] }),
    );

    await waitFor(() => { expect(result.current.error).toBe('오류'); });

    act(() => { result.current.clearError(); });
    expect(result.current.error).toBeNull();
  });

  it('uses fallback error message for non-Error throws', async () => {
    const fetchPage = vi.fn().mockRejectedValue('unknown');
    const { result } = renderHook(() =>
      usePagedList({ fetchPage, deps: [] }),
    );

    await waitFor(() => {
      expect(result.current.error).toBe('목록을 불러오지 못했습니다');
    });
  });

  it('does not update state after unmount (cancellation)', async () => {
    let resolvePromise: (v: PageResult<string>) => void;
    const fetchPage = vi.fn(() => new Promise<PageResult<string>>((resolve) => {
      resolvePromise = resolve;
    }));

    const { result, unmount } = renderHook(() =>
      usePagedList({ fetchPage, deps: [] }),
    );

    expect(result.current.loading).toBe(true);
    unmount();

    // Resolve after unmount — should not throw
    act(() => { resolvePromise!(makePage(['a'])); });
  });
});
