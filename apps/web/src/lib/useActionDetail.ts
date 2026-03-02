import { useEffect, useRef, useState } from 'react';
import { fetchActionDetail } from './api';
import type { SavedActionDetail } from './types';

export type UseActionDetailOptions = Readonly<{
  initialActionId: string | null;
  onActionSelect: (id: string | null) => void;
}>;

export type UseActionDetailResult = {
  readonly selectedId: string | null;
  readonly detail: SavedActionDetail | null;
  readonly detailError: string | null;
  readonly handleSelect: (id: string) => void;
  readonly clearSelection: () => void;
  readonly setDetail: React.Dispatch<React.SetStateAction<SavedActionDetail | null>>;
};

export function useActionDetail({ initialActionId, onActionSelect }: UseActionDetailOptions): UseActionDetailResult {
  const [selectedId, setSelectedId] = useState<string | null>(initialActionId);
  const [detail, setDetail] = useState<SavedActionDetail | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);

  // Use ref to avoid the sync effect resetting selectedId when handleSelect changes it
  const selectedIdRef = useRef(selectedId);
  selectedIdRef.current = selectedId;

  // Sync with external initialActionId changes only
  useEffect(() => {
    if (initialActionId === null) {
      setSelectedId(null);
      setDetail(null);
      setDetailError(null);
      return;
    }
    if (initialActionId === selectedIdRef.current) {
      return;
    }
    setSelectedId(initialActionId);
    setDetail(null);
    setDetailError(null);
    fetchActionDetail(initialActionId)
      .then((result) => { setDetail(result); })
      .catch(() => { /* handled by select */ });
  }, [initialActionId]);

  function handleSelect(id: string): void {
    setSelectedId(id);
    setDetail(null);
    setDetailError(null);
    onActionSelect(id);

    fetchActionDetail(id)
      .then((result) => {
        setDetail(result);
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : '상세 정보를 불러오지 못했습니다';
        setDetailError(message);
      });
  }

  function clearSelection(): void {
    setSelectedId(null);
    setDetail(null);
    setDetailError(null);
    onActionSelect(null);
  }

  return {
    selectedId,
    detail,
    detailError,
    handleSelect,
    clearSelection,
    setDetail,
  };
}
