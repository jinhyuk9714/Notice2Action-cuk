import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EMPTY_PROFILE, FULL_PROFILE, makeActionDetail } from '../test-helpers';
import { ActionDetailPanel } from './ActionDetailPanel';

vi.mock('../lib/api', () => ({
  updateAction: vi.fn(),
  revertActionField: vi.fn(),
}));

vi.mock('../lib/reminder', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../lib/reminder')>();
  return {
    ...actual,
    requestNotificationPermission: vi.fn(),
  };
});

import { updateAction, revertActionField } from '../lib/api';
import { requestNotificationPermission, saveReminder } from '../lib/reminder';

const mockUpdateAction = vi.mocked(updateAction);
const mockRevertActionField = vi.mocked(revertActionField);
const mockRequestPermission = vi.mocked(requestNotificationPermission);

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('ActionDetailPanel', () => {
  // --- Existing tests (migrated to shared factory) ---

  it('resets editing state and reminder selection when detail target changes', () => {
    saveReminder({
      actionId: 'action-1',
      offsetKey: 'D-1',
      remindAtIso: '2026-03-14T00:00:00+09:00',
      title: 'A1',
      dueLabel: 'D-1',
      dismissed: false,
    });
    saveReminder({
      actionId: 'action-2',
      offsetKey: 'D-3',
      remindAtIso: '2026-03-12T00:00:00+09:00',
      title: 'A2',
      dueLabel: 'D-3',
      dismissed: false,
    });

    const detail1 = makeActionDetail({ id: 'action-1', title: '액션 1' });
    const detail2 = makeActionDetail({ id: 'action-2', title: '액션 2' });

    const { rerender } = render(
      <ActionDetailPanel detail={detail1} profile={EMPTY_PROFILE} />,
    );

    const d1Before = screen.getByRole('button', { name: 'D-1' });
    const d3Before = screen.getByRole('button', { name: 'D-3' });
    expect(d1Before).toHaveClass('reminder-option-active');
    expect(d3Before).not.toHaveClass('reminder-option-active');

    fireEvent.click(screen.getByRole('button', { name: '편집' }));
    expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument();

    rerender(<ActionDetailPanel detail={detail2} profile={EMPTY_PROFILE} />);

    expect(screen.queryByRole('button', { name: '저장' })).toBeNull();
    const d1After = screen.getByRole('button', { name: 'D-1' });
    const d3After = screen.getByRole('button', { name: 'D-3' });
    expect(d1After).not.toHaveClass('reminder-option-active');
    expect(d3After).toHaveClass('reminder-option-active');
  });

  it('shows override badge when field is user-edited', () => {
    const detail = makeActionDetail({
      title: '수정된 제목',
      overrides: [{ fieldName: 'title', machineValue: '원래 제목' }],
    });
    render(<ActionDetailPanel detail={detail} profile={EMPTY_PROFILE} />);

    expect(screen.getByText('수정됨')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '되돌리기' })).toBeInTheDocument();
  });

  it('does not show override badge when no overrides', () => {
    render(<ActionDetailPanel detail={makeActionDetail()} profile={EMPTY_PROFILE} />);
    expect(screen.queryByText('수정됨')).toBeNull();
  });

  it('shows multiple override badges for multiple overridden fields', () => {
    const detail = makeActionDetail({
      overrides: [
        { fieldName: 'title', machineValue: '원래 제목' },
        { fieldName: 'eligibility', machineValue: '재학생' },
        { fieldName: 'systemHint', machineValue: 'TRINITY' },
      ],
    });
    render(<ActionDetailPanel detail={detail} profile={EMPTY_PROFILE} />);

    const badges = screen.getAllByText('수정됨');
    expect(badges).toHaveLength(3);
    const revertButtons = screen.getAllByRole('button', { name: '되돌리기' });
    expect(revertButtons).toHaveLength(3);
  });

  // --- Editing workflow ---

  describe('editing', () => {
    it('enters edit mode when "편집" is clicked', () => {
      render(<ActionDetailPanel detail={makeActionDetail()} profile={EMPTY_PROFILE} />);

      fireEvent.click(screen.getByRole('button', { name: '편집' }));

      expect(screen.getByLabelText('제목 수정')).toBeInTheDocument();
      expect(screen.getByLabelText('요약 수정')).toBeInTheDocument();
      expect(screen.getByLabelText('마감 레이블')).toBeInTheDocument();
      expect(screen.getByLabelText('대상/조건')).toBeInTheDocument();
      expect(screen.getByLabelText('시스템')).toBeInTheDocument();
    });

    it('populates edit fields with current detail values', () => {
      const detail = makeActionDetail({
        title: '장학금 신청',
        actionSummary: '요약 텍스트',
        dueAtLabel: '3월 15일까지',
        eligibility: '재학생',
        systemHint: 'TRINITY',
      });
      render(<ActionDetailPanel detail={detail} profile={EMPTY_PROFILE} />);

      fireEvent.click(screen.getByRole('button', { name: '편집' }));

      expect(screen.getByLabelText('제목 수정')).toHaveValue('장학금 신청');
      expect(screen.getByLabelText('요약 수정')).toHaveValue('요약 텍스트');
      expect(screen.getByLabelText('마감 레이블')).toHaveValue('3월 15일까지');
      expect(screen.getByLabelText('대상/조건')).toHaveValue('재학생');
      expect(screen.getByLabelText('시스템')).toHaveValue('TRINITY');
    });

    it('saves edited fields and calls onActionUpdated', async () => {
      const updated = makeActionDetail({ title: '수정된 제목' });
      mockUpdateAction.mockResolvedValue(updated);
      const onUpdated = vi.fn();

      render(
        <ActionDetailPanel
          detail={makeActionDetail()}
          profile={EMPTY_PROFILE}
          onActionUpdated={onUpdated}
        />,
      );

      fireEvent.click(screen.getByRole('button', { name: '편집' }));
      fireEvent.change(screen.getByLabelText('제목 수정'), { target: { value: '수정된 제목' } });
      fireEvent.click(screen.getByRole('button', { name: '저장' }));

      await waitFor(() => {
        expect(mockUpdateAction).toHaveBeenCalledWith('act-1', expect.objectContaining({
          title: '수정된 제목',
        }));
        expect(onUpdated).toHaveBeenCalledWith(updated);
      });

      // Should exit edit mode
      expect(screen.queryByLabelText('제목 수정')).toBeNull();
    });

    it('shows validation error when title is empty on save', async () => {
      render(<ActionDetailPanel detail={makeActionDetail()} profile={EMPTY_PROFILE} />);

      fireEvent.click(screen.getByRole('button', { name: '편집' }));
      fireEvent.change(screen.getByLabelText('제목 수정'), { target: { value: '  ' } });
      fireEvent.click(screen.getByRole('button', { name: '저장' }));

      expect(screen.getByText('제목은 비워둘 수 없습니다.')).toBeInTheDocument();
      expect(mockUpdateAction).not.toHaveBeenCalled();
    });

    it('shows API error when save fails', async () => {
      mockUpdateAction.mockRejectedValue(new Error('서버 오류'));

      render(<ActionDetailPanel detail={makeActionDetail()} profile={EMPTY_PROFILE} />);

      fireEvent.click(screen.getByRole('button', { name: '편집' }));
      fireEvent.click(screen.getByRole('button', { name: '저장' }));

      await waitFor(() => {
        expect(screen.getByText('서버 오류')).toBeInTheDocument();
      });
    });

    it('cancels editing and hides edit form', () => {
      render(<ActionDetailPanel detail={makeActionDetail()} profile={EMPTY_PROFILE} />);

      fireEvent.click(screen.getByRole('button', { name: '편집' }));
      expect(screen.getByLabelText('제목 수정')).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: '취소' }));
      expect(screen.queryByLabelText('제목 수정')).toBeNull();
    });
  });

  // --- Revert workflow ---

  describe('revert', () => {
    it('calls revertActionField and fires onActionUpdated on success', async () => {
      const updated = makeActionDetail({ title: '원래 제목', overrides: [] });
      mockRevertActionField.mockResolvedValue(updated);
      const onUpdated = vi.fn();

      const detail = makeActionDetail({
        overrides: [{ fieldName: 'title', machineValue: '원래 제목' }],
      });
      render(
        <ActionDetailPanel detail={detail} profile={EMPTY_PROFILE} onActionUpdated={onUpdated} />,
      );

      fireEvent.click(screen.getByRole('button', { name: '되돌리기' }));

      await waitFor(() => {
        expect(mockRevertActionField).toHaveBeenCalledWith('act-1', 'title');
        expect(onUpdated).toHaveBeenCalledWith(updated);
      });
    });

    it('re-enables revert button after revert failure', async () => {
      mockRevertActionField.mockRejectedValue(new Error('되돌리기 실패'));

      const detail = makeActionDetail({
        overrides: [{ fieldName: 'title', machineValue: '원래 제목' }],
      });
      render(<ActionDetailPanel detail={detail} profile={EMPTY_PROFILE} />);

      const revertBtn = screen.getByRole('button', { name: '되돌리기' });
      fireEvent.click(revertBtn);

      // Button should be re-enabled after failure (reverting state cleared in finally block)
      await waitFor(() => {
        expect(screen.getByRole('button', { name: '되돌리기' })).toBeEnabled();
      });
      expect(mockRevertActionField).toHaveBeenCalledWith('act-1', 'title');
    });

    it('disables revert button during reverting', async () => {
      let resolveRevert: (v: ReturnType<typeof makeActionDetail>) => void;
      mockRevertActionField.mockReturnValue(
        new Promise((r) => { resolveRevert = r; }),
      );

      const detail = makeActionDetail({
        overrides: [{ fieldName: 'title', machineValue: '원래 제목' }],
      });
      render(<ActionDetailPanel detail={detail} profile={EMPTY_PROFILE} />);

      fireEvent.click(screen.getByRole('button', { name: '되돌리기' }));

      expect(screen.getByRole('button', { name: '되돌리기' })).toBeDisabled();

      await act(async () => { resolveRevert!(makeActionDetail()); });
    });
  });

  // --- Confidence score ---

  describe('confidence score', () => {
    it('shows confidence-high class for score >= 0.75', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ confidenceScore: 0.85 })}
          profile={EMPTY_PROFILE}
        />,
      );
      expect(screen.getByText('85% 신뢰도')).toHaveClass('confidence-high');
    });

    it('shows confidence-medium class for score between 0.5 and 0.75', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ confidenceScore: 0.6 })}
          profile={EMPTY_PROFILE}
        />,
      );
      expect(screen.getByText('60% 신뢰도')).toHaveClass('confidence-medium');
    });

    it('shows confidence-low class for score < 0.5', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ confidenceScore: 0.3 })}
          profile={EMPTY_PROFILE}
        />,
      );
      expect(screen.getByText('30% 신뢰도')).toHaveClass('confidence-low');
    });
  });

  // --- D-day badge ---

  describe('dday badge', () => {
    it('shows dday badge when dueAtIso is set', () => {
      const tomorrow = new Date(Date.now() + 86400000).toISOString();
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ dueAtIso: tomorrow })}
          profile={EMPTY_PROFILE}
        />,
      );
      const badges = document.querySelectorAll('.dday-badge');
      expect(badges.length).toBeGreaterThan(0);
    });

    it('does not show dday badge when dueAtIso is null', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ dueAtIso: null, dueAtLabel: null })}
          profile={EMPTY_PROFILE}
        />,
      );
      const badges = document.querySelectorAll('.dday-badge');
      expect(badges.length).toBe(0);
    });
  });

  // --- Relevance badge ---

  describe('relevance badge', () => {
    it('shows "관련" badge when profile matches eligibility', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ eligibility: '컴퓨터공학과 재학생' })}
          profile={FULL_PROFILE}
        />,
      );
      expect(screen.getByText('관련')).toBeInTheDocument();
    });

    it('shows "해당없음" badge when profile does not match', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ eligibility: '간호학과 휴학생' })}
          profile={FULL_PROFILE}
        />,
      );
      expect(screen.getByText('해당없음')).toBeInTheDocument();
    });

    it('shows no relevance badge when profile is empty', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ eligibility: '컴퓨터공학과 재학생' })}
          profile={EMPTY_PROFILE}
        />,
      );
      expect(screen.queryByText('관련')).toBeNull();
      expect(screen.queryByText('해당없음')).toBeNull();
    });
  });

  // --- Calendar export ---

  describe('calendar export', () => {
    it('shows calendar link when dueAtIso is present', () => {
      render(
        <ActionDetailPanel detail={makeActionDetail()} profile={EMPTY_PROFILE} />,
      );
      const link = screen.getByText('일정 추가 (.ics)');
      expect(link).toBeInTheDocument();
      expect(link).toHaveAttribute('href', '/api/v1/actions/act-1/calendar.ics');
      expect(link).toHaveAttribute('download');
    });

    it('hides calendar link when dueAtIso is null', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ dueAtIso: null })}
          profile={EMPTY_PROFILE}
        />,
      );
      expect(screen.queryByText('일정 추가 (.ics)')).toBeNull();
    });
  });

  // --- Reminder options ---

  describe('reminder options', () => {
    it('shows reminder buttons when dueAtIso is present', () => {
      render(
        <ActionDetailPanel detail={makeActionDetail()} profile={EMPTY_PROFILE} />,
      );
      expect(screen.getByRole('button', { name: 'D-7' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'D-3' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'D-1' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '당일' })).toBeInTheDocument();
    });

    it('hides reminder buttons when dueAtIso is null', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ dueAtIso: null })}
          profile={EMPTY_PROFILE}
        />,
      );
      expect(screen.queryByRole('button', { name: 'D-7' })).toBeNull();
    });

    it('toggles reminder active class on click', async () => {
      mockRequestPermission.mockResolvedValue(true);

      render(
        <ActionDetailPanel detail={makeActionDetail()} profile={EMPTY_PROFILE} />,
      );

      const d3Btn = screen.getByRole('button', { name: 'D-3' });
      expect(d3Btn).not.toHaveClass('reminder-option-active');

      await act(async () => { fireEvent.click(d3Btn); });

      expect(screen.getByRole('button', { name: 'D-3' })).toHaveClass('reminder-option-active');

      // Toggle off
      await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'D-3' })); });

      expect(screen.getByRole('button', { name: 'D-3' })).not.toHaveClass('reminder-option-active');
    });
  });

  // --- Source info ---

  describe('source info', () => {
    it('shows source info when source is not null', () => {
      render(
        <ActionDetailPanel detail={makeActionDetail()} profile={EMPTY_PROFILE} />,
      );
      expect(screen.getByText('출처')).toBeInTheDocument();
      expect(screen.getByText(/장학 안내/)).toBeInTheDocument();
    });

    it('hides source info when source is null', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ source: null })}
          profile={EMPTY_PROFILE}
        />,
      );
      expect(screen.queryByText('출처')).toBeNull();
    });
  });

  // --- Evidence ---

  describe('evidence', () => {
    it('renders evidence snippets with field labels', () => {
      const detail = makeActionDetail({
        evidence: [
          { fieldName: 'dueAtLabel', snippet: '접수 마감 3월 15일', confidence: 0.9 },
          { fieldName: 'systemHint', snippet: 'TRINITY 포털에서 신청', confidence: 0.8 },
        ],
      });
      render(<ActionDetailPanel detail={detail} profile={EMPTY_PROFILE} />);

      const evidenceBlock = document.querySelector('.evidence-block')!;
      const fieldLabels = evidenceBlock.querySelectorAll('.evidence-field');
      expect(fieldLabels).toHaveLength(2);
      expect(fieldLabels[0]!.textContent).toBe('마감일');
      expect(fieldLabels[1]!.textContent).toBe('시스템');
      expect(screen.getByText('접수 마감 3월 15일')).toBeInTheDocument();
      expect(screen.getByText('TRINITY 포털에서 신청')).toBeInTheDocument();
    });

    it('shows empty evidence message when no evidence', () => {
      render(
        <ActionDetailPanel
          detail={makeActionDetail({ evidence: [] })}
          profile={EMPTY_PROFILE}
        />,
      );
      expect(screen.getByText('근거 snippet 없음')).toBeInTheDocument();
    });
  });
});
