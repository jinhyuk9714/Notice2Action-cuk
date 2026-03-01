import type { UserProfile } from './profile';
import { isProfileConfigured, STUDENT_STATUSES } from './profile';

export type RelevanceLevel = 'relevant' | 'not_relevant' | 'unknown';

export type RelevanceResult = Readonly<{
  level: RelevanceLevel;
  reason: string | null;
}>;

const UNIVERSAL_PATTERNS = [
  '전체 학생', '전체학생', '모든 학생', '모든학생', '전 학생', '재학생 전체',
];

const UNKNOWN: RelevanceResult = { level: 'unknown', reason: null };

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

  // Status matching
  if (profile.status !== null) {
    if (text.includes(profile.status)) {
      matches.push(profile.status);
    } else {
      for (const other of STUDENT_STATUSES) {
        if (other !== profile.status && text.includes(other)) {
          exclusions.push(`${other} 대상`);
        }
      }
    }
  }

  // Year matching
  if (profile.year !== null) {
    const yearStr = `${profile.year}학년`;
    if (text.includes(yearStr)) {
      matches.push(yearStr);
    } else {
      for (let y = 1; y <= 4; y++) {
        if (y !== profile.year && text.includes(`${y}학년`)) {
          exclusions.push(`${y}학년 대상`);
        }
      }
    }
  }

  // Department matching
  if (profile.department !== null && profile.department.length > 0) {
    if (text.includes(profile.department)) {
      matches.push(profile.department);
    }
  }

  if (matches.length > 0) {
    return { level: 'relevant', reason: matches.join(', ') + ' 해당' };
  }
  if (exclusions.length > 0) {
    return { level: 'not_relevant', reason: exclusions.join(', ') };
  }

  return UNKNOWN;
}
