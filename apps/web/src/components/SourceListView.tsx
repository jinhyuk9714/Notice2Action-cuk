import { useCallback, useEffect, useRef, useState, type ReactElement } from 'react';
import { fetchSourceDetail, fetchSourceList } from '../lib/api';
import { categoryLabel } from '../lib/labels';
import type { SavedActionSummary, SourceDetail, SourceSummary } from '../lib/types';
import { usePagedList } from '../lib/usePagedList';
import { SkeletonCard } from './SkeletonCard';
import { SourceCard } from './SourceCard';

type SourceListViewProps = Readonly<{
  initialSourceId: string | null;
  onSourceSelect: (id: string | null) => void;
}>;

export function SourceListView({ initialSourceId, onSourceSelect }: SourceListViewProps): ReactElement {
  const [selectedId, setSelectedId] = useState<string | null>(initialSourceId);
  const [detail, setDetail] = useState<SourceDetail | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);

  const fetchPage = useCallback(
    (page: number) =>
      fetchSourceList(page)
        .then((r) => ({ items: [...r.sources], hasNext: r.hasNext })),
    [],
  );

  const list = usePagedList<SourceSummary>({
    fetchPage,
    deps: [],
  });

  // Sync initialSourceId from URL changes
  const selectedIdRef = useRef(selectedId);
  selectedIdRef.current = selectedId;

  useEffect(() => {
    if (initialSourceId === null) {
      setSelectedId(null);
      setDetail(null);
      setDetailError(null);
      return;
    }
    if (initialSourceId === selectedIdRef.current) {
      return;
    }
    setSelectedId(initialSourceId);
    setDetail(null);
    setDetailError(null);
    fetchSourceDetail(initialSourceId)
      .then((result) => { setDetail(result); })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : '소스 상세 정보를 불러오지 못했습니다';
        setDetailError(message);
      });
  }, [initialSourceId]);

  function handleSelect(id: string): void {
    setSelectedId(id);
    setDetail(null);
    setDetailError(null);
    onSourceSelect(id);

    fetchSourceDetail(id)
      .then((result) => {
        setDetail(result);
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : '소스 상세 정보를 불러오지 못했습니다';
        setDetailError(message);
      });
  }

  if (list.loading) {
    return (
      <div className="card-list" role="status" aria-label="로딩 중">
        <SkeletonCard lines={2} />
        <SkeletonCard lines={2} />
        <SkeletonCard lines={2} />
      </div>
    );
  }

  if (list.error !== null) {
    return (
      <div className="error-banner" role="alert">
        {list.error}
        <button className="retry-btn" onClick={list.retry}>다시 시도</button>
      </div>
    );
  }

  if (list.items.length === 0) {
    return (
      <div className="inbox-state">
        <span className="state-icon" aria-hidden="true">&#128196;</span>
        <p className="state-title">저장된 소스가 없습니다</p>
        <p className="state-desc">&quot;액션 추출&quot; 탭에서 텍스트를 입력하면 소스가 저장됩니다.</p>
      </div>
    );
  }

  return (
    <section className={`inbox-layout${selectedId !== null ? ' inbox-has-selection' : ''}`}>
      <div className="inbox-list">
        <div className="panel-header">
          <p className="eyebrow">소스 히스토리</p>
          <h2>{list.items.length}개</h2>
        </div>
        <div className="card-list">
          {list.items.map((source) => (
            <SourceCard
              key={source.id}
              source={source}
              selected={source.id === selectedId}
              onSelect={handleSelect}
            />
          ))}
          {list.loadMoreError ? (
            <button
              className="load-more-btn"
              onClick={list.loadMore}
            >
              불러오기 실패 — 다시 시도
            </button>
          ) : list.hasNext ? (
            <button
              className="load-more-btn"
              onClick={list.loadMore}
              disabled={list.loadingMore}
            >
              {list.loadingMore ? '불러오는 중...' : '더 보기'}
            </button>
          ) : null}
        </div>
      </div>

      <div className="inbox-detail">
        {detailError !== null ? (
          <div className="error-banner" role="alert">
            {detailError}
          </div>
        ) : detail !== null ? (
          <>
            <button
              className="mobile-back-btn"
              onClick={() => { setSelectedId(null); setDetail(null); setDetailError(null); onSourceSelect(null); }}
            >
              &larr; 목록으로
            </button>
            <SourceDetailPanel detail={detail} />
          </>
        ) : (
          <div className="inbox-state">
            <span className="state-icon" aria-hidden="true">&#128196;</span>
            <p className="state-title">목록에서 소스를 선택하세요</p>
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
