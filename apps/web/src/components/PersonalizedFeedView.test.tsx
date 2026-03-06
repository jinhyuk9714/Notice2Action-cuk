import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
        onNoticeSelect={() => {}}
        onToggleSaved={() => {}}
        onHide={() => {}}
        onUnhide={() => {}}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내')).toBeInTheDocument();
    });

    expect(screen.getAllByText('행동 필요 공지')).not.toHaveLength(0);
    expect(screen.queryByText('프로필 미설정')).not.toBeInTheDocument();
    expect(screen.getByText('3. 25. (수) 17:00')).toBeInTheDocument();
    expect(screen.getAllByText('행동 필요')).not.toHaveLength(0);
  });

  it('renders at most three reasons per card', async () => {
    mockFetchNoticeFeed.mockResolvedValue(makeNoticeFeedResponse([
      {
        ...QUALITY_ACTION_NOTICE,
        importanceReasons: ['컴퓨터정보공학부 해당', '학생증 키워드', '행동 필요 공지', '7일 이내 마감'],
      },
    ]));

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        onNoticeSelect={() => {}}
        onToggleSaved={() => {}}
        onHide={() => {}}
        onUnhide={() => {}}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내')).toBeInTheDocument();
    });

    expect(screen.getByText('컴퓨터정보공학부 해당')).toBeInTheDocument();
    expect(screen.getByText('학생증 키워드')).toBeInTheDocument();
    expect(screen.getByText('행동 필요 공지')).toBeInTheDocument();
    expect(screen.queryByText('7일 이내 마감')).not.toBeInTheDocument();
  });

  it('renders exclusion reasons with the new wording', async () => {
    mockFetchNoticeFeed.mockResolvedValue(makeNoticeFeedResponse([
      {
        ...QUALITY_INFORMATIONAL_NOTICE,
        importanceReasons: ['다른 대상 공지', '최근 공지'],
      },
    ]));

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        onNoticeSelect={() => {}}
        onToggleSaved={() => {}}
        onHide={() => {}}
        onUnhide={() => {}}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 강의시간표 등 변경사항 안내(전공강좌) / 일별 업데이트(2026.03.05.) / 폐강 포함')).toBeInTheDocument();
    });

    expect(screen.getByText('다른 대상 공지')).toBeInTheDocument();
    expect(screen.queryByText('프로필 추가 확인 필요')).not.toBeInTheDocument();
  });

  it('loads detail when a notice is selected and shows informational empty state', async () => {
    mockFetchNoticeDetail.mockResolvedValue(QUALITY_INFORMATIONAL_DETAIL);

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={EMPTY_PREFS}
        initialNoticeId={null}
        onNoticeSelect={() => {}}
        onToggleSaved={() => {}}
        onHide={() => {}}
        onUnhide={() => {}}
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
        onNoticeSelect={() => {}}
        onToggleSaved={onToggleSaved}
        onHide={onHide}
        onUnhide={() => {}}
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
        onNoticeSelect={() => {}}
        onToggleSaved={() => {}}
        onHide={() => {}}
        onUnhide={() => {}}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('[학사지원팀] 2026-1학기 수강과목 취소 기간 안내')).toBeInTheDocument();
    });

    const saveButton = screen.getAllByRole('button', { name: '저장' })[0];
    expect(saveButton.parentElement).not.toBeNull();
    expect(saveButton.parentElement?.className).toContain('card-actions-row');
  });

  it('shows hidden notices in a recovery section and allows unhide', async () => {
    const onUnhide = vi.fn();

    render(
      <PersonalizedFeedView
        profile={EMPTY_PROFILE}
        preferences={{ savedIds: [], hiddenIds: ['269011'] }}
        initialNoticeId={null}
        onNoticeSelect={() => {}}
        onToggleSaved={() => {}}
        onHide={() => {}}
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
});
