import {
  type ActionExtractionRequest,
  type ActionExtractionResponse,
  type ActionListResponse,
  type SavedActionDetail,
  isActionExtractionResponse,
  isActionListResponse,
  isSavedActionDetail
} from './types';

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
    throw new Error(body || 'API request failed');
  }

  const json: unknown = await response.json();
  if (!isActionExtractionResponse(json)) {
    throw new Error('API response shape is invalid');
  }

  return json;
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
    throw new Error(body || 'PDF extraction request failed');
  }

  const json: unknown = await response.json();
  if (!isActionExtractionResponse(json)) {
    throw new Error('API response shape is invalid');
  }

  return json;
}

export async function fetchActionList(sort: 'recent' | 'due' = 'recent'): Promise<ActionListResponse> {
  const response = await fetch(`/api/v1/actions?sort=${encodeURIComponent(sort)}`);

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || 'Failed to fetch actions');
  }

  const json: unknown = await response.json();
  if (!isActionListResponse(json)) {
    throw new Error('Action list response shape is invalid');
  }

  return json;
}

export async function fetchActionDetail(id: string): Promise<SavedActionDetail> {
  const response = await fetch(`/api/v1/actions/${encodeURIComponent(id)}`);

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || 'Failed to fetch action detail');
  }

  const json: unknown = await response.json();
  if (!isSavedActionDetail(json)) {
    throw new Error('Action detail response shape is invalid');
  }

  return json;
}
