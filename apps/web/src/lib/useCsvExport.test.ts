import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { makeActionSummary } from '../test-helpers';
import { useCsvExport } from './useCsvExport';

vi.mock('./api', () => ({
  fetchAllMatchingActions: vi.fn(),
}));

vi.mock('./csv', () => ({
  generateActionsCsv: vi.fn(),
  downloadCsv: vi.fn(),
}));

import { fetchAllMatchingActions } from './api';
import { downloadCsv, generateActionsCsv } from './csv';

const mockFetchAllMatchingActions = vi.mocked(fetchAllMatchingActions);
const mockGenerateActionsCsv = vi.mocked(generateActionsCsv);
const mockDownloadCsv = vi.mocked(downloadCsv);

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useCsvExport', () => {
  it('exports all matching actions as CSV', async () => {
    const actions = [makeActionSummary({ id: 'a1' })];
    mockFetchAllMatchingActions.mockResolvedValue(actions);
    mockGenerateActionsCsv.mockReturnValue('csv-content');

    const { result } = renderHook(() =>
      useCsvExport({
        sort: 'due',
        search: { q: '장학' },
      }),
    );

    await act(async () => {
      await result.current.exportCsv();
    });

    expect(mockFetchAllMatchingActions).toHaveBeenCalledWith('due', { q: '장학' });
    expect(mockGenerateActionsCsv).toHaveBeenCalledWith(actions);
    expect(mockDownloadCsv).toHaveBeenCalledWith('csv-content', 'notice2action-actions.csv');
    expect(result.current.csvExporting).toBe(false);
    expect(result.current.csvError).toBeNull();
  });

  it('sets and auto clears csvError on failure', async () => {
    mockFetchAllMatchingActions.mockRejectedValue(new Error('CSV 실패'));

    const { result } = renderHook(() =>
      useCsvExport({
        sort: 'recent',
        search: {},
      }),
    );

    await act(async () => {
      await result.current.exportCsv();
    });
    expect(result.current.csvError).toBe('CSV 실패');

    await act(async () => {
      vi.advanceTimersByTime(4000);
    });
    await waitFor(() => {
      expect(result.current.csvError).toBeNull();
    });
  });

  it('clears csvError manually', async () => {
    mockFetchAllMatchingActions.mockRejectedValue(new Error('수동 닫기'));

    const { result } = renderHook(() =>
      useCsvExport({
        sort: 'recent',
        search: {},
      }),
    );

    await act(async () => {
      await result.current.exportCsv();
    });
    expect(result.current.csvError).toBe('수동 닫기');

    act(() => {
      result.current.clearCsvError();
    });
    expect(result.current.csvError).toBeNull();
  });
});
