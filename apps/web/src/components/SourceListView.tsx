import { useEffect, useState, type ReactElement } from 'react';
import { fetchSourceDetail, fetchSourceList } from '../lib/api';
import { categoryLabel } from '../lib/labels';
import type { SavedActionSummary, SourceDetail, SourceSummary } from '../lib/types';
import { SourceCard } from './SourceCard';

export function SourceListView(): ReactElement {
  const [sources, setSources] = useState<readonly SourceSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<SourceDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [hasNext, setHasNext] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);

  useEffect(() => {
    const isFirstPage = currentPage === 0;
    if (isFirstPage) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }
    fetchSourceList(currentPage)
      .then((result) => {
        setSources((prev) => isFirstPage ? result.sources : [...prev, ...result.sources]);
        setHasNext(result.hasNext);
        setLoading(false);
        setLoadingMore(false);
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : '소스 목록을 불러오지 못했습니다';
        setError(message);
        setLoading(false);
        setLoadingMore(false);
      });
  }, [currentPage]);

  function handleSelect(id: string): void {
    setSelectedId(id);
    setDetail(null);

    fetchSourceDetail(id)
      .then((result) => {
        setDetail(result);
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : '소스 상세 정보를 불러오지 못했습니다';
        setError(message);
      });
  }

  if (loading) {
    return <div className="inbox-state">불러오는 중...</div>;
  }

  if (error !== null) {
    return <div className="error-banner">{error}</div>;
  }

  if (sources.length === 0) {
    return (
      <div className="inbox-state">
        <p>저장된 소스가 없습니다.</p>
        <p>"액션 추출" 탭에서 텍스트를 입력하면 소스가 저장됩니다.</p>
      </div>
    );
  }

  return (
    <section className={`inbox-layout${selectedId !== null ? ' inbox-has-selection' : ''}`}>
      <div className="inbox-list">
        <div className="panel-header">
          <p className="eyebrow">소스 히스토리</p>
          <h2>{sources.length}개</h2>
        </div>
        <div className="card-list">
          {sources.map((source) => (
            <SourceCard
              key={source.id}
              source={source}
              selected={source.id === selectedId}
              onSelect={handleSelect}
            />
          ))}
          {hasNext ? (
            <button
              className="load-more-btn"
              onClick={() => { setCurrentPage((p) => p + 1); }}
              disabled={loadingMore}
            >
              {loadingMore ? '불러오는 중...' : '더 보기'}
            </button>
          ) : null}
        </div>
      </div>

      <div className="inbox-detail">
        {detail !== null ? (
          <>
            <button
              className="mobile-back-btn"
              onClick={() => { setSelectedId(null); setDetail(null); }}
            >
              &larr; 목록으로
            </button>
            <SourceDetailPanel detail={detail} />
          </>
        ) : (
          <div className="inbox-state">
            <p>목록에서 소스를 선택하세요.</p>
          </div>
        )}
      </div>
    </section>
  );
}

function SourceDetailPanel({ detail }: Readonly<{ detail: SourceDetail }>): ReactElement {
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
