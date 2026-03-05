import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { fetchNoticeDetail } from '../lib/api';
import type { UserProfile } from '../lib/profile';
import type { PersonalizedNoticeDetail } from '../lib/types';
import { NoticeDetailContent } from './NoticeDetailContent';

type SavedNoticeViewProps = Readonly<{
  profile: UserProfile;
  savedIds: readonly string[];
  hiddenIds: readonly string[];
  initialNoticeId: string | null;
  onNoticeSelect: (id: string | null) => void;
  onToggleSaved: (id: string) => void;
  onUnhide: (id: string) => void;
}>;

export function SavedNoticeView({
  profile,
  savedIds,
  hiddenIds,
  initialNoticeId,
  onNoticeSelect,
  onToggleSaved,
  onUnhide,
}: SavedNoticeViewProps): ReactElement {
  const [details, setDetails] = useState<readonly PersonalizedNoticeDetail[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(initialNoticeId);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const profileKey = useMemo(
    () => JSON.stringify({ ...profile, interestKeywords: [...(profile.interestKeywords ?? [])].sort() }),
    [profile],
  );

  useEffect(() => {
    setSelectedId(initialNoticeId);
  }, [initialNoticeId]);

  useEffect(() => {
    let cancelled = false;
    if (savedIds.length === 0) {
      setDetails([]);
      setLoading(false);
      return () => {};
    }
    setLoading(true);
    setError(null);
    void Promise.all(savedIds.map((id) => fetchNoticeDetail(id, profile).catch(() => null))).then((results) => {
      if (cancelled) return;
      setDetails(results.filter((item): item is PersonalizedNoticeDetail => item !== null));
    }).catch((caught) => {
      if (!cancelled) setError(caught instanceof Error ? caught.message : '저장한 공지를 불러오지 못했습니다.');
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [profileKey, JSON.stringify(savedIds)]);

  const selectedDetail = details.find((detail) => detail.id === selectedId) ?? details[0] ?? null;

  return (
    <section className="layout">
      <div className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">저장함</p>
            <h2>저장한 공지</h2>
          </div>
        </div>

        {error !== null ? <div className="error-banner">{error}</div> : null}
        {loading ? <p>저장한 공지 불러오는 중...</p> : null}
        {!loading && details.length === 0 ? <p>저장한 공지가 없습니다.</p> : null}

        <div className="card-list">
          {details.map((detail) => (
            <article key={detail.id} className="action-card">
              <button className="summary-card-link" aria-label={`${detail.title} 상세 보기`} onClick={() => { setSelectedId(detail.id); onNoticeSelect(detail.id); }}>
                <h3>{detail.title}</h3>
              </button>
              {hiddenIds.includes(detail.id) ? <span className="badge">숨김됨</span> : null}
              <div className="detail-actions-row">
                <button className="secondary-btn" onClick={() => { onToggleSaved(detail.id); }}>저장 해제</button>
                {hiddenIds.includes(detail.id) ? (
                  <button className="secondary-btn" onClick={() => { onUnhide(detail.id); }}>숨김 해제</button>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      </div>

      <div>
        {selectedDetail !== null ? (
          <NoticeDetailContent
            detail={selectedDetail}
            isSaved={savedIds.includes(selectedDetail.id)}
            isHidden={hiddenIds.includes(selectedDetail.id)}
            onToggleSaved={onToggleSaved}
            onUnhide={onUnhide}
          />
        ) : (
          <div className="panel"><p>저장한 공지를 선택하면 상세가 표시됩니다.</p></div>
        )}
      </div>
    </section>
  );
}
