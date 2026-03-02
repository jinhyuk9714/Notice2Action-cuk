import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { deleteAction, fetchActionDetail, fetchActionList, type SearchParams } from '../lib/api';
import { generateActionsCsv, downloadCsv } from '../lib/csv';
import { computeDateRange, getPresetLabel, type DateRangePreset } from '../lib/dateRange';
import { loadProfile, saveProfile, isProfileConfigured } from '../lib/profile';
import type { UserProfile } from '../lib/profile';
import { computeRelevance } from '../lib/relevance';
import type { SavedActionDetail, SavedActionSummary, SourceCategory } from '../lib/types';
import { ActionDetailPanel } from './ActionDetailPanel';
import { ActionSummaryCard } from './ActionSummaryCard';
import { ProfileSettings } from './ProfileSettings';
import { SkeletonCard } from './SkeletonCard';

type InboxViewProps = Readonly<{
  initialActionId: string | null;
  onActionSelect: (id: string | null) => void;
}>;

export function InboxView({ initialActionId, onActionSelect }: InboxViewProps): ReactElement {
  const [actions, setActions] = useState<readonly SavedActionSummary[]>([]);
  const [sort, setSort] = useState<'recent' | 'due'>('due');
  const [selectedId, setSelectedId] = useState<string | null>(initialActionId);
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
  const [dateRangePreset, setDateRangePreset] = useState<DateRangePreset>('all');
  const [customFrom, setCustomFrom] = useState<string>('');
  const [customTo, setCustomTo] = useState<string>('');
  const [retryKey, setRetryKey] = useState<number>(0);

  useEffect(() => {
    if (initialActionId === null) {
      setSelectedId(null);
      setDetail(null);
      return;
    }
    if (initialActionId === selectedId) {
      return;
    }
    setSelectedId(initialActionId);
    setDetail(null);
    fetchActionDetail(initialActionId)
      .then((result) => { setDetail(result); })
      .catch(() => { /* handled by select */ });
  }, [initialActionId, selectedId]);

  useEffect(() => {
    const timer = setTimeout(() => { setSearchQuery(searchInput); }, 300);
    return () => { clearTimeout(timer); };
  }, [searchInput]);

  const dateRange = useMemo(
    () => computeDateRange(dateRangePreset, customFrom, customTo),
    [dateRangePreset, customFrom, customTo],
  );

  useEffect(() => {
    setActions([]);
    setCurrentPage(0);
    setHasNext(false);
  }, [sort, searchQuery, categoryFilter, dateRange]);

  useEffect(() => {
    let cancelled = false;
    const isFirstPage = currentPage === 0;
    if (isFirstPage) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }
    const search: SearchParams = {
      ...(searchQuery.length > 0 ? { q: searchQuery } : {}),
      ...(categoryFilter !== '' ? { category: categoryFilter } : {}),
      ...(dateRange.from !== undefined ? { dueDateFrom: dateRange.from } : {}),
      ...(dateRange.to !== undefined ? { dueDateTo: dateRange.to } : {}),
    };
    fetchActionList(sort, currentPage, search)
      .then((result) => {
        if (cancelled) return;
        setActions((prev) => isFirstPage ? result.actions : [...prev, ...result.actions]);
        setHasNext(result.hasNext);
        setLoading(false);
        setLoadingMore(false);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const message = err instanceof Error ? err.message : '액션 목록을 불러오지 못했습니다';
        setError(message);
        setLoading(false);
        setLoadingMore(false);
      });
    return () => { cancelled = true; };
  }, [sort, currentPage, searchQuery, categoryFilter, dateRange, retryKey]);

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
        onActionSelect(null);
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
    onActionSelect(id);

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
    return (
      <div className="card-list">
        <SkeletonCard lines={2} />
        <SkeletonCard lines={3} />
        <SkeletonCard lines={2} />
      </div>
    );
  }

  if (error !== null) {
    return (
      <div className="error-banner">
        {error}
        <button className="retry-btn" onClick={() => {
          setError(null);
          setActions([]);
          setCurrentPage(0);
          setRetryKey((k) => k + 1);
        }}>다시 시도</button>
      </div>
    );
  }

  const hasActiveSearch = searchQuery.length > 0 || categoryFilter !== '' || dateRangePreset !== 'all';

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

          <div className="export-row">
            <a
              className="calendar-btn"
              href="/api/v1/actions/calendar.ics"
              download="notice2action.ics"
            >
              캘린더 내보내기
            </a>
            <button
              className="calendar-btn"
              onClick={() => {
                const csv = generateActionsCsv(actions);
                downloadCsv(csv, 'notice2action-actions.csv');
              }}
              disabled={actions.length === 0}
            >
              CSV 내보내기
            </button>
          </div>

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

          <div className="date-range-row">
            {(['all', 'this-week', 'this-month', 'overdue', 'custom'] as const).map((preset) => (
              <button
                key={preset}
                className={`sort-btn${dateRangePreset === preset ? ' sort-btn-active' : ''}`}
                onClick={() => { setDateRangePreset(preset); }}
              >
                {getPresetLabel(preset)}
              </button>
            ))}
          </div>

          {dateRangePreset === 'custom' ? (
            <div className="date-range-custom">
              <input
                type="date"
                className="date-input"
                value={customFrom}
                onChange={(e) => { setCustomFrom(e.target.value); }}
                aria-label="시작일"
              />
              <span className="date-range-sep">~</span>
              <input
                type="date"
                className="date-input"
                value={customTo}
                onChange={(e) => { setCustomTo(e.target.value); }}
                aria-label="종료일"
              />
            </div>
          ) : null}

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
              onClick={() => { setSelectedId(null); setDetail(null); onActionSelect(null); }}
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
