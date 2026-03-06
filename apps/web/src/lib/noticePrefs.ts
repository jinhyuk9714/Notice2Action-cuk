export type NoticePreferences = Readonly<{
  savedIds: readonly string[];
  hiddenIds: readonly string[];
}>;

const STORAGE_KEY = 'n2a_notice_prefs';

export const EMPTY_NOTICE_PREFERENCES: NoticePreferences = {
  savedIds: [],
  hiddenIds: [],
};

export function loadNoticePreferences(): NoticePreferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) return EMPTY_NOTICE_PREFERENCES;
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    return {
      savedIds: sanitizeIdArray(parsed.savedIds),
      hiddenIds: sanitizeIdArray(parsed.hiddenIds),
    };
  } catch {
    return EMPTY_NOTICE_PREFERENCES;
  }
}

export function saveNoticePreferences(preferences: NoticePreferences): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences));
}

export function toggleNoticeSaved(preferences: NoticePreferences, id: string): NoticePreferences {
  return preferences.savedIds.includes(id)
    ? { ...preferences, savedIds: preferences.savedIds.filter((value) => value !== id) }
    : { ...preferences, savedIds: [...preferences.savedIds, id] };
}

export function toggleNoticeHidden(preferences: NoticePreferences, id: string): NoticePreferences {
  return preferences.hiddenIds.includes(id)
    ? { ...preferences, hiddenIds: preferences.hiddenIds.filter((value) => value !== id) }
    : { ...preferences, hiddenIds: [...preferences.hiddenIds, id] };
}

export function unhideNotice(preferences: NoticePreferences, id: string): NoticePreferences {
  return { ...preferences, hiddenIds: preferences.hiddenIds.filter((value) => value !== id) };
}

export function cleanupNoticePreferences(
  preferences: NoticePreferences,
  existingIds: readonly string[],
): NoticePreferences {
  const allowed = new Set(existingIds);
  return {
    savedIds: preferences.savedIds.filter((id) => allowed.has(id)),
    hiddenIds: preferences.hiddenIds.filter((id) => allowed.has(id)),
  };
}

function sanitizeIdArray(value: unknown): readonly string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === 'string' && item.length > 0);
}
