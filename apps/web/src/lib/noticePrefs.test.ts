import { beforeEach, describe, expect, it } from 'vitest';
import {
  cleanupNoticePreferences,
  EMPTY_NOTICE_PREFERENCES,
  loadNoticePreferences,
  saveNoticePreferences,
  toggleNoticeHidden,
  toggleNoticeSaved,
  unhideNotice,
} from './noticePrefs';

describe('noticePrefs', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('loads empty preferences by default', () => {
    expect(loadNoticePreferences()).toEqual(EMPTY_NOTICE_PREFERENCES);
  });

  it('saves and restores saved/hidden ids', () => {
    saveNoticePreferences({ savedIds: ['n1'], hiddenIds: ['n2'] });
    expect(loadNoticePreferences()).toEqual({ savedIds: ['n1'], hiddenIds: ['n2'] });
  });

  it('toggles saved ids without duplicating entries', () => {
    let prefs = toggleNoticeSaved(EMPTY_NOTICE_PREFERENCES, 'n1');
    expect(prefs.savedIds).toEqual(['n1']);
    prefs = toggleNoticeSaved(prefs, 'n1');
    expect(prefs.savedIds).toEqual([]);
  });

  it('keeps saved notice visible while hidden until explicitly unhidden', () => {
    let prefs = toggleNoticeSaved(EMPTY_NOTICE_PREFERENCES, 'n1');
    prefs = toggleNoticeHidden(prefs, 'n1');
    expect(prefs.savedIds).toEqual(['n1']);
    expect(prefs.hiddenIds).toEqual(['n1']);

    prefs = unhideNotice(prefs, 'n1');
    expect(prefs.savedIds).toEqual(['n1']);
    expect(prefs.hiddenIds).toEqual([]);
  });

  it('removes ids missing from latest dataset during cleanup', () => {
    const prefs = cleanupNoticePreferences({ savedIds: ['n1', 'n2'], hiddenIds: ['n2', 'n3'] }, ['n2']);
    expect(prefs).toEqual({ savedIds: ['n2'], hiddenIds: ['n2'] });
  });
});
