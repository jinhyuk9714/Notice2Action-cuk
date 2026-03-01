import { useState, type ReactElement } from 'react';
import { computeDday } from '../lib/dday';
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

export function ActionDetailPanel({ detail, profile }: ActionDetailPanelProps): ReactElement {
  const dday = computeDday(detail.dueAtIso);
  const relevance = computeRelevance(detail.eligibility, profile);
  const [activeKeys, setActiveKeys] = useState<Set<ReminderOffsetKey>>(
    () => getActiveOffsetKeys(detail.id),
  );

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
            <p className="eyebrow">{detail.source.sourceCategory}</p>
          ) : null}
          <h3>{detail.title}</h3>
        </div>
        <span className={detail.inferred ? 'badge badge-warn' : 'badge'}>
          {detail.inferred ? 'inferred' : 'confirmed'}
        </span>
      </div>

      <p className="summary">{detail.actionSummary}</p>

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
          <p>{detail.source.title ?? '제목 없음'} ({detail.source.sourceCategory})</p>
        </div>
      ) : null}

      <div className="evidence-block">
        <strong>근거</strong>
        <ul>
          {detail.evidence.length > 0 ? (
            detail.evidence.map((item) => (
              <li key={`${item.fieldName}-${item.snippet}`}>
                <span className="evidence-field">{item.fieldName}</span>
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
