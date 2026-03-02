import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useActionFilters } from './useActionFilters';

vi.mock('./router', () => ({
  replaceFilters: vi.fn(),
}));

import { replaceFilters } from './router';

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useActionFilters', () => {
  it('initializes from initialFilters', () => {
    const { result } = renderHook(() =>
      useActionFilters({
        initialFilters: { sort: 'recent', q: '장학', category: 'NOTICE' },
        selectedId: null,
      }),
    );
    expect(result.current.sort).toBe('recent');
    expect(result.current.searchInput).toBe('장학');
    expect(result.current.categoryFilter).toBe('NOTICE');
  });

  it('defaults sort to due', () => {
    const { result } = renderHook(() =>
      useActionFilters({ initialFilters: {}, selectedId: null }),
    );
    expect(result.current.sort).toBe('due');
  });

  it('debounces search input by 300ms', async () => {
    const { result } = renderHook(() =>
      useActionFilters({ initialFilters: {}, selectedId: null }),
    );

    act(() => { result.current.setSearchInput('테스트'); });
    expect(result.current.searchQuery).toBe('');

    await act(async () => { vi.advanceTimersByTime(300); });
    expect(result.current.searchQuery).toBe('테스트');
  });

  it('computes currentSearch from filters', async () => {
    const { result } = renderHook(() =>
      useActionFilters({
        initialFilters: { q: '장학', category: 'PDF' },
        selectedId: null,
      }),
    );
    expect(result.current.currentSearch).toEqual(
      expect.objectContaining({ q: '장학', category: 'PDF' }),
    );
  });

  it('computes hasActiveSearch when search is active', () => {
    const { result } = renderHook(() =>
      useActionFilters({
        initialFilters: { category: 'PDF' },
        selectedId: null,
      }),
    );
    expect(result.current.hasActiveSearch).toBe(true);
  });

  it('hasActiveSearch is false when no filters', () => {
    const { result } = renderHook(() =>
      useActionFilters({ initialFilters: {}, selectedId: null }),
    );
    expect(result.current.hasActiveSearch).toBe(false);
  });

  it('computes calendarUrl with sort', () => {
    const { result } = renderHook(() =>
      useActionFilters({ initialFilters: {}, selectedId: null }),
    );
    expect(result.current.calendarUrl).toContain('sort=due');
  });

  it('calls replaceFilters on render', async () => {
    renderHook(() =>
      useActionFilters({ initialFilters: {}, selectedId: null }),
    );
    await waitFor(() => {
      expect(replaceFilters).toHaveBeenCalled();
    });
  });

  it('updates sort', () => {
    const { result } = renderHook(() =>
      useActionFilters({ initialFilters: {}, selectedId: null }),
    );
    act(() => { result.current.setSort('recent'); });
    expect(result.current.sort).toBe('recent');
  });

  it('updates category filter', () => {
    const { result } = renderHook(() =>
      useActionFilters({ initialFilters: {}, selectedId: null }),
    );
    act(() => { result.current.setCategoryFilter('EMAIL'); });
    expect(result.current.categoryFilter).toBe('EMAIL');
    expect(result.current.hasActiveSearch).toBe(true);
  });

  it('updates date range preset', () => {
    const { result } = renderHook(() =>
      useActionFilters({ initialFilters: {}, selectedId: null }),
    );
    act(() => { result.current.setDateRangePreset('this-week'); });
    expect(result.current.dateRangePreset).toBe('this-week');
    expect(result.current.dateRange.from).toBeDefined();
  });

  it('computes custom date range', () => {
    const { result } = renderHook(() =>
      useActionFilters({
        initialFilters: { dateRange: 'custom', customFrom: '2026-03-01', customTo: '2026-03-31' },
        selectedId: null,
      }),
    );
    expect(result.current.dateRange.from).toBe('2026-03-01');
    expect(result.current.dateRange.to).toBe('2026-03-31');
  });
});
