export type ReminderOffsetKey = 'D-7' | 'D-3' | 'D-1' | 'D-Day';

export type ReminderSetting = Readonly<{
  actionId: string;
  offsetKey: ReminderOffsetKey;
  remindAtIso: string;
  title: string;
  dueLabel: string;
  dismissed: boolean;
}>;

const STORAGE_KEY = 'n2a_reminders';

/** Load all reminders, migrating legacy entries (no offsetKey) to 'D-1'. */
export function loadReminders(): ReminderSetting[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) return [];
    const parsed = JSON.parse(raw) as Record<string, unknown>[];
    return parsed.map((r) => ({
      actionId: String(r.actionId ?? ''),
      offsetKey: (typeof r.offsetKey === 'string' ? r.offsetKey : 'D-1') as ReminderOffsetKey,
      remindAtIso: String(r.remindAtIso ?? ''),
      title: String(r.title ?? ''),
      dueLabel: String(r.dueLabel ?? ''),
      dismissed: Boolean(r.dismissed),
    }));
  } catch {
    return [];
  }
}

/** Save a reminder, keyed by actionId + offsetKey. */
export function saveReminder(reminder: ReminderSetting): void {
  const existing = loadReminders().filter(
    (r) => !(r.actionId === reminder.actionId && r.offsetKey === reminder.offsetKey),
  );
  existing.push(reminder);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(existing));
}

/** Remove a specific reminder by actionId + offsetKey. */
export function removeReminder(actionId: string, offsetKey: ReminderOffsetKey): void {
  const filtered = loadReminders().filter(
    (r) => !(r.actionId === actionId && r.offsetKey === offsetKey),
  );
  localStorage.setItem(STORAGE_KEY, JSON.stringify(filtered));
}

/** Mark a specific reminder as dismissed so it won't fire again. */
export function dismissReminder(actionId: string, offsetKey: ReminderOffsetKey): void {
  const reminders = loadReminders().map((r) =>
    r.actionId === actionId && r.offsetKey === offsetKey ? { ...r, dismissed: true } : r,
  );
  localStorage.setItem(STORAGE_KEY, JSON.stringify(reminders));
}

/** Get all active (non-dismissed) reminders for a given action. */
export function getRemindersForAction(actionId: string): ReminderSetting[] {
  return loadReminders().filter((r) => r.actionId === actionId && !r.dismissed);
}

/** Check whether an action has any active reminders. */
export function hasActiveReminders(actionId: string): boolean {
  return loadReminders().some((r) => r.actionId === actionId && !r.dismissed);
}

/** Get all pending reminders whose time has come. */
export function getPendingReminders(now?: Date): ReminderSetting[] {
  const currentTime = (now ?? new Date()).getTime();
  return loadReminders().filter((r) => {
    if (r.dismissed) return false;
    return new Date(r.remindAtIso).getTime() <= currentTime;
  });
}

export async function requestNotificationPermission(): Promise<boolean> {
  if (!('Notification' in window)) return false;
  if (Notification.permission === 'granted') return true;
  if (Notification.permission === 'denied') return false;
  const result = await Notification.requestPermission();
  return result === 'granted';
}

export function fireNotification(reminder: ReminderSetting): void {
  if (Notification.permission !== 'granted') return;
  new Notification(`Notice2Action: ${reminder.title}`, {
    body: `마감 ${reminder.offsetKey}: ${reminder.dueLabel}`,
    tag: `n2a-${reminder.actionId}-${reminder.offsetKey}`,
  });
}
