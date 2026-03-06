import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ActionCard } from './ActionCard';
import type { ExtractedAction } from '../lib/types';

const BASE_ACTION: ExtractedAction = {
  id: 'test-1',
  sourceId: 'src-1',
  title: '공결 신청',
  actionSummary: 'TRINITY에서 공결 신청을 완료하세요.',
  dueAtIso: '2026-03-12T18:00:00',
  dueAtLabel: '3월 12일 18시',
  additionalDates: [],
  eligibility: '재학생',
  structuredEligibility: null,
  requiredItems: ['재학증명서'],
  systemHint: 'TRINITY',
  sourceCategory: 'NOTICE',
  evidence: [{ fieldName: 'dueAtLabel', snippet: '3월 12일 18시까지', confidence: 0.95 }],
  inferred: false,
  confidenceScore: 0.92,
  createdAt: '2026-03-01T12:00:00',
};

describe('ActionCard', () => {
  it('renders title and summary', () => {
    render(<ActionCard action={BASE_ACTION} />);
    expect(screen.getByText('공결 신청')).toBeInTheDocument();
    expect(screen.getByText('TRINITY에서 공결 신청을 완료하세요.')).toBeInTheDocument();
  });

  it('shows 미확인 for null optional fields', () => {
    const action: ExtractedAction = { ...BASE_ACTION, dueAtLabel: null, systemHint: null, eligibility: null };
    render(<ActionCard action={action} />);
    expect(screen.getAllByText('미확인')).toHaveLength(3);
  });

  it('shows confirmed badge when not inferred', () => {
    render(<ActionCard action={BASE_ACTION} />);
    expect(screen.getByText('확인됨')).toBeInTheDocument();
  });

  it('shows inferred badge when inferred', () => {
    render(<ActionCard action={{ ...BASE_ACTION, inferred: true }} />);
    expect(screen.getByText('추론됨')).toBeInTheDocument();
  });

  it('renders evidence snippets', () => {
    render(<ActionCard action={BASE_ACTION} />);
    expect(screen.getByText('3월 12일 18시까지')).toBeInTheDocument();
  });

  it('shows empty evidence message when no evidence', () => {
    render(<ActionCard action={{ ...BASE_ACTION, evidence: [] }} />);
    expect(screen.getByText('근거 snippet 없음')).toBeInTheDocument();
  });

  it('renders required items', () => {
    render(<ActionCard action={BASE_ACTION} />);
    expect(screen.getByText('재학증명서')).toBeInTheDocument();
  });
});
