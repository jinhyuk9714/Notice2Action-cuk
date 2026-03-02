import { z } from 'zod';

// --- Schemas ---

export const SourceCategorySchema = z.enum(['NOTICE', 'SYLLABUS', 'EMAIL', 'PDF', 'SCREENSHOT']);
export type SourceCategory = z.infer<typeof SourceCategorySchema>;

export const EvidenceSnippetSchema = z.object({
  fieldName: z.string(),
  snippet: z.string(),
  confidence: z.number(),
});
export type EvidenceSnippet = z.infer<typeof EvidenceSnippetSchema>;

export const ExtractedActionSchema = z.object({
  id: z.string().nullable(),
  sourceId: z.string().nullable(),
  title: z.string(),
  actionSummary: z.string(),
  dueAtIso: z.string().nullable(),
  dueAtLabel: z.string().nullable(),
  eligibility: z.string().nullable(),
  requiredItems: z.array(z.string()),
  systemHint: z.string().nullable(),
  sourceCategory: SourceCategorySchema,
  evidence: z.array(EvidenceSnippetSchema),
  inferred: z.boolean(),
  confidenceScore: z.number(),
  createdAt: z.string().nullable(),
});
export type ExtractedAction = z.infer<typeof ExtractedActionSchema>;

export const ActionExtractionResponseSchema = z.object({
  actions: z.array(ExtractedActionSchema),
  duplicate: z.boolean(),
});
export type ActionExtractionResponse = z.infer<typeof ActionExtractionResponseSchema>;

// --- Send-only types (no schema needed) ---

export type ActionExtractionRequest = Readonly<{
  sourceText: string;
  sourceUrl: string | null;
  sourceTitle: string | null;
  sourceCategory: SourceCategory;
  focusProfile: readonly string[];
}>;

export type ActionUpdatePayload = Readonly<{
  title?: string;
  actionSummary?: string;
  dueAtIso?: string;
  dueAtLabel?: string;
  eligibility?: string;
  requiredItems?: readonly string[];
  systemHint?: string;
  revertFields?: readonly string[];
}>;

// --- Inbox types ---

const PaginationSchema = z.object({
  currentPage: z.number(),
  pageSize: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
  hasNext: z.boolean(),
});

export const SavedActionSummarySchema = z.object({
  id: z.string(),
  title: z.string(),
  actionSummary: z.string(),
  dueAtIso: z.string().nullable(),
  dueAtLabel: z.string().nullable(),
  eligibility: z.string().nullable(),
  sourceCategory: SourceCategorySchema.nullable(),
  sourceTitle: z.string().nullable(),
  confidenceScore: z.number(),
  createdAt: z.string(),
});
export type SavedActionSummary = z.infer<typeof SavedActionSummarySchema>;

export const ActionListResponseSchema = PaginationSchema.extend({
  actions: z.array(SavedActionSummarySchema),
});
export type ActionListResponse = z.infer<typeof ActionListResponseSchema>;

export const SourceInfoSchema = z.object({
  id: z.string(),
  title: z.string().nullable(),
  sourceCategory: SourceCategorySchema,
  createdAt: z.string(),
});
export type SourceInfo = z.infer<typeof SourceInfoSchema>;

export const FieldOverrideInfoSchema = z.object({
  fieldName: z.string(),
  machineValue: z.string().nullable(),
});
export type FieldOverrideInfo = z.infer<typeof FieldOverrideInfoSchema>;

export const SavedActionDetailSchema = z.object({
  id: z.string(),
  title: z.string(),
  actionSummary: z.string(),
  dueAtIso: z.string().nullable(),
  dueAtLabel: z.string().nullable(),
  eligibility: z.string().nullable(),
  requiredItems: z.array(z.string()),
  systemHint: z.string().nullable(),
  inferred: z.boolean(),
  confidenceScore: z.number(),
  createdAt: z.string(),
  source: SourceInfoSchema.nullable(),
  evidence: z.array(EvidenceSnippetSchema),
  overrides: z.array(FieldOverrideInfoSchema).default([]),
});
export type SavedActionDetail = z.infer<typeof SavedActionDetailSchema>;

// --- Source history types ---

export const SourceSummarySchema = z.object({
  id: z.string(),
  title: z.string().nullable(),
  sourceCategory: SourceCategorySchema,
  sourceUrl: z.string().nullable(),
  createdAt: z.string(),
  actionCount: z.number(),
});
export type SourceSummary = z.infer<typeof SourceSummarySchema>;

export const SourceListResponseSchema = PaginationSchema.extend({
  sources: z.array(SourceSummarySchema),
});
export type SourceListResponse = z.infer<typeof SourceListResponseSchema>;

export const SourceDetailSchema = z.object({
  id: z.string(),
  title: z.string().nullable(),
  sourceCategory: SourceCategorySchema,
  sourceUrl: z.string().nullable(),
  createdAt: z.string(),
  actions: z.array(SavedActionSummarySchema),
});
export type SourceDetail = z.infer<typeof SourceDetailSchema>;

// --- Parse helpers ---

function safeParse<T>(schema: z.ZodType<T>, value: unknown, label: string): T {
  const result = schema.safeParse(value);
  if (result.success) {
    return result.data;
  }
  console.warn(`[zod] ${label} parse failed:`, result.error.issues);
  throw new Error(`${label} response shape is invalid`);
}

export function parseActionExtractionResponse(value: unknown): ActionExtractionResponse {
  return safeParse(ActionExtractionResponseSchema, value, 'ActionExtractionResponse');
}

export function parseActionListResponse(value: unknown): ActionListResponse {
  return safeParse(ActionListResponseSchema, value, 'ActionListResponse');
}

export function parseSavedActionDetail(value: unknown): SavedActionDetail {
  return safeParse(SavedActionDetailSchema, value, 'SavedActionDetail');
}

export function parseSourceListResponse(value: unknown): SourceListResponse {
  return safeParse(SourceListResponseSchema, value, 'SourceListResponse');
}

export function parseSourceDetail(value: unknown): SourceDetail {
  return safeParse(SourceDetailSchema, value, 'SourceDetail');
}
