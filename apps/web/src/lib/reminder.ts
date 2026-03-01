export type ReminderSetting = Readonly<{
  actionId: string;
  remindAtIso: string;
  title: string;
  dueLabel: string;
  dismissed: boolean;
}>;

const STORAGE_KEY = 'n2a_reminders';

export function loadReminders(): ReminderSetting[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) return [];
    return JSON.parse(raw) as ReminderSetting[];
  } catch {
    return [];
  }
}

export function saveReminder(reminder: ReminderSetting): void {
  const existing = loadReminders().filter(r => r.actionId !== reminder.actionId);
  existing.push(reminder);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(existing));
}

export function removeReminder(actionId: string): void {
  const filtered = loadReminders().filter(r => r.actionId !== actionId);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(filtered));
}

export function dismissReminder(actionId: string): void {
  const reminders = loadReminders().map(r =>
    r.actionId === actionId ? { ...r, dismissed: true } : r
  );
  localStorage.setItem(STORAGE_KEY, JSON.stringify(reminders));
}

export function getPendingReminders(now?: Date): ReminderSetting[] {
  const currentTime = (now ?? new Date()).getTime();
  return loadReminders().filter(r => {
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
    body: `마감: ${reminder.dueLabel}`,
    tag: `n2a-${reminder.actionId}`,
  });
}
