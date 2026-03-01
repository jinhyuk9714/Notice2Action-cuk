import { describe, it, expect, beforeEach } from 'vitest';
import { loadTheme, saveTheme, resolveTheme, nextTheme } from './theme';

beforeEach(() => {
  localStorage.clear();
});

describe('loadTheme', () => {
  it('returns system when nothing stored', () => {
    expect(loadTheme()).toBe('system');
  });

  it('returns stored theme', () => {
    localStorage.setItem('n2a_theme', 'dark');
    expect(loadTheme()).toBe('dark');
  });

  it('returns system for invalid stored value', () => {
    localStorage.setItem('n2a_theme', 'neon');
    expect(loadTheme()).toBe('system');
  });
});

describe('saveTheme', () => {
  it('writes to localStorage', () => {
    saveTheme('dark');
    expect(localStorage.getItem('n2a_theme')).toBe('dark');
  });
});

describe('resolveTheme', () => {
  it('returns light for light', () => {
    expect(resolveTheme('light')).toBe('light');
  });

  it('returns dark for dark', () => {
    expect(resolveTheme('dark')).toBe('dark');
  });

  it('returns light or dark for system based on matchMedia', () => {
    const result = resolveTheme('system');
    expect(['light', 'dark']).toContain(result);
  });
});

describe('nextTheme', () => {
  it('cycles light → dark → system → light', () => {
    expect(nextTheme('light')).toBe('dark');
    expect(nextTheme('dark')).toBe('system');
    expect(nextTheme('system')).toBe('light');
  });
});
