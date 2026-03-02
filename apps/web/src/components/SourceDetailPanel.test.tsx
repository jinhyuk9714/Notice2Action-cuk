import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { makeActionSummary, makeSourceDetail } from '../test-helpers';
import { SourceDetailPanel } from './SourceDetailPanel';

describe('SourceDetailPanel', () => {
  it('renders category label', () => {
    render(<SourceDetailPanel detail={makeSourceDetail({ sourceCategory: 'NOTICE' })} />);
    expect(screen.getByText('공지')).toBeInTheDocument();
  });

  it('renders title', () => {
    render(<SourceDetailPanel detail={makeSourceDetail({ title: '장학 안내' })} />);
    expect(screen.getByText('장학 안내')).toBeInTheDocument();
  });

  it('shows "제목 없음" when title is null', () => {
    render(<SourceDetailPanel detail={makeSourceDetail({ title: null })} />);
    expect(screen.getByText('제목 없음')).toBeInTheDocument();
  });

  it('renders date in Korean format', () => {
    render(<SourceDetailPanel detail={makeSourceDetail({ createdAt: '2026-06-15T14:00:00', actions: [] })} />);
    // toLocaleDateString with ko-KR includes year, month, day
    expect(screen.getByText(/2026년/)).toBeInTheDocument();
    expect(screen.getByText(/6월/)).toBeInTheDocument();
  });

  it('shows URL link when sourceUrl is not null', () => {
    render(
      <SourceDetailPanel
        detail={makeSourceDetail({ sourceUrl: 'https://example.com/notice' })}
      />,
    );
    const link = screen.getByText('https://example.com/notice');
    expect(link).toHaveAttribute('href', 'https://example.com/notice');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('hides URL section when sourceUrl is null', () => {
    render(<SourceDetailPanel detail={makeSourceDetail({ sourceUrl: null })} />);
    expect(screen.queryByText('URL')).toBeNull();
  });

  it('shows action list with titles and due labels', () => {
    const actions = [
      makeActionSummary({ id: 'a1', title: '신청하기', dueAtLabel: '3월 10일까지' }),
      makeActionSummary({ id: 'a2', title: '제출하기', dueAtLabel: '3월 20일까지' }),
    ];
    render(<SourceDetailPanel detail={makeSourceDetail({ actions })} />);

    expect(screen.getByText('신청하기')).toBeInTheDocument();
    expect(screen.getByText('3월 10일까지')).toBeInTheDocument();
    expect(screen.getByText('제출하기')).toBeInTheDocument();
    expect(screen.getByText('3월 20일까지')).toBeInTheDocument();
  });

  it('shows empty message when no actions', () => {
    render(<SourceDetailPanel detail={makeSourceDetail({ actions: [] })} />);
    expect(screen.getByText('이 소스에서 추출된 액션이 없습니다.')).toBeInTheDocument();
  });
});
