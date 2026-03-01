import type { ReactElement } from 'react';
import { categoryLabel } from '../lib/labels';
import type { SourceSummary } from '../lib/types';

type SourceCardProps = Readonly<{
  source: SourceSummary;
  selected: boolean;
  onSelect: (id: string) => void;
}>;

export function SourceCard({ source, selected, onSelect }: SourceCardProps): ReactElement {
  const date = new Date(source.createdAt).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });

  return (
    <div
      className={`card source-card${selected ? ' source-card-selected' : ''}`}
      onClick={() => { onSelect(source.id); }}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => { if (e.key === 'Enter') onSelect(source.id); }}
    >
      <div className="source-card-header">
        <span className="badge">{categoryLabel(source.sourceCategory)}</span>
        <span className="source-card-date">{date}</span>
      </div>
      <h4 className="source-card-title">{source.title ?? '제목 없음'}</h4>
      <p className="source-card-meta">{source.actionCount}개 액션</p>
    </div>
  );
}
