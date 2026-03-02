export function categoryLabel(category: string | null): string {
  if (category === null) return '';
  const map: Record<string, string> = {
    NOTICE: '공지',
    SYLLABUS: '강의계획서',
    EMAIL: '이메일',
    PDF: 'PDF',
    SCREENSHOT: '스크린샷',
  };
  return map[category] ?? category;
}

export function evidenceFieldLabel(fieldName: string): string {
  const map: Record<string, string> = {
    dueAtLabel: '마감일',
    systemHint: '시스템',
    actionVerb: '행동',
    requiredItems: '준비물',
    eligibility: '대상자',
  };
  return map[fieldName] ?? fieldName;
}

export function inferredLabel(inferred: boolean): string {
  return inferred ? '추론됨' : '확인됨';
}

export function overrideFieldLabel(fieldName: string): string {
  const map: Record<string, string> = {
    title: '제목',
    actionSummary: '요약',
    dueAtIso: '마감일(ISO)',
    dueAtLabel: '마감일',
    eligibility: '대상/조건',
    requiredItems: '준비물',
    systemHint: '시스템',
  };
  return map[fieldName] ?? fieldName;
}
