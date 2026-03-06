import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SavedNoticeView } from './SavedNoticeView';
import { EMPTY_PROFILE, makeNoticeDetail } from '../test-helpers';
import { QUALITY_ACTION_NOTICE } from '../test-fixtures/noticeFeedQuality';

vi.mock('../lib/api', () => ({
  fetchNoticeDetail: vi.fn(),
}));

import { fetchNoticeDetail } from '../lib/api';

const mockFetchNoticeDetail = vi.mocked(fetchNoticeDetail);

describe('SavedNoticeView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchNoticeDetail.mockImplementation(async (id) => makeNoticeDetail({
      ...QUALITY_ACTION_NOTICE,
      id,
      title: id === '269011' ? QUALITY_ACTION_NOTICE.title : `공지 ${id}`,
    }));
  });

  it('shows hidden saved notice and allows unhide', async () => {
    const onUnhide = vi.fn();

    render(
      <SavedNoticeView
        profile={EMPTY_PROFILE}
        savedIds={['269011']}
        hiddenIds={['269011']}
        initialNoticeId={null}
        onNoticeSelect={() => {}}
        onToggleSaved={() => {}}
        onUnhide={onUnhide}
      />,
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: `${QUALITY_ACTION_NOTICE.title} 상세 보기` })).toBeInTheDocument();
    });

    expect(screen.getAllByText('숨김됨')).not.toHaveLength(0);
    fireEvent.click(screen.getAllByRole('button', { name: '숨김 해제' })[0]);
    expect(onUnhide).toHaveBeenCalledWith('269011');
  });
});
