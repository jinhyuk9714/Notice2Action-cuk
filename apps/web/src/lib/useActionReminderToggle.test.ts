import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { makeActionDetail } from '../test-helpers';
import type { ReminderOption } from './useActionReminderToggle';
import { useActionReminderToggle } from './useActionReminderToggle';

vi.mock('./reminder', () => ({
  getRemindersForAction: vi.fn(),
  removeReminder: vi.fn(),
  requestNotificationPermission: vi.fn(),
  saveReminder: vi.fn(),
}));

import {
  getRemindersForAction,
  removeReminder,
  requestNotificationPermission,
  saveReminder,
} from './reminder';

const mockGetRemindersForAction = vi.mocked(getRemindersForAction);
const mockRemoveReminder = vi.mocked(removeReminder);
const mockRequestNotificationPermission = vi.mocked(requestNotificationPermission);
const mockSaveReminder = vi.mocked(saveReminder);

const D3_OPTION: ReminderOption = { key: 'D-3', label: 'D-3', offsetMs: 3 * 24 * 60 * 60 * 1000 };

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-03-10T00:00:00+09:00'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useActionReminderToggle', () => {
  it('loads active keys and re-syncs when detail changes', async () => {
    mockGetRemindersForAction.mockImplementation((actionId: string) => {
      if (actionId === 'act-1') {
        return [{
          actionId: 'act-1',
          offsetKey: 'D-1',
          remindAtIso: '2026-03-14T00:00:00+09:00',
          title: 'A1',
          dueLabel: 'D-1',
          dismissed: false,
        }];
      }
      return [{
        actionId: 'act-2',
        offsetKey: 'D-3',
        remindAtIso: '2026-03-12T00:00:00+09:00',
        title: 'A2',
        dueLabel: 'D-3',
        dismissed: false,
      }];
    });

    const detail1 = makeActionDetail({ id: 'act-1' });
    const detail2 = makeActionDetail({ id: 'act-2' });
    const { result, rerender } = renderHook(
      ({ detail }) => useActionReminderToggle({ detail }),
      { initialProps: { detail: detail1 } },
    );

    expect(result.current.activeKeys.has('D-1')).toBe(true);
    expect(result.current.activeKeys.has('D-3')).toBe(false);

    act(() => {
      rerender({ detail: detail2 });
    });

    expect(result.current.activeKeys.has('D-1')).toBe(false);
    expect(result.current.activeKeys.has('D-3')).toBe(true);
  });

  it('removes reminder when toggling active key', async () => {
    mockGetRemindersForAction.mockReturnValue([{
      actionId: 'act-1',
      offsetKey: 'D-3',
      remindAtIso: '2026-03-12T00:00:00+09:00',
      title: 'A',
      dueLabel: 'D-3',
      dismissed: false,
    }]);

    const { result } = renderHook(() =>
      useActionReminderToggle({ detail: makeActionDetail({ id: 'act-1' }) }),
    );

    expect(result.current.activeKeys.has('D-3')).toBe(true);

    await act(async () => {
      await result.current.toggleReminder(D3_OPTION);
    });

    expect(mockRemoveReminder).toHaveBeenCalledWith('act-1', 'D-3');
    expect(result.current.activeKeys.has('D-3')).toBe(false);
  });

  it('saves reminder when permission is granted', async () => {
    mockGetRemindersForAction.mockReturnValue([]);
    mockRequestNotificationPermission.mockResolvedValue(true);

    const { result } = renderHook(() =>
      useActionReminderToggle({
        detail: makeActionDetail({
          id: 'act-1',
          dueAtIso: '2026-03-15T00:00:00+09:00',
          dueAtLabel: '3월 15일까지',
        }),
      }),
    );

    await act(async () => {
      await result.current.toggleReminder(D3_OPTION);
    });

    expect(mockSaveReminder).toHaveBeenCalledWith(expect.objectContaining({
      actionId: 'act-1',
      offsetKey: 'D-3',
      title: '장학금 신청',
      dueLabel: '3월 15일까지',
      dismissed: false,
    }));
    expect(result.current.activeKeys.has('D-3')).toBe(true);
  });

  it('keeps reminder unchanged when permission is denied', async () => {
    mockGetRemindersForAction.mockReturnValue([]);
    mockRequestNotificationPermission.mockResolvedValue(false);

    const { result } = renderHook(() =>
      useActionReminderToggle({ detail: makeActionDetail({ id: 'act-1' }) }),
    );

    await act(async () => {
      await result.current.toggleReminder(D3_OPTION);
    });

    expect(mockSaveReminder).not.toHaveBeenCalled();
    expect(result.current.activeKeys.has('D-3')).toBe(false);
  });

  it('does not save reminder when due date is missing', async () => {
    mockGetRemindersForAction.mockReturnValue([]);
    mockRequestNotificationPermission.mockResolvedValue(true);

    const { result } = renderHook(() =>
      useActionReminderToggle({
        detail: makeActionDetail({
          id: 'act-1',
          dueAtIso: null,
          dueAtLabel: null,
        }),
      }),
    );

    await act(async () => {
      await result.current.toggleReminder(D3_OPTION);
    });

    expect(mockSaveReminder).not.toHaveBeenCalled();
    expect(result.current.activeKeys.has('D-3')).toBe(false);
  });
});
