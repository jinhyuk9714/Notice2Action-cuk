import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { fetchNoticeDetail, fetchNoticeFeed } from '../lib/api';
import type { NoticePreferences } from '../lib/noticePrefs';
import type { UserProfile } from '../lib/profile';
import type { PersonalizedNoticeDetail, PersonalizedNoticeSummary } from '../lib/types';
import { NoticeDetailContent } from './NoticeDetailContent';

type PersonalizedFeedViewProps = Readonly<{
  profile: UserProfile;
  preferences: NoticePreferences;
  initialNoticeId: string | null;
  onNoticeSelect: (id: string | null) => void;
  onToggleSaved: (id: string) => void;
  onHide: (id: string) => void;
  onUnhide: (id: string) => void;
}>;

export function PersonalizedFeedView({
  profile,
  preferences,
  initialNoticeId,
  onNoticeSelect,
  onToggleSaved,
  onHide,
  onUnhide,
}: PersonalizedFeedViewProps): ReactElement {
  const [notices, setNotices] = useState<readonly PersonalizedNoticeSummary[]>([]);
  const [detail, setDetail] = useState<PersonalizedNoticeDetail | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(initialNoticeId);
  const [loading, setLoading] = useState<boolean>(true);
  const [detailLoading, setDetailLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const profileKey = useMemo(
    () => JSON.stringify({ ...profile, interestKeywords: [...(profile.interestKeywords ?? [])].sort() }),
    [profile],
  );

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    void fetchNoticeFeed(profile).then((response) => {
      if (cancelled) return;
      setNotices(response.notices);
    }).catch((caught) => {
      if (cancelled) return;
      setError(caught instanceof Error ? caught.message : '공지 피드를 불러오지 못했습니다.');
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [profileKey]);

  useEffect(() => {
    setSelectedId(initialNoticeId);
  }, [initialNoticeId]);

  useEffect(() => {
    if (selectedId === null) {
      setDetail(null);
      return;
    }
    let cancelled = false;
    setDetailLoading(true);
    void fetchNoticeDetail(selectedId, profile).then((response) => {
      if (!cancelled) setDetail(response);
    }).catch((caught) => {
      if (!cancelled) setError(caught instanceof Error ? caught.message : '공지 상세 정보를 불러오지 못했습니다.');
    }).finally(() => {
      if (!cancelled) setDetailLoading(false);
    });
    return () => { cancelled = true; };
  }, [profileKey, selectedId]);

  const visibleNotices = notices.filter((notice) => !preferences.hiddenIds.includes(notice.id));
  const hiddenNotices = notices.filter((notice) => preferences.hiddenIds.includes(notice.id));

  function selectNotice(id: string): void {
    setSelectedId(id);
    onNoticeSelect(id);
  }

  return (
    <section className="layout">
      <div className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">개인화 피드</p>
            <h2>내게 중요한 공지</h2>
          </div>
        </div>

        {error !== null ? <div className="error-banner">{error}</div> : null}
        {loading ? <p>공지 불러오는 중...</p> : null}
        {!loading && visibleNotices.length === 0 ? <p>중요 공지가 없습니다.</p> : null}

        <div className="card-list">
          {visibleNotices.map((notice) => {
            const saved = preferences.savedIds.includes(notice.id);
            const visibleReasons = notice.importanceReasons.slice(0, 3);
            return (
              <article key={notice.id} className="action-card">
                <button className="summary-card-link" aria-label={`${notice.title} 상세 보기`} onClick={() => { selectNotice(notice.id); }}>
                  <h3>{notice.title}</h3>
                </button>
                <div className="chip-row">
                  {notice.boardLabel !== null ? <span className="badge">{notice.boardLabel}</span> : null}
                  {visibleReasons.map((reason) => <span key={reason} className="badge">{reason}</span>)}
                </div>
                <p>{notice.dueHint?.label ?? '마감 정보 없음'}</p>
                <p>{notice.actionability === 'action_required' ? '행동 필요' : '정보성 공지'}</p>
                <div className="card-actions-row">
                  <button className="secondary-btn" onClick={() => { onToggleSaved(notice.id); }}>{saved ? '저장됨' : '저장'}</button>
                  <button className="secondary-btn" onClick={() => { onHide(notice.id); }}>{'숨김'}</button>
                </div>
              </article>
            );
          })}
        </div>

        {hiddenNotices.length > 0 ? (
          <div className="detail-section">
            <h3>숨긴 공지 {hiddenNotices.length}개</h3>
            <div className="card-list">
              {hiddenNotices.map((notice) => {
                const visibleReasons = notice.importanceReasons.slice(0, 3);
                return (
                  <article key={`hidden-${notice.id}`} className="action-card">
                  <button className="summary-card-link" aria-label={`${notice.title} 상세 보기`} onClick={() => { selectNotice(notice.id); }}>
                    <h3>{notice.title}</h3>
                  </button>
                  <div className="chip-row">
                    <span className="badge">숨김됨</span>
                    {notice.boardLabel !== null ? <span className="badge">{notice.boardLabel}</span> : null}
                    {visibleReasons.map((reason) => <span key={`${notice.id}-${reason}`} className="badge">{reason}</span>)}
                  </div>
                  <div className="card-actions-row">
                    <button className="secondary-btn" onClick={() => { onUnhide(notice.id); }}>숨김 해제</button>
                  </div>
                  </article>
                );
              })}
            </div>
          </div>
        ) : null}
      </div>

      <div>
        {detailLoading ? <p>상세 불러오는 중...</p> : null}
        {detail !== null ? (
          <NoticeDetailContent
            detail={detail}
            isSaved={preferences.savedIds.includes(detail.id)}
            isHidden={preferences.hiddenIds.includes(detail.id)}
            onToggleSaved={onToggleSaved}
            onHide={onHide}
            onUnhide={onUnhide}
          />
        ) : (
          <div className="panel"><p>공지를 선택하면 상세가 표시됩니다.</p></div>
        )}
      </div>
    </section>
  );
}
