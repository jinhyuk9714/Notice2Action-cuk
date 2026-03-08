import type { DateRangePreset } from './dateRange';
import type { ActionStatus, SourceCategory } from './types';

export const ACTION_SORTS = ['recent', 'due'] as const;
export type ActionSort = (typeof ACTION_SORTS)[number];

export const ACTION_STATUS_FILTERS = ['all', 'pending', 'completed'] as const;
export type ActionStatusFilter = (typeof ACTION_STATUS_FILTERS)[number];

export type InboxFilters = Readonly<{
  sort?: ActionSort;
  q?: string;
  category?: SourceCategory;
  dateRange?: DateRangePreset;
  customFrom?: string;
  customTo?: string;
  status?: ActionStatus;
}>;

export type ActionSearchParams = Readonly<{
  q?: string;
  category?: SourceCategory;
  dueDateFrom?: string;
  dueDateTo?: string;
  status?: ActionStatus;
}>;

export function isActionSort(value: string | null | undefined): value is ActionSort {
  return value === 'recent' || value === 'due';
}

export function isActionStatus(value: string | null | undefined): value is ActionStatus {
  return value === 'pending' || value === 'completed';
}

export function isActionStatusFilter(value: string | null | undefined): value is ActionStatusFilter {
  return value === 'all' || isActionStatus(value);
}
