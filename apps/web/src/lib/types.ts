export type SourceCategory = 'NOTICE' | 'SYLLABUS' | 'EMAIL' | 'PDF' | 'SCREENSHOT';

export type EvidenceSnippet = Readonly<{
  fieldName: string;
  snippet: string;
  confidence: number;
}>;

export type ExtractedAction = Readonly<{
  id: string | null;
  sourceId: string | null;
  title: string;
  actionSummary: string;
  dueAtIso: string | null;
  dueAtLabel: string | null;
  eligibility: string | null;
  requiredItems: readonly string[];
  systemHint: string | null;
  sourceCategory: SourceCategory;
  evidence: readonly EvidenceSnippet[];
  inferred: boolean;
  confidenceScore: number;
  createdAt: string | null;
}>;

export type ActionExtractionRequest = Readonly<{
  sourceText: string;
  sourceUrl: string | null;
  sourceTitle: string | null;
  sourceCategory: SourceCategory;
  focusProfile: readonly string[];
}>;

export type ActionExtractionResponse = Readonly<{
  actions: readonly ExtractedAction[];
  duplicate: boolean;
}>;

export type ActionUpdatePayload = Readonly<{
  title?: string;
  actionSummary?: string;
  dueAtIso?: string;
  dueAtLabel?: string;
  eligibility?: string;
  requiredItems?: readonly string[];
  systemHint?: string;
}>;

function isEvidenceSnippet(value: unknown): value is EvidenceSnippet {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const record = value as Record<string, unknown>;
  return (
    typeof record.fieldName === 'string' &&
    typeof record.snippet === 'string' &&
    typeof record.confidence === 'number'
  );
}

function isExtractedAction(value: unknown): value is ExtractedAction {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const record = value as Record<string, unknown>;
  return (
    typeof record.title === 'string' &&
    typeof record.actionSummary === 'string' &&
    (typeof record.dueAtIso === 'string' || record.dueAtIso === null) &&
    (typeof record.dueAtLabel === 'string' || record.dueAtLabel === null) &&
    (typeof record.eligibility === 'string' || record.eligibility === null) &&
    Array.isArray(record.requiredItems) &&
    record.requiredItems.every((item) => typeof item === 'string') &&
    (typeof record.systemHint === 'string' || record.systemHint === null) &&
    typeof record.sourceCategory === 'string' &&
    Array.isArray(record.evidence) &&
    record.evidence.every(isEvidenceSnippet) &&
    typeof record.inferred === 'boolean'
  );
}

export function isActionExtractionResponse(value: unknown): value is ActionExtractionResponse {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const record = value as Record<string, unknown>;
  return typeof record.duplicate === 'boolean'
    && Array.isArray(record.actions)
    && record.actions.every(isExtractedAction);
}

// --- Inbox types ---

export type SavedActionSummary = Readonly<{
  id: string;
  title: string;
  actionSummary: string;
  dueAtIso: string | null;
  dueAtLabel: string | null;
  eligibility: string | null;
  sourceCategory: SourceCategory | null;
  sourceTitle: string | null;
  confidenceScore: number;
  createdAt: string;
}>;

export type ActionListResponse = Readonly<{
  actions: readonly SavedActionSummary[];
  currentPage: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}>;

export type SourceInfo = Readonly<{
  id: string;
  title: string | null;
  sourceCategory: SourceCategory;
  createdAt: string;
}>;

export type SavedActionDetail = Readonly<{
  id: string;
  title: string;
  actionSummary: string;
  dueAtIso: string | null;
  dueAtLabel: string | null;
  eligibility: string | null;
  requiredItems: readonly string[];
  systemHint: string | null;
  inferred: boolean;
  confidenceScore: number;
  createdAt: string;
  source: SourceInfo | null;
  evidence: readonly EvidenceSnippet[];
}>;

function isSavedActionSummary(value: unknown): value is SavedActionSummary {
  if (typeof value !== 'object' || value === null) {
    console.warn('[type-guard] isSavedActionSummary: not an object', value);
    return false;
  }

  const record = value as Record<string, unknown>;
  const valid = (
    typeof record.id === 'string' &&
    typeof record.title === 'string' &&
    typeof record.actionSummary === 'string' &&
    typeof record.createdAt === 'string'
  );
  if (!valid) {
    console.warn('[type-guard] isSavedActionSummary: field mismatch', {
      id: typeof record.id,
      title: typeof record.title,
      actionSummary: typeof record.actionSummary,
      createdAt: typeof record.createdAt,
    });
  }
  return valid;
}

export function isActionListResponse(value: unknown): value is ActionListResponse {
  if (typeof value !== 'object' || value === null) {
    console.warn('[type-guard] isActionListResponse: not an object', value);
    return false;
  }

  const record = value as Record<string, unknown>;
  const valid = (
    Array.isArray(record.actions) &&
    record.actions.every(isSavedActionSummary) &&
    typeof record.currentPage === 'number' &&
    typeof record.pageSize === 'number' &&
    typeof record.totalElements === 'number' &&
    typeof record.totalPages === 'number' &&
    typeof record.hasNext === 'boolean'
  );
  if (!valid) {
    console.warn('[type-guard] isActionListResponse: field mismatch', {
      actions: Array.isArray(record.actions) ? `Array(${record.actions.length})` : typeof record.actions,
      currentPage: typeof record.currentPage,
      pageSize: typeof record.pageSize,
      totalElements: typeof record.totalElements,
      totalPages: typeof record.totalPages,
      hasNext: typeof record.hasNext,
    });
  }
  return valid;
}

// --- Source history types ---

export type SourceSummary = Readonly<{
  id: string;
  title: string | null;
  sourceCategory: SourceCategory;
  sourceUrl: string | null;
  createdAt: string;
  actionCount: number;
}>;

export type SourceListResponse = Readonly<{
  sources: readonly SourceSummary[];
  currentPage: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}>;

export type SourceDetail = Readonly<{
  id: string;
  title: string | null;
  sourceCategory: SourceCategory;
  sourceUrl: string | null;
  createdAt: string;
  actions: readonly SavedActionSummary[];
}>;

function isSourceSummary(value: unknown): value is SourceSummary {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const record = value as Record<string, unknown>;
  return (
    typeof record.id === 'string' &&
    typeof record.sourceCategory === 'string' &&
    typeof record.createdAt === 'string' &&
    typeof record.actionCount === 'number'
  );
}

export function isSourceListResponse(value: unknown): value is SourceListResponse {
  if (typeof value !== 'object' || value === null) {
    console.warn('[type-guard] isSourceListResponse: not an object', value);
    return false;
  }

  const record = value as Record<string, unknown>;
  const valid = (
    Array.isArray(record.sources) &&
    record.sources.every(isSourceSummary) &&
    typeof record.currentPage === 'number' &&
    typeof record.pageSize === 'number' &&
    typeof record.totalElements === 'number' &&
    typeof record.totalPages === 'number' &&
    typeof record.hasNext === 'boolean'
  );
  if (!valid) {
    console.warn('[type-guard] isSourceListResponse: field mismatch', {
      sources: Array.isArray(record.sources) ? `Array(${record.sources.length})` : typeof record.sources,
      currentPage: typeof record.currentPage,
      pageSize: typeof record.pageSize,
      totalElements: typeof record.totalElements,
      totalPages: typeof record.totalPages,
      hasNext: typeof record.hasNext,
    });
  }
  return valid;
}

export function isSourceDetail(value: unknown): value is SourceDetail {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const record = value as Record<string, unknown>;
  return (
    typeof record.id === 'string' &&
    typeof record.sourceCategory === 'string' &&
    typeof record.createdAt === 'string' &&
    Array.isArray(record.actions) &&
    record.actions.every(isSavedActionSummary)
  );
}

export function isSavedActionDetail(value: unknown): value is SavedActionDetail {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const record = value as Record<string, unknown>;
  return (
    typeof record.id === 'string' &&
    typeof record.title === 'string' &&
    typeof record.actionSummary === 'string' &&
    typeof record.createdAt === 'string' &&
    Array.isArray(record.evidence) &&
    record.evidence.every(isEvidenceSnippet)
  );
}
