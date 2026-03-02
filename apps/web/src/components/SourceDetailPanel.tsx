import type { ReactElement } from 'react';
import { categoryLabel } from '../lib/labels';
import type { SavedActionSummary, SourceDetail } from '../lib/types';

export function SourceDetailPanel({ detail }: Readonly<{ detail: SourceDetail }>): ReactElement {
  const date = new Date(detail.createdAt).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <article className="card detail-panel">
      <div className="card-header">
        <div>
          <p className="eyebrow">{categoryLabel(detail.sourceCategory)}</p>
          <h3>{detail.title ?? '제목 없음'}</h3>
        </div>
      </div>

      <dl className="meta-grid">
        <div>
          <dt>등록일</dt>
          <dd>{date}</dd>
        </div>
        {detail.sourceUrl !== null ? (
          <div>
            <dt>URL</dt>
            <dd><a href={detail.sourceUrl} target="_blank" rel="noopener noreferrer">{detail.sourceUrl}</a></dd>
          </div>
        ) : null}
        <div>
          <dt>추출된 액션</dt>
          <dd>{detail.actions.length}개</dd>
        </div>
      </dl>

      {detail.actions.length > 0 ? (
        <div className="source-actions-list">
          <strong>관련 액션</strong>
          <ul>
            {detail.actions.map((action: SavedActionSummary) => (
              <li key={action.id} className="source-action-item">
                <span className="source-action-title">{action.title}</span>
                {action.dueAtLabel !== null ? (
                  <span className="source-action-due">{action.dueAtLabel}</span>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="empty-hint">이 소스에서 추출된 액션이 없습니다.</p>
      )}
    </article>
  );
}
