import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PersonalizedFeedView } from './PersonalizedFeedView';
import { EMPTY_PROFILE, makeNoticeFeedResponse } from '../test-helpers';
import type { NoticePreferences } from '../lib/noticePrefs';
import {
  QUALITY_ACTION_NOTICE,
  QUALITY_INFORMATIONAL_DETAIL,
  QUALITY_INFORMATIONAL_NOTICE,
} from '../test-fixtures/noticeFeedQuality';

vi.mock('../lib/api', () => ({
  fetchNoticeDetail: vi.fn(),
  fetchNoticeFeed: vi.fn(),
}));

import { fetchNoticeDetail, fetchNoticeFeed } from '../lib/api';

const mockFetchNoticeFeed = vi.mocked(fetchNoticeFeed);
const mockFetchNoticeDetail = vi.mocked(fetchNoticeDetail);
const EMPTY_PREFS: NoticePreferences = { savedIds: [], hiddenIds: [] };
const noop = () => {};

describe('PersonalizedFeedView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchNoticeFeed.mockResolvedValue(makeNoticeFeedResponse([
      QUALITY_ACTION_NOTICE,
      QUALITY_INFORMATIONAL_NOTICE,
    ]));
    mockFetchNoticeDetail.mockResolvedValue(QUALITY_INFORMATIONAL_DETAIL);
  });

  it('renders personalized notice cards with reasons and due hint', async () => {
    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={noop}
        onToggleSaved={noop}
        onHide={noop}
        onUnhide={noop}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내')).toBeInTheDocument();
    });

    expect(screen.getAllByText('행동 필요')).not.toHaveLength(0);
    expect(screen.getAllByText('학사')).not.toHaveLength(0);
    expect(screen.queryByText('프로필 미설정')).not.toBeInTheDocument();
    expect(screen.getByText('3. 25. (수) 17:00')).toBeInTheDocument();
    expect(screen.getAllByText('행동 필요')).not.toHaveLength(0);
    expect(screen.getByRole('button', { name: '전체' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '학사' })).toBeInTheDocument();
  });

  it('hides board badge when board label is null', async () => {
    mockFetchNoticeFeed.mockResolvedValue(makeNoticeFeedResponse([
      {
        ...QUALITY_ACTION_NOTICE,
        boardLabel: null,
      },
    ]));

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={noop}
        onToggleSaved={noop}
        onHide={noop}
        onUnhide={noop}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내')).toBeInTheDocument();
    });

    expect(screen.queryByText('학사')).not.toBeInTheDocument();
  });

  it('renders at most three reasons per card', async () => {
    mockFetchNoticeFeed.mockResolvedValue(makeNoticeFeedResponse([
      {
        ...QUALITY_ACTION_NOTICE,
        importanceReasons: ['컴퓨터정보공학부 공지', '학생증 관련', '행동 필요', '7일 안에 마감'],
      },
    ]));

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={noop}
        onToggleSaved={noop}
        onHide={noop}
        onUnhide={noop}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내')).toBeInTheDocument();
    });

    expect(screen.getByText('컴퓨터정보공학부 공지')).toBeInTheDocument();
    expect(screen.getByText('학생증 관련')).toBeInTheDocument();
    expect(screen.getAllByText('행동 필요')).not.toHaveLength(0);
    expect(screen.queryByText('7일 안에 마감')).not.toBeInTheDocument();
  });

  it('renders exclusion reasons with the new wording', async () => {
    mockFetchNoticeFeed.mockResolvedValue(makeNoticeFeedResponse([
      {
        ...QUALITY_INFORMATIONAL_NOTICE,
        importanceReasons: ['최근 등록', '다른 대상 공지'],
      },
    ]));

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={noop}
        onToggleSaved={noop}
        onHide={noop}
        onUnhide={noop}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 강의시간표 등 변경사항 안내(전공강좌) / 일별 업데이트(2026.03.05.) / 폐강 포함')).toBeInTheDocument();
    });

    expect(screen.getByText('다른 대상 공지')).toBeInTheDocument();
    expect(screen.queryByText('프로필 추가 확인 필요')).not.toBeInTheDocument();
    const card = screen
      .getByText('[학사지원팀] 2026-1학기 강의시간표 등 변경사항 안내(전공강좌) / 일별 업데이트(2026.03.05.) / 폐강 포함')
      .closest('article');
    expect(card).not.toBeNull();
    const chipRow = (card as HTMLElement).querySelector('.chip-row');
    expect(chipRow).not.toBeNull();
    const badges = within(chipRow as HTMLElement).getAllByText(/등록|공지/);
    expect(badges.at(-1)).toHaveTextContent('다른 대상 공지');
  });

  it('renders preferred board reasons on cards when provided', async () => {
    mockFetchNoticeFeed.mockResolvedValue(makeNoticeFeedResponse([
      {
        ...QUALITY_ACTION_NOTICE,
        importanceReasons: ['컴퓨터정보공학부 공지', '선호 게시판', '행동 필요'],
      },
    ]));

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={noop}
        onToggleSaved={noop}
        onHide={noop}
        onUnhide={noop}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내')).toBeInTheDocument();
    });

    expect(screen.getByText('선호 게시판')).toBeInTheDocument();
  });

  it('loads detail when a notice is selected and shows informational empty state', async () => {
    mockFetchNoticeDetail.mockResolvedValue(QUALITY_INFORMATIONAL_DETAIL);

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={noop}
        onToggleSaved={noop}
        onHide={noop}
        onUnhide={noop}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 강의시간표 등 변경사항 안내(전공강좌) / 일별 업데이트(2026.03.05.) / 폐강 포함')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: '[학사지원팀] 2026-1학기 강의시간표 등 변경사항 안내(전공강좌) / 일별 업데이트(2026.03.05.) / 폐강 포함 상세 보기' }));

    await waitFor(() => {
      expect(mockFetchNoticeDetail).toHaveBeenCalledWith('268838', EMPTY_PROFILE);
    });
    expect(screen.getByText('행동 없음')).toBeInTheDocument();
  });

  it('calls save and hide handlers from card actions', async () => {
    const onToggleSaved = vi.fn();
    const onHide = vi.fn();

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={noop}
        onToggleSaved={onToggleSaved}
        onHide={onHide}
        onUnhide={noop}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내')).toBeInTheDocument();
    });

    fireEvent.click(screen.getAllByRole('button', { name: '저장' })[0]);
    fireEvent.click(screen.getAllByRole('button', { name: '숨김' })[0]);

    expect(onToggleSaved).toHaveBeenCalledWith('269011');
    expect(onHide).toHaveBeenCalledWith('269011');
  });

  it('renders card action buttons inside a dedicated action row', async () => {
    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={noop}
        onToggleSaved={noop}
        onHide={noop}
        onUnhide={noop}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내')).toBeInTheDocument();
    });

    const saveButton = screen.getAllByRole('button', { name: '저장' })[0];
    expect(saveButton.parentElement).not.toBeNull();
    expect(saveButton.parentElement?.className).toContain('card-actions-row');
  });

  it('renders split panel structure for independent desktop scrolling', async () => {
    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={noop}
        onToggleSaved={noop}
        onHide={noop}
        onUnhide={noop}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내')).toBeInTheDocument();
    });

    expect(screen.getByTestId('feed-panel-shell')).toBeInTheDocument();
    expect(screen.getByTestId('feed-panel-header')).toContainElement(screen.getByRole('button', { name: '전체' }));
    expect(screen.getByTestId('feed-panel-body')).toContainElement(
      screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내'),
    );
  });

  it('shows hidden notices in a recovery section and allows unhide', async () => {
    const onUnhide = vi.fn();

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={{ savedIds: [], hiddenIds: ['269011'] }}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={noop}
        onToggleSaved={noop}
        onHide={noop}
        onUnhide={onUnhide}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('숨긴 공지 1개')).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: '숨김 해제' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '숨김 해제' }));

    expect(onUnhide).toHaveBeenCalledWith('269011');
  });

  it('filters notices by selected board chip and scopes hidden notices to the same board', async () => {
    const onBoardSelect = vi.fn();
    mockFetchNoticeFeed
      .mockResolvedValueOnce(makeNoticeFeedResponse([
        { ...QUALITY_ACTION_NOTICE, id: 'notice-academic', boardLabel: '학사' },
        { ...QUALITY_INFORMATIONAL_NOTICE, id: 'notice-scholarship', boardLabel: '장학' },
      ]))
      .mockResolvedValueOnce(makeNoticeFeedResponse([
        { ...QUALITY_ACTION_NOTICE, id: 'notice-academic', boardLabel: '학사' },
      ]));

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={{ savedIds: [], hiddenIds: ['notice-academic', 'notice-scholarship'] }}
        initialNoticeId={null}
        initialBoard={null}
        onNoticeSelect={noop}
        onBoardSelect={onBoardSelect}
        onToggleSaved={noop}
        onHide={noop}
        onUnhide={noop}
      />,
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '장학' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: '학사' }));

    await waitFor(() => {
      expect(mockFetchNoticeFeed).toHaveBeenLastCalledWith(EMPTY_PROFILE, 0, 20, '학사');
    });

    expect(screen.getByText('숨긴 공지 1개')).toBeInTheDocument();
    expect(onBoardSelect).toHaveBeenCalledWith('학사');
  });
});
