import { describe, it, expect, beforeEach } from 'vitest';
import { loadProfile, saveProfile, isProfileConfigured, EMPTY_PROFILE } from './profile';
import type { UserProfile } from './profile';

describe('loadProfile', () => {
  beforeEach(() => { localStorage.clear(); });

  it('returns EMPTY_PROFILE when localStorage is empty', () => {
    expect(loadProfile()).toEqual(EMPTY_PROFILE);
  });

  it('returns saved profile after save/load roundtrip', () => {
    const profile: UserProfile = { department: '컴퓨터공학과', year: 3, status: '재학생' };
    saveProfile(profile);
    expect(loadProfile()).toEqual(profile);
  });

  it('returns EMPTY_PROFILE for invalid JSON', () => {
    localStorage.setItem('n2a_profile', 'not json');
    expect(loadProfile()).toEqual(EMPTY_PROFILE);
  });

  it('sanitizes empty department string to null', () => {
    localStorage.setItem('n2a_profile', JSON.stringify({ department: '', year: 2, status: '재학생' }));
    const result = loadProfile();
    expect(result.department).toBeNull();
  });

  it('sanitizes invalid year to null', () => {
    localStorage.setItem('n2a_profile', JSON.stringify({ department: null, year: 0, status: null }));
    expect(loadProfile().year).toBeNull();

    localStorage.setItem('n2a_profile', JSON.stringify({ department: null, year: 5, status: null }));
    expect(loadProfile().year).toBeNull();

    localStorage.setItem('n2a_profile', JSON.stringify({ department: null, year: -1, status: null }));
    expect(loadProfile().year).toBeNull();
  });

  it('sanitizes invalid status to null', () => {
    localStorage.setItem('n2a_profile', JSON.stringify({ department: null, year: null, status: 'invalid' }));
    expect(loadProfile().status).toBeNull();
  });

  it('preserves valid partial profile', () => {
    localStorage.setItem('n2a_profile', JSON.stringify({ department: '경영학과', year: null, status: null }));
    const result = loadProfile();
    expect(result.department).toBe('경영학과');
    expect(result.year).toBeNull();
    expect(result.status).toBeNull();
  });
});

describe('saveProfile', () => {
  beforeEach(() => { localStorage.clear(); });

  it('saves to localStorage under n2a_profile key', () => {
    const profile: UserProfile = { department: '전자공학과', year: 1, status: '신입생' };
    saveProfile(profile);
    expect(localStorage.getItem('n2a_profile')).not.toBeNull();
  });

  it('overwrites previous value', () => {
    saveProfile({ department: 'A', year: 1, status: null });
    saveProfile({ department: 'B', year: 2, status: null });
    expect(loadProfile().department).toBe('B');
    expect(loadProfile().year).toBe(2);
  });
});

describe('isProfileConfigured', () => {
  it('returns false when all fields are null', () => {
    expect(isProfileConfigured(EMPTY_PROFILE)).toBe(false);
  });

  it('returns true when only department is set', () => {
    expect(isProfileConfigured({ department: '컴퓨터공학과', year: null, status: null })).toBe(true);
  });

  it('returns true when only year is set', () => {
    expect(isProfileConfigured({ department: null, year: 3, status: null })).toBe(true);
  });

  it('returns true when only status is set', () => {
    expect(isProfileConfigured({ department: null, year: null, status: '재학생' })).toBe(true);
  });

  it('returns true when all fields are set', () => {
    expect(isProfileConfigured({ department: '컴퓨터공학과', year: 3, status: '재학생' })).toBe(true);
  });
});
