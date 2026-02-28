import type { ReactElement } from 'react';
import type { ExtractedAction } from '../lib/types';

type ActionCardProps = Readonly<{
  action: ExtractedAction;
}>;

export function ActionCard({ action }: ActionCardProps): ReactElement {
  return (
    <article className="card">
      <div className="card-header">
        <div>
          <p className="eyebrow">{action.sourceCategory}</p>
          <h3>{action.title}</h3>
        </div>
        <span className={action.inferred ? 'badge badge-warn' : 'badge'}>
          {action.inferred ? 'inferred' : 'confirmed'}
        </span>
      </div>

      <p className="summary">{action.actionSummary}</p>

      <dl className="meta-grid">
        <div>
          <dt>마감</dt>
          <dd>{action.dueAtLabel ?? '미확인'}</dd>
        </div>
        <div>
          <dt>시스템</dt>
          <dd>{action.systemHint ?? '미확인'}</dd>
        </div>
        <div>
          <dt>대상/조건</dt>
          <dd>{action.eligibility ?? '미확인'}</dd>
        </div>
        <div>
          <dt>준비물</dt>
          <dd>{action.requiredItems.length > 0 ? action.requiredItems.join(', ') : '없음'}</dd>
        </div>
      </dl>

      <div className="evidence-block">
        <strong>근거</strong>
        <ul>
          {action.evidence.length > 0 ? (
            action.evidence.map((item) => (
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
