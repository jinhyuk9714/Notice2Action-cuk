import { describe, it, expect } from 'vitest';
import { computeRelevance } from './relevance';
import type { UserProfile } from './profile';
import type { StructuredEligibility } from './types';

const CONFIGURED: UserProfile = { department: '컴퓨터공학과', year: 3, status: '재학생' };
const UNCONFIGURED: UserProfile = { department: null, year: null, status: null };

describe('computeRelevance', () => {
  describe('profile not configured', () => {
    it('returns unknown regardless of eligibility', () => {
      expect(computeRelevance('재학생 대상', UNCONFIGURED).level).toBe('unknown');
    });
  });

  describe('eligibility null or empty', () => {
    it('returns unknown for null eligibility', () => {
      expect(computeRelevance(null, CONFIGURED).level).toBe('unknown');
    });

    it('returns unknown for empty string', () => {
      expect(computeRelevance('', CONFIGURED).level).toBe('unknown');
    });

    it('returns unknown for whitespace-only', () => {
      expect(computeRelevance('   ', CONFIGURED).level).toBe('unknown');
    });
  });

  describe('universal patterns', () => {
    it('matches "전체 학생"', () => {
      const result = computeRelevance('전체 학생 대상', CONFIGURED);
      expect(result.level).toBe('relevant');
      expect(result.reason).toBe('전체 학생');
    });

    it('matches "전체학생" (no space)', () => {
      expect(computeRelevance('전체학생 대상', CONFIGURED).level).toBe('relevant');
    });

    it('matches "모든 학생"', () => {
      expect(computeRelevance('모든 학생 대상', CONFIGURED).level).toBe('relevant');
    });

    it('matches "모든학생"', () => {
      expect(computeRelevance('모든학생 대상', CONFIGURED).level).toBe('relevant');
    });
  });

  describe('status matching', () => {
    it('returns relevant when status matches', () => {
      const result = computeRelevance('재학생 대상', CONFIGURED);
      expect(result.level).toBe('relevant');
      expect(result.reason).toContain('재학생');
    });

    it('returns not_relevant when different status is mentioned', () => {
      const profile: UserProfile = { department: null, year: null, status: '재학생' };
      const result = computeRelevance('휴학생 대상', profile);
      expect(result.level).toBe('not_relevant');
      expect(result.reason).toContain('휴학생');
    });

    it('returns unknown when no status is mentioned', () => {
      const profile: UserProfile = { department: null, year: null, status: '재학생' };
      const result = computeRelevance('신청 가능', profile);
      expect(result.level).toBe('unknown');
    });
  });

  describe('year matching', () => {
    it('returns relevant when year matches', () => {
      const profile: UserProfile = { department: null, year: 3, status: null };
      const result = computeRelevance('3학년 대상', profile);
      expect(result.level).toBe('relevant');
      expect(result.reason).toContain('3학년');
    });

    it('returns not_relevant when different year is mentioned', () => {
      const profile: UserProfile = { department: null, year: 3, status: null };
      const result = computeRelevance('1학년, 2학년 대상', profile);
      expect(result.level).toBe('not_relevant');
    });

    it('returns unknown when no year is mentioned', () => {
      const profile: UserProfile = { department: null, year: 3, status: null };
      expect(computeRelevance('신청 가능', profile).level).toBe('unknown');
    });
  });

  describe('department matching', () => {
    it('returns relevant when department matches', () => {
      const profile: UserProfile = { department: '컴퓨터공학과', year: null, status: null };
      const result = computeRelevance('컴퓨터공학과 학생 대상', profile);
      expect(result.level).toBe('relevant');
      expect(result.reason).toContain('컴퓨터공학과');
    });

    it('returns unknown when department is not mentioned (not not_relevant)', () => {
      const profile: UserProfile = { department: '컴퓨터공학과', year: null, status: null };
      expect(computeRelevance('경영학과 대상', profile).level).toBe('unknown');
    });
  });

  describe('combined matching', () => {
    it('includes both status and year in reason when both match', () => {
      const result = computeRelevance('재학생 3학년 대상', CONFIGURED);
      expect(result.level).toBe('relevant');
      expect(result.reason).toContain('재학생');
      expect(result.reason).toContain('3학년');
    });

    it('returns relevant when match exists even with exclusions', () => {
      const profile: UserProfile = { department: null, year: 3, status: '재학생' };
      // status matches, but year 1 is mentioned (exclusion)
      const result = computeRelevance('재학생, 1학년 대상', profile);
      expect(result.level).toBe('relevant');
    });

    it('returns not_relevant with joined exclusion reasons', () => {
      const profile: UserProfile = { department: null, year: 3, status: '재학생' };
      const result = computeRelevance('휴학생 1학년 대상', profile);
      expect(result.level).toBe('not_relevant');
      expect(result.reason).toContain('휴학생');
      expect(result.reason).toContain('1학년');
    });
  });

  describe('structured eligibility', () => {
    it('matches department with suffix-normalized structured department', () => {
      const profile: UserProfile = { department: '컴퓨터정보공학부', year: null, status: null };
      const structured: StructuredEligibility = {
        universal: false,
        statuses: [],
        excludedStatuses: [],
        years: [],
        department: '컴퓨터정보공학',
      };

      const result = computeRelevance(null, profile, structured);
      expect(result.level).toBe('relevant');
      expect(result.reason).toContain('컴퓨터정보공학부');
    });

    it('uses raw eligibility fallback when structured data is not decisive', () => {
      const profile: UserProfile = { department: '컴퓨터정보공학부', year: null, status: null };
      const structured: StructuredEligibility = {
        universal: false,
        statuses: [],
        excludedStatuses: [],
        years: [],
        department: null,
      };

      const result = computeRelevance('컴퓨터정보공학부 대상', profile, structured);
      expect(result.level).toBe('relevant');
      expect(result.reason).toContain('컴퓨터정보공학부');
    });

    it('returns not_relevant when structured status decisively excludes the profile', () => {
      const profile: UserProfile = { department: null, year: null, status: '재학생' };
      const structured: StructuredEligibility = {
        universal: false,
        statuses: ['휴학생'],
        excludedStatuses: [],
        years: [],
        department: null,
      };

      const result = computeRelevance(null, profile, structured);
      expect(result.level).toBe('not_relevant');
    });
  });

  // --- Phase 4: New test suites ---

  describe('exclusion patterns', () => {
    it('"휴학생 제외" with status 휴학생 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: null, status: '휴학생' };
      const result = computeRelevance('휴학생 제외', profile);
      expect(result.level).toBe('not_relevant');
      expect(result.reason).toContain('제외');
    });

    it('"졸업예정자 불가" with status 졸업예정자 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: null, status: '졸업예정자' };
      expect(computeRelevance('졸업예정자 불가', profile).level).toBe('not_relevant');
    });

    it('"재학생 제외" with status 재학생 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: null, status: '재학생' };
      expect(computeRelevance('재학생 제외', profile).level).toBe('not_relevant');
    });

    it('"재학생 대상 (휴학생 제외)" with status 재학생 → relevant', () => {
      const profile: UserProfile = { department: null, year: null, status: '재학생' };
      const result = computeRelevance('재학생 대상 (휴학생 제외)', profile);
      expect(result.level).toBe('relevant');
      expect(result.reason).toContain('재학생');
    });

    it('"재학생 대상 (휴학생 제외)" with status 휴학생 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: null, status: '휴학생' };
      const result = computeRelevance('재학생 대상 (휴학생 제외)', profile);
      expect(result.level).toBe('not_relevant');
    });

    it('"재학생 불포함" with status 재학생 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: null, status: '재학생' };
      expect(computeRelevance('재학생 불포함', profile).level).toBe('not_relevant');
    });

    it('"휴학생은 해당없음" with status 휴학생 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: null, status: '휴학생' };
      expect(computeRelevance('휴학생은 해당없음', profile).level).toBe('not_relevant');
    });

    it('"3학년 제외" with year 3 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: 3, status: null };
      const result = computeRelevance('3학년 제외', profile);
      expect(result.level).toBe('not_relevant');
      expect(result.reason).toContain('제외');
    });
  });

  describe('year range matching', () => {
    it('"1~3학년 대상" with year 2 → relevant', () => {
      const profile: UserProfile = { department: null, year: 2, status: null };
      expect(computeRelevance('1~3학년 대상', profile).level).toBe('relevant');
    });

    it('"1~3학년 대상" with year 4 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: 4, status: null };
      expect(computeRelevance('1~3학년 대상', profile).level).toBe('not_relevant');
    });

    it('"3, 4학년 대상" with year 3 → relevant', () => {
      const profile: UserProfile = { department: null, year: 3, status: null };
      expect(computeRelevance('3, 4학년 대상', profile).level).toBe('relevant');
    });

    it('"3, 4학년 대상" with year 2 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: 2, status: null };
      expect(computeRelevance('3, 4학년 대상', profile).level).toBe('not_relevant');
    });

    it('"3·4학년" with year 4 → relevant', () => {
      const profile: UserProfile = { department: null, year: 4, status: null };
      expect(computeRelevance('3·4학년 대상', profile).level).toBe('relevant');
    });

    it('"3학년 이상" with year 3 → relevant', () => {
      const profile: UserProfile = { department: null, year: 3, status: null };
      expect(computeRelevance('3학년 이상', profile).level).toBe('relevant');
    });

    it('"3학년 이상" with year 4 → relevant', () => {
      const profile: UserProfile = { department: null, year: 4, status: null };
      expect(computeRelevance('3학년 이상', profile).level).toBe('relevant');
    });

    it('"3학년 이상" with year 2 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: 2, status: null };
      expect(computeRelevance('3학년 이상', profile).level).toBe('not_relevant');
    });

    it('"2학년 이하" with year 2 → relevant', () => {
      const profile: UserProfile = { department: null, year: 2, status: null };
      expect(computeRelevance('2학년 이하', profile).level).toBe('relevant');
    });

    it('"2학년 이하" with year 3 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: 3, status: null };
      expect(computeRelevance('2학년 이하', profile).level).toBe('not_relevant');
    });

    it('"고학년 대상" with year 3 → relevant', () => {
      const profile: UserProfile = { department: null, year: 3, status: null };
      expect(computeRelevance('고학년 대상', profile).level).toBe('relevant');
    });

    it('"고학년 대상" with year 1 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: 1, status: null };
      expect(computeRelevance('고학년 대상', profile).level).toBe('not_relevant');
    });

    it('"저학년 대상" with year 2 → relevant', () => {
      const profile: UserProfile = { department: null, year: 2, status: null };
      expect(computeRelevance('저학년 대상', profile).level).toBe('relevant');
    });

    it('"2026학년도 1학기" does not parse as year 6', () => {
      const profile: UserProfile = { department: null, year: 2, status: null };
      // Should be unknown — no real year info
      expect(computeRelevance('2026학년도 1학기 안내', profile).level).toBe('unknown');
    });
  });

  describe('department suffix normalization', () => {
    it('profile "컴퓨터공학" matches text "컴퓨터공학과"', () => {
      const profile: UserProfile = { department: '컴퓨터공학', year: null, status: null };
      const result = computeRelevance('컴퓨터공학과 학생 대상', profile);
      expect(result.level).toBe('relevant');
    });

    it('profile "컴퓨터공학과" matches text "컴퓨터공학 전공"', () => {
      const profile: UserProfile = { department: '컴퓨터공학과', year: null, status: null };
      const result = computeRelevance('컴퓨터공학 전공 대상', profile);
      expect(result.level).toBe('relevant');
    });

    it('profile "경영학" matches text "경영학부 대상"', () => {
      const profile: UserProfile = { department: '경영학', year: null, status: null };
      expect(computeRelevance('경영학부 대상', profile).level).toBe('relevant');
    });

    it('profile "국어국문" matches text "국어국문학과 대상"', () => {
      const profile: UserProfile = { department: '국어국문', year: null, status: null };
      expect(computeRelevance('국어국문학과 대상', profile).level).toBe('relevant');
    });

    it('profile "수학과" matches text "수학 전공"', () => {
      const profile: UserProfile = { department: '수학과', year: null, status: null };
      expect(computeRelevance('수학 전공 대상', profile).level).toBe('relevant');
    });
  });

  describe('conjunctive eligibility', () => {
    it('"재학생 중 3학년" with status 재학생, year 3 → relevant', () => {
      const profile: UserProfile = { department: null, year: 3, status: '재학생' };
      expect(computeRelevance('재학생 중 3학년', profile).level).toBe('relevant');
    });

    it('"재학생 중 1학년" with status 재학생, year 3 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: 3, status: '재학생' };
      expect(computeRelevance('재학생 중 1학년', profile).level).toBe('not_relevant');
    });

    it('"재학생 중 3, 4학년" with status 재학생, year 3 → relevant', () => {
      const profile: UserProfile = { department: null, year: 3, status: '재학생' };
      expect(computeRelevance('재학생 중 3, 4학년', profile).level).toBe('relevant');
    });

    it('"재학생 중 1, 2학년" with status 재학생, year 3 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: 3, status: '재학생' };
      expect(computeRelevance('재학생 중 1, 2학년', profile).level).toBe('not_relevant');
    });

    it('"접수 중입니다" does not trigger conjunctive mode', () => {
      const profile: UserProfile = { department: null, year: null, status: '재학생' };
      // "접수 중입니다" with 재학생 not mentioned → unknown
      expect(computeRelevance('접수 중입니다', profile).level).toBe('unknown');
    });
  });

  describe('edge cases', () => {
    it('"전 학년" triggers universal match', () => {
      expect(computeRelevance('전 학년 대상', CONFIGURED).level).toBe('relevant');
    });

    it('"전학년" triggers universal match', () => {
      expect(computeRelevance('전학년 대상', CONFIGURED).level).toBe('relevant');
    });

    it('"1~4학년 전체" with year 2 → relevant', () => {
      const profile: UserProfile = { department: null, year: 2, status: null };
      expect(computeRelevance('1~4학년 전체', profile).level).toBe('relevant');
    });

    it('"복학생 및 재학생" with status 재학생 → relevant', () => {
      const profile: UserProfile = { department: null, year: null, status: '재학생' };
      expect(computeRelevance('복학생 및 재학생', profile).level).toBe('relevant');
    });

    it('"신입생 대상" with status 대학원생 → not_relevant', () => {
      const profile: UserProfile = { department: null, year: null, status: '대학원생' };
      expect(computeRelevance('신입생 대상', profile).level).toBe('not_relevant');
    });
  });
});
