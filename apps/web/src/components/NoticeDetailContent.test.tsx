import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { NoticeDetailContent } from './NoticeDetailContent';
import { makeNoticeDetail } from '../test-helpers';

describe('NoticeDetailContent', () => {
  it('renders representative action block first', () => {
    const detail = makeNoticeDetail({
      actionBlocks: [
        {
          title: 'Self-making Project Portfolio 참여 신청',
          summary: '할 일: Self-making Project Portfolio 참여 신청. 준비물: 신청서.',
          dueAtIso: null,
          dueAtLabel: null,
          requiredItems: ['신청서'],
          systemHint: null,
          evidence: [{ fieldName: 'summary', snippet: '교과목 개설 신청서 작성 후 전공사무실 제출', confidence: 0.92 }],
          confidenceScore: 0.92,
        },
        {
          title: '결과물을 대외공모전 제출',
          summary: '할 일: 결과물을 대외공모전 제출. 마감: 2026년 2월 2일(월).',
          dueAtIso: '2026-02-02T00:00:00+09:00',
          dueAtLabel: '2026년 2월 2일(월)',
          requiredItems: [],
          systemHint: null,
          evidence: [{ fieldName: 'summary', snippet: '결과물을 대외공모전에 제출하여 참가해야 함.', confidence: 0.89 }],
          confidenceScore: 0.89,
        },
      ],
    });

    render(
      <NoticeDetailContent
        detail={detail}
        isSaved={false}
        isHidden={false}
        onToggleSaved={vi.fn()}
      />,
    );

    const actionCards = screen.getAllByRole('article');
    expect(within(actionCards[0]!).getByText('Self-making Project Portfolio 참여 신청')).toBeInTheDocument();
    expect(within(actionCards[1]!).getByText('결과물을 대외공모전 제출')).toBeInTheDocument();
  });

  it('shows empty state for informational notice', () => {
    const detail = makeNoticeDetail({
      actionability: 'informational',
      actionBlocks: [],
    });

    render(
      <NoticeDetailContent
        detail={detail}
        isSaved={false}
        isHidden={false}
        onToggleSaved={vi.fn()}
      />,
    );

    expect(screen.getByText('행동 없음')).toBeInTheDocument();
  });

  it('shows empty state for image-only informational notice without evidence', () => {
    const detail = makeNoticeDetail({
      title: '[2~4학년] 2026학년도 1학기 부전공(2차) 신청/변경 안내',
      body: '본문이 이미지로만 제공된 공지입니다.',
      actionability: 'informational',
      actionBlocks: [],
    });

    render(
      <NoticeDetailContent
        detail={detail}
        isSaved={false}
        isHidden={false}
        onToggleSaved={vi.fn()}
      />,
    );

    expect(screen.getByText('행동 없음')).toBeInTheDocument();
    expect(screen.queryByText('근거 없음')).not.toBeInTheDocument();
  });

  it('renders expanded due label and contextual evidence snippets inside each block', () => {
    const detail = makeNoticeDetail({
      actionBlocks: [
        {
          title: '신입생 수강신청',
          summary: '할 일: 신입생 수강신청. 마감: 2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00 (주말 및 공휴일 제외).',
          dueAtIso: '2026-03-08T15:00:00+09:00',
          dueAtLabel: '2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00 (주말 및 공휴일 제외)',
          requiredItems: [],
          systemHint: null,
          evidence: [
            {
              fieldName: 'dueAtLabel',
              snippet: '수강신청 변경기간: 2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00 (주말 및 공휴일 제외)',
              confidence: 0.95,
            },
          ],
          confidenceScore: 0.87,
        },
      ],
    });

    render(
      <NoticeDetailContent
        detail={detail}
        isSaved={false}
        isHidden={false}
        onToggleSaved={vi.fn()}
      />,
    );

    const block = screen.getByRole('article');
    expect(within(block).getByText('마감: 2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00 (주말 및 공휴일 제외)')).toBeInTheDocument();
    expect(within(block).getByText('근거')).toBeInTheDocument();
    expect(within(block).getByText('수강신청 변경기간: 2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00 (주말 및 공휴일 제외)')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '근거 snippet' })).not.toBeInTheDocument();
  });

  it('collapses long body by default and expands on demand', () => {
    const longBody = Array.from({ length: 24 }, (_, index) => `줄 ${index + 1}`).join('\n');
    const detail = makeNoticeDetail({ body: longBody });

    render(
      <NoticeDetailContent
        detail={detail}
        isSaved={false}
        isHidden={false}
        onToggleSaved={vi.fn()}
      />,
    );

    const body = document.querySelector('.detail-body');
    expect(body).not.toBeNull();
    expect(body).toHaveTextContent('줄 18');
    expect(body).not.toHaveTextContent('줄 19');
    const expandButton = screen.getByRole('button', { name: '본문 더보기' });
    fireEvent.click(expandButton);
    expect(screen.getByRole('button', { name: '본문 접기' })).toBeInTheDocument();
    expect(body).toHaveTextContent('줄 24');
  });

  it('shows short body without collapse button', () => {
    const detail = makeNoticeDetail({ body: '짧은 원문\n둘째 줄' });

    render(
      <NoticeDetailContent
        detail={detail}
        isSaved={false}
        isHidden={false}
        onToggleSaved={vi.fn()}
      />,
    );

    const body = document.querySelector('.detail-body');
    expect(body).not.toBeNull();
    expect(body).toHaveTextContent('짧은 원문 둘째 줄');
    expect(screen.queryByRole('button', { name: '본문 더보기' })).not.toBeInTheDocument();
  });

  it('hides evidence section for blocks without evidence', () => {
    const detail = makeNoticeDetail({
      actionBlocks: [
        {
          title: '학생증 신청',
          summary: '할 일: 학생증 신청.',
          dueAtIso: null,
          dueAtLabel: null,
          requiredItems: [],
          systemHint: 'TRINITY',
          evidence: [],
          confidenceScore: 0.8,
        },
      ],
    });

    render(
      <NoticeDetailContent
        detail={detail}
        isSaved={false}
        isHidden={false}
        onToggleSaved={vi.fn()}
      />,
    );

    const block = screen.getByRole('article');
    expect(within(block).queryByText('근거')).not.toBeInTheDocument();
  });
});
