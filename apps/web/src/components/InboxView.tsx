import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { deleteAction, fetchActionDetail, fetchActionList, type SearchParams } from '../lib/api';
import { loadProfile, saveProfile, isProfileConfigured } from '../lib/profile';
import type { UserProfile } from '../lib/profile';
import { computeRelevance } from '../lib/relevance';
import type { SavedActionDetail, SavedActionSummary, SourceCategory } from '../lib/types';
import { ActionDetailPanel } from './ActionDetailPanel';
import { ActionSummaryCard } from './ActionSummaryCard';
import { ProfileSettings } from './ProfileSettings';

export function InboxView(): ReactElement {
  const [actions, setActions] = useState<readonly SavedActionSummary[]>([]);
  const [sort, setSort] = useState<'recent' | 'due'>('due');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<SavedActionDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [profile, setProfile] = useState<UserProfile>(() => loadProfile());
  const [showRelevantOnly, setShowRelevantOnly] = useState<boolean>(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [hasNext, setHasNext] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [searchInput, setSearchInput] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [categoryFilter, setCategoryFilter] = useState<SourceCategory | ''>('');

  useEffect(() => {
    const timer = setTimeout(() => { setSearchQuery(searchInput); }, 300);
    return () => { clearTimeout(timer); };
  }, [searchInput]);

  useEffect(() => {
    setActions([]);
    setCurrentPage(0);
    setHasNext(false);
  }, [sort, searchQuery, categoryFilter]);

  useEffect(() => {
    const isFirstPage = currentPage === 0;
    if (isFirstPage) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }
    const search: SearchParams = {
      ...(searchQuery.length > 0 ? { q: searchQuery } : {}),
      ...(categoryFilter !== '' ? { category: categoryFilter } : {}),
    };
    fetchActionList(sort, currentPage, search)
      .then((result) => {
        setActions((prev) => isFirstPage ? result.actions : [...prev, ...result.actions]);
        setHasNext(result.hasNext);
        setLoading(false);
        setLoadingMore(false);
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : '액션 목록을 불러오지 못했습니다';
        setError(message);
        setLoading(false);
        setLoadingMore(false);
      });
  }, [sort, currentPage, searchQuery, categoryFilter]);

  function handleProfileChange(newProfile: UserProfile): void {
    saveProfile(newProfile);
    setProfile(newProfile);
  }

  async function handleDelete(id: string): Promise<void> {
    if (!window.confirm('이 액션을 삭제하시겠습니까?')) return;
    setDeletingId(id);
    try {
      await deleteAction(id);
      setActions((prev) => prev.filter((a) => a.id !== id));
      if (selectedId === id) {
        setSelectedId(null);
        setDetail(null);
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '삭제 중 오류가 발생했습니다';
      setError(message);
    } finally {
      setDeletingId(null);
    }
  }

  function handleSelect(id: string): void {
    setSelectedId(id);
    setDetail(null);

    fetchActionDetail(id)
      .then((result) => {
        setDetail(result);
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : '상세 정보를 불러오지 못했습니다';
        setError(message);
      });
  }

  const actionsWithRelevance = useMemo(() => {
    return actions.map((action) => ({
      action,
      relevance: computeRelevance(action.eligibility, profile),
    }));
  }, [actions, profile]);

  const filteredActions = useMemo(() => {
    if (!showRelevantOnly) return actionsWithRelevance;
    return actionsWithRelevance.filter(
      ({ relevance }) => relevance.level === 'relevant' || relevance.level === 'unknown',
    );
  }, [actionsWithRelevance, showRelevantOnly]);

  if (loading) {
    return <div className="inbox-state">불러오는 중...</div>;
  }

  if (error !== null) {
    return <div className="error-banner">{error}</div>;
  }

  const hasActiveSearch = searchQuery.length > 0 || categoryFilter !== '';

  if (actions.length === 0 && !hasActiveSearch) {
    return (
      <div className="inbox-state">
        <p>저장된 액션이 없습니다.</p>
        <p>"액션 추출" 탭에서 텍스트를 입력하면 여기에 저장됩니다.</p>
      </div>
    );
  }

  const profileConfigured = isProfileConfigured(profile);

  return (
    <section className={`inbox-layout${selectedId !== null ? ' inbox-has-selection' : ''}`}>
      <div className="inbox-list">
        <div className="panel-header">
          <p className="eyebrow">저장된 액션</p>
          <h2>{filteredActions.length}개</h2>

          <ProfileSettings profile={profile} onProfileChange={handleProfileChange} />

          <a
            className="calendar-btn"
            href="/api/v1/actions/calendar.ics"
            download="notice2action.ics"
          >
            캘린더 내보내기
          </a>

          <div className="filter-row">
            <input
              type="search"
              className="search-bar"
              placeholder="제목 또는 요약 검색..."
              value={searchInput}
              onChange={(e) => { setSearchInput(e.target.value); }}
            />
            <select
              className="filter-select"
              value={categoryFilter}
              onChange={(e) => { setCategoryFilter(e.target.value as SourceCategory | ''); }}
            >
              <option value="">전체 카테고리</option>
              <option value="NOTICE">공지</option>
              <option value="SYLLABUS">강의계획서</option>
              <option value="EMAIL">이메일</option>
              <option value="PDF">PDF</option>
              <option value="SCREENSHOT">스크린샷</option>
            </select>
          </div>

          <div className="sort-toggle">
            <button
              className={`sort-btn${sort === 'due' ? ' sort-btn-active' : ''}`}
              onClick={() => { setSort('due'); }}
            >마감순</button>
            <button
              className={`sort-btn${sort === 'recent' ? ' sort-btn-active' : ''}`}
              onClick={() => { setSort('recent'); }}
            >최신순</button>
          </div>

          {profileConfigured ? (
            <div className="filter-toggle">
              <label className="filter-label">
                <input
                  type="checkbox"
                  checked={showRelevantOnly}
                  onChange={(e) => { setShowRelevantOnly(e.target.checked); }}
                />
                관련 항목만 보기
              </label>
            </div>
          ) : null}
        </div>
        <div className="card-list">
          {actions.length === 0 && hasActiveSearch ? (
            <p className="empty-hint">검색 결과가 없습니다.</p>
          ) : null}
          {filteredActions.map(({ action, relevance }) => (
            <ActionSummaryCard
              key={action.id}
              action={action}
              selected={action.id === selectedId}
              onSelect={handleSelect}
              onDelete={handleDelete}
              isDeleting={deletingId === action.id}
              relevance={relevance}
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
            <ActionDetailPanel
              detail={detail}
              profile={profile}
              onActionUpdated={(updated) => {
                setDetail(updated);
                setActions((prev) => prev.map((a) =>
                  a.id === updated.id
                    ? { ...a, title: updated.title, actionSummary: updated.actionSummary,
                        dueAtIso: updated.dueAtIso, dueAtLabel: updated.dueAtLabel,
                        eligibility: updated.eligibility }
                    : a
                ));
              }}
            />
          </>
        ) : (
          <div className="inbox-state">
            <p>목록에서 액션을 선택하세요.</p>
          </div>
        )}
      </div>
    </section>
  );
}
