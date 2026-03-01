import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ThemeToggle } from './ThemeToggle';

beforeEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset['theme'];
});

describe('ThemeToggle', () => {
  it('renders a button', () => {
    render(<ThemeToggle />);
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('cycles theme on click', () => {
    localStorage.setItem('n2a_theme', 'light');
    render(<ThemeToggle />);
    const btn = screen.getByRole('button');

    expect(btn).toHaveAttribute('aria-label', '라이트 모드');

    fireEvent.click(btn);
    expect(btn).toHaveAttribute('aria-label', '다크 모드');

    fireEvent.click(btn);
    expect(btn).toHaveAttribute('aria-label', '시스템 설정');

    fireEvent.click(btn);
    expect(btn).toHaveAttribute('aria-label', '라이트 모드');
  });

  it('applies data-theme attribute', () => {
    localStorage.setItem('n2a_theme', 'dark');
    render(<ThemeToggle />);
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });
});
