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
    expect(parseHash('#/inbox')).toEqual({ view: 'inbox', actionId: null, filters: {} });
  });

  it('parses #/inbox/action-123', () => {
    expect(parseHash('#/inbox/action-123')).toEqual({ view: 'inbox', actionId: 'action-123', filters: {} });
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

  it('parses inbox with sort and query filters', () => {
    expect(parseHash('#/inbox?sort=due&q=장학')).toEqual({
      view: 'inbox', actionId: null,
      filters: { sort: 'due', q: '장학' },
    });
  });

  it('parses inbox with action id and filters', () => {
    expect(parseHash('#/inbox/abc123?sort=due&q=장학&category=NOTICE')).toEqual({
      view: 'inbox', actionId: 'abc123',
      filters: { sort: 'due', q: '장학', category: 'NOTICE' },
    });
  });

  it('ignores unknown filter keys', () => {
    expect(parseHash('#/inbox?unknown=value')).toEqual({
      view: 'inbox', actionId: null, filters: {},
    });
  });

  it('parses custom date range filters', () => {
    expect(parseHash('#/inbox?dateRange=custom&customFrom=2026-03-01&customTo=2026-03-31')).toEqual({
      view: 'inbox', actionId: null,
      filters: { dateRange: 'custom', customFrom: '2026-03-01', customTo: '2026-03-31' },
    });
  });

  it('rejects invalid sort values', () => {
    expect(parseHash('#/inbox?sort=invalid')).toEqual({
      view: 'inbox', actionId: null, filters: {},
    });
  });
});

describe('buildHash', () => {
  it('builds #/extract', () => {
    expect(buildHash({ view: 'extract' })).toBe('#/extract');
  });

  it('builds #/inbox with empty filters', () => {
    expect(buildHash({ view: 'inbox', actionId: null, filters: {} })).toBe('#/inbox');
  });

  it('builds #/inbox/action-123 with empty filters', () => {
    expect(buildHash({ view: 'inbox', actionId: 'action-123', filters: {} })).toBe('#/inbox/action-123');
  });

  it('builds #/sources', () => {
    expect(buildHash({ view: 'sources', sourceId: null })).toBe('#/sources');
  });

  it('builds #/sources/src-456', () => {
    expect(buildHash({ view: 'sources', sourceId: 'src-456' })).toBe('#/sources/src-456');
  });

  it('builds inbox with sort and query', () => {
    const hash = buildHash({ view: 'inbox', actionId: null, filters: { sort: 'due', q: '장학' } });
    expect(hash).toContain('#/inbox?');
    expect(hash).toContain('sort=due');
    expect(hash).toContain('q=');
  });

  it('builds inbox with action id and filters', () => {
    const hash = buildHash({ view: 'inbox', actionId: 'abc123', filters: { category: 'NOTICE' } });
    expect(hash).toContain('#/inbox/abc123?');
    expect(hash).toContain('category=NOTICE');
  });

  it('is inverse of parseHash', () => {
    const routes = [
      { view: 'extract' as const },
      { view: 'inbox' as const, actionId: null, filters: {} },
      { view: 'inbox' as const, actionId: 'abc', filters: {} },
      { view: 'inbox' as const, actionId: null, filters: { sort: 'due' as const } },
      { view: 'sources' as const, sourceId: null },
      { view: 'sources' as const, sourceId: 'xyz' },
    ];
    for (const route of routes) {
      expect(parseHash(buildHash(route))).toEqual(route);
    }
  });

  it('roundtrips inbox with complex filters', () => {
    const route = {
      view: 'inbox' as const,
      actionId: 'x',
      filters: { sort: 'recent', category: 'PDF', dateRange: 'custom', customFrom: '2026-01-01', customTo: '2026-12-31' },
    };
    expect(parseHash(buildHash(route))).toEqual(route);
  });
});
