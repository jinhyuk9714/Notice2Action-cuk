export type StudentStatus = '재학생' | '복학생' | '휴학생' | '졸업예정자' | '신입생' | '대학원생';

export const STUDENT_STATUSES: readonly StudentStatus[] = [
  '재학생', '복학생', '휴학생', '졸업예정자', '신입생', '대학원생',
];

export type UserProfile = Readonly<{
  department: string | null;
  year: number | null;
  status: StudentStatus | null;
  interestKeywords?: readonly string[];
  preferredBoards?: readonly string[];
}>;

const STORAGE_KEY = 'n2a_profile_v2';
const LEGACY_STORAGE_KEY = 'n2a_profile';

export const EMPTY_PROFILE: UserProfile = {
  department: null,
  year: null,
  status: null,
  interestKeywords: [],
  preferredBoards: [],
};

export function loadProfile(): UserProfile {
  try {
    const raw = localStorage.getItem(STORAGE_KEY) ?? localStorage.getItem(LEGACY_STORAGE_KEY);
    if (raw === null) return EMPTY_PROFILE;
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    return {
      department: typeof parsed.department === 'string' && parsed.department.length > 0
        ? parsed.department
        : null,
      year: typeof parsed.year === 'number' && parsed.year >= 1 && parsed.year <= 4
        ? parsed.year
        : null,
      status: typeof parsed.status === 'string' && STUDENT_STATUSES.includes(parsed.status as StudentStatus)
        ? (parsed.status as StudentStatus)
        : null,
      interestKeywords: Array.isArray(parsed.interestKeywords)
        ? parsed.interestKeywords
          .filter((keyword): keyword is string => typeof keyword === 'string')
          .map((keyword) => keyword.trim())
          .filter((keyword) => keyword.length > 0)
        : [],
      preferredBoards: Array.isArray(parsed.preferredBoards)
        ? parsed.preferredBoards
          .filter((board): board is string => typeof board === 'string')
          .map((board) => board.trim())
          .filter((board) => board.length > 0)
        : [],
    };
  } catch {
    return EMPTY_PROFILE;
  }
}

export function saveProfile(profile: UserProfile): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
}

export function isProfileConfigured(profile: UserProfile): boolean {
  return profile.department !== null
    || profile.year !== null
    || profile.status !== null
    || (profile.interestKeywords ?? []).length > 0
    || (profile.preferredBoards ?? []).length > 0;
}
