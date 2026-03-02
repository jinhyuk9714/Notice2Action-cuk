import { useEffect, useState } from 'react';
import { revertActionField, updateAction } from './api';
import type { SavedActionDetail } from './types';

export type UseActionEditorOptions = Readonly<{
  detail: SavedActionDetail;
  onActionUpdated?: (updated: SavedActionDetail) => void;
}>;

export type UseActionEditorResult = Readonly<{
  editing: boolean;
  editTitle: string;
  setEditTitle: React.Dispatch<React.SetStateAction<string>>;
  editSummary: string;
  setEditSummary: React.Dispatch<React.SetStateAction<string>>;
  editDueLabel: string;
  setEditDueLabel: React.Dispatch<React.SetStateAction<string>>;
  editEligibility: string;
  setEditEligibility: React.Dispatch<React.SetStateAction<string>>;
  editSystemHint: string;
  setEditSystemHint: React.Dispatch<React.SetStateAction<string>>;
  saving: boolean;
  editError: string | null;
  reverting: string | null;
  startEditing: () => void;
  cancelEditing: () => void;
  save: () => Promise<void>;
  revertField: (fieldName: string) => Promise<void>;
}>;

export function useActionEditor({
  detail,
  onActionUpdated,
}: UseActionEditorOptions): UseActionEditorResult {
  const [editing, setEditing] = useState(false);
  const [editTitle, setEditTitle] = useState(detail.title);
  const [editSummary, setEditSummary] = useState(detail.actionSummary);
  const [editDueLabel, setEditDueLabel] = useState(detail.dueAtLabel ?? '');
  const [editEligibility, setEditEligibility] = useState(detail.eligibility ?? '');
  const [editSystemHint, setEditSystemHint] = useState(detail.systemHint ?? '');
  const [saving, setSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [reverting, setReverting] = useState<string | null>(null);

  useEffect(() => {
    setEditing(false);
    setEditTitle(detail.title);
    setEditSummary(detail.actionSummary);
    setEditDueLabel(detail.dueAtLabel ?? '');
    setEditEligibility(detail.eligibility ?? '');
    setEditSystemHint(detail.systemHint ?? '');
    setEditError(null);
    setSaving(false);
    setReverting(null);
  }, [
    detail.id,
    detail.title,
    detail.actionSummary,
    detail.dueAtLabel,
    detail.eligibility,
    detail.systemHint,
  ]);

  function startEditing(): void {
    setEditTitle(detail.title);
    setEditSummary(detail.actionSummary);
    setEditDueLabel(detail.dueAtLabel ?? '');
    setEditEligibility(detail.eligibility ?? '');
    setEditSystemHint(detail.systemHint ?? '');
    setEditError(null);
    setEditing(true);
  }

  function cancelEditing(): void {
    setEditing(false);
    setEditError(null);
  }

  async function save(): Promise<void> {
    if (editTitle.trim().length === 0) {
      setEditError('제목은 비워둘 수 없습니다.');
      return;
    }
    setSaving(true);
    setEditError(null);
    try {
      const updated = await updateAction(detail.id, {
        title: editTitle.trim(),
        actionSummary: editSummary.trim(),
        dueAtLabel: editDueLabel.trim().length > 0 ? editDueLabel.trim() : undefined,
        eligibility: editEligibility.trim().length > 0 ? editEligibility.trim() : undefined,
        systemHint: editSystemHint.trim().length > 0 ? editSystemHint.trim() : undefined,
      });
      setEditing(false);
      onActionUpdated?.(updated);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '수정 중 오류가 발생했습니다';
      setEditError(message);
    } finally {
      setSaving(false);
    }
  }

  async function revertField(fieldName: string): Promise<void> {
    setReverting(fieldName);
    try {
      const updated = await revertActionField(detail.id, fieldName);
      onActionUpdated?.(updated);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '되돌리기 중 오류가 발생했습니다';
      setEditError(message);
    } finally {
      setReverting(null);
    }
  }

  return {
    editing,
    editTitle,
    setEditTitle,
    editSummary,
    setEditSummary,
    editDueLabel,
    setEditDueLabel,
    editEligibility,
    setEditEligibility,
    editSystemHint,
    setEditSystemHint,
    saving,
    editError,
    reverting,
    startEditing,
    cancelEditing,
    save,
    revertField,
  };
}
