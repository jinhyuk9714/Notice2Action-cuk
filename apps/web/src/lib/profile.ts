export type StudentStatus = '재학생' | '복학생' | '휴학생' | '졸업예정자' | '신입생' | '대학원생';

export const STUDENT_STATUSES: readonly StudentStatus[] = [
  '재학생', '복학생', '휴학생', '졸업예정자', '신입생', '대학원생',
];

export type UserProfile = Readonly<{
  department: string | null;
  year: number | null;
  status: StudentStatus | null;
}>;

const STORAGE_KEY = 'n2a_profile';

export const EMPTY_PROFILE: UserProfile = {
  department: null,
  year: null,
  status: null,
};

export function loadProfile(): UserProfile {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
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
    };
  } catch {
    return EMPTY_PROFILE;
  }
}

export function saveProfile(profile: UserProfile): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
}

export function isProfileConfigured(profile: UserProfile): boolean {
  return profile.department !== null || profile.year !== null || profile.status !== null;
}
