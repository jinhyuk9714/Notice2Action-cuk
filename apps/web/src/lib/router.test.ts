import { describe, expect, it } from 'vitest';
import { buildHash, parseHash } from './router';

describe('parseHash', () => {
  it('returns feed for empty hash', () => {
    expect(parseHash('')).toEqual({ view: 'feed', noticeId: null });
  });

  it('parses feed and saved routes', () => {
    expect(parseHash('#/feed')).toEqual({ view: 'feed', noticeId: null });
    expect(parseHash('#/feed/notice-1')).toEqual({ view: 'feed', noticeId: 'notice-1' });
    expect(parseHash('#/saved')).toEqual({ view: 'saved', noticeId: null });
    expect(parseHash('#/saved/notice-2')).toEqual({ view: 'saved', noticeId: 'notice-2' });
  });

  it('parses profile and keeps debug routes', () => {
    expect(parseHash('#/profile')).toEqual({ view: 'profile' });
    expect(parseHash('#/extract')).toEqual({ view: 'extract' });
    expect(parseHash('#/inbox/abc?sort=due')).toEqual({ view: 'inbox', actionId: 'abc', filters: { sort: 'due' } });
    expect(parseHash('#/sources/src-1')).toEqual({ view: 'sources', sourceId: 'src-1' });
  });

  it('falls back to feed for unknown paths', () => {
    expect(parseHash('#/unknown')).toEqual({ view: 'feed', noticeId: null });
  });
});

describe('buildHash', () => {
  it('builds main routes', () => {
    expect(buildHash({ view: 'feed', noticeId: null })).toBe('#/feed');
    expect(buildHash({ view: 'feed', noticeId: 'notice-1' })).toBe('#/feed/notice-1');
    expect(buildHash({ view: 'saved', noticeId: null })).toBe('#/saved');
    expect(buildHash({ view: 'saved', noticeId: 'notice-2' })).toBe('#/saved/notice-2');
    expect(buildHash({ view: 'profile' })).toBe('#/profile');
  });

  it('roundtrips main and debug routes', () => {
    const routes = [
      { view: 'feed' as const, noticeId: null },
      { view: 'feed' as const, noticeId: 'notice-1' },
      { view: 'saved' as const, noticeId: null },
      { view: 'saved' as const, noticeId: 'notice-2' },
      { view: 'profile' as const },
      { view: 'extract' as const },
      { view: 'inbox' as const, actionId: 'abc', filters: { sort: 'due' as const } },
      { view: 'sources' as const, sourceId: 'src-1' },
    ];

    for (const route of routes) {
      expect(parseHash(buildHash(route))).toEqual(route);
    }
  });
});
