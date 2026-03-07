import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ProfileSettings } from './ProfileSettings';
import { EMPTY_PROFILE } from '../test-helpers';
import type { UserProfile } from '../lib/profile';

function renderProfile(overrides: Partial<UserProfile> = {}) {
  const profile: UserProfile = { ...EMPTY_PROFILE, ...overrides };
  const onChange = vi.fn();
  render(<ProfileSettings profile={profile} onProfileChange={onChange} availableBoards={['학사', '장학', '취창업']} />);
  return { onChange };
}

describe('ProfileSettings', () => {
  it('shows all profile fields without collapsing', () => {
    renderProfile();
    expect(screen.getByLabelText('학과')).toBeInTheDocument();
    expect(screen.getByLabelText('신분')).toBeInTheDocument();
    expect(screen.getByLabelText('관심 키워드')).toBeInTheDocument();
    expect(screen.getByRole('group', { name: '학년' })).toBeInTheDocument();
  });

  it('updates department and year', () => {
    const { onChange } = renderProfile();
    fireEvent.change(screen.getByLabelText('학과'), { target: { value: '경영학과' } });
    fireEvent.click(screen.getByRole('button', { name: '3' }));

    expect(onChange).toHaveBeenNthCalledWith(1, expect.objectContaining({ department: '경영학과' }));
    expect(onChange).toHaveBeenNthCalledWith(2, expect.objectContaining({ year: 3 }));
  });

  it('updates status and keyword array', () => {
    const { onChange } = renderProfile();
    fireEvent.change(screen.getByLabelText('신분'), { target: { value: '휴학생' } });
    fireEvent.change(screen.getByLabelText('관심 키워드'), { target: { value: '장학금, 학생증' } });

    expect(onChange).toHaveBeenNthCalledWith(1, expect.objectContaining({ status: '휴학생' }));
    expect(onChange).toHaveBeenNthCalledWith(2, expect.objectContaining({ interestKeywords: ['장학금', '학생증'] }));
  });

  it('shows preferred board chips and toggles selection', () => {
    const { onChange } = renderProfile({ preferredBoards: ['학사'] });

    expect(screen.getByRole('button', { name: '학사' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '장학' })).toHaveAttribute('aria-pressed', 'false');

    fireEvent.click(screen.getByRole('button', { name: '장학' }));

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ preferredBoards: ['학사', '장학'] }));
  });
});
