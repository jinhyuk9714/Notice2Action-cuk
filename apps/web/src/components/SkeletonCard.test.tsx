import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SkeletonCard } from './SkeletonCard';

describe('SkeletonCard', () => {
  it('renders with aria-hidden="true"', () => {
    const { container } = render(<SkeletonCard />);
    const card = container.querySelector('.skeleton-card');
    expect(card).toHaveAttribute('aria-hidden', 'true');
  });

  it('renders default 3 lines plus 1 short = 4 skeleton-line elements', () => {
    const { container } = render(<SkeletonCard />);
    const lines = container.querySelectorAll('.skeleton-line');
    expect(lines).toHaveLength(4);
    expect(lines[0]).toHaveClass('skeleton-line-short');
  });

  it('renders configurable number of lines', () => {
    const { container } = render(<SkeletonCard lines={5} />);
    const lines = container.querySelectorAll('.skeleton-line');
    expect(lines).toHaveLength(6); // 1 short + 5 lines
  });

  it('applies skeleton-line-medium class to last line', () => {
    const { container } = render(<SkeletonCard />);
    const lines = container.querySelectorAll('.skeleton-line');
    const lastLine = lines[lines.length - 1];
    expect(lastLine).toHaveClass('skeleton-line-medium');
  });

  it('renders correctly with lines=1', () => {
    const { container } = render(<SkeletonCard lines={1} />);
    const lines = container.querySelectorAll('.skeleton-line');
    expect(lines).toHaveLength(2); // 1 short + 1 line
    expect(lines[1]).toHaveClass('skeleton-line-medium');
  });
});
