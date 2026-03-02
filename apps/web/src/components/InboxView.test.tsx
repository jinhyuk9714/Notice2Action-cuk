import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { InboxView } from './InboxView';
import { makeActionSummary, makeActionDetail, makeActionListResponse } from '../test-helpers';

vi.mock('../lib/api', () => ({
  fetchActionList: vi.fn(),
  fetchActionDetail: vi.fn(),
  fetchAllMatchingActions: vi.fn(),
  deleteAction: vi.fn(),
}));

vi.mock('../lib/profile', () => ({
  loadProfile: vi.fn(() => ({ department: null, year: null, status: null })),
  saveProfile: vi.fn(),
  isProfileConfigured: vi.fn(() => false),
  STUDENT_STATUSES: ['재학생', '복학생', '휴학생', '졸업예정자', '신입생', '대학원생'],
  EMPTY_PROFILE: { department: null, year: null, status: null },
}));

vi.mock('../lib/router', () => ({
  replaceFilters: vi.fn(),
}));

vi.mock('../lib/csv', () => ({
  generateActionsCsv: vi.fn(() => 'mock-csv'),
  downloadCsv: vi.fn(),
}));

import { fetchActionList, fetchActionDetail, fetchAllMatchingActions, deleteAction } from '../lib/api';
import { isProfileConfigured } from '../lib/profile';
import { replaceFilters } from '../lib/router';
import { downloadCsv } from '../lib/csv';

const mockFetchActionList = vi.mocked(fetchActionList);
const mockFetchActionDetail = vi.mocked(fetchActionDetail);
const mockFetchAllMatchingActions = vi.mocked(fetchAllMatchingActions);
const mockDeleteAction = vi.mocked(deleteAction);

function renderInbox(overrides: Partial<Parameters<typeof InboxView>[0]> = {}) {
  const props = {
    initialActionId: null as string | null,
    initialFilters: {},
    onActionSelect: vi.fn(),
    ...overrides,
  };
  render(<InboxView {...props} />);
  return props;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
});

// --- Loading ---

describe('InboxView - loading', () => {
  it('shows skeleton cards while loading', () => {
    mockFetchActionList.mockReturnValue(new Promise(() => {}));
    const { container } = render(
      <InboxView initialActionId={null} initialFilters={{}} onActionSelect={vi.fn()} />,
    );
    expect(container.querySelectorAll('.skeleton-card')).toHaveLength(3);
  });

  it('hides skeleton after data loads', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    const { container } = render(
      <InboxView initialActionId={null} initialFilters={{}} onActionSelect={vi.fn()} />,
    );
    await waitFor(() => {
      expect(container.querySelectorAll('.skeleton-card')).toHaveLength(0);
    });
  });
});

// --- Error ---

describe('InboxView - error', () => {
  it('shows error banner when fetch rejects', async () => {
    mockFetchActionList.mockRejectedValue(new Error('네트워크 오류'));
    renderInbox();
    await waitFor(() => {
      expect(screen.getByText('네트워크 오류')).toBeInTheDocument();
    });
  });

  it('retries on "다시 시도" click', async () => {
    mockFetchActionList.mockRejectedValueOnce(new Error('실패'));
    renderInbox();
    await waitFor(() => {
      expect(screen.getByText('다시 시도')).toBeInTheDocument();
    });

    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    fireEvent.click(screen.getByText('다시 시도'));
    await waitFor(() => {
      expect(screen.getByText('1개')).toBeInTheDocument();
    });
  });
});

// --- Empty states ---

describe('InboxView - empty states', () => {
  it('shows empty inbox state when no actions and no active search', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([]));
    renderInbox();
    await waitFor(() => {
      expect(screen.getByText('저장된 액션이 없습니다')).toBeInTheDocument();
    });
  });

  it('shows search empty state when no results with category filter', async () => {
    // Start with empty results but an active category filter via initialFilters
    mockFetchActionList.mockResolvedValue(makeActionListResponse([]));
    renderInbox({ initialFilters: { category: 'PDF' } });
    await waitFor(() => {
      expect(screen.getByText('검색 결과가 없습니다')).toBeInTheDocument();
    });
  });

  it('shows placeholder when no action selected', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => {
      expect(screen.getByText('목록에서 액션을 선택하세요')).toBeInTheDocument();
    });
  });
});

// --- List rendering ---

describe('InboxView - list rendering', () => {
  it('renders action cards', async () => {
    const actions = [
      makeActionSummary({ id: 'a1', title: '장학금 신청' }),
      makeActionSummary({ id: 'a2', title: '수강 신청' }),
    ];
    mockFetchActionList.mockResolvedValue(makeActionListResponse(actions));
    renderInbox();
    await waitFor(() => {
      expect(screen.getByText('장학금 신청')).toBeInTheDocument();
      expect(screen.getByText('수강 신청')).toBeInTheDocument();
    });
  });

  it('shows action count', async () => {
    const actions = [
      makeActionSummary({ id: 'a1' }),
      makeActionSummary({ id: 'a2' }),
      makeActionSummary({ id: 'a3' }),
    ];
    mockFetchActionList.mockResolvedValue(makeActionListResponse(actions));
    renderInbox();
    await waitFor(() => {
      expect(screen.getByText('3개')).toBeInTheDocument();
    });
  });
});

// --- Sorting ---

describe('InboxView - sorting', () => {
  it('defaults to "마감순"', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => {
      const dueBtn = screen.getByText('마감순');
      expect(dueBtn).toHaveClass('sort-btn-active');
    });
  });

  it('switches to "최신순" when clicked', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });

    fireEvent.click(screen.getByText('최신순'));
    await waitFor(() => {
      expect(mockFetchActionList).toHaveBeenCalledWith('recent', expect.anything(), expect.anything());
    });
  });

  it('initializes sort from initialFilters', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox({ initialFilters: { sort: 'recent' } });
    await waitFor(() => {
      expect(screen.getByText('최신순')).toHaveClass('sort-btn-active');
    });
  });
});

// --- Search debounce ---

describe('InboxView - search', () => {
  it('debounces search input by 300ms', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });

    const callsBefore = mockFetchActionList.mock.calls.length;
    const searchInput = screen.getByPlaceholderText('제목 또는 요약 검색...');
    fireEvent.change(searchInput, { target: { value: '장학' } });

    expect(mockFetchActionList.mock.calls.length).toBe(callsBefore);

    await act(async () => { vi.advanceTimersByTime(300); });

    await waitFor(() => {
      const calls = mockFetchActionList.mock.calls;
      const lastCall = calls[calls.length - 1];
      expect(lastCall[2]).toEqual(expect.objectContaining({ q: '장학' }));
    });
  });

  it('initializes search from initialFilters.q', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox({ initialFilters: { q: '장학' } });
    await waitFor(() => {
      const searchInput = screen.getByPlaceholderText('제목 또는 요약 검색...') as HTMLInputElement;
      expect(searchInput.value).toBe('장학');
    });
  });
});

// --- Category filter ---

describe('InboxView - category', () => {
  it('defaults to empty (전체 카테고리)', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });
    const select = screen.getByDisplayValue('전체 카테고리') as HTMLSelectElement;
    expect(select.value).toBe('');
  });

  it('updates fetch on category change', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });

    const select = screen.getByDisplayValue('전체 카테고리');
    fireEvent.change(select, { target: { value: 'PDF' } });

    await waitFor(() => {
      const calls = mockFetchActionList.mock.calls;
      const lastCall = calls[calls.length - 1];
      expect(lastCall[2]).toEqual(expect.objectContaining({ category: 'PDF' }));
    });
  });
});

// --- Date range ---

describe('InboxView - date range', () => {
  it('shows date inputs when "직접 선택" is clicked', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });

    fireEvent.click(screen.getByText('직접 선택'));
    await waitFor(() => {
      expect(screen.getByLabelText('시작일')).toBeInTheDocument();
      expect(screen.getByLabelText('종료일')).toBeInTheDocument();
    });
  });

  it('hides date inputs for non-custom presets', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });

    expect(screen.queryByLabelText('시작일')).not.toBeInTheDocument();
  });
});

// --- Pagination ---

describe('InboxView - pagination', () => {
  it('shows "더 보기" when hasNext', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()], true));
    renderInbox();
    await waitFor(() => {
      expect(screen.getByText('더 보기')).toBeInTheDocument();
    });
  });

  it('does not show "더 보기" when no next', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()], false));
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });
    expect(screen.queryByText('더 보기')).not.toBeInTheDocument();
  });

  it('appends actions on load more', async () => {
    mockFetchActionList.mockResolvedValueOnce(
      makeActionListResponse([makeActionSummary({ id: 'a1', title: '첫번째' })], true),
    );
    renderInbox();
    await waitFor(() => { screen.getByText('첫번째'); });

    mockFetchActionList.mockResolvedValueOnce(
      makeActionListResponse([makeActionSummary({ id: 'a2', title: '두번째' })], false),
    );
    fireEvent.click(screen.getByText('더 보기'));
    await waitFor(() => {
      expect(screen.getByText('두번째')).toBeInTheDocument();
      expect(screen.getByText('첫번째')).toBeInTheDocument();
    });
  });

  it('shows retry on load-more failure', async () => {
    mockFetchActionList.mockResolvedValueOnce(
      makeActionListResponse([makeActionSummary()], true),
    );
    renderInbox();
    await waitFor(() => { screen.getByText('더 보기'); });

    mockFetchActionList.mockRejectedValueOnce(new Error('실패'));
    fireEvent.click(screen.getByText('더 보기'));
    await waitFor(() => {
      expect(screen.getByText('불러오기 실패 — 다시 시도')).toBeInTheDocument();
    });
  });
});

// --- Selection ---

describe('InboxView - selection', () => {
  it('fetches detail when action is clicked', async () => {
    const action = makeActionSummary({ id: 'a1', title: '장학금 신청' });
    mockFetchActionList.mockResolvedValue(makeActionListResponse([action]));
    mockFetchActionDetail.mockResolvedValue(makeActionDetail({ id: 'a1', title: '장학금 신청' }));
    const props = renderInbox();

    await waitFor(() => { screen.getByText('장학금 신청'); });
    fireEvent.click(screen.getByText('장학금 신청'));

    expect(props.onActionSelect).toHaveBeenCalledWith('a1');
    expect(mockFetchActionDetail).toHaveBeenCalledWith('a1');
  });

  it('shows back button when detail is displayed', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    mockFetchActionDetail.mockResolvedValue(makeActionDetail());
    renderInbox();

    await waitFor(() => { screen.getByText('장학금 신청'); });
    fireEvent.click(screen.getByText('장학금 신청'));

    await waitFor(() => {
      expect(screen.getByText(/목록으로/)).toBeInTheDocument();
    });
  });

  it('clears selection on back button click', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    mockFetchActionDetail.mockResolvedValue(makeActionDetail());
    const props = renderInbox();

    await waitFor(() => { screen.getByText('장학금 신청'); });
    fireEvent.click(screen.getByText('장학금 신청'));
    await waitFor(() => { screen.getByText(/목록으로/); });

    fireEvent.click(screen.getByText(/목록으로/));
    expect(props.onActionSelect).toHaveBeenCalledWith(null);
  });
});

// --- Delete ---

describe('InboxView - delete', () => {
  it('shows confirm dialog when delete is requested', async () => {
    mockFetchActionList.mockResolvedValue(
      makeActionListResponse([makeActionSummary({ id: 'a1', title: '삭제할 액션' })]),
    );
    renderInbox();
    await waitFor(() => { screen.getByText('삭제할 액션'); });

    fireEvent.click(screen.getByRole('button', { name: '액션 삭제' }));
    await waitFor(() => {
      expect(screen.getByText('액션 삭제', { selector: 'h3' })).toBeInTheDocument();
    });
  });

  it('removes action after confirmed delete', async () => {
    mockFetchActionList.mockResolvedValue(
      makeActionListResponse([makeActionSummary({ id: 'a1', title: '삭제 대상' })]),
    );
    mockDeleteAction.mockResolvedValue(undefined);
    renderInbox();
    await waitFor(() => { screen.getByText('삭제 대상'); });

    fireEvent.click(screen.getByRole('button', { name: '액션 삭제' }));
    await waitFor(() => { screen.getByRole('alertdialog'); });

    // Find the confirm button inside the dialog
    const dialog = screen.getByRole('alertdialog');
    const confirmBtn = dialog.querySelector('.confirm-dialog-danger') as HTMLElement;
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(screen.queryByText('삭제 대상')).not.toBeInTheDocument();
    });
  });

  it('shows toast after successful delete', async () => {
    mockFetchActionList.mockResolvedValue(
      makeActionListResponse([
        makeActionSummary({ id: 'a1', title: '삭제됨' }),
        makeActionSummary({ id: 'a2', title: '남아있음' }),
      ]),
    );
    mockDeleteAction.mockResolvedValue(undefined);
    renderInbox();
    await waitFor(() => { screen.getByText('삭제됨'); });

    const deleteBtns = screen.getAllByRole('button', { name: '액션 삭제' });
    fireEvent.click(deleteBtns[0]);
    await waitFor(() => { screen.getByRole('alertdialog'); });

    const dialog = screen.getByRole('alertdialog');
    const confirmBtn = dialog.querySelector('.confirm-dialog-danger') as HTMLElement;
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(/삭제 완료/);
    });
  });

  it('clears toast after 2500ms', async () => {
    mockFetchActionList.mockResolvedValue(
      makeActionListResponse([
        makeActionSummary({ id: 'a1', title: 'X' }),
        makeActionSummary({ id: 'a2', title: 'Y' }),
      ]),
    );
    mockDeleteAction.mockResolvedValue(undefined);
    renderInbox();
    await waitFor(() => { screen.getByText('X'); });

    const deleteBtns = screen.getAllByRole('button', { name: '액션 삭제' });
    fireEvent.click(deleteBtns[0]);
    await waitFor(() => { screen.getByRole('alertdialog'); });

    const dialog = screen.getByRole('alertdialog');
    const confirmBtn = dialog.querySelector('.confirm-dialog-danger') as HTMLElement;
    fireEvent.click(confirmBtn);

    await waitFor(() => { screen.getByRole('status'); });

    await act(async () => { vi.advanceTimersByTime(2500); });
    await waitFor(() => {
      expect(screen.queryByRole('status')).not.toBeInTheDocument();
    });
  });

  it('shows error when delete fails', async () => {
    mockFetchActionList.mockResolvedValue(
      makeActionListResponse([makeActionSummary({ id: 'a1', title: '실패' })]),
    );
    mockDeleteAction.mockRejectedValue(new Error('삭제 오류'));
    renderInbox();
    await waitFor(() => { screen.getByText('실패'); });

    fireEvent.click(screen.getByRole('button', { name: '액션 삭제' }));
    await waitFor(() => { screen.getByRole('alertdialog'); });

    const dialog = screen.getByRole('alertdialog');
    const confirmBtn = dialog.querySelector('.confirm-dialog-danger') as HTMLElement;
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(screen.getByText('삭제 오류')).toBeInTheDocument();
    });
  });
});

// --- CSV export ---

describe('InboxView - CSV export', () => {
  it('calls fetchAllMatchingActions and downloadCsv on export', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    mockFetchAllMatchingActions.mockResolvedValue([makeActionSummary()]);
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });

    fireEvent.click(screen.getByText('CSV 내보내기'));
    await waitFor(() => {
      expect(mockFetchAllMatchingActions).toHaveBeenCalled();
      expect(downloadCsv).toHaveBeenCalled();
    });
  });

  it('shows "내보내는 중..." during export', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    mockFetchAllMatchingActions.mockReturnValue(new Promise(() => {}));
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });

    fireEvent.click(screen.getByText('CSV 내보내기'));
    await waitFor(() => {
      expect(screen.getByText('내보내는 중...')).toBeInTheDocument();
    });
  });
});

// --- Profile & relevance ---

describe('InboxView - profile', () => {
  it('renders ProfileSettings', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => {
      expect(screen.getByText('내 프로필')).toBeInTheDocument();
    });
  });

  it('shows relevance checkbox when profile is configured', async () => {
    vi.mocked(isProfileConfigured).mockReturnValue(true);
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => {
      expect(screen.getByText('관련 항목만 보기')).toBeInTheDocument();
    });
  });

  it('does not show relevance checkbox when profile is not configured', async () => {
    vi.mocked(isProfileConfigured).mockReturnValue(false);
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });
    expect(screen.queryByText('관련 항목만 보기')).not.toBeInTheDocument();
  });
});

// --- URL sync ---

describe('InboxView - URL sync', () => {
  it('calls replaceFilters on initial render', async () => {
    mockFetchActionList.mockResolvedValue(makeActionListResponse([makeActionSummary()]));
    renderInbox();
    await waitFor(() => { screen.getByText('1개'); });
    expect(replaceFilters).toHaveBeenCalled();
  });

  it('calls onActionSelect when action is selected', async () => {
    mockFetchActionList.mockResolvedValue(
      makeActionListResponse([makeActionSummary({ id: 'a1', title: '클릭' })]),
    );
    mockFetchActionDetail.mockResolvedValue(makeActionDetail({ id: 'a1' }));
    const props = renderInbox();
    await waitFor(() => { screen.getByText('클릭'); });

    fireEvent.click(screen.getByText('클릭'));
    expect(props.onActionSelect).toHaveBeenCalledWith('a1');
  });
});
