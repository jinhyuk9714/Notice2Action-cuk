import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { applyTheme, loadTheme, nextTheme, resolveTheme, saveTheme, type Theme } from '../lib/theme';

const THEME_ICON: Record<Theme, string> = {
  light: '\u2600\uFE0F',
  dark: '\uD83C\uDF19',
  system: '\uD83D\uDDA5\uFE0F',
};

const THEME_LABEL: Record<Theme, string> = {
  light: '라이트 모드',
  dark: '다크 모드',
  system: '시스템 설정',
};

export function ThemeToggle(): ReactElement {
  const [theme, setTheme] = useState<Theme>(() => loadTheme());

  useEffect(() => {
    applyTheme(resolveTheme(theme));
  }, [theme]);

  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    function handleChange(): void {
      setTheme((current) => {
        if (current === 'system') {
          applyTheme(resolveTheme('system'));
        }
        return current;
      });
    }
    mq.addEventListener('change', handleChange);
    return () => { mq.removeEventListener('change', handleChange); };
  }, []);

  const handleClick = useCallback(() => {
    setTheme((current) => {
      const next = nextTheme(current);
      saveTheme(next);
      return next;
    });
  }, []);

  return (
    <button
      className="theme-toggle"
      onClick={handleClick}
      aria-label={THEME_LABEL[theme]}
      title={THEME_LABEL[theme]}
    >
      {THEME_ICON[theme]}
    </button>
  );
}
