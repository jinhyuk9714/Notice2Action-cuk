import { describe, it, expect } from 'vitest';
import { computeDday } from './dday';

const NOW = new Date(2026, 2, 1); // 2026-03-01

describe('computeDday', () => {
  it('returns null for null input', () => {
    expect(computeDday(null, NOW)).toBeNull();
  });

  it('returns null for invalid ISO string', () => {
    expect(computeDday('not-a-date', NOW)).toBeNull();
  });

  it('returns null for empty string', () => {
    expect(computeDday('', NOW)).toBeNull();
  });

  it('returns D-Day with urgency imminent for same day', () => {
    const result = computeDday('2026-03-01', NOW);
    expect(result).toEqual({ label: 'D-Day', daysLeft: 0, urgency: 'imminent' });
  });

  it('returns D-1 with urgency imminent for 1 day ahead', () => {
    const result = computeDday('2026-03-02', NOW);
    expect(result).toEqual({ label: 'D-1', daysLeft: 1, urgency: 'imminent' });
  });

  it('returns D-2 with urgency upcoming for 2 days ahead', () => {
    const result = computeDday('2026-03-03', NOW);
    expect(result).toEqual({ label: 'D-2', daysLeft: 2, urgency: 'upcoming' });
  });

  it('returns D-3 with urgency upcoming for 3 days ahead', () => {
    const result = computeDday('2026-03-04', NOW);
    expect(result).toEqual({ label: 'D-3', daysLeft: 3, urgency: 'upcoming' });
  });

  it('returns D-4 with urgency normal for 4 days ahead', () => {
    const result = computeDday('2026-03-05', NOW);
    expect(result).toEqual({ label: 'D-4', daysLeft: 4, urgency: 'normal' });
  });

  it('returns D-30 with urgency normal for 30 days ahead', () => {
    const result = computeDday('2026-03-31', NOW);
    expect(result).toEqual({ label: 'D-30', daysLeft: 30, urgency: 'normal' });
  });

  it('returns D+1 with urgency overdue for 1 day past', () => {
    const result = computeDday('2026-02-28', NOW);
    expect(result).toEqual({ label: 'D+1', daysLeft: -1, urgency: 'overdue' });
  });

  it('returns D+5 with urgency overdue for 5 days past', () => {
    const result = computeDday('2026-02-24', NOW);
    expect(result).toEqual({ label: 'D+5', daysLeft: -5, urgency: 'overdue' });
  });

  it('treats same-day regardless of time as D-Day', () => {
    const result = computeDday('2026-03-01T23:59:59', NOW);
    expect(result).toEqual({ label: 'D-Day', daysLeft: 0, urgency: 'imminent' });
  });

  it('uses current date when now is not provided', () => {
    const result = computeDday('2099-12-31');
    expect(result).not.toBeNull();
    expect(result!.urgency).toBe('normal');
  });
});
