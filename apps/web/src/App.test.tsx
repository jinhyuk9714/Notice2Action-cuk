import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import type { ActionExtractionRequest, ActionExtractionResponse } from './lib/types';
import { makeActionExtractionResponse, makeExtractedAction } from './test-helpers';

// --- Mocks ---

const mockNavigate = vi.fn();

vi.mock('./lib/useHashRoute', () => ({
  useHashRoute: vi.fn(),
}));

vi.mock('./lib/useReminderCheck', () => ({
  useReminderCheck: vi.fn(),
}));

vi.mock('./lib/api', () => ({
  requestActionExtraction: vi.fn(),
  requestEmailExtraction: vi.fn(),
  requestPdfExtraction: vi.fn(),
  requestScreenshotExtraction: vi.fn(),
}));

// Mock child components that have their own dedicated test suites
vi.mock('./components/SourceIngestionForm', () => ({
  SourceIngestionForm: (props: {
    onSubmit: (p: ActionExtractionRequest) => Promise<void>;
    onFileSubmit: (f: File, t: string | null) => Promise<void>;
    onEmailSubmit: (b: string, s: string | null) => Promise<void>;
    isSubmitting: boolean;
  }) => (
    <div data-testid="ingestion-form">
      {props.isSubmitting ? <span data-testid="submitting-indicator">submitting</span> : null}
      <button
        data-testid="submit-text"
        onClick={() => {
          void props.onSubmit({
            sourceText: '테스트 텍스트',
            sourceUrl: null,
            sourceTitle: null,
            sourceCategory: 'NOTICE',
            focusProfile: [],
          });
        }}
      >
        텍스트 추출
      </button>
      <button
        data-testid="submit-email"
        onClick={() => { void props.onEmailSubmit('이메일 본문', '이메일 제목'); }}
      >
        이메일 추출
      </button>
      <button
        data-testid="submit-pdf"
        onClick={() => { void props.onFileSubmit(new File(['pdf'], 'test.pdf'), null); }}
      >
        PDF 추출
      </button>
      <button
        data-testid="submit-image"
        onClick={() => { void props.onFileSubmit(new File(['img'], 'test.png'), null); }}
      >
        이미지 추출
      </button>
    </div>
  ),
}));

vi.mock('./components/InboxView', () => ({
  InboxView: () => <div data-testid="inbox-view">InboxView</div>,
}));

vi.mock('./components/SourceListView', () => ({
  SourceListView: () => <div data-testid="source-list-view">SourceListView</div>,
}));

import { useHashRoute } from './lib/useHashRoute';
import {
  requestActionExtraction,
  requestEmailExtraction,
  requestPdfExtraction,
  requestScreenshotExtraction,
} from './lib/api';
import type { Route } from './lib/router';

const mockUseHashRoute = vi.mocked(useHashRoute);
const mockRequestText = vi.mocked(requestActionExtraction);
const mockRequestEmail = vi.mocked(requestEmailExtraction);
const mockRequestPdf = vi.mocked(requestPdfExtraction);
const mockRequestScreenshot = vi.mocked(requestScreenshotExtraction);

function setRoute(route: Route): void {
  mockUseHashRoute.mockReturnValue([route, mockNavigate] as const);
}

beforeEach(() => {
  vi.clearAllMocks();
  setRoute({ view: 'extract' });
});

afterEach(() => {
  vi.useRealTimers();
});

describe('App', () => {
  // --- Tab navigation ---

  describe('tab navigation', () => {
    it('renders extract view by default', () => {
      render(<App />);

      expect(screen.getByTestId('ingestion-form')).toBeInTheDocument();
      expect(screen.getByText('추출 결과')).toBeInTheDocument();
    });

    it('navigates to inbox on tab click', () => {
      render(<App />);

      fireEvent.click(screen.getByText('액션 인박스'));

      expect(mockNavigate).toHaveBeenCalledWith(
        expect.objectContaining({ view: 'inbox' }),
      );
    });

    it('navigates to sources on tab click', () => {
      render(<App />);

      fireEvent.click(screen.getByText('소스 히스토리'));

      expect(mockNavigate).toHaveBeenCalledWith(
        expect.objectContaining({ view: 'sources' }),
      );
    });
  });

  // --- Lazy mount ---

  describe('lazy mount', () => {
    it('does not mount inbox view until tab is visited', () => {
      render(<App />);

      expect(screen.queryByTestId('inbox-view')).toBeNull();
    });

    it('keeps previously mounted views alive when switching tabs', () => {
      const { rerender } = render(<App />);

      // Visit inbox
      setRoute({ view: 'inbox', actionId: null, filters: {} });
      rerender(<App />);
      expect(screen.getByTestId('inbox-view')).toBeInTheDocument();

      // Switch back to extract — inbox should still be in DOM (hidden)
      setRoute({ view: 'extract' });
      rerender(<App />);
      expect(screen.getByTestId('inbox-view')).toBeInTheDocument();
      expect(screen.getByTestId('ingestion-form')).toBeInTheDocument();
    });
  });

  // --- Text extraction ---

  describe('text extraction', () => {
    it('shows extracted actions after text submission', async () => {
      const response = makeActionExtractionResponse([
        makeExtractedAction({ title: '장학금 신청' }),
      ]);
      mockRequestText.mockResolvedValue(response);

      render(<App />);
      fireEvent.click(screen.getByTestId('submit-text'));

      await waitFor(() => {
        expect(screen.getByText('장학금 신청')).toBeInTheDocument();
      });
    });

    it('shows error and retry button on extraction failure', async () => {
      mockRequestText.mockRejectedValue(new Error('추출 실패'));

      render(<App />);
      fireEvent.click(screen.getByTestId('submit-text'));

      await waitFor(() => {
        expect(screen.getByText('추출 실패')).toBeInTheDocument();
        expect(screen.getByText('다시 시도')).toBeInTheDocument();
      });
    });
  });

  // --- Email extraction ---

  describe('email extraction', () => {
    it('calls requestEmailExtraction on email submit', async () => {
      mockRequestEmail.mockResolvedValue(makeActionExtractionResponse());

      render(<App />);
      fireEvent.click(screen.getByTestId('submit-email'));

      await waitFor(() => {
        expect(mockRequestEmail).toHaveBeenCalledWith('이메일 본문', '이메일 제목');
      });
    });
  });

  // --- File extraction ---

  describe('file extraction', () => {
    it('calls requestPdfExtraction for PDF files', async () => {
      mockRequestPdf.mockResolvedValue(makeActionExtractionResponse());

      render(<App />);
      fireEvent.click(screen.getByTestId('submit-pdf'));

      await waitFor(() => {
        expect(mockRequestPdf).toHaveBeenCalled();
      });
    });

    it('calls requestScreenshotExtraction for image files', async () => {
      mockRequestScreenshot.mockResolvedValue(makeActionExtractionResponse());

      render(<App />);
      fireEvent.click(screen.getByTestId('submit-image'));

      await waitFor(() => {
        expect(mockRequestScreenshot).toHaveBeenCalled();
      });
    });
  });

  // --- Retry ---

  describe('retry', () => {
    it('retries last submission on retry click', async () => {
      mockRequestText
        .mockRejectedValueOnce(new Error('실패'))
        .mockResolvedValueOnce(makeActionExtractionResponse());

      render(<App />);
      fireEvent.click(screen.getByTestId('submit-text'));

      await waitFor(() => {
        expect(screen.getByText('다시 시도')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByText('다시 시도'));

      await waitFor(() => {
        expect(mockRequestText).toHaveBeenCalledTimes(2);
      });
    });
  });

  // --- Toast ---

  describe('toast', () => {
    it('shows toast after successful extraction', async () => {
      mockRequestText.mockResolvedValue(
        makeActionExtractionResponse([makeExtractedAction()]),
      );

      render(<App />);
      fireEvent.click(screen.getByTestId('submit-text'));

      await waitFor(() => {
        expect(screen.getByText(/1개 액션이 인박스에 저장되었습니다/)).toBeInTheDocument();
      });
    });

    it('shows duplicate toast when extraction has duplicate=true', async () => {
      mockRequestText.mockResolvedValue(
        makeActionExtractionResponse([makeExtractedAction()], true),
      );

      render(<App />);
      fireEvent.click(screen.getByTestId('submit-text'));

      await waitFor(() => {
        expect(screen.getByText(/이미 추출된 소스입니다/)).toBeInTheDocument();
      });
    });

    it('auto-dismisses toast after 3 seconds', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      mockRequestText.mockResolvedValue(makeActionExtractionResponse());

      render(<App />);

      fireEvent.click(screen.getByTestId('submit-text'));

      // Wait for toast to appear (promise resolves with shouldAdvanceTime)
      await waitFor(() => {
        expect(screen.getByRole('status')).toBeInTheDocument();
      });

      // Advance past the 3s auto-dismiss timeout
      await act(async () => { vi.advanceTimersByTime(3100); });

      expect(screen.queryByRole('status')).toBeNull();
    });
  });

  // --- Submitting state ---

  describe('submitting state', () => {
    it('passes isSubmitting during API request', async () => {
      let resolveRequest: (v: ActionExtractionResponse) => void;
      mockRequestText.mockReturnValue(
        new Promise((r) => { resolveRequest = r; }),
      );

      render(<App />);
      fireEvent.click(screen.getByTestId('submit-text'));

      await waitFor(() => {
        expect(screen.getByTestId('submitting-indicator')).toBeInTheDocument();
      });

      await act(async () => { resolveRequest!(makeActionExtractionResponse()); });

      expect(screen.queryByTestId('submitting-indicator')).toBeNull();
    });
  });

  // --- Skip link ---

  describe('skip link', () => {
    it('renders skip link with correct href', () => {
      render(<App />);

      const skipLink = screen.getByText('본문으로 건너뛰기');
      expect(skipLink).toHaveAttribute('href', '#main-content');
    });
  });
});
