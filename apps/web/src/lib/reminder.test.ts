import { describe, it, expect, beforeEach } from 'vitest';
import {
  loadReminders,
  saveReminder,
  removeReminder,
  dismissReminder,
  getRemindersForAction,
  hasActiveReminders,
  getPendingReminders,
  fireNotification,
} from './reminder';
import type { ReminderSetting } from './reminder';

function makeReminder(overrides: Partial<ReminderSetting> = {}): ReminderSetting {
  return {
    actionId: 'action-1',
    offsetKey: 'D-1',
    remindAtIso: '2026-03-01T09:00:00Z',
    title: 'Test Reminder',
    dueLabel: '2026-03-02',
    dismissed: false,
    ...overrides,
  };
}

describe('loadReminders', () => {
  beforeEach(() => { localStorage.clear(); });

  it('returns empty array when localStorage is empty', () => {
    expect(loadReminders()).toEqual([]);
  });

  it('returns empty array for invalid JSON', () => {
    localStorage.setItem('n2a_reminders', 'not json');
    expect(loadReminders()).toEqual([]);
  });

  it('migrates legacy entries without offsetKey to D-1', () => {
    const legacy = [{ actionId: 'a1', remindAtIso: '2026-03-01T00:00:00Z', title: 'T', dueLabel: 'L', dismissed: false }];
    localStorage.setItem('n2a_reminders', JSON.stringify(legacy));
    const result = loadReminders();
    expect(result[0].offsetKey).toBe('D-1');
  });

  it('preserves existing offsetKey', () => {
    const data = [makeReminder({ offsetKey: 'D-7' })];
    localStorage.setItem('n2a_reminders', JSON.stringify(data));
    expect(loadReminders()[0].offsetKey).toBe('D-7');
  });
});

describe('saveReminder', () => {
  beforeEach(() => { localStorage.clear(); });

  it('saves a new reminder', () => {
    saveReminder(makeReminder());
    expect(loadReminders()).toHaveLength(1);
  });

  it('replaces existing reminder with same actionId + offsetKey', () => {
    saveReminder(makeReminder({ title: 'Old' }));
    saveReminder(makeReminder({ title: 'New' }));
    const result = loadReminders();
    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('New');
  });

  it('keeps different offsetKey for same actionId', () => {
    saveReminder(makeReminder({ offsetKey: 'D-1' }));
    saveReminder(makeReminder({ offsetKey: 'D-3' }));
    expect(loadReminders()).toHaveLength(2);
  });

  it('keeps different actionId for same offsetKey', () => {
    saveReminder(makeReminder({ actionId: 'a1' }));
    saveReminder(makeReminder({ actionId: 'a2' }));
    expect(loadReminders()).toHaveLength(2);
  });
});

describe('removeReminder', () => {
  beforeEach(() => { localStorage.clear(); });

  it('removes specific reminder by actionId + offsetKey', () => {
    saveReminder(makeReminder({ offsetKey: 'D-1' }));
    saveReminder(makeReminder({ offsetKey: 'D-3' }));
    removeReminder('action-1', 'D-1');
    const result = loadReminders();
    expect(result).toHaveLength(1);
    expect(result[0].offsetKey).toBe('D-3');
  });

  it('is a no-op if reminder does not exist', () => {
    saveReminder(makeReminder());
    removeReminder('nonexistent', 'D-1');
    expect(loadReminders()).toHaveLength(1);
  });
});

describe('dismissReminder', () => {
  beforeEach(() => { localStorage.clear(); });

  it('sets dismissed to true for matching reminder', () => {
    saveReminder(makeReminder());
    dismissReminder('action-1', 'D-1');
    expect(loadReminders()[0].dismissed).toBe(true);
  });

  it('does not dismiss non-matching reminders', () => {
    saveReminder(makeReminder({ offsetKey: 'D-1' }));
    saveReminder(makeReminder({ offsetKey: 'D-3' }));
    dismissReminder('action-1', 'D-1');
    const result = loadReminders();
    expect(result.find((r) => r.offsetKey === 'D-3')!.dismissed).toBe(false);
  });
});

describe('getRemindersForAction', () => {
  beforeEach(() => { localStorage.clear(); });

  it('returns only non-dismissed reminders for given actionId', () => {
    saveReminder(makeReminder({ offsetKey: 'D-1' }));
    saveReminder(makeReminder({ offsetKey: 'D-3' }));
    saveReminder(makeReminder({ actionId: 'other', offsetKey: 'D-1' }));
    const result = getRemindersForAction('action-1');
    expect(result).toHaveLength(2);
  });

  it('excludes dismissed reminders', () => {
    saveReminder(makeReminder());
    dismissReminder('action-1', 'D-1');
    expect(getRemindersForAction('action-1')).toHaveLength(0);
  });

  it('returns empty array for unknown actionId', () => {
    expect(getRemindersForAction('nonexistent')).toEqual([]);
  });
});

describe('hasActiveReminders', () => {
  beforeEach(() => { localStorage.clear(); });

  it('returns true when non-dismissed reminder exists', () => {
    saveReminder(makeReminder());
    expect(hasActiveReminders('action-1')).toBe(true);
  });

  it('returns false when all dismissed', () => {
    saveReminder(makeReminder());
    dismissReminder('action-1', 'D-1');
    expect(hasActiveReminders('action-1')).toBe(false);
  });

  it('returns false when no reminders', () => {
    expect(hasActiveReminders('action-1')).toBe(false);
  });
});

describe('getPendingReminders', () => {
  beforeEach(() => { localStorage.clear(); });

  const past = '2026-02-28T00:00:00Z';
  const future = '2026-04-01T00:00:00Z';
  const now = new Date('2026-03-01T12:00:00Z');

  it('returns reminders whose time has passed', () => {
    saveReminder(makeReminder({ remindAtIso: past }));
    expect(getPendingReminders(now)).toHaveLength(1);
  });

  it('excludes future reminders', () => {
    saveReminder(makeReminder({ remindAtIso: future }));
    expect(getPendingReminders(now)).toHaveLength(0);
  });

  it('excludes dismissed reminders', () => {
    saveReminder(makeReminder({ remindAtIso: past }));
    dismissReminder('action-1', 'D-1');
    expect(getPendingReminders(now)).toHaveLength(0);
  });

  it('returns multiple pending reminders', () => {
    saveReminder(makeReminder({ offsetKey: 'D-1', remindAtIso: past }));
    saveReminder(makeReminder({ offsetKey: 'D-3', remindAtIso: past }));
    saveReminder(makeReminder({ offsetKey: 'D-7', remindAtIso: future }));
    expect(getPendingReminders(now)).toHaveLength(2);
  });
});

describe('fireNotification', () => {
  beforeEach(() => { localStorage.clear(); });

  it('returns false when Notification API is unavailable', () => {
    const original = (globalThis as { Notification?: unknown }).Notification;
    delete (globalThis as { Notification?: unknown }).Notification;
    expect(fireNotification(makeReminder())).toBe(false);
    (globalThis as { Notification?: unknown }).Notification = original;
  });

  it('returns false when permission is denied', () => {
    const OriginalNotification = globalThis.Notification;
    const MockNotification = class {
      static permission: NotificationPermission = 'denied';
      constructor() {}
    };
    Object.defineProperty(globalThis, 'Notification', {
      configurable: true,
      value: MockNotification,
    });
    expect(fireNotification(makeReminder())).toBe(false);
    Object.defineProperty(globalThis, 'Notification', {
      configurable: true,
      value: OriginalNotification,
    });
  });

  it('returns true when notification is shown', () => {
    const OriginalNotification = globalThis.Notification;
    let called = false;
    const MockNotification = class {
      static permission: NotificationPermission = 'granted';
      constructor() { called = true; }
    };
    Object.defineProperty(globalThis, 'Notification', {
      configurable: true,
      value: MockNotification,
    });
    expect(fireNotification(makeReminder())).toBe(true);
    expect(called).toBe(true);
    Object.defineProperty(globalThis, 'Notification', {
      configurable: true,
      value: OriginalNotification,
    });
  });
});
