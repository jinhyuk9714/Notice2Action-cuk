export type InboxFilters = Readonly<{
  sort?: string;
  q?: string;
  category?: string;
  dateRange?: string;
  customFrom?: string;
  customTo?: string;
}>;

export type Route =
  | Readonly<{ view: 'extract' }>
  | Readonly<{ view: 'inbox'; actionId: string | null; filters: InboxFilters }>
  | Readonly<{ view: 'sources'; sourceId: string | null }>;

function parseInboxFilters(queryString: string): InboxFilters {
  if (queryString.length === 0) return {};

  const params = new URLSearchParams(queryString);
  const filters: Record<string, string> = {};

  const sort = params.get('sort');
  if (sort === 'recent' || sort === 'due') filters.sort = sort;

  const q = params.get('q');
  if (q !== null && q.length > 0) filters.q = q;

  const category = params.get('category');
  if (category !== null && category.length > 0) filters.category = category;

  const dateRange = params.get('dateRange');
  if (dateRange !== null && dateRange.length > 0) filters.dateRange = dateRange;

  const customFrom = params.get('customFrom');
  if (customFrom !== null && customFrom.length > 0) filters.customFrom = customFrom;

  const customTo = params.get('customTo');
  if (customTo !== null && customTo.length > 0) filters.customTo = customTo;

  return filters;
}

function buildFilterQueryString(filters: InboxFilters): string {
  const params = new URLSearchParams();
  if (filters.sort !== undefined) params.set('sort', filters.sort);
  if (filters.q !== undefined && filters.q.length > 0) params.set('q', filters.q);
  if (filters.category !== undefined && filters.category.length > 0) params.set('category', filters.category);
  if (filters.dateRange !== undefined && filters.dateRange.length > 0) params.set('dateRange', filters.dateRange);
  if (filters.customFrom !== undefined && filters.customFrom.length > 0) params.set('customFrom', filters.customFrom);
  if (filters.customTo !== undefined && filters.customTo.length > 0) params.set('customTo', filters.customTo);
  return params.toString();
}

export function parseHash(hash: string): Route {
  const raw = hash.replace(/^#\/?/, '');

  const qIndex = raw.indexOf('?');
  const pathPart = qIndex >= 0 ? raw.substring(0, qIndex) : raw;
  const queryPart = qIndex >= 0 ? raw.substring(qIndex + 1) : '';

  const segments = pathPart.split('/').filter((s) => s.length > 0);

  if (segments.length === 0) return { view: 'extract' };

  const view = segments[0];

  if (view === 'inbox') {
    const filters = parseInboxFilters(queryPart);
    return { view: 'inbox', actionId: segments[1] ?? null, filters };
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
    const base = route.actionId !== null ? `#/inbox/${route.actionId}` : '#/inbox';
    const qs = buildFilterQueryString(route.filters);
    return qs.length > 0 ? `${base}?${qs}` : base;
  }
  return route.sourceId !== null ? `#/sources/${route.sourceId}` : '#/sources';
}

export function navigateTo(route: Route): void {
  const hash = buildHash(route);
  if (window.location.hash !== hash) {
    window.location.hash = hash;
  }
}

export function replaceFilters(filters: InboxFilters, actionId: string | null): void {
  const hash = buildHash({ view: 'inbox', actionId, filters });
  if (window.location.hash !== hash) {
    history.replaceState(null, '', hash);
  }
}
