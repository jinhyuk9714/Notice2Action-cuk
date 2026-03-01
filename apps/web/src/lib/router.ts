export type Route =
  | Readonly<{ view: 'extract' }>
  | Readonly<{ view: 'inbox'; actionId: string | null }>
  | Readonly<{ view: 'sources'; sourceId: string | null }>;

export function parseHash(hash: string): Route {
  const path = hash.replace(/^#\/?/, '');
  const segments = path.split('/').filter((s) => s.length > 0);

  if (segments.length === 0) return { view: 'extract' };

  const view = segments[0];

  if (view === 'inbox') {
    return { view: 'inbox', actionId: segments[1] ?? null };
  }
  if (view === 'sources') {
    return { view: 'sources', sourceId: segments[1] ?? null };
  }
  if (view === 'extract') {
    return { view: 'extract' };
  }

  return { view: 'extract' };
}

export function buildHash(route: Route): string {
  if (route.view === 'extract') return '#/extract';
  if (route.view === 'inbox') {
    return route.actionId !== null ? `#/inbox/${route.actionId}` : '#/inbox';
  }
  return route.sourceId !== null ? `#/sources/${route.sourceId}` : '#/sources';
}

export function navigateTo(route: Route): void {
  const hash = buildHash(route);
  if (window.location.hash !== hash) {
    window.location.hash = hash;
  }
}
