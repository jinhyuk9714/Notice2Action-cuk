import {
  type ActionExtractionRequest,
  type ActionExtractionResponse,
  type ActionListResponse,
  type SavedActionDetail,
  type SourceDetail,
  type SourceListResponse,
  isActionExtractionResponse,
  isActionListResponse,
  isSavedActionDetail,
  isSourceDetail,
  isSourceListResponse
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
    throw new Error(body || 'Screenshot extraction request failed');
  }

  const json: unknown = await response.json();
  if (!isActionExtractionResponse(json)) {
    throw new Error('API response shape is invalid');
  }

  return json;
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
    throw new Error(body || 'Email extraction request failed');
  }

  const json: unknown = await response.json();
  if (!isActionExtractionResponse(json)) {
    throw new Error('API response shape is invalid');
  }

  return json;
}

export async function fetchActionList(sort: 'recent' | 'due' = 'recent', page: number = 0): Promise<ActionListResponse> {
  const response = await fetch(`/api/v1/actions?sort=${encodeURIComponent(sort)}&page=${page}&size=20`);

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

export async function deleteAction(id: string): Promise<void> {
  const response = await fetch(`/api/v1/actions/${encodeURIComponent(id)}`, {
    method: 'DELETE'
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || '액션 삭제에 실패했습니다');
  }
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

export async function fetchSourceList(page: number = 0): Promise<SourceListResponse> {
  const response = await fetch(`/api/v1/sources?page=${page}&size=20`);

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || '소스 목록을 불러오지 못했습니다');
  }

  const json: unknown = await response.json();
  if (!isSourceListResponse(json)) {
    throw new Error('Source list response shape is invalid');
  }

  return json;
}

export async function fetchSourceDetail(id: string): Promise<SourceDetail> {
  const response = await fetch(`/api/v1/sources/${encodeURIComponent(id)}`);

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || '소스 상세 정보를 불러오지 못했습니다');
  }

  const json: unknown = await response.json();
  if (!isSourceDetail(json)) {
    throw new Error('Source detail response shape is invalid');
  }

  return json;
}
