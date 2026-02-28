import type { ReactElement } from 'react';
import type { SavedActionSummary } from '../lib/types';

type ActionSummaryCardProps = Readonly<{
  action: SavedActionSummary;
  selected: boolean;
  onSelect: (id: string) => void;
}>;

export function ActionSummaryCard({ action, selected, onSelect }: ActionSummaryCardProps): ReactElement {
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
        {action.dueAtLabel !== null ? (
          <span className="summary-card-due">{action.dueAtLabel}</span>
        ) : null}
        <span className="summary-card-date">
          {new Date(action.createdAt).toLocaleDateString('ko-KR')}
        </span>
      </div>
    </article>
  );
}
