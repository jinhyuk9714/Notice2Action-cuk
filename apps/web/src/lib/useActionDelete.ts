import { useEffect, useState } from 'react';
import { deleteAction } from './api';
import type { SavedActionSummary } from './types';

export type UseActionDeleteOptions = Readonly<{
  actions: readonly SavedActionSummary[];
  onItemsChange: React.Dispatch<React.SetStateAction<readonly SavedActionSummary[]>>;
  selectedId: string | null;
  clearSelection: () => void;
}>;

export type UseActionDeleteResult = {
  readonly deletingId: string | null;
  readonly pendingDeleteId: string | null;
  readonly deleteToast: string | null;
  readonly deleteError: string | null;
  readonly requestDelete: (id: string) => void;
  readonly confirmDelete: () => Promise<void>;
  readonly cancelDelete: () => void;
};

export function useActionDelete({
  actions,
  onItemsChange,
  selectedId,
  clearSelection,
}: UseActionDeleteOptions): UseActionDeleteResult {
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);
  const [deleteToast, setDeleteToast] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // Auto-dismiss toast
  useEffect(() => {
    if (deleteToast === null) return;
    const timer = setTimeout(() => { setDeleteToast(null); }, 2500);
    return () => { clearTimeout(timer); };
  }, [deleteToast]);

  function requestDelete(id: string): void {
    setPendingDeleteId(id);
  }

  async function confirmDelete(): Promise<void> {
    if (pendingDeleteId === null) return;
    const id = pendingDeleteId;
    const deletedAction = actions.find((a) => a.id === id);
    setPendingDeleteId(null);
    setDeletingId(id);
    setDeleteError(null);
    try {
      await deleteAction(id);
      onItemsChange((prev) => prev.filter((a) => a.id !== id));
      if (selectedId === id) {
        clearSelection();
      }
      setDeleteToast(deletedAction !== undefined
        ? `"${deletedAction.title}" 삭제 완료`
        : '삭제 완료');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '삭제 중 오류가 발생했습니다';
      setDeleteError(message);
    } finally {
      setDeletingId(null);
    }
  }

  function cancelDelete(): void {
    setPendingDeleteId(null);
  }

  return {
    deletingId,
    pendingDeleteId,
    deleteToast,
    deleteError,
    requestDelete,
    confirmDelete,
    cancelDelete,
  };
}
