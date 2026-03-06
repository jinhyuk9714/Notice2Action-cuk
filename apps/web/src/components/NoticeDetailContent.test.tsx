import { render, screen, within } from '@testing-library/react';
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

  it('renders expanded due label and contextual evidence snippets', () => {
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

    expect(screen.getByText('마감: 2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00 (주말 및 공휴일 제외)')).toBeInTheDocument();
    expect(screen.getByText('수강신청 변경기간: 2026.03.03.(화) ~ 03.9.(월) 09:00 ~ 17:00 (주말 및 공휴일 제외)')).toBeInTheDocument();
  });
});
