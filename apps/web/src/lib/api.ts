import {
  type ActionExtractionRequest,
  type ActionExtractionResponse,
  type ActionListResponse,
  type ActionStatus,
  type ActionUpdatePayload,
  type NoticeFeedResponse,
  type PersonalizedNoticeDetail,
  type SavedActionDetail,
  type SavedActionSummary,
  type SourceCategory,
  type SourceDetail,
  type SourceListResponse,
  parseActionExtractionResponse,
  parseActionListResponse,
  parseNoticeFeedResponse,
  parsePersonalizedNoticeDetail,
  parseSavedActionDetail,
  parseSourceDetail,
  parseSourceListResponse
} from './types';
import type { UserProfile } from './profile';

function parseApiError(body: string, fallback: string): string {
  try {
    const json: unknown = JSON.parse(body);
    if (typeof json === 'object' && json !== null) {
      const record = json as Record<string, unknown>;
      if (typeof record.message === 'string') {
        const details = Array.isArray(record.details)
          ? record.details.filter((item): item is string => typeof item === 'string' && item.length > 0)
          : [];
        if (details.length === 0) {
          return record.message;
        }
        return `${record.message} (${details.join('; ')})`;
      }
      if (typeof record.error === 'string') {
        const status = typeof record.status === 'number' ? ` (${record.status})` : '';
        return `서버 오류: ${record.error}${status}`;
      }
    }
  } catch { /* not JSON, use raw body */ }
  return body.length > 0 ? body : fallback;
}

export type SearchParams = Readonly<{
  q?: string;
  category?: SourceCategory;
  dueDateFrom?: string;
  dueDateTo?: string;
  status?: ActionStatus;
}>;

export async function requestActionExtraction(
  payload: ActionExtractionRequest
): Promise<ActionExtractionResponse> {
  const response = await fetch('/api/v1/extractions/actions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '액션 추출 요청에 실패했습니다'));
  }

  const json: unknown = await response.json();
  return parseActionExtractionResponse(json);
}

export async function requestPdfExtraction(
  file: File,
  sourceTitle: string | null
): Promise<ActionExtractionResponse> {
  const formData = new FormData();
  formData.append('file', file);
  if (sourceTitle !== null && sourceTitle.trim().length > 0) {
    formData.append('sourceTitle', sourceTitle);
  }

  const response = await fetch('/api/v1/extractions/pdf', {
    method: 'POST',
    body: formData
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, 'PDF 추출 요청에 실패했습니다'));
  }

  const json: unknown = await response.json();
  return parseActionExtractionResponse(json);
}

export async function requestScreenshotExtraction(
  file: File,
  sourceTitle: string | null
): Promise<ActionExtractionResponse> {
  const formData = new FormData();
  formData.append('file', file);
  if (sourceTitle !== null && sourceTitle.trim().length > 0) {
    formData.append('sourceTitle', sourceTitle);
  }

  const response = await fetch('/api/v1/extractions/screenshot', {
    method: 'POST',
    body: formData
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '스크린샷 추출 요청에 실패했습니다'));
  }

  const json: unknown = await response.json();
  return parseActionExtractionResponse(json);
}

export async function requestEmailExtraction(
  emailBody: string,
  subject: string | null
): Promise<ActionExtractionResponse> {
  const response = await fetch('/api/v1/extractions/email', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      emailBody,
      subject,
      senderAddress: null
    })
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '이메일 추출 요청에 실패했습니다'));
  }

  const json: unknown = await response.json();
  return parseActionExtractionResponse(json);
}

export async function fetchActionList(
  sort: 'recent' | 'due' = 'recent',
  page: number = 0,
  search?: SearchParams
): Promise<ActionListResponse> {
  const params = new URLSearchParams();
  params.set('sort', sort);
  params.set('page', String(page));
  params.set('size', '20');
  if (search?.q !== undefined && search.q.length > 0) params.set('q', search.q);
  if (search?.category !== undefined) params.set('category', search.category);
  if (search?.dueDateFrom !== undefined) params.set('dueDateFrom', search.dueDateFrom);
  if (search?.dueDateTo !== undefined) params.set('dueDateTo', search.dueDateTo);
  if (search?.status !== undefined) params.set('status', search.status);

  const response = await fetch(`/api/v1/actions?${params.toString()}`);

  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '액션 목록을 불러오지 못했습니다'));
  }

  const json: unknown = await response.json();
  return parseActionListResponse(json);
}

function appendProfileParams(params: URLSearchParams, profile: UserProfile): void {
  if (profile.department !== null && profile.department.length > 0) {
    params.set('department', profile.department);
  }
  if (profile.year !== null) {
    params.set('year', String(profile.year));
  }
  if (profile.status !== null && profile.status.length > 0) {
    params.set('status', profile.status);
  }
  for (const keyword of profile.interestKeywords ?? []) {
    if (keyword.trim().length > 0) params.append('keyword', keyword.trim());
  }
  for (const preferredBoard of profile.preferredBoards ?? []) {
    if (preferredBoard.trim().length > 0) params.append('preferredBoard', preferredBoard.trim());
  }
}

export async function fetchNoticeFeed(
  profile: UserProfile,
  page: number = 0,
  size: number = 20,
  board: string | null = null,
): Promise<NoticeFeedResponse> {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(size));
  appendProfileParams(params, profile);
  if (board !== null && board.length > 0) {
    params.set('board', board);
  }

  const response = await fetch(`/api/v1/notices/feed?${params.toString()}`);
  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '개인화 공지 피드를 불러오지 못했습니다'));
  }

  const json: unknown = await response.json();
  return parseNoticeFeedResponse(json);
}

export async function fetchNoticeDetail(
  id: string,
  profile: UserProfile,
): Promise<PersonalizedNoticeDetail> {
  const params = new URLSearchParams();
  appendProfileParams(params, profile);
  const qs = params.toString();

  const response = await fetch(`/api/v1/notices/${encodeURIComponent(id)}${qs.length > 0 ? `?${qs}` : ''}`);
  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '공지 상세 정보를 불러오지 못했습니다'));
  }

  const json: unknown = await response.json();
  return parsePersonalizedNoticeDetail(json);
}

export async function fetchAllMatchingActions(
  sort: 'recent' | 'due',
  search?: SearchParams
): Promise<readonly SavedActionSummary[]> {
  const all: SavedActionSummary[] = [];
  let page = 0;
  let hasMore = true;

  while (hasMore) {
    const result = await fetchActionList(sort, page, search);
    all.push(...result.actions);
    hasMore = result.hasNext;
    page++;
  }

  return all;
}

export async function deleteAction(id: string): Promise<void> {
  const response = await fetch(`/api/v1/actions/${encodeURIComponent(id)}`, {
    method: 'DELETE'
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '액션 삭제에 실패했습니다'));
  }
}

export async function fetchActionDetail(id: string): Promise<SavedActionDetail> {
  const response = await fetch(`/api/v1/actions/${encodeURIComponent(id)}`);

  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '액션 상세 정보를 불러오지 못했습니다'));
  }

  const json: unknown = await response.json();
  return parseSavedActionDetail(json);
}

export async function updateAction(
  id: string,
  updates: ActionUpdatePayload
): Promise<SavedActionDetail> {
  const response = await fetch(`/api/v1/actions/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(updates)
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '액션 수정에 실패했습니다'));
  }

  const json: unknown = await response.json();
  return parseSavedActionDetail(json);
}

export async function revertActionField(
  id: string,
  fieldName: string
): Promise<SavedActionDetail> {
  return updateAction(id, { revertFields: [fieldName] });
}

export async function fetchSourceList(page: number = 0): Promise<SourceListResponse> {
  const response = await fetch(`/api/v1/sources?page=${page}&size=20`);

  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '소스 목록을 불러오지 못했습니다'));
  }

  const json: unknown = await response.json();
  return parseSourceListResponse(json);
}

export async function fetchSourceDetail(id: string): Promise<SourceDetail> {
  const response = await fetch(`/api/v1/sources/${encodeURIComponent(id)}`);

  if (!response.ok) {
    const body = await response.text();
    throw new Error(parseApiError(body, '소스 상세 정보를 불러오지 못했습니다'));
  }

  const json: unknown = await response.json();
  return parseSourceDetail(json);
}
