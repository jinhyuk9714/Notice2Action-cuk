import { describe, it, expect } from 'vitest';
import { parseHash, buildHash } from './router';

describe('parseHash', () => {
  it('returns extract for empty hash', () => {
    expect(parseHash('')).toEqual({ view: 'extract' });
  });

  it('returns extract for #/', () => {
    expect(parseHash('#/')).toEqual({ view: 'extract' });
  });

  it('parses #/extract', () => {
    expect(parseHash('#/extract')).toEqual({ view: 'extract' });
  });

  it('parses #/inbox', () => {
    expect(parseHash('#/inbox')).toEqual({ view: 'inbox', actionId: null });
  });

  it('parses #/inbox/action-123', () => {
    expect(parseHash('#/inbox/action-123')).toEqual({ view: 'inbox', actionId: 'action-123' });
  });

  it('parses #/sources', () => {
    expect(parseHash('#/sources')).toEqual({ view: 'sources', sourceId: null });
  });

  it('parses #/sources/src-456', () => {
    expect(parseHash('#/sources/src-456')).toEqual({ view: 'sources', sourceId: 'src-456' });
  });

  it('returns extract for unknown path', () => {
    expect(parseHash('#/unknown')).toEqual({ view: 'extract' });
  });
});

describe('buildHash', () => {
  it('builds #/extract', () => {
    expect(buildHash({ view: 'extract' })).toBe('#/extract');
  });

  it('builds #/inbox', () => {
    expect(buildHash({ view: 'inbox', actionId: null })).toBe('#/inbox');
  });

  it('builds #/inbox/action-123', () => {
    expect(buildHash({ view: 'inbox', actionId: 'action-123' })).toBe('#/inbox/action-123');
  });

  it('builds #/sources', () => {
    expect(buildHash({ view: 'sources', sourceId: null })).toBe('#/sources');
  });

  it('builds #/sources/src-456', () => {
    expect(buildHash({ view: 'sources', sourceId: 'src-456' })).toBe('#/sources/src-456');
  });

  it('is inverse of parseHash', () => {
    const routes = [
      { view: 'extract' as const },
      { view: 'inbox' as const, actionId: null },
      { view: 'inbox' as const, actionId: 'abc' },
      { view: 'sources' as const, sourceId: null },
      { view: 'sources' as const, sourceId: 'xyz' },
    ];
    for (const route of routes) {
      expect(parseHash(buildHash(route))).toEqual(route);
    }
  });
});
