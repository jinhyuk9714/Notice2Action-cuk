import type { ReactElement } from 'react';
import { computeDday } from '../lib/dday';
import { hasActiveReminders } from '../lib/reminder';
import type { RelevanceResult } from '../lib/relevance';
import type { SavedActionSummary } from '../lib/types';

type ActionSummaryCardProps = Readonly<{
  action: SavedActionSummary;
  selected: boolean;
  onSelect: (id: string) => void;
  relevance: RelevanceResult;
}>;

export function ActionSummaryCard({ action, selected, onSelect, relevance }: ActionSummaryCardProps): ReactElement {
  const dday = computeDday(action.dueAtIso);
  const hasReminder = hasActiveReminders(action.id);

  return (
    <article
      className={`summary-card${selected ? ' summary-card-selected' : ''}`}
      onClick={() => { onSelect(action.id); }}
    >
      <div className="summary-card-header">
        <div>
          {action.sourceCategory !== null ? (
            <span className="eyebrow">{action.sourceCategory}</span>
          ) : null}
          <h4>{action.title}</h4>
        </div>
        {relevance.level === 'relevant' ? (
          <span className="relevance-badge relevance-relevant" title={relevance.reason ?? undefined}>관련</span>
        ) : relevance.level === 'not_relevant' ? (
          <span className="relevance-badge relevance-not-relevant" title={relevance.reason ?? undefined}>해당없음</span>
        ) : null}
      </div>

      <p className="summary-card-body">{action.actionSummary}</p>

      <div className="summary-card-footer">
        <span className="summary-card-due-group">
          {dday !== null ? (
            <span className={`dday-badge dday-${dday.urgency}`}>{dday.label}</span>
          ) : null}
          {action.dueAtLabel !== null ? (
            <span className="summary-card-due">{action.dueAtLabel}</span>
          ) : null}
        </span>
        <span className="summary-card-date">
          {hasReminder ? <span className="reminder-indicator" title="리마인더 설정됨">&#128276;</span> : null}
          {new Date(action.createdAt).toLocaleDateString('ko-KR')}
        </span>
      </div>
    </article>
  );
}
