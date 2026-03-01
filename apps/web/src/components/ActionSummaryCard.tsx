import type { ReactElement } from 'react';
import { computeDday } from '../lib/dday';
import type { SavedActionSummary } from '../lib/types';

type ActionSummaryCardProps = Readonly<{
  action: SavedActionSummary;
  selected: boolean;
  onSelect: (id: string) => void;
}>;

export function ActionSummaryCard({ action, selected, onSelect }: ActionSummaryCardProps): ReactElement {
  const dday = computeDday(action.dueAtIso);

  return (
    <article
      className={`summary-card${selected ? ' summary-card-selected' : ''}`}
      onClick={() => { onSelect(action.id); }}
    >
      <div className="summary-card-header">
        {action.sourceCategory !== null ? (
          <span className="eyebrow">{action.sourceCategory}</span>
        ) : null}
        <h4>{action.title}</h4>
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
          {new Date(action.createdAt).toLocaleDateString('ko-KR')}
        </span>
      </div>
    </article>
  );
}
