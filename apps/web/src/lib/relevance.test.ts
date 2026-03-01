import { describe, it, expect } from 'vitest';
import { computeRelevance } from './relevance';
import type { UserProfile } from './profile';

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
});
