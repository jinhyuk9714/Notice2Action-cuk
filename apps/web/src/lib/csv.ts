import type { SavedActionSummary } from './types';

const CSV_HEADERS = [
  '제목',
  '요약',
  '마감일',
  '마감 레이블',
  '대상/조건',
  '카테고리',
  '소스 제목',
  '신뢰도',
  '생성일',
] as const;

function escapeField(value: string): string {
  if (value.includes('"') || value.includes(',') || value.includes('\n')) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

export function generateActionsCsv(actions: readonly SavedActionSummary[]): string {
  const header = CSV_HEADERS.join(',');
  const rows = actions.map((a) => [
    escapeField(a.title),
    escapeField(a.actionSummary),
    a.dueAtIso ?? '',
    escapeField(a.dueAtLabel ?? ''),
    escapeField(a.eligibility ?? ''),
    a.sourceCategory ?? '',
    escapeField(a.sourceTitle ?? ''),
    String(Math.round(a.confidenceScore * 100)),
    a.createdAt,
  ].join(','));

  return '\uFEFF' + [header, ...rows].join('\n');
}

export function downloadCsv(csv: string, filename: string): void {
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
