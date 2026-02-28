import type { ReactElement } from 'react';
import type { SavedActionDetail } from '../lib/types';

type ActionDetailPanelProps = Readonly<{
  detail: SavedActionDetail;
}>;

export function ActionDetailPanel({ detail }: ActionDetailPanelProps): ReactElement {
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
          <dd>{detail.dueAtLabel ?? '미확인'}</dd>
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
