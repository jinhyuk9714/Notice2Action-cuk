import { type ReactElement, useState } from 'react';
import { updateAction } from '../lib/api';
import { computeDday } from '../lib/dday';
import { categoryLabel, evidenceFieldLabel, inferredLabel } from '../lib/labels';
import type { UserProfile } from '../lib/profile';
import { computeRelevance } from '../lib/relevance';
import type { SavedActionDetail } from '../lib/types';
import { useActionEditor } from '../lib/useActionEditor';
import { type ReminderOption, useActionReminderToggle } from '../lib/useActionReminderToggle';
import { OverrideBadge } from './OverrideBadge';

type ActionDetailPanelProps = Readonly<{
  detail: SavedActionDetail;
  profile: UserProfile;
  onActionUpdated?: (updated: SavedActionDetail) => void;
}>;

const REMINDER_OPTIONS: readonly ReminderOption[] = [
  { key: 'D-7', label: 'D-7', offsetMs: 7 * 24 * 60 * 60 * 1000 },
  { key: 'D-3', label: 'D-3', offsetMs: 3 * 24 * 60 * 60 * 1000 },
  { key: 'D-1', label: 'D-1', offsetMs: 1 * 24 * 60 * 60 * 1000 },
  { key: 'D-Day', label: '당일', offsetMs: 0 },
];

export function ActionDetailPanel({ detail, profile, onActionUpdated }: ActionDetailPanelProps): ReactElement {
  const dday = computeDday(detail.dueAtIso);
  const relevance = computeRelevance(detail.eligibility, profile);
  const editor = useActionEditor({ detail, onActionUpdated });
  const reminder = useActionReminderToggle({ detail });
  const [statusUpdating, setStatusUpdating] = useState(false);

  const toggleStatus = async (): Promise<void> => {
    const next = detail.status === 'completed' ? 'pending' : 'completed';
    setStatusUpdating(true);
    try {
      const updated = await updateAction(detail.id, { status: next });
      onActionUpdated?.(updated);
    } finally {
      setStatusUpdating(false);
    }
  };

  return (
    <article className="card detail-panel">
      <div className="card-header">
        <div>
          {detail.source !== null ? (
            <p className="eyebrow">{categoryLabel(detail.source.sourceCategory)}</p>
          ) : null}
          {editor.editing ? (
            <input
              className="edit-field"
              value={editor.editTitle}
              onChange={(e) => { editor.setEditTitle(e.target.value); }}
              aria-label="제목 수정"
            />
          ) : (
            <h3>
              {detail.title}
              <OverrideBadge
                overrides={detail.overrides}
                fieldName="title"
                reverting={editor.reverting}
                onRevert={(f) => { void editor.revertField(f); }}
                showOriginalTitle={true}
              />
            </h3>
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

      {editor.editing ? (
        <textarea
          className="edit-field edit-textarea"
          value={editor.editSummary}
          onChange={(e) => { editor.setEditSummary(e.target.value); }}
          rows={3}
          aria-label="요약 수정"
        />
      ) : (
        <p className="summary">
          {detail.actionSummary}
          <OverrideBadge
            overrides={detail.overrides}
            fieldName="actionSummary"
            reverting={editor.reverting}
            onRevert={(f) => { void editor.revertField(f); }}
          />
        </p>
      )}

      {editor.editing ? (
        <div className="edit-form">
          <div className="edit-row">
            <label htmlFor="editDueLabel">마감 레이블</label>
            <input
              id="editDueLabel"
              className="edit-field"
              value={editor.editDueLabel}
              onChange={(e) => { editor.setEditDueLabel(e.target.value); }}
              placeholder="예: 3월 12일 18시"
            />
          </div>
          <div className="edit-row">
            <label htmlFor="editEligibility">대상/조건</label>
            <input
              id="editEligibility"
              className="edit-field"
              value={editor.editEligibility}
              onChange={(e) => { editor.setEditEligibility(e.target.value); }}
              placeholder="예: 재학생"
            />
          </div>
          <div className="edit-row">
            <label htmlFor="editSystemHint">시스템</label>
            <input
              id="editSystemHint"
              className="edit-field"
              value={editor.editSystemHint}
              onChange={(e) => { editor.setEditSystemHint(e.target.value); }}
              placeholder="예: TRINITY"
            />
          </div>
          {editor.editError !== null ? (
            <p className="edit-error">{editor.editError}</p>
          ) : null}
          <div className="edit-btn-row">
            <button className="edit-save-btn" onClick={() => { void editor.save(); }} disabled={editor.saving}>
              {editor.saving ? '저장 중...' : '저장'}
            </button>
            <button className="edit-cancel-btn" onClick={editor.cancelEditing} disabled={editor.saving}>
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
                <OverrideBadge
                  overrides={detail.overrides}
                  fieldName="dueAtLabel"
                  reverting={editor.reverting}
                  onRevert={(f) => { void editor.revertField(f); }}
                />
              </dd>
            </div>
            <div>
              <dt>시스템</dt>
              <dd>
                {detail.systemHint ?? '미확인'}
                <OverrideBadge
                  overrides={detail.overrides}
                  fieldName="systemHint"
                  reverting={editor.reverting}
                  onRevert={(f) => { void editor.revertField(f); }}
                />
              </dd>
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
                <OverrideBadge
                  overrides={detail.overrides}
                  fieldName="eligibility"
                  reverting={editor.reverting}
                  onRevert={(f) => { void editor.revertField(f); }}
                />
              </dd>
            </div>
            <div>
              <dt>준비물</dt>
              <dd>
                {detail.requiredItems.length > 0 ? detail.requiredItems.join(', ') : '없음'}
                <OverrideBadge
                  overrides={detail.overrides}
                  fieldName="requiredItems"
                  reverting={editor.reverting}
                  onRevert={(f) => { void editor.revertField(f); }}
                />
              </dd>
            </div>
          </dl>

          <div className="action-btn-row">
            <button className="edit-toggle-btn" onClick={editor.startEditing}>편집</button>
            <button
              className={`status-toggle-btn${detail.status === 'completed' ? ' status-completed' : ''}`}
              onClick={() => { void toggleStatus(); }}
              disabled={statusUpdating}
              aria-pressed={detail.status === 'completed'}
            >
              {detail.status === 'completed' ? '진행중으로 표시' : '완료로 표시'}
            </button>
          </div>
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
              className={`reminder-option${reminder.activeKeys.has(option.key) ? ' reminder-option-active' : ''}`}
              onClick={() => { void reminder.toggleReminder(option); }}
              aria-pressed={reminder.activeKeys.has(option.key)}
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
