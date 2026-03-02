import type { UserProfile } from './profile';
import { isProfileConfigured, STUDENT_STATUSES } from './profile';

export type RelevanceLevel = 'relevant' | 'not_relevant' | 'unknown';

export type RelevanceResult = Readonly<{
  level: RelevanceLevel;
  reason: string | null;
}>;

const UNIVERSAL_PATTERNS = [
  '전체 학생', '전체학생', '모든 학생', '모든학생', '전 학생', '재학생 전체',
  '전 학년', '전학년', '전체 학년',
];

const EXCLUSION_KEYWORDS = ['제외', '불가', '불포함', '해당없음', '해당 없음'] as const;

const DEPARTMENT_SUFFIXES = ['학부', '학과', '과', '전공'] as const;

const UNKNOWN: RelevanceResult = { level: 'unknown', reason: null };

// --- Helper functions ---

function hasExclusionAfter(text: string, term: string): boolean {
  let startIdx = 0;
  for (;;) {
    const idx = text.indexOf(term, startIdx);
    if (idx === -1) break;
    const after = text.slice(idx + term.length, idx + term.length + 10);
    if (EXCLUSION_KEYWORDS.some((kw) => after.includes(kw))) return true;
    startIdx = idx + 1;
  }
  return false;
}

function parseYearInfo(text: string): { included: ReadonlySet<number>; excluded: ReadonlySet<number> } {
  const included = new Set<number>();
  const excluded = new Set<number>();

  // "학년도" 오탐 방지: "2026학년도" → skip
  const cleaned = text.replace(/\d{4}학년도/g, '');

  // 1) Range: "1~3학년", "1-3학년", "1–3학년"
  for (const m of cleaned.matchAll(/(\d)[~\-–](\d)\s*학년/g)) {
    const start = Number(m[1]);
    const end = Number(m[2]);
    for (let y = start; y <= end; y++) included.add(y);
  }

  // 2) Enumeration: "3, 4학년", "3·4학년", "3및4학년", "3과4학년"
  for (const m of cleaned.matchAll(/(\d(?:\s*[,·및과]\s*\d)+)\s*학년/g)) {
    for (const d of m[1].matchAll(/\d/g)) included.add(Number(d[0]));
  }

  // 3) Comparison: "3학년 이상" → 3~4, "2학년 이하" → 1~2
  for (const m of cleaned.matchAll(/(\d)\s*학년\s*이상/g)) {
    for (let y = Number(m[1]); y <= 4; y++) included.add(y);
  }
  for (const m of cleaned.matchAll(/(\d)\s*학년\s*이하/g)) {
    for (let y = 1; y <= Number(m[1]); y++) included.add(y);
  }

  // 4) Colloquial: "고학년" → {3,4}, "저학년" → {1,2}
  if (cleaned.includes('고학년')) {
    included.add(3);
    included.add(4);
  }
  if (cleaned.includes('저학년')) {
    included.add(1);
    included.add(2);
  }

  // 5) Single: "3학년" (standalone, not already captured by range/enum)
  for (const m of cleaned.matchAll(/(\d)\s*학년/g)) {
    const y = Number(m[1]);
    if (y >= 1 && y <= 4) included.add(y);
  }

  // 6) Exclusion: "3학년 제외" → move from included to excluded
  for (const m of cleaned.matchAll(/(\d)\s*학년/g)) {
    const y = Number(m[1]);
    if (y < 1 || y > 4) continue;
    const afterText = cleaned.slice(m.index! + m[0].length, m.index! + m[0].length + 10);
    if (EXCLUSION_KEYWORDS.some((kw) => afterText.includes(kw))) {
      excluded.add(y);
      included.delete(y);
    }
  }

  return { included, excluded };
}

function normalizeDepartment(name: string): string {
  for (const suffix of DEPARTMENT_SUFFIXES) {
    if (name.endsWith(suffix)) {
      const base = name.slice(0, -suffix.length);
      if (base.length >= 2) return base;
    }
  }
  return name;
}

function isConjunctiveEligibility(text: string): boolean {
  // "~생 중 N학년" 패턴 감지 (열거 "1, 2학년" 포함, 단순 "접수 중" 등 제외)
  if (/[가-힣]+생\s*중\s*\d[,·\s\d]*학년/.test(text)) return true;
  return ['이면서', '이고', '으로서', '중에서'].some((kw) => text.includes(kw));
}

// --- Main function ---

export function computeRelevance(
  eligibility: string | null,
  profile: UserProfile,
): RelevanceResult {
  if (!isProfileConfigured(profile)) return UNKNOWN;
  if (eligibility === null || eligibility.trim().length === 0) return UNKNOWN;

  const text = eligibility;

  // Universal eligibility — matches everyone
  for (const pattern of UNIVERSAL_PATTERNS) {
    if (text.includes(pattern)) {
      return { level: 'relevant', reason: pattern };
    }
  }

  const matches: string[] = [];
  const exclusions: string[] = [];

  // Status matching (with exclusion detection)
  if (profile.status !== null) {
    if (text.includes(profile.status)) {
      if (hasExclusionAfter(text, profile.status)) {
        exclusions.push(`${profile.status} 제외`);
      } else {
        matches.push(profile.status);
      }
    } else {
      for (const other of STUDENT_STATUSES) {
        if (other !== profile.status && text.includes(other)) {
          if (!hasExclusionAfter(text, other)) {
            exclusions.push(`${other} 대상`);
          }
        }
      }
    }
  }

  // Year matching (range, enumeration, comparison, colloquial)
  if (profile.year !== null) {
    const { included, excluded } = parseYearInfo(text);
    if (excluded.has(profile.year)) {
      exclusions.push(`${profile.year}학년 제외`);
    } else if (included.size > 0) {
      if (included.has(profile.year)) {
        matches.push(`${profile.year}학년`);
      } else {
        const yearList = [...included].sort((a, b) => a - b).map((y) => `${y}학년`).join(', ');
        exclusions.push(`${yearList} 대상`);
      }
    }
  }

  // Department matching (with suffix normalization)
  if (profile.department !== null && profile.department.length > 0) {
    const norm = normalizeDepartment(profile.department);
    if (norm.length >= 2) {
      const variants = new Set([profile.department, norm, ...DEPARTMENT_SUFFIXES.map((s) => norm + s)]);
      const matched = [...variants].find((v) => v.length >= 2 && text.includes(v));
      if (matched !== undefined) {
        matches.push(profile.department);
      }
    }
  }

  // Conjunction-aware resolution
  if (matches.length > 0 && exclusions.length > 0 && isConjunctiveEligibility(text)) {
    return { level: 'not_relevant', reason: exclusions.join(', ') };
  }
  if (matches.length > 0) {
    return { level: 'relevant', reason: matches.join(', ') + ' 해당' };
  }
  if (exclusions.length > 0) {
    return { level: 'not_relevant', reason: exclusions.join(', ') };
  }

  return UNKNOWN;
}
