import { useEffect, useState } from 'react';
import {
  type ReminderOffsetKey,
  getRemindersForAction,
  removeReminder,
  requestNotificationPermission,
  saveReminder,
} from './reminder';
import type { SavedActionDetail } from './types';

export type ReminderOption = Readonly<{
  key: ReminderOffsetKey;
  label: string;
  offsetMs: number;
}>;

function getActiveOffsetKeys(actionId: string): Set<ReminderOffsetKey> {
  const reminders = getRemindersForAction(actionId);
  return new Set(reminders.map((r) => r.offsetKey));
}

function computeReminderIso(dueDate: Date, offsetMs: number): string {
  if (offsetMs === 0) {
    const sameDayMidnight = new Date(
      dueDate.getFullYear(),
      dueDate.getMonth(),
      dueDate.getDate(),
      0,
      0,
      0,
    );
    return sameDayMidnight.getTime() < Date.now()
      ? new Date().toISOString()
      : sameDayMidnight.toISOString();
  }

  const scheduled = new Date(dueDate.getTime() - offsetMs);
  return scheduled.getTime() < Date.now()
    ? new Date().toISOString()
    : scheduled.toISOString();
}

export type UseActionReminderToggleOptions = Readonly<{
  detail: SavedActionDetail;
}>;

export type UseActionReminderToggleResult = Readonly<{
  activeKeys: ReadonlySet<ReminderOffsetKey>;
  toggleReminder: (option: ReminderOption) => Promise<void>;
}>;

export function useActionReminderToggle({
  detail,
}: UseActionReminderToggleOptions): UseActionReminderToggleResult {
  const [activeKeys, setActiveKeys] = useState<Set<ReminderOffsetKey>>(
    () => getActiveOffsetKeys(detail.id),
  );

  useEffect(() => {
    setActiveKeys(getActiveOffsetKeys(detail.id));
  }, [detail.id]);

  async function toggleReminder(option: ReminderOption): Promise<void> {
    if (activeKeys.has(option.key)) {
      removeReminder(detail.id, option.key);
      setActiveKeys((prev) => {
        const next = new Set(prev);
        next.delete(option.key);
        return next;
      });
      return;
    }

    const granted = await requestNotificationPermission();
    if (!granted) return;
    if (detail.dueAtIso === null) return;

    const dueDate = new Date(detail.dueAtIso);
    const remindAtIso = computeReminderIso(dueDate, option.offsetMs);

    saveReminder({
      actionId: detail.id,
      offsetKey: option.key,
      remindAtIso,
      title: detail.title,
      dueLabel: detail.dueAtLabel ?? dueDate.toLocaleDateString('ko-KR'),
      dismissed: false,
    });

    setActiveKeys((prev) => new Set([...prev, option.key]));
  }

  return {
    activeKeys,
    toggleReminder,
  };
}
