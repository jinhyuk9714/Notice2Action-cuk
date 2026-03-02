import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { makeActionDetail } from '../test-helpers';
import { useActionEditor } from './useActionEditor';

vi.mock('./api', () => ({
  updateAction: vi.fn(),
  revertActionField: vi.fn(),
}));

import { revertActionField, updateAction } from './api';

const mockUpdateAction = vi.mocked(updateAction);
const mockRevertActionField = vi.mocked(revertActionField);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('useActionEditor', () => {
  it('initializes with detail values', () => {
    const detail = makeActionDetail({
      title: '초기 제목',
      actionSummary: '초기 요약',
      dueAtLabel: '3월 15일까지',
      eligibility: '재학생',
      systemHint: 'TRINITY',
    });

    const { result } = renderHook(() => useActionEditor({ detail }));

    expect(result.current.editing).toBe(false);
    expect(result.current.editTitle).toBe('초기 제목');
    expect(result.current.editSummary).toBe('초기 요약');
    expect(result.current.editDueLabel).toBe('3월 15일까지');
    expect(result.current.editEligibility).toBe('재학생');
    expect(result.current.editSystemHint).toBe('TRINITY');
  });

  it('starts and cancels editing mode', () => {
    const detail = makeActionDetail();
    const { result } = renderHook(() => useActionEditor({ detail }));

    act(() => {
      result.current.startEditing();
    });
    expect(result.current.editing).toBe(true);

    act(() => {
      result.current.cancelEditing();
    });
    expect(result.current.editing).toBe(false);
    expect(result.current.editError).toBeNull();
  });

  it('validates blank title on save', async () => {
    const detail = makeActionDetail();
    const { result } = renderHook(() => useActionEditor({ detail }));

    act(() => {
      result.current.setEditTitle('   ');
    });
    await act(async () => {
      await result.current.save();
    });

    expect(result.current.editError).toBe('제목은 비워둘 수 없습니다.');
    expect(mockUpdateAction).not.toHaveBeenCalled();
  });

  it('saves and calls onActionUpdated', async () => {
    const updated = makeActionDetail({ title: '수정된 제목' });
    mockUpdateAction.mockResolvedValue(updated);
    const onActionUpdated = vi.fn();
    const detail = makeActionDetail();

    const { result } = renderHook(() =>
      useActionEditor({ detail, onActionUpdated }),
    );

    act(() => {
      result.current.startEditing();
      result.current.setEditTitle('수정된 제목');
    });
    await act(async () => {
      await result.current.save();
    });

    expect(mockUpdateAction).toHaveBeenCalledWith('act-1', expect.objectContaining({
      title: '수정된 제목',
    }));
    expect(onActionUpdated).toHaveBeenCalledWith(updated);
    expect(result.current.editing).toBe(false);
  });

  it('sets editError when save fails', async () => {
    mockUpdateAction.mockRejectedValue(new Error('저장 실패'));
    const detail = makeActionDetail();
    const { result } = renderHook(() => useActionEditor({ detail }));

    await act(async () => {
      await result.current.save();
    });

    expect(result.current.editError).toBe('저장 실패');
    expect(result.current.saving).toBe(false);
  });

  it('reverts field and calls onActionUpdated', async () => {
    const updated = makeActionDetail({ title: '원래 제목', overrides: [] });
    mockRevertActionField.mockResolvedValue(updated);
    const onActionUpdated = vi.fn();

    const { result } = renderHook(() =>
      useActionEditor({
        detail: makeActionDetail({
          overrides: [{ fieldName: 'title', machineValue: '원래 제목' }],
        }),
        onActionUpdated,
      }),
    );

    await act(async () => {
      await result.current.revertField('title');
    });

    expect(mockRevertActionField).toHaveBeenCalledWith('act-1', 'title');
    expect(onActionUpdated).toHaveBeenCalledWith(updated);
    expect(result.current.reverting).toBeNull();
  });

  it('resets state when detail target changes', async () => {
    const detail1 = makeActionDetail({ id: 'act-1', title: 'A' });
    const detail2 = makeActionDetail({ id: 'act-2', title: 'B' });

    const { result, rerender } = renderHook(
      ({ detail }) => useActionEditor({ detail }),
      { initialProps: { detail: detail1 } },
    );

    act(() => {
      result.current.startEditing();
      result.current.setEditTitle('임시 제목');
    });
    expect(result.current.editing).toBe(true);

    rerender({ detail: detail2 });

    await waitFor(() => {
      expect(result.current.editing).toBe(false);
      expect(result.current.editTitle).toBe('B');
      expect(result.current.editError).toBeNull();
    });
  });
});
