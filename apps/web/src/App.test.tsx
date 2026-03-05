import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { useHashRoute } from './lib/useHashRoute';
import type { Route } from './lib/router';

const mockNavigate = vi.fn();

vi.mock('./lib/useHashRoute', () => ({
  useHashRoute: vi.fn(),
}));

vi.mock('./lib/useReminderCheck', () => ({
  useReminderCheck: vi.fn(),
}));

vi.mock('./components/PersonalizedFeedView', () => ({
  PersonalizedFeedView: () => <div data-testid="feed-view">FeedView</div>,
}));

vi.mock('./components/SavedNoticeView', () => ({
  SavedNoticeView: () => <div data-testid="saved-view">SavedView</div>,
}));

vi.mock('./components/ProfileSettings', () => ({
  ProfileSettings: () => <div data-testid="profile-view">ProfileView</div>,
}));

vi.mock('./components/SourceIngestionForm', () => ({
  SourceIngestionForm: () => <div data-testid="debug-extract-view">DebugExtract</div>,
}));

vi.mock('./components/InboxView', () => ({
  InboxView: () => <div data-testid="debug-inbox-view">DebugInbox</div>,
}));

vi.mock('./components/SourceListView', () => ({
  SourceListView: () => <div data-testid="debug-sources-view">DebugSources</div>,
}));

import { useReminderCheck } from './lib/useReminderCheck';

const mockUseHashRoute = vi.mocked(useHashRoute);
const mockUseReminderCheck = vi.mocked(useReminderCheck);

function setRoute(route: Route): void {
  mockUseHashRoute.mockReturnValue([route, mockNavigate] as const);
}

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseReminderCheck.mockImplementation(() => {});
    setRoute({ view: 'feed', noticeId: null });
  });

  it('renders personalized feed by default', () => {
    render(<App />);
    expect(screen.getByTestId('feed-view')).toBeInTheDocument();
    expect(screen.getByText('중요 공지')).toBeInTheDocument();
  });

  it('navigates to saved and profile from main navigation', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: '저장한 공지' }));
    fireEvent.click(screen.getByRole('button', { name: '프로필' }));

    expect(mockNavigate).toHaveBeenNthCalledWith(1, { view: 'saved', noticeId: null });
    expect(mockNavigate).toHaveBeenNthCalledWith(2, { view: 'profile' });
  });

  it('keeps debug extract route accessible without main navigation entry', () => {
    setRoute({ view: 'extract' });
    render(<App />);

    expect(screen.getByTestId('debug-extract-view')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '액션 추출' })).toBeNull();
  });
});
