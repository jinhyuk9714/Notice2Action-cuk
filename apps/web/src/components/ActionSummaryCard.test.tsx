import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ActionSummaryCard } from './ActionSummaryCard';
import type { SavedActionSummary } from '../lib/types';
import type { RelevanceResult } from '../lib/relevance';

const BASE_SUMMARY: SavedActionSummary = {
  id: 'action-1',
  title: '장학금 신청',
  actionSummary: '교내 장학금 신청을 완료하세요.',
  dueAtIso: '2026-03-15T00:00:00',
  dueAtLabel: '3월 15일',
  eligibility: '재학생',
  sourceCategory: 'NOTICE',
  sourceTitle: '학사공지',
  confidenceScore: 0.85,
  createdAt: '2026-03-01T00:00:00Z',
};

const UNKNOWN_RELEVANCE: RelevanceResult = { level: 'unknown', reason: null };

describe('ActionSummaryCard', () => {
  it('renders title and summary', () => {
    render(
      <ActionSummaryCard
        action={BASE_SUMMARY}
        selected={false}
        onSelect={vi.fn()}
        onDelete={vi.fn()}
        isDeleting={false}
        relevance={UNKNOWN_RELEVANCE}
      />,
    );
    expect(screen.getByText('장학금 신청')).toBeInTheDocument();
    expect(screen.getByText('교내 장학금 신청을 완료하세요.')).toBeInTheDocument();
  });

  it('renders category badge', () => {
    render(
      <ActionSummaryCard
        action={BASE_SUMMARY}
        selected={false}
        onSelect={vi.fn()}
        onDelete={vi.fn()}
        isDeleting={false}
        relevance={UNKNOWN_RELEVANCE}
      />,
    );
    expect(screen.getByText('공지')).toBeInTheDocument();
  });

  it('calls onSelect when clicked', () => {
    const onSelect = vi.fn();
    render(
      <ActionSummaryCard
        action={BASE_SUMMARY}
        selected={false}
        onSelect={onSelect}
        onDelete={vi.fn()}
        isDeleting={false}
        relevance={UNKNOWN_RELEVANCE}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: /장학금 신청/i }));
    expect(onSelect).toHaveBeenCalledWith('action-1');
  });

  it('calls onSelect on Enter key', () => {
    const onSelect = vi.fn();
    render(
      <ActionSummaryCard
        action={BASE_SUMMARY}
        selected={false}
        onSelect={onSelect}
        onDelete={vi.fn()}
        isDeleting={false}
        relevance={UNKNOWN_RELEVANCE}
      />,
    );
    fireEvent.keyDown(screen.getByRole('button', { name: /장학금 신청/i }), { key: 'Enter' });
    expect(onSelect).toHaveBeenCalledWith('action-1');
  });

  it('calls onDelete when delete button clicked without triggering onSelect', () => {
    const onSelect = vi.fn();
    const onDelete = vi.fn();
    render(
      <ActionSummaryCard
        action={BASE_SUMMARY}
        selected={false}
        onSelect={onSelect}
        onDelete={onDelete}
        isDeleting={false}
        relevance={UNKNOWN_RELEVANCE}
      />,
    );
    fireEvent.click(screen.getByLabelText('액션 삭제'));
    expect(onDelete).toHaveBeenCalledWith('action-1');
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('shows 삭제 중... when isDeleting is true', () => {
    render(
      <ActionSummaryCard
        action={BASE_SUMMARY}
        selected={false}
        onSelect={vi.fn()}
        onDelete={vi.fn()}
        isDeleting={true}
        relevance={UNKNOWN_RELEVANCE}
      />,
    );
    expect(screen.getByText('삭제 중...')).toBeInTheDocument();
  });

  it('shows relevance badge when relevant', () => {
    render(
      <ActionSummaryCard
        action={BASE_SUMMARY}
        selected={false}
        onSelect={vi.fn()}
        onDelete={vi.fn()}
        isDeleting={false}
        relevance={{ level: 'relevant', reason: '재학생 매칭' }}
      />,
    );
    expect(screen.getByText('관련')).toBeInTheDocument();
  });
});
