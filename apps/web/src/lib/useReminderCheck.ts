import { useEffect } from 'react';
import { getPendingReminders, fireNotification, dismissReminder } from './reminder';

export function useReminderCheck(intervalMs: number = 60_000): void {
  useEffect(() => {
    function checkAndFire(): void {
      const pending = getPendingReminders();
      for (const reminder of pending) {
        fireNotification(reminder);
        dismissReminder(reminder.actionId);
      }
    }

    checkAndFire();
    const handle = setInterval(checkAndFire, intervalMs);
    return () => { clearInterval(handle); };
  }, [intervalMs]);
}
