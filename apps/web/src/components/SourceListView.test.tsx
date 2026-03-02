import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SourceListView } from './SourceListView';
import { makeSourceSummary, makeSourceDetail, makeSourceListResponse, makeActionSummary } from '../test-helpers';

vi.mock('../lib/api', () => ({
  fetchSourceList: vi.fn(),
  fetchSourceDetail: vi.fn(),
}));

import { fetchSourceList, fetchSourceDetail } from '../lib/api';

const mockFetchSourceList = vi.mocked(fetchSourceList);
const mockFetchSourceDetail = vi.mocked(fetchSourceDetail);

function renderView(initialSourceId: string | null = null) {
  const onSourceSelect = vi.fn();
  render(<SourceListView initialSourceId={initialSourceId} onSourceSelect={onSourceSelect} />);
  return { onSourceSelect };
}

beforeEach(() => {
  vi.clearAllMocks();
});

// --- Loading ---

describe('SourceListView - loading', () => {
  it('shows skeleton cards while loading', () => {
    mockFetchSourceList.mockReturnValue(new Promise(() => {})); // never resolves
    const { container } = render(
      <SourceListView initialSourceId={null} onSourceSelect={vi.fn()} />,
    );
    expect(container.querySelectorAll('.skeleton-card')).toHaveLength(3);
  });

  it('does not show skeleton after data loads', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()]));
    const { container } = render(
      <SourceListView initialSourceId={null} onSourceSelect={vi.fn()} />,
    );
    await waitFor(() => {
      expect(container.querySelectorAll('.skeleton-card')).toHaveLength(0);
    });
  });
});

// --- Error ---

describe('SourceListView - error', () => {
  it('shows error banner when fetch rejects', async () => {
    mockFetchSourceList.mockRejectedValue(new Error('네트워크 오류'));
    renderView();
    await waitFor(() => {
      expect(screen.getByText('네트워크 오류')).toBeInTheDocument();
    });
  });

  it('retries on "다시 시도" click', async () => {
    mockFetchSourceList.mockRejectedValueOnce(new Error('실패'));
    renderView();
    await waitFor(() => {
      expect(screen.getByText('다시 시도')).toBeInTheDocument();
    });

    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()]));
    fireEvent.click(screen.getByText('다시 시도'));
    await waitFor(() => {
      expect(screen.getByText('1개')).toBeInTheDocument();
    });
  });
});

// --- Empty ---

describe('SourceListView - empty', () => {
  it('shows empty state when no sources', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([]));
    renderView();
    await waitFor(() => {
      expect(screen.getByText('저장된 소스가 없습니다')).toBeInTheDocument();
    });
  });

  it('shows guidance text', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([]));
    renderView();
    await waitFor(() => {
      expect(screen.getByText(/액션 추출/)).toBeInTheDocument();
    });
  });
});

// --- Source list rendering ---

describe('SourceListView - list rendering', () => {
  it('renders SourceCard for each source', async () => {
    const sources = [
      makeSourceSummary({ id: 'src-1', title: '소스 A' }),
      makeSourceSummary({ id: 'src-2', title: '소스 B' }),
    ];
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse(sources));
    renderView();
    await waitFor(() => {
      expect(screen.getByText('소스 A')).toBeInTheDocument();
      expect(screen.getByText('소스 B')).toBeInTheDocument();
    });
  });

  it('shows source count in header', async () => {
    const sources = [
      makeSourceSummary({ id: 'src-1' }),
      makeSourceSummary({ id: 'src-2' }),
      makeSourceSummary({ id: 'src-3' }),
    ];
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse(sources));
    renderView();
    await waitFor(() => {
      expect(screen.getByText('3개')).toBeInTheDocument();
    });
  });
});

// --- Pagination ---

describe('SourceListView - pagination', () => {
  it('shows load more button when hasNext', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()], true));
    renderView();
    await waitFor(() => {
      expect(screen.getByText('더 보기')).toBeInTheDocument();
    });
  });

  it('does not show load more when no next', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()], false));
    renderView();
    await waitFor(() => {
      expect(screen.getByText('1개')).toBeInTheDocument();
    });
    expect(screen.queryByText('더 보기')).not.toBeInTheDocument();
  });

  it('appends sources on load more', async () => {
    mockFetchSourceList.mockResolvedValueOnce(
      makeSourceListResponse([makeSourceSummary({ id: 'src-1', title: '첫번째' })], true),
    );
    renderView();
    await waitFor(() => {
      expect(screen.getByText('첫번째')).toBeInTheDocument();
    });

    mockFetchSourceList.mockResolvedValueOnce(
      makeSourceListResponse([makeSourceSummary({ id: 'src-2', title: '두번째' })], false),
    );
    fireEvent.click(screen.getByText('더 보기'));
    await waitFor(() => {
      expect(screen.getByText('두번째')).toBeInTheDocument();
      expect(screen.getByText('첫번째')).toBeInTheDocument();
    });
  });
});

// --- Selection ---

describe('SourceListView - selection', () => {
  it('shows placeholder when no source selected', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()]));
    renderView();
    await waitFor(() => {
      expect(screen.getByText('목록에서 소스를 선택하세요')).toBeInTheDocument();
    });
  });

  it('fetches detail when source is clicked', async () => {
    const src = makeSourceSummary({ id: 'src-1', title: '소스 A' });
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([src]));
    mockFetchSourceDetail.mockResolvedValue(makeSourceDetail({ id: 'src-1', title: '소스 A' }));
    const { onSourceSelect } = renderView();

    await waitFor(() => {
      expect(screen.getByText('소스 A')).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText('소스 A'));
    expect(onSourceSelect).toHaveBeenCalledWith('src-1');
    expect(mockFetchSourceDetail).toHaveBeenCalledWith('src-1');
  });

  it('shows back button when detail is displayed', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()]));
    mockFetchSourceDetail.mockResolvedValue(makeSourceDetail());
    renderView();

    await waitFor(() => {
      expect(screen.getByText('장학 안내')).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText('장학 안내'));
    await waitFor(() => {
      expect(screen.getByText(/목록으로/)).toBeInTheDocument();
    });
  });

  it('clears selection on back button click', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()]));
    mockFetchSourceDetail.mockResolvedValue(makeSourceDetail());
    const { onSourceSelect } = renderView();

    await waitFor(() => {
      expect(screen.getByText('장학 안내')).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText('장학 안내'));
    await waitFor(() => {
      expect(screen.getByText(/목록으로/)).toBeInTheDocument();
    });
    fireEvent.click(screen.getByText(/목록으로/));
    expect(onSourceSelect).toHaveBeenCalledWith(null);
  });
});

// --- Detail panel ---

describe('SourceListView - detail panel', () => {
  it('shows source title and category in detail', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()]));
    mockFetchSourceDetail.mockResolvedValue(makeSourceDetail({ title: '장학 안내', sourceCategory: 'NOTICE' }));
    renderView();

    await waitFor(() => { screen.getByText('장학 안내'); });
    fireEvent.click(screen.getByText('장학 안내'));
    await waitFor(() => {
      expect(screen.getByText('공지')).toBeInTheDocument(); // categoryLabel for NOTICE
    });
  });

  it('shows "제목 없음" when title is null', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary({ title: null })]));
    mockFetchSourceDetail.mockResolvedValue(makeSourceDetail({ title: null }));
    renderView();

    await waitFor(() => { expect(screen.queryAllByText(/소스/).length).toBeGreaterThan(0); });
    // Click the source card — it renders via SourceCard which shows title ?? something
    const cards = document.querySelectorAll('.source-card');
    if (cards.length > 0) fireEvent.click(cards[0] as HTMLElement);
    await waitFor(() => {
      expect(screen.getByText('제목 없음')).toBeInTheDocument();
    });
  });

  it('shows action count in detail', async () => {
    const actions = [makeActionSummary({ id: 'a1' }), makeActionSummary({ id: 'a2' })];
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()]));
    mockFetchSourceDetail.mockResolvedValue(makeSourceDetail({ actions }));
    renderView();

    await waitFor(() => { screen.getByText('장학 안내'); });
    fireEvent.click(screen.getByText('장학 안내'));
    await waitFor(() => {
      expect(screen.getByText('2개', { exact: false })).toBeInTheDocument();
    });
  });

  it('shows empty actions message when no actions', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()]));
    mockFetchSourceDetail.mockResolvedValue(makeSourceDetail({ actions: [] }));
    renderView();

    await waitFor(() => { screen.getByText('장학 안내'); });
    fireEvent.click(screen.getByText('장학 안내'));
    await waitFor(() => {
      expect(screen.getByText('이 소스에서 추출된 액션이 없습니다.')).toBeInTheDocument();
    });
  });

  it('shows source URL as link when present', async () => {
    mockFetchSourceList.mockResolvedValue(makeSourceListResponse([makeSourceSummary()]));
    mockFetchSourceDetail.mockResolvedValue(
      makeSourceDetail({ sourceUrl: 'https://example.com/notice' }),
    );
    renderView();

    await waitFor(() => { screen.getByText('장학 안내'); });
    fireEvent.click(screen.getByText('장학 안내'));
    await waitFor(() => {
      const link = screen.getByText('https://example.com/notice');
      expect(link.closest('a')).toHaveAttribute('href', 'https://example.com/notice');
    });
  });
});
