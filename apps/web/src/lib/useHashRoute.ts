import { useCallback, useEffect, useState } from 'react';
import { navigateTo, parseHash, type Route } from './router';

export function useHashRoute(): readonly [Route, typeof navigateTo] {
  const [route, setRoute] = useState<Route>(() => parseHash(window.location.hash));

  useEffect(() => {
    function handleHashChange(): void {
      setRoute(parseHash(window.location.hash));
    }
    window.addEventListener('hashchange', handleHashChange);
    return () => { window.removeEventListener('hashchange', handleHashChange); };
  }, []);

  const navigate = useCallback((r: Route) => {
    navigateTo(r);
  }, []);

  return [route, navigate] as const;
}
