import { beforeEach, describe, expect, it } from 'vitest';
import { EMPTY_PROFILE, loadProfile, saveProfile } from './profile';
import type { UserProfile } from './profile';

describe('profile storage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('returns EMPTY_PROFILE when storage is empty', () => {
    expect(loadProfile()).toEqual(EMPTY_PROFILE);
  });

  it('roundtrips profile with interest keywords using v2 storage key', () => {
    const profile: UserProfile = {
      department: '컴퓨터공학과',
      year: 3,
      status: '재학생',
      interestKeywords: ['장학금', '학생증'],
    };

    saveProfile(profile);

    expect(localStorage.getItem('n2a_profile_v2')).not.toBeNull();
    expect(loadProfile()).toEqual(profile);
  });

  it('falls back to legacy storage when v2 key is absent', () => {
    localStorage.setItem('n2a_profile', JSON.stringify({ department: '경영학과', year: 2, status: '재학생' }));

    expect(loadProfile()).toEqual({
      department: '경영학과',
      year: 2,
      status: '재학생',
      interestKeywords: [],
    });
  });

  it('sanitizes invalid keywords and empty strings', () => {
    localStorage.setItem('n2a_profile_v2', JSON.stringify({
      department: '컴퓨터공학과',
      year: 1,
      status: '신입생',
      interestKeywords: ['학생증', '', 10, '  장학금  '],
    }));

    expect(loadProfile().interestKeywords).toEqual(['학생증', '장학금']);
  });
});
