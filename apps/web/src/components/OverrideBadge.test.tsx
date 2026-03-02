import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { OverrideBadge } from './OverrideBadge';
import type { FieldOverrideInfo } from '../lib/types';

const BASE_OVERRIDES: readonly FieldOverrideInfo[] = [
  { fieldName: 'title', machineValue: '원래 제목' },
  { fieldName: 'eligibility', machineValue: null },
];

describe('OverrideBadge', () => {
  it('returns null when no matching override exists', () => {
    const { container } = render(
      <OverrideBadge
        overrides={BASE_OVERRIDES}
        fieldName="systemHint"
        reverting={null}
        onRevert={vi.fn()}
      />,
    );
    expect(container.innerHTML).toBe('');
  });

  it('renders "수정됨" label when override exists', () => {
    render(
      <OverrideBadge
        overrides={BASE_OVERRIDES}
        fieldName="title"
        reverting={null}
        onRevert={vi.fn()}
      />,
    );
    expect(screen.getByText('수정됨')).toBeInTheDocument();
  });

  it('calls onRevert with fieldName when button clicked', () => {
    const onRevert = vi.fn();
    render(
      <OverrideBadge
        overrides={BASE_OVERRIDES}
        fieldName="title"
        reverting={null}
        onRevert={onRevert}
      />,
    );
    fireEvent.click(screen.getByText('되돌리기'));
    expect(onRevert).toHaveBeenCalledWith('title');
  });

  it('disables button when reverting matches fieldName', () => {
    render(
      <OverrideBadge
        overrides={BASE_OVERRIDES}
        fieldName="title"
        reverting="title"
        onRevert={vi.fn()}
      />,
    );
    expect(screen.getByText('되돌리기')).toBeDisabled();
  });

  it('shows title attribute with original value when showOriginalTitle is true', () => {
    render(
      <OverrideBadge
        overrides={BASE_OVERRIDES}
        fieldName="title"
        reverting={null}
        onRevert={vi.fn()}
        showOriginalTitle={true}
      />,
    );
    expect(screen.getByText('되돌리기')).toHaveAttribute('title', '원래 값: 원래 제목');
  });

  it('does not show title attribute when showOriginalTitle is false', () => {
    render(
      <OverrideBadge
        overrides={BASE_OVERRIDES}
        fieldName="title"
        reverting={null}
        onRevert={vi.fn()}
      />,
    );
    expect(screen.getByText('되돌리기').getAttribute('title')).toBeNull();
  });

  it('enables button when reverting is a different field', () => {
    render(
      <OverrideBadge
        overrides={BASE_OVERRIDES}
        fieldName="title"
        reverting="eligibility"
        onRevert={vi.fn()}
      />,
    );
    expect(screen.getByText('되돌리기')).not.toBeDisabled();
  });
});
