import { useState, type ReactElement } from 'react';
import { computeDday } from '../lib/dday';
import {
  loadReminders,
  saveReminder,
  removeReminder,
  requestNotificationPermission,
} from '../lib/reminder';
import type { SavedActionDetail } from '../lib/types';

type ActionDetailPanelProps = Readonly<{
  detail: SavedActionDetail;
}>;

export function ActionDetailPanel({ detail }: ActionDetailPanelProps): ReactElement {
  const dday = computeDday(detail.dueAtIso);
  const existingReminder = loadReminders().find(r => r.actionId === detail.id);
  const [hasReminder, setHasReminder] = useState(existingReminder !== undefined && !existingReminder.dismissed);

  async function handleToggleReminder(): Promise<void> {
    if (hasReminder) {
      removeReminder(detail.id);
      setHasReminder(false);
      return;
    }

    const granted = await requestNotificationPermission();
    if (!granted) return;
    if (detail.dueAtIso === null) return;

    const dueDate = new Date(detail.dueAtIso);
    const remindAt = new Date(dueDate.getTime() - 24 * 60 * 60 * 1000);

    const remindAtIso = remindAt.getTime() < Date.now()
      ? new Date().toISOString()
      : remindAt.toISOString();

    saveReminder({
      actionId: detail.id,
      remindAtIso,
      title: detail.title,
      dueLabel: detail.dueAtLabel ?? dueDate.toLocaleDateString('ko-KR'),
      dismissed: false,
    });

    setHasReminder(true);
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
          <dd>{detail.eligibility ?? '미확인'}</dd>
        </div>
        <div>
          <dt>준비물</dt>
          <dd>{detail.requiredItems.length > 0 ? detail.requiredItems.join(', ') : '없음'}</dd>
        </div>
      </dl>

      {detail.dueAtIso !== null ? (
        <button
          className={`reminder-btn${hasReminder ? ' reminder-btn-active' : ''}`}
          onClick={() => { void handleToggleReminder(); }}
        >
          {hasReminder ? '리마인더 해제' : '리마인더 설정 (D-1)'}
        </button>
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
