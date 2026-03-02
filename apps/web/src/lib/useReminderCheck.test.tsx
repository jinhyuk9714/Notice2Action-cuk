import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReminderSetting } from './reminder';
import { useReminderCheck } from './useReminderCheck';

const mockGetPendingReminders = vi.fn<() => ReminderSetting[]>();
const mockFireNotification = vi.fn<(reminder: ReminderSetting) => boolean>();
const mockDismissReminder = vi.fn<(actionId: string, offsetKey: ReminderSetting['offsetKey']) => void>();

vi.mock('./reminder', () => ({
  getPendingReminders: () => mockGetPendingReminders(),
  fireNotification: (reminder: ReminderSetting) => mockFireNotification(reminder),
  dismissReminder: (actionId: string, offsetKey: ReminderSetting['offsetKey']) =>
    mockDismissReminder(actionId, offsetKey),
}));

function TestComponent({ intervalMs }: Readonly<{ intervalMs: number }>) {
  useReminderCheck(intervalMs);
  return null;
}

function reminder(actionId: string, offsetKey: ReminderSetting['offsetKey']): ReminderSetting {
  return {
    actionId,
    offsetKey,
    remindAtIso: '2026-03-01T09:00:00Z',
    title: '테스트',
    dueLabel: '3월 2일',
    dismissed: false,
  };
}

describe('useReminderCheck', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockGetPendingReminders.mockReset();
    mockFireNotification.mockReset();
    mockDismissReminder.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('dismisses only reminders that were actually notified', async () => {
    const pending = [reminder('a-1', 'D-1'), reminder('a-2', 'D-3')];
    mockGetPendingReminders.mockReturnValue(pending);
    mockFireNotification.mockReturnValueOnce(false).mockReturnValueOnce(true);

    render(<TestComponent intervalMs={60_000} />);

    await act(async () => {
      await Promise.resolve();
    });
    expect(mockFireNotification).toHaveBeenCalledTimes(2);
    expect(mockDismissReminder).toHaveBeenCalledTimes(1);
    expect(mockDismissReminder).toHaveBeenCalledWith('a-2', 'D-3');
  });

  it('checks reminders on every interval tick', async () => {
    mockGetPendingReminders.mockReturnValue([]);

    render(<TestComponent intervalMs={1_000} />);

    await act(async () => {
      await Promise.resolve();
    });
    expect(mockGetPendingReminders).toHaveBeenCalledTimes(1);

    act(() => {
      vi.advanceTimersByTime(3_000);
    });
    expect(mockGetPendingReminders).toHaveBeenCalledTimes(4);
  });
});
