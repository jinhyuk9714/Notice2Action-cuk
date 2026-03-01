export type DateRangePreset = 'all' | 'this-week' | 'this-month' | 'overdue' | 'custom';

export type DateRange = Readonly<{
  from: string | undefined;
  to: string | undefined;
}>;

const PRESET_LABELS: Record<DateRangePreset, string> = {
  'all': '전체',
  'this-week': '이번 주',
  'this-month': '이번 달',
  'overdue': '기한 초과',
  'custom': '직접 선택',
};

export function getPresetLabel(preset: DateRangePreset): string {
  return PRESET_LABELS[preset];
}

function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function computeDateRange(
  preset: DateRangePreset,
  customFrom?: string,
  customTo?: string,
): DateRange {
  if (preset === 'all') {
    return { from: undefined, to: undefined };
  }

  if (preset === 'custom') {
    return {
      from: customFrom !== undefined && customFrom.length > 0 ? customFrom : undefined,
      to: customTo !== undefined && customTo.length > 0 ? customTo : undefined,
    };
  }

  const now = new Date();

  if (preset === 'this-week') {
    const day = now.getDay();
    const diffToMonday = day === 0 ? -6 : 1 - day;
    const monday = new Date(now);
    monday.setDate(now.getDate() + diffToMonday);
    const sunday = new Date(monday);
    sunday.setDate(monday.getDate() + 6);
    return { from: toIsoDate(monday), to: toIsoDate(sunday) };
  }

  if (preset === 'this-month') {
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
    const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    return { from: toIsoDate(firstDay), to: toIsoDate(lastDay) };
  }

  // overdue: due date before today
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  return { from: undefined, to: toIsoDate(yesterday) };
}
