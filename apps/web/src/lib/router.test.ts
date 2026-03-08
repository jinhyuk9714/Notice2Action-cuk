import { describe, it, expect } from 'vitest';
import { parseHash, buildHash } from './router';

describe('parseHash', () => {
  it('returns feed for empty hash', () => {
    expect(parseHash('')).toEqual({ view: 'feed', noticeId: null, board: null });
  });

  it('returns feed for #/', () => {
    expect(parseHash('#/')).toEqual({ view: 'feed', noticeId: null, board: null });
  });

  it('parses #/feed', () => {
    expect(parseHash('#/feed')).toEqual({ view: 'feed', noticeId: null, board: null });
  });

  it('parses #/feed/notice-123 with board filter', () => {
    expect(parseHash('#/feed/notice-123?board=%ED%95%99%EC%82%AC')).toEqual({
      view: 'feed',
      noticeId: 'notice-123',
      board: '학사',
    });
  });

  it('parses #/saved', () => {
    expect(parseHash('#/saved')).toEqual({ view: 'saved', noticeId: null });
  });

  it('parses #/saved/notice-123', () => {
    expect(parseHash('#/saved/notice-123')).toEqual({ view: 'saved', noticeId: 'notice-123' });
  });

  it('parses #/profile', () => {
    expect(parseHash('#/profile')).toEqual({ view: 'profile' });
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

  it('returns feed for unknown path', () => {
    expect(parseHash('#/unknown')).toEqual({ view: 'feed', noticeId: null, board: null });
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

  it('parses inbox status filter', () => {
    expect(parseHash('#/inbox?status=completed')).toEqual({
      view: 'inbox', actionId: null,
      filters: { status: 'completed' },
    });
  });

  it('rejects invalid sort values', () => {
    expect(parseHash('#/inbox?sort=invalid')).toEqual({
      view: 'inbox', actionId: null, filters: {},
    });
  });
});

describe('buildHash', () => {
  it('builds #/feed', () => {
    expect(buildHash({ view: 'feed', noticeId: null, board: null })).toBe('#/feed');
  });

  it('builds #/feed/notice-123?board=', () => {
    expect(buildHash({ view: 'feed', noticeId: 'notice-123', board: '학사' })).toBe('#/feed/notice-123?board=%ED%95%99%EC%82%AC');
  });

  it('builds #/saved', () => {
    expect(buildHash({ view: 'saved', noticeId: null })).toBe('#/saved');
  });

  it('builds #/saved/notice-123', () => {
    expect(buildHash({ view: 'saved', noticeId: 'notice-123' })).toBe('#/saved/notice-123');
  });

  it('builds #/profile', () => {
    expect(buildHash({ view: 'profile' })).toBe('#/profile');
  });

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
      { view: 'feed' as const, noticeId: null, board: null },
      { view: 'feed' as const, noticeId: 'notice-1', board: '학사' },
      { view: 'saved' as const, noticeId: null },
      { view: 'saved' as const, noticeId: 'notice-1' },
      { view: 'profile' as const },
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
      filters: {
        sort: 'recent',
        category: 'PDF',
        dateRange: 'custom',
        customFrom: '2026-01-01',
        customTo: '2026-12-31',
        status: 'pending',
      },
    };
    expect(parseHash(buildHash(route))).toEqual(route);
  });
});
