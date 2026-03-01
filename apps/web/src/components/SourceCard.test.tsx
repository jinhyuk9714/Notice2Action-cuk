import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SourceCard } from './SourceCard';
import type { SourceSummary } from '../lib/types';

const BASE_SOURCE: SourceSummary = {
  id: 'src-1',
  title: '공결 신청 안내',
  sourceCategory: 'NOTICE',
  sourceUrl: null,
  createdAt: '2026-03-01T00:00:00Z',
  actionCount: 2,
};

describe('SourceCard', () => {
  it('renders title and category', () => {
    render(<SourceCard source={BASE_SOURCE} selected={false} onSelect={vi.fn()} />);
    expect(screen.getByText('공결 신청 안내')).toBeInTheDocument();
    expect(screen.getByText('공지')).toBeInTheDocument();
  });

  it('renders action count', () => {
    render(<SourceCard source={BASE_SOURCE} selected={false} onSelect={vi.fn()} />);
    expect(screen.getByText('2개 액션')).toBeInTheDocument();
  });

  it('shows 제목 없음 when title is null', () => {
    const source: SourceSummary = { ...BASE_SOURCE, title: null };
    render(<SourceCard source={source} selected={false} onSelect={vi.fn()} />);
    expect(screen.getByText('제목 없음')).toBeInTheDocument();
  });

  it('calls onSelect on click', () => {
    const onSelect = vi.fn();
    render(<SourceCard source={BASE_SOURCE} selected={false} onSelect={onSelect} />);
    fireEvent.click(screen.getByRole('button'));
    expect(onSelect).toHaveBeenCalledWith('src-1');
  });

  it('calls onSelect on Enter key', () => {
    const onSelect = vi.fn();
    render(<SourceCard source={BASE_SOURCE} selected={false} onSelect={onSelect} />);
    fireEvent.keyDown(screen.getByRole('button'), { key: 'Enter' });
    expect(onSelect).toHaveBeenCalledWith('src-1');
  });

  it('applies selected class when selected', () => {
    render(<SourceCard source={BASE_SOURCE} selected={true} onSelect={vi.fn()} />);
    expect(screen.getByRole('button')).toHaveClass('source-card-selected');
  });
});
