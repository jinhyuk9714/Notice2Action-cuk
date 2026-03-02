import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useHashRoute } from './useHashRoute';
vi.mock('./router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./router')>();
  return {
    ...actual,
    navigateTo: vi.fn(),
  };
});

import { navigateTo } from './router';

function TestComponent() {
  const [route, navigate] = useHashRoute();
  return (
    <div>
      <span data-testid="view">{route.view}</span>
      {'actionId' in route ? <span data-testid="actionId">{route.actionId ?? 'null'}</span> : null}
      {'sourceId' in route ? <span data-testid="sourceId">{route.sourceId ?? 'null'}</span> : null}
      <button onClick={() => { navigate({ view: 'inbox', actionId: null, filters: {} }); }}>
        go inbox
      </button>
    </div>
  );
}

describe('useHashRoute', () => {
  beforeEach(() => {
    window.location.hash = '';
    vi.clearAllMocks();
  });

  afterEach(() => {
    window.location.hash = '';
  });

  it('returns initial route from empty hash as extract', () => {
    render(<TestComponent />);
    expect(screen.getByTestId('view').textContent).toBe('extract');
  });

  it('returns inbox route when hash is #/inbox', () => {
    window.location.hash = '#/inbox';
    render(<TestComponent />);
    expect(screen.getByTestId('view').textContent).toBe('inbox');
  });

  it('returns inbox route with actionId', () => {
    window.location.hash = '#/inbox/act-1';
    render(<TestComponent />);
    expect(screen.getByTestId('view').textContent).toBe('inbox');
    expect(screen.getByTestId('actionId').textContent).toBe('act-1');
  });

  it('returns sources route with sourceId', () => {
    window.location.hash = '#/sources/src-1';
    render(<TestComponent />);
    expect(screen.getByTestId('view').textContent).toBe('sources');
    expect(screen.getByTestId('sourceId').textContent).toBe('src-1');
  });

  it('updates route when hashchange fires', () => {
    render(<TestComponent />);
    expect(screen.getByTestId('view').textContent).toBe('extract');

    act(() => {
      window.location.hash = '#/inbox';
      window.dispatchEvent(new HashChangeEvent('hashchange'));
    });
    expect(screen.getByTestId('view').textContent).toBe('inbox');
  });

  it('calls navigateTo when navigate function is invoked', async () => {
    render(<TestComponent />);
    const { fireEvent } = await import('@testing-library/react');
    fireEvent.click(screen.getByText('go inbox'));
    expect(navigateTo).toHaveBeenCalledWith({ view: 'inbox', actionId: null, filters: {} });
  });

  it('removes listener on unmount', () => {
    const removeSpy = vi.spyOn(window, 'removeEventListener');
    const { unmount } = render(<TestComponent />);
    unmount();
    expect(removeSpy).toHaveBeenCalledWith('hashchange', expect.any(Function));
    removeSpy.mockRestore();
  });

  it('parses inbox filters from hash', () => {
    window.location.hash = '#/inbox?sort=recent&q=장학';
    render(<TestComponent />);
    expect(screen.getByTestId('view').textContent).toBe('inbox');
  });
});
