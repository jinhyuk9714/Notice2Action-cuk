import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SourceIngestionForm } from './SourceIngestionForm';
import { FULL_PROFILE } from '../test-helpers';

vi.mock('../lib/profile', () => ({
  loadProfile: vi.fn(() => ({ department: null, year: null, status: null })),
}));

import { loadProfile } from '../lib/profile';

type Props = Parameters<typeof SourceIngestionForm>[0];

function renderForm(overrides: Partial<Props> = {}) {
  const props: Props = {
    onSubmit: vi.fn(() => Promise.resolve()),
    onFileSubmit: vi.fn(() => Promise.resolve()),
    onEmailSubmit: vi.fn(() => Promise.resolve()),
    isSubmitting: false,
    ...overrides,
  };
  render(<SourceIngestionForm {...props} />);
  return props;
}

// --- Mode switching ---

describe('SourceIngestionForm - mode switching', () => {
  it('defaults to text mode with textarea', () => {
    renderForm();
    expect(screen.getByLabelText('원문 텍스트')).toBeInTheDocument();
  });

  it('switches to URL mode', () => {
    renderForm();
    fireEvent.click(screen.getByText('URL 입력'));
    expect(screen.getByLabelText('공지 URL')).toBeInTheDocument();
    expect(screen.queryByLabelText('원문 텍스트')).not.toBeInTheDocument();
  });

  it('switches to file mode', () => {
    renderForm();
    fireEvent.click(screen.getByText('파일 업로드'));
    expect(screen.getByLabelText('파일 (PDF / 스크린샷)')).toBeInTheDocument();
  });

  it('switches to email mode', () => {
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: '이메일' }));
    expect(screen.getByLabelText('이메일 본문')).toBeInTheDocument();
  });

  it('applies toggle-active class to selected mode', () => {
    renderForm();
    const urlBtn = screen.getByText('URL 입력');
    fireEvent.click(urlBtn);
    expect(urlBtn).toHaveClass('toggle-active');
  });
});

// --- Text mode ---

describe('SourceIngestionForm - text mode', () => {
  it('renders title input, category select, and textarea', () => {
    renderForm();
    expect(screen.getByLabelText('제목')).toBeInTheDocument();
    expect(screen.getByLabelText('카테고리')).toBeInTheDocument();
    expect(screen.getByLabelText('원문 텍스트')).toBeInTheDocument();
  });

  it('pre-populates sourceText with default text', () => {
    renderForm();
    const textarea = screen.getByLabelText('원문 텍스트') as HTMLTextAreaElement;
    expect(textarea.value).toContain('TRINITY');
  });

  it('pre-populates sourceTitle', () => {
    renderForm();
    const titleInput = screen.getByLabelText('제목') as HTMLInputElement;
    expect(titleInput.value).toBe('공결 신청 안내');
  });

  it('calls onSubmit with correct payload', async () => {
    const props = renderForm();
    fireEvent.submit(screen.getByText('액션 추출').closest('form')!);
    await waitFor(() => {
      expect(props.onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          sourceText: expect.stringContaining('TRINITY'),
          sourceTitle: '공결 신청 안내',
          sourceCategory: 'NOTICE',
        }),
      );
    });
  });

  it('includes focusProfile when profile has values', async () => {
    vi.mocked(loadProfile).mockReturnValue(FULL_PROFILE);
    const props = renderForm();
    fireEvent.submit(screen.getByText('액션 추출').closest('form')!);
    await waitFor(() => {
      expect(props.onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          focusProfile: expect.arrayContaining(['재학생', '3학년', '컴퓨터공학과']),
        }),
      );
    });
  });

  it('disables submit when text is empty', () => {
    renderForm();
    const textarea = screen.getByLabelText('원문 텍스트');
    fireEvent.change(textarea, { target: { value: '' } });
    expect(screen.getByText('액션 추출')).toBeDisabled();
  });
});

// --- URL mode ---

describe('SourceIngestionForm - URL mode', () => {
  beforeEach(() => {
    renderForm();
    fireEvent.click(screen.getByText('URL 입력'));
  });

  it('renders URL input with placeholder', () => {
    expect(screen.getByPlaceholderText('https://www.catholic.ac.kr/...')).toBeInTheDocument();
  });

  it('shows URL hint text', () => {
    expect(screen.getByText('로그인이 필요한 페이지는 직접 텍스트를 붙여넣어 주세요.')).toBeInTheDocument();
  });

  it('disables submit when URL is empty', () => {
    expect(screen.getByText('액션 추출')).toBeDisabled();
  });
});

// --- File mode ---

describe('SourceIngestionForm - file mode', () => {
  it('renders file input', () => {
    renderForm();
    fireEvent.click(screen.getByText('파일 업로드'));
    expect(screen.getByLabelText('파일 (PDF / 스크린샷)')).toBeInTheDocument();
  });

  it('shows error for invalid extension', () => {
    renderForm();
    fireEvent.click(screen.getByText('파일 업로드'));
    const input = screen.getByLabelText('파일 (PDF / 스크린샷)') as HTMLInputElement;
    const file = new File(['content'], 'test.txt', { type: 'text/plain' });
    fireEvent.change(input, { target: { files: [file] } });
    expect(screen.getByText(/PDF 또는 이미지 파일만 업로드할 수 있습니다/)).toBeInTheDocument();
  });

  it('shows error for PDF exceeding 10MB', () => {
    renderForm();
    fireEvent.click(screen.getByText('파일 업로드'));
    const input = screen.getByLabelText('파일 (PDF / 스크린샷)') as HTMLInputElement;
    const bigPdf = new File([new ArrayBuffer(11 * 1024 * 1024)], 'big.pdf', { type: 'application/pdf' });
    fireEvent.change(input, { target: { files: [bigPdf] } });
    expect(screen.getByText('파일 크기가 10MB를 초과합니다.')).toBeInTheDocument();
  });

  it('shows error for image exceeding 5MB', () => {
    renderForm();
    fireEvent.click(screen.getByText('파일 업로드'));
    const input = screen.getByLabelText('파일 (PDF / 스크린샷)') as HTMLInputElement;
    const bigImg = new File([new ArrayBuffer(6 * 1024 * 1024)], 'big.png', { type: 'image/png' });
    fireEvent.change(input, { target: { files: [bigImg] } });
    expect(screen.getByText('파일 크기가 5MB를 초과합니다.')).toBeInTheDocument();
  });

  it('shows file info for valid file', () => {
    renderForm();
    fireEvent.click(screen.getByText('파일 업로드'));
    const input = screen.getByLabelText('파일 (PDF / 스크린샷)') as HTMLInputElement;
    const file = new File(['x'.repeat(1024)], 'test.pdf', { type: 'application/pdf' });
    fireEvent.change(input, { target: { files: [file] } });
    expect(screen.getByText(/test\.pdf/)).toBeInTheDocument();
  });

  it('calls onFileSubmit on form submit', async () => {
    const props = renderForm();
    fireEvent.click(screen.getByText('파일 업로드'));
    const input = screen.getByLabelText('파일 (PDF / 스크린샷)') as HTMLInputElement;
    const file = new File(['content'], 'test.pdf', { type: 'application/pdf' });
    fireEvent.change(input, { target: { files: [file] } });
    fireEvent.submit(screen.getByText('액션 추출').closest('form')!);
    await waitFor(() => {
      expect(props.onFileSubmit).toHaveBeenCalledWith(file, '공결 신청 안내');
    });
  });

  it('disables submit when no file selected', () => {
    renderForm();
    fireEvent.click(screen.getByText('파일 업로드'));
    expect(screen.getByText('액션 추출')).toBeDisabled();
  });
});

// --- Email mode ---

describe('SourceIngestionForm - email mode', () => {
  it('renders email subject and body inputs', () => {
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: '이메일' }));
    expect(screen.getByLabelText('제목 (Subject)')).toBeInTheDocument();
    expect(screen.getByLabelText('이메일 본문')).toBeInTheDocument();
  });

  it('hides title input in email mode', () => {
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: '이메일' }));
    expect(screen.queryByLabelText('제목')).not.toBeInTheDocument();
  });

  it('calls onEmailSubmit with body and subject', async () => {
    const props = renderForm();
    fireEvent.click(screen.getByRole('button', { name: '이메일' }));
    fireEvent.change(screen.getByLabelText('이메일 본문'), { target: { value: '내용입니다' } });
    fireEvent.change(screen.getByLabelText('제목 (Subject)'), { target: { value: '이메일 제목' } });
    fireEvent.submit(screen.getByText('액션 추출').closest('form')!);
    await waitFor(() => {
      expect(props.onEmailSubmit).toHaveBeenCalledWith('내용입니다', '이메일 제목');
    });
  });

  it('disables submit when body is empty', () => {
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: '이메일' }));
    expect(screen.getByText('액션 추출')).toBeDisabled();
  });
});

// --- Common ---

describe('SourceIngestionForm - common', () => {
  it('shows submitting state', () => {
    renderForm({ isSubmitting: true });
    expect(screen.getByText('추출 중...')).toBeInTheDocument();
    expect(screen.getByText('추출 중...').closest('button')).toBeDisabled();
  });

  it('applies primary-button-loading class when submitting', () => {
    renderForm({ isSubmitting: true });
    const btn = screen.getByText('추출 중...').closest('button')!;
    expect(btn).toHaveClass('primary-button-loading');
  });

  it('hides category select in file mode', () => {
    renderForm();
    fireEvent.click(screen.getByText('파일 업로드'));
    expect(screen.queryByLabelText('카테고리')).not.toBeInTheDocument();
  });

  it('shows correct title placeholder in URL mode', () => {
    renderForm();
    fireEvent.click(screen.getByText('URL 입력'));
    expect(screen.getByPlaceholderText('비워두면 페이지 제목을 자동으로 가져옵니다')).toBeInTheDocument();
  });

  it('shows correct title placeholder in file mode', () => {
    renderForm();
    fireEvent.click(screen.getByText('파일 업로드'));
    expect(screen.getByPlaceholderText('비워두면 파일명을 사용합니다')).toBeInTheDocument();
  });
});
