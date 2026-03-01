import { describe, it, expect, vi, afterEach } from 'vitest';
import { computeDateRange, getPresetLabel } from './dateRange';

afterEach(() => {
  vi.useRealTimers();
});

describe('getPresetLabel', () => {
  it('returns Korean labels', () => {
    expect(getPresetLabel('all')).toBe('전체');
    expect(getPresetLabel('this-week')).toBe('이번 주');
    expect(getPresetLabel('this-month')).toBe('이번 달');
    expect(getPresetLabel('overdue')).toBe('기한 초과');
    expect(getPresetLabel('custom')).toBe('직접 선택');
  });
});

describe('computeDateRange', () => {
  it('returns undefined for all', () => {
    expect(computeDateRange('all')).toEqual({ from: undefined, to: undefined });
  });

  it('returns custom values for custom preset', () => {
    expect(computeDateRange('custom', '2026-03-01', '2026-03-31')).toEqual({
      from: '2026-03-01',
      to: '2026-03-31',
    });
  });

  it('returns undefined for empty custom values', () => {
    expect(computeDateRange('custom', '', '')).toEqual({
      from: undefined,
      to: undefined,
    });
  });

  it('computes this-week range (Monday to Sunday)', () => {
    // 2026-03-02 is a Monday
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 2));
    const range = computeDateRange('this-week');
    expect(range.from).toBe('2026-03-02');
    expect(range.to).toBe('2026-03-08');
  });

  it('computes this-week range from mid-week', () => {
    // 2026-03-05 is a Thursday
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 5));
    const range = computeDateRange('this-week');
    expect(range.from).toBe('2026-03-02');
    expect(range.to).toBe('2026-03-08');
  });

  it('computes this-week range from Sunday', () => {
    // 2026-03-08 is a Sunday
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 8));
    const range = computeDateRange('this-week');
    expect(range.from).toBe('2026-03-02');
    expect(range.to).toBe('2026-03-08');
  });

  it('computes this-month range', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 15));
    const range = computeDateRange('this-month');
    expect(range.from).toBe('2026-03-01');
    expect(range.to).toBe('2026-03-31');
  });

  it('computes this-month for February', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 1, 10));
    const range = computeDateRange('this-month');
    expect(range.from).toBe('2026-02-01');
    expect(range.to).toBe('2026-02-28');
  });

  it('computes overdue (to = yesterday)', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 2));
    const range = computeDateRange('overdue');
    expect(range.from).toBeUndefined();
    expect(range.to).toBe('2026-03-01');
  });
});
