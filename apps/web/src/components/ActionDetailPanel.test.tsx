import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import type { UserProfile } from '../lib/profile';
import { saveReminder } from '../lib/reminder';
import type { SavedActionDetail } from '../lib/types';
import { ActionDetailPanel } from './ActionDetailPanel';

const EMPTY_PROFILE: UserProfile = {
  department: null,
  year: null,
  status: null,
};

function makeDetail(id: string, title: string): SavedActionDetail {
  return {
    id,
    title,
    actionSummary: `${title} 요약`,
    dueAtIso: '2026-03-15T18:00:00+09:00',
    dueAtLabel: '2026년 3월 15일 18시',
    eligibility: '재학생',
    requiredItems: [],
    systemHint: 'TRINITY',
    inferred: false,
    confidenceScore: 0.9,
    createdAt: '2026-03-01T00:00:00+09:00',
    source: null,
    evidence: [],
  };
}

describe('ActionDetailPanel', () => {
  beforeEach(() => {
    localStorage.clear();
  });

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

    const { rerender } = render(
      <ActionDetailPanel detail={makeDetail('action-1', '액션 1')} profile={EMPTY_PROFILE} />,
    );

    const d1Before = screen.getByRole('button', { name: 'D-1' });
    const d3Before = screen.getByRole('button', { name: 'D-3' });
    expect(d1Before).toHaveClass('reminder-option-active');
    expect(d3Before).not.toHaveClass('reminder-option-active');

    fireEvent.click(screen.getByRole('button', { name: '편집' }));
    expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument();

    rerender(<ActionDetailPanel detail={makeDetail('action-2', '액션 2')} profile={EMPTY_PROFILE} />);

    expect(screen.queryByRole('button', { name: '저장' })).toBeNull();
    const d1After = screen.getByRole('button', { name: 'D-1' });
    const d3After = screen.getByRole('button', { name: 'D-3' });
    expect(d1After).not.toHaveClass('reminder-option-active');
    expect(d3After).toHaveClass('reminder-option-active');
  });
});
