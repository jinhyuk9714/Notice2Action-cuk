import { useEffect, useMemo, useState } from 'react';
import type { SearchParams } from './api';
import { computeDateRange, type DateRange, type DateRangePreset } from './dateRange';
import { replaceFilters, type InboxFilters } from './router';
import type { ActionStatus, SourceCategory } from './types';

const VALID_CATEGORIES = new Set(['NOTICE', 'SYLLABUS', 'EMAIL', 'PDF', 'SCREENSHOT']);
const VALID_PRESETS = new Set(['all', 'this-week', 'this-month', 'overdue', 'custom']);

function toCategory(val: string | undefined): SourceCategory | '' {
  return val !== undefined && VALID_CATEGORIES.has(val) ? (val as SourceCategory) : '';
}

function toPreset(val: string | undefined): DateRangePreset {
  return val !== undefined && VALID_PRESETS.has(val) ? (val as DateRangePreset) : 'all';
}

function toStatus(val: string | undefined): 'all' | ActionStatus {
  return val === 'pending' || val === 'completed' ? val : 'all';
}

export type UseActionFiltersOptions = Readonly<{
  initialFilters: InboxFilters;
  selectedId: string | null;
}>;

export type UseActionFiltersResult = {
  readonly sort: 'recent' | 'due';
  readonly setSort: (s: 'recent' | 'due') => void;
  readonly searchInput: string;
  readonly setSearchInput: (s: string) => void;
  readonly searchQuery: string;
  readonly categoryFilter: SourceCategory | '';
  readonly setCategoryFilter: (c: SourceCategory | '') => void;
  readonly dateRangePreset: DateRangePreset;
  readonly setDateRangePreset: (p: DateRangePreset) => void;
  readonly customFrom: string;
  readonly setCustomFrom: (s: string) => void;
  readonly customTo: string;
  readonly setCustomTo: (s: string) => void;
  readonly statusFilter: 'all' | ActionStatus;
  readonly setStatusFilter: (s: 'all' | ActionStatus) => void;
  readonly currentSearch: SearchParams;
  readonly dateRange: DateRange;
  readonly calendarUrl: string;
  readonly hasActiveSearch: boolean;
};

export function useActionFilters({ initialFilters, selectedId }: UseActionFiltersOptions): UseActionFiltersResult {
  const [sort, setSort] = useState<'recent' | 'due'>(
    initialFilters.sort === 'recent' ? 'recent' : 'due',
  );
  const [searchInput, setSearchInput] = useState<string>(initialFilters.q ?? '');
  const [searchQuery, setSearchQuery] = useState<string>(initialFilters.q ?? '');
  const [categoryFilter, setCategoryFilter] = useState<SourceCategory | ''>(toCategory(initialFilters.category));
  const [dateRangePreset, setDateRangePreset] = useState<DateRangePreset>(toPreset(initialFilters.dateRange));
  const [customFrom, setCustomFrom] = useState<string>(initialFilters.customFrom ?? '');
  const [customTo, setCustomTo] = useState<string>(initialFilters.customTo ?? '');
  const [statusFilter, setStatusFilter] = useState<'all' | ActionStatus>(toStatus(initialFilters.status));

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => { setSearchQuery(searchInput); }, 300);
    return () => { clearTimeout(timer); };
  }, [searchInput]);

  const dateRange = useMemo(
    () => computeDateRange(dateRangePreset, customFrom, customTo),
    [dateRangePreset, customFrom, customTo],
  );

  const currentSearch: SearchParams = useMemo(() => ({
    ...(searchQuery.length > 0 ? { q: searchQuery } : {}),
    ...(categoryFilter !== '' ? { category: categoryFilter } : {}),
    ...(dateRange.from !== undefined ? { dueDateFrom: dateRange.from } : {}),
    ...(dateRange.to !== undefined ? { dueDateTo: dateRange.to } : {}),
    ...(statusFilter !== 'all' ? { status: statusFilter } : {}),
  }), [searchQuery, categoryFilter, dateRange, statusFilter]);

  const calendarUrl = useMemo(() => {
    const params = new URLSearchParams();
    if (searchQuery.length > 0) params.set('q', searchQuery);
    if (categoryFilter !== '') params.set('category', categoryFilter);
    if (dateRange.from !== undefined) params.set('dueDateFrom', dateRange.from);
    if (dateRange.to !== undefined) params.set('dueDateTo', dateRange.to);
    if (statusFilter !== 'all') params.set('status', statusFilter);
    params.set('sort', sort);
    const qs = params.toString();
    return qs.length > 0 ? `/api/v1/actions/calendar.ics?${qs}` : '/api/v1/actions/calendar.ics';
  }, [searchQuery, categoryFilter, dateRange, statusFilter, sort]);

  // URL sync
  useEffect(() => {
    replaceFilters(
      {
        sort,
        q: searchQuery.length > 0 ? searchQuery : undefined,
        category: categoryFilter !== '' ? categoryFilter : undefined,
        dateRange: dateRangePreset !== 'all' ? dateRangePreset : undefined,
        customFrom: dateRangePreset === 'custom' && customFrom.length > 0 ? customFrom : undefined,
        customTo: dateRangePreset === 'custom' && customTo.length > 0 ? customTo : undefined,
        status: statusFilter !== 'all' ? statusFilter : undefined,
      },
      selectedId,
    );
  }, [sort, searchQuery, categoryFilter, dateRangePreset, customFrom, customTo, statusFilter, selectedId]);

  const hasActiveSearch =
    searchQuery.length > 0 || categoryFilter !== '' || dateRangePreset !== 'all' || statusFilter !== 'all';

  return {
    sort,
    setSort,
    searchInput,
    setSearchInput,
    searchQuery,
    categoryFilter,
    setCategoryFilter,
    dateRangePreset,
    setDateRangePreset,
    customFrom,
    setCustomFrom,
    customTo,
    setCustomTo,
    statusFilter,
    setStatusFilter,
    currentSearch,
    dateRange,
    calendarUrl,
    hasActiveSearch,
  };
}
