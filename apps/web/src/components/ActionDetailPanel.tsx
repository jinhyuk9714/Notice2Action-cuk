import { useState, type ReactElement } from 'react';
import { updateAction } from '../lib/api';
import { computeDday } from '../lib/dday';
import { categoryLabel, evidenceFieldLabel, inferredLabel } from '../lib/labels';
import type { UserProfile } from '../lib/profile';
import { computeRelevance } from '../lib/relevance';
import type { ReminderOffsetKey } from '../lib/reminder';
import {
  getRemindersForAction,
  saveReminder,
  removeReminder,
  requestNotificationPermission,
} from '../lib/reminder';
import type { SavedActionDetail } from '../lib/types';

type ActionDetailPanelProps = Readonly<{
  detail: SavedActionDetail;
  profile: UserProfile;
  onActionUpdated?: (updated: SavedActionDetail) => void;
}>;

type ReminderOption = Readonly<{
  key: ReminderOffsetKey;
  label: string;
  offsetMs: number;
}>;

const REMINDER_OPTIONS: readonly ReminderOption[] = [
  { key: 'D-7', label: 'D-7', offsetMs: 7 * 24 * 60 * 60 * 1000 },
  { key: 'D-3', label: 'D-3', offsetMs: 3 * 24 * 60 * 60 * 1000 },
  { key: 'D-1', label: 'D-1', offsetMs: 1 * 24 * 60 * 60 * 1000 },
  { key: 'D-Day', label: '당일', offsetMs: 0 },
];

function getActiveOffsetKeys(actionId: string): Set<ReminderOffsetKey> {
  const reminders = getRemindersForAction(actionId);
  return new Set(reminders.map((r) => r.offsetKey));
}

export function ActionDetailPanel({ detail, profile, onActionUpdated }: ActionDetailPanelProps): ReactElement {
  const dday = computeDday(detail.dueAtIso);
  const relevance = computeRelevance(detail.eligibility, profile);
  const [activeKeys, setActiveKeys] = useState<Set<ReminderOffsetKey>>(
    () => getActiveOffsetKeys(detail.id),
  );
  const [editing, setEditing] = useState(false);
  const [editTitle, setEditTitle] = useState(detail.title);
  const [editSummary, setEditSummary] = useState(detail.actionSummary);
  const [editDueLabel, setEditDueLabel] = useState(detail.dueAtLabel ?? '');
  const [editEligibility, setEditEligibility] = useState(detail.eligibility ?? '');
  const [editSystemHint, setEditSystemHint] = useState(detail.systemHint ?? '');
  const [saving, setSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

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

  async function handleSave(): Promise<void> {
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

  async function handleToggle(option: ReminderOption): Promise<void> {
    if (activeKeys.has(option.key)) {
      removeReminder(detail.id, option.key);
      setActiveKeys((prev) => {
        const next = new Set(prev);
        next.delete(option.key);
        return next;
      });
      return;
    }

    const granted = await requestNotificationPermission();
    if (!granted) return;
    if (detail.dueAtIso === null) return;

    const dueDate = new Date(detail.dueAtIso);

    let remindAt: Date;
    if (option.offsetMs === 0) {
      remindAt = new Date(dueDate.getFullYear(), dueDate.getMonth(), dueDate.getDate(), 0, 0, 0);
    } else {
      remindAt = new Date(dueDate.getTime() - option.offsetMs);
    }

    const remindAtIso =
      remindAt.getTime() < Date.now() ? new Date().toISOString() : remindAt.toISOString();

    saveReminder({
      actionId: detail.id,
      offsetKey: option.key,
      remindAtIso,
      title: detail.title,
      dueLabel: detail.dueAtLabel ?? dueDate.toLocaleDateString('ko-KR'),
      dismissed: false,
    });

    setActiveKeys((prev) => new Set([...prev, option.key]));
  }

  return (
    <article className="card detail-panel">
      <div className="card-header">
        <div>
          {detail.source !== null ? (
            <p className="eyebrow">{categoryLabel(detail.source.sourceCategory)}</p>
          ) : null}
          {editing ? (
            <input
              className="edit-field"
              value={editTitle}
              onChange={(e) => { setEditTitle(e.target.value); }}
            />
          ) : (
            <h3>{detail.title}</h3>
          )}
        </div>
        <div className="badge-group">
          <span className={`confidence-badge ${detail.confidenceScore >= 0.75 ? 'confidence-high' : detail.confidenceScore >= 0.5 ? 'confidence-medium' : 'confidence-low'}`}>
            {Math.round(detail.confidenceScore * 100)}% 신뢰도
          </span>
          <span className={detail.inferred ? 'badge badge-warn' : 'badge'}>
            {inferredLabel(detail.inferred)}
          </span>
        </div>
      </div>

      {editing ? (
        <textarea
          className="edit-field edit-textarea"
          value={editSummary}
          onChange={(e) => { setEditSummary(e.target.value); }}
          rows={3}
        />
      ) : (
        <p className="summary">{detail.actionSummary}</p>
      )}

      {editing ? (
        <div className="edit-form">
          <div className="edit-row">
            <label>마감 레이블</label>
            <input
              className="edit-field"
              value={editDueLabel}
              onChange={(e) => { setEditDueLabel(e.target.value); }}
              placeholder="예: 3월 12일 18시"
            />
          </div>
          <div className="edit-row">
            <label>대상/조건</label>
            <input
              className="edit-field"
              value={editEligibility}
              onChange={(e) => { setEditEligibility(e.target.value); }}
              placeholder="예: 재학생"
            />
          </div>
          <div className="edit-row">
            <label>시스템</label>
            <input
              className="edit-field"
              value={editSystemHint}
              onChange={(e) => { setEditSystemHint(e.target.value); }}
              placeholder="예: TRINITY"
            />
          </div>
          {editError !== null ? (
            <p className="edit-error">{editError}</p>
          ) : null}
          <div className="edit-btn-row">
            <button className="edit-save-btn" onClick={() => { void handleSave(); }} disabled={saving}>
              {saving ? '저장 중...' : '저장'}
            </button>
            <button className="edit-cancel-btn" onClick={cancelEditing} disabled={saving}>
              취소
            </button>
          </div>
        </div>
      ) : (
        <>
          <dl className="meta-grid">
            <div>
              <dt>마감</dt>
              <dd>
                {detail.dueAtLabel ?? '미확인'}
                {dday !== null ? (
                  <span className={`dday-badge dday-${dday.urgency} dday-inline`}>
                    {dday.label}
                  </span>
                ) : null}
              </dd>
            </div>
            <div>
              <dt>시스템</dt>
              <dd>{detail.systemHint ?? '미확인'}</dd>
            </div>
            <div>
              <dt>대상/조건</dt>
              <dd>
                {detail.eligibility ?? '미확인'}
                {relevance.level === 'relevant' ? (
                  <span className="relevance-badge relevance-relevant dday-inline" title={relevance.reason ?? undefined}>관련</span>
                ) : relevance.level === 'not_relevant' ? (
                  <span className="relevance-badge relevance-not-relevant dday-inline" title={relevance.reason ?? undefined}>해당없음</span>
                ) : null}
              </dd>
            </div>
            <div>
              <dt>준비물</dt>
              <dd>{detail.requiredItems.length > 0 ? detail.requiredItems.join(', ') : '없음'}</dd>
            </div>
          </dl>

          <button className="edit-toggle-btn" onClick={startEditing}>편집</button>
        </>
      )}

      {detail.dueAtIso !== null ? (
        <div className="calendar-export-row">
          <a
            className="calendar-btn"
            href={`/api/v1/actions/${encodeURIComponent(detail.id)}/calendar.ics`}
            download
          >
            일정 추가 (.ics)
          </a>
        </div>
      ) : null}

      {detail.dueAtIso !== null ? (
        <div className="reminder-options">
          <span className="reminder-options-label">리마인더</span>
          {REMINDER_OPTIONS.map((option) => (
            <button
              key={option.key}
              className={`reminder-option${activeKeys.has(option.key) ? ' reminder-option-active' : ''}`}
              onClick={() => { void handleToggle(option); }}
            >
              {option.label}
            </button>
          ))}
        </div>
      ) : null}

      {detail.source !== null ? (
        <div className="source-info">
          <strong>출처</strong>
          <p>{detail.source.title ?? '제목 없음'} ({categoryLabel(detail.source.sourceCategory)})</p>
        </div>
      ) : null}

      <div className="evidence-block">
        <strong>근거</strong>
        <ul>
          {detail.evidence.length > 0 ? (
            detail.evidence.map((item) => (
              <li key={`${item.fieldName}-${item.snippet}`}>
                <span className="evidence-field">{evidenceFieldLabel(item.fieldName)}</span>
                <span>{item.snippet}</span>
              </li>
            ))
          ) : (
            <li>근거 snippet 없음</li>
          )}
        </ul>
      </div>
    </article>
  );
}
