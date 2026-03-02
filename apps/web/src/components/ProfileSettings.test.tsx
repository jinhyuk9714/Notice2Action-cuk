import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ProfileSettings } from './ProfileSettings';
import { EMPTY_PROFILE } from '../test-helpers';
import type { UserProfile } from '../lib/profile';

function renderProfile(overrides: Partial<UserProfile> = {}) {
  const profile: UserProfile = { ...EMPTY_PROFILE, ...overrides };
  const onChange = vi.fn();
  render(<ProfileSettings profile={profile} onProfileChange={onChange} />);
  return { onChange, profile };
}

function expand(): void {
  fireEvent.click(screen.getByText('내 프로필'));
}

describe('ProfileSettings - expand/collapse', () => {
  it('shows "내 프로필" header button', () => {
    renderProfile();
    expect(screen.getByText('내 프로필')).toBeInTheDocument();
  });

  it('does not show fields by default (collapsed)', () => {
    renderProfile();
    expect(screen.queryByText('학과')).not.toBeInTheDocument();
  });

  it('expands and shows all fields when header clicked', () => {
    renderProfile();
    expand();
    expect(screen.getByText('학과')).toBeInTheDocument();
    expect(screen.getByText('학년')).toBeInTheDocument();
    expect(screen.getByText('신분')).toBeInTheDocument();
  });

  it('collapses when header clicked again', () => {
    renderProfile();
    expand();
    expect(screen.getByText('학과')).toBeInTheDocument();
    fireEvent.click(screen.getByText('내 프로필'));
    expect(screen.queryByText('학과')).not.toBeInTheDocument();
  });

  it('shows correct arrow icon', () => {
    renderProfile();
    expect(screen.getByText('\u25BC')).toBeInTheDocument(); // ▼ collapsed
    expand();
    expect(screen.getByText('\u25B2')).toBeInTheDocument(); // ▲ expanded
  });
});

describe('ProfileSettings - department', () => {
  it('renders department input with placeholder', () => {
    renderProfile();
    expand();
    expect(screen.getByPlaceholderText('예: 컴퓨터공학과')).toBeInTheDocument();
  });

  it('calls onProfileChange with department on input', () => {
    const { onChange } = renderProfile();
    expand();
    fireEvent.change(screen.getByPlaceholderText('예: 컴퓨터공학과'), { target: { value: '경영학과' } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ department: '경영학과' }));
  });

  it('sets department to null when input is empty', () => {
    const { onChange } = renderProfile({ department: '경영학과' });
    expand();
    fireEvent.change(screen.getByPlaceholderText('예: 컴퓨터공학과'), { target: { value: '' } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ department: null }));
  });

  it('shows current department value', () => {
    renderProfile({ department: '컴퓨터공학과' });
    expand();
    expect(screen.getByPlaceholderText('예: 컴퓨터공학과')).toHaveValue('컴퓨터공학과');
  });
});

describe('ProfileSettings - year', () => {
  it('renders year buttons 1-4', () => {
    renderProfile();
    expand();
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
  });

  it('calls onProfileChange with year when clicked', () => {
    const { onChange } = renderProfile();
    expand();
    fireEvent.click(screen.getByText('3'));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ year: 3 }));
  });

  it('toggles year to null when same year clicked', () => {
    const { onChange } = renderProfile({ year: 3 });
    expand();
    fireEvent.click(screen.getByText('3'));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ year: null }));
  });
});

describe('ProfileSettings - status', () => {
  it('renders status select with options', () => {
    renderProfile();
    expand();
    expect(screen.getByText('선택 안 함')).toBeInTheDocument();
    expect(screen.getByText('재학생')).toBeInTheDocument();
    expect(screen.getByText('휴학생')).toBeInTheDocument();
  });

  it('calls onProfileChange with status on select', () => {
    const { onChange } = renderProfile();
    expand();
    const select = screen.getByRole('combobox');
    fireEvent.change(select, { target: { value: '휴학생' } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ status: '휴학생' }));
  });
});
