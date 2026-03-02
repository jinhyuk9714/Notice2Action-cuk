import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useActionDelete } from './useActionDelete';
import { makeActionSummary } from '../test-helpers';
import type { SavedActionSummary } from './types';

vi.mock('./api', () => ({
  deleteAction: vi.fn(),
}));

import { deleteAction } from './api';

const mockDeleteAction = vi.mocked(deleteAction);

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
});

function makeOptions(overrides: Partial<{
  actions: readonly SavedActionSummary[];
  selectedId: string | null;
}> = {}) {
  const items: SavedActionSummary[] = [...(overrides.actions ?? [makeActionSummary({ id: 'a1', title: '테스트 액션' })])];
  const onItemsChange = vi.fn((updater: React.SetStateAction<readonly SavedActionSummary[]>) => {
    if (typeof updater === 'function') {
      const next = updater(items);
      items.length = 0;
      items.push(...next);
    }
  });
  const clearSelection = vi.fn();
  return {
    actions: items as readonly SavedActionSummary[],
    onItemsChange,
    selectedId: overrides.selectedId ?? null,
    clearSelection,
  };
}

describe('useActionDelete', () => {
  it('starts with no pending or deleting state', () => {
    const opts = makeOptions();
    const { result } = renderHook(() => useActionDelete(opts));
    expect(result.current.deletingId).toBeNull();
    expect(result.current.pendingDeleteId).toBeNull();
    expect(result.current.deleteToast).toBeNull();
    expect(result.current.deleteError).toBeNull();
  });

  it('sets pendingDeleteId on requestDelete', () => {
    const opts = makeOptions();
    const { result } = renderHook(() => useActionDelete(opts));

    act(() => { result.current.requestDelete('a1'); });
    expect(result.current.pendingDeleteId).toBe('a1');
  });

  it('clears pendingDeleteId on cancelDelete', () => {
    const opts = makeOptions();
    const { result } = renderHook(() => useActionDelete(opts));

    act(() => { result.current.requestDelete('a1'); });
    act(() => { result.current.cancelDelete(); });
    expect(result.current.pendingDeleteId).toBeNull();
  });

  it('deletes action and shows toast on confirmDelete', async () => {
    mockDeleteAction.mockResolvedValue(undefined);
    const opts = makeOptions();
    const { result } = renderHook(() => useActionDelete(opts));

    act(() => { result.current.requestDelete('a1'); });
    await act(async () => { await result.current.confirmDelete(); });

    expect(mockDeleteAction).toHaveBeenCalledWith('a1');
    expect(opts.onItemsChange).toHaveBeenCalled();
    expect(result.current.deleteToast).toContain('삭제 완료');
    expect(result.current.pendingDeleteId).toBeNull();
    expect(result.current.deletingId).toBeNull();
  });

  it('clears selection when deleted item is selected', async () => {
    mockDeleteAction.mockResolvedValue(undefined);
    const opts = makeOptions({ selectedId: 'a1' });
    const { result } = renderHook(() => useActionDelete(opts));

    act(() => { result.current.requestDelete('a1'); });
    await act(async () => { await result.current.confirmDelete(); });

    expect(opts.clearSelection).toHaveBeenCalled();
  });

  it('does not clear selection when deleting non-selected item', async () => {
    mockDeleteAction.mockResolvedValue(undefined);
    const opts = makeOptions({ selectedId: 'other' });
    const { result } = renderHook(() => useActionDelete(opts));

    act(() => { result.current.requestDelete('a1'); });
    await act(async () => { await result.current.confirmDelete(); });

    expect(opts.clearSelection).not.toHaveBeenCalled();
  });

  it('auto-dismisses toast after 2500ms', async () => {
    mockDeleteAction.mockResolvedValue(undefined);
    const opts = makeOptions();
    const { result } = renderHook(() => useActionDelete(opts));

    act(() => { result.current.requestDelete('a1'); });
    await act(async () => { await result.current.confirmDelete(); });

    expect(result.current.deleteToast).not.toBeNull();

    await act(async () => { vi.advanceTimersByTime(2500); });
    await waitFor(() => {
      expect(result.current.deleteToast).toBeNull();
    });
  });

  it('sets deleteError on failure', async () => {
    mockDeleteAction.mockRejectedValue(new Error('삭제 오류'));
    const opts = makeOptions();
    const { result } = renderHook(() => useActionDelete(opts));

    act(() => { result.current.requestDelete('a1'); });
    await act(async () => { await result.current.confirmDelete(); });

    expect(result.current.deleteError).toBe('삭제 오류');
    expect(result.current.deletingId).toBeNull();
  });

  it('does nothing when confirmDelete called without pending', async () => {
    const opts = makeOptions();
    const { result } = renderHook(() => useActionDelete(opts));

    await act(async () => { await result.current.confirmDelete(); });
    expect(mockDeleteAction).not.toHaveBeenCalled();
  });
});
