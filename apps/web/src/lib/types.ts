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
  return Array.isArray(record.actions) && record.actions.every(isExtractedAction);
}

// --- Inbox types ---

export type SavedActionSummary = Readonly<{
  id: string;
  title: string;
  actionSummary: string;
  dueAtIso: string | null;
  dueAtLabel: string | null;
  sourceCategory: SourceCategory | null;
  sourceTitle: string | null;
  createdAt: string;
}>;

export type ActionListResponse = Readonly<{
  actions: readonly SavedActionSummary[];
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
  createdAt: string;
  source: SourceInfo | null;
  evidence: readonly EvidenceSnippet[];
}>;

function isSavedActionSummary(value: unknown): value is SavedActionSummary {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const record = value as Record<string, unknown>;
  return (
    typeof record.id === 'string' &&
    typeof record.title === 'string' &&
    typeof record.actionSummary === 'string' &&
    typeof record.createdAt === 'string'
  );
}

export function isActionListResponse(value: unknown): value is ActionListResponse {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const record = value as Record<string, unknown>;
  return Array.isArray(record.actions) && record.actions.every(isSavedActionSummary);
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
