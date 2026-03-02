import { useEffect, useRef, useState } from 'react';

export type PageResult<T> = Readonly<{
  items: T[];
  hasNext: boolean;
}>;

export type UsePagedListOptions<T> = Readonly<{
  fetchPage: (page: number) => Promise<PageResult<T>>;
  deps: readonly unknown[];
}>;

export type UsePagedListResult<T> = {
  readonly items: readonly T[];
  readonly loading: boolean;
  readonly error: string | null;
  readonly hasNext: boolean;
  readonly loadingMore: boolean;
  readonly loadMoreError: boolean;
  readonly retry: () => void;
  readonly loadMore: () => void;
  readonly setItems: React.Dispatch<React.SetStateAction<readonly T[]>>;
  readonly clearError: () => void;
};

export function usePagedList<T>(options: UsePagedListOptions<T>): UsePagedListResult<T> {
  const [items, setItems] = useState<readonly T[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [hasNext, setHasNext] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [retryKey, setRetryKey] = useState<number>(0);
  const [loadMoreError, setLoadMoreError] = useState<boolean>(false);
  const [depsVersion, setDepsVersion] = useState<number>(0);

  // Use ref for fetchPage to avoid infinite loops when callers create it inline
  const fetchPageRef = useRef(options.fetchPage);
  fetchPageRef.current = options.fetchPage;

  // Detect deps changes and reset pagination
  const prevDepsJson = useRef(JSON.stringify(options.deps));

  useEffect(() => {
    const newJson = JSON.stringify(options.deps);
    if (newJson !== prevDepsJson.current) {
      prevDepsJson.current = newJson;
      setItems([]);
      setCurrentPage(0);
      setHasNext(false);
      setLoadMoreError(false);
      setDepsVersion((v) => v + 1);
    }
  });

  // Fetch effect — triggered by page change, retry, or deps version change
  useEffect(() => {
    let cancelled = false;
    const isFirstPage = currentPage === 0;
    if (isFirstPage) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }

    fetchPageRef.current(currentPage)
      .then((result) => {
        if (cancelled) return;
        setItems((prev) => isFirstPage ? result.items : [...prev, ...result.items]);
        setHasNext(result.hasNext);
        setLoading(false);
        setLoadingMore(false);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        if (isFirstPage) {
          const message = err instanceof Error ? err.message : '목록을 불러오지 못했습니다';
          setError(message);
          setLoading(false);
        } else {
          setLoadMoreError(true);
          setLoadingMore(false);
        }
      });

    return () => { cancelled = true; };
  }, [currentPage, retryKey, depsVersion]);

  function retry(): void {
    setError(null);
    setItems([]);
    setCurrentPage(0);
    setRetryKey((k) => k + 1);
  }

  function loadMore(): void {
    if (loadMoreError) {
      setLoadMoreError(false);
      setRetryKey((k) => k + 1);
    } else {
      setCurrentPage((p) => p + 1);
    }
  }

  function clearError(): void {
    setError(null);
  }

  return {
    items,
    loading,
    error,
    hasNext,
    loadingMore,
    loadMoreError,
    retry,
    loadMore,
    setItems,
    clearError,
  };
}
