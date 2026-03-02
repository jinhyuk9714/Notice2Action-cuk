import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import { fetchActionList, fetchAllMatchingActions } from '../lib/api';
import { generateActionsCsv, downloadCsv } from '../lib/csv';
import { getPresetLabel } from '../lib/dateRange';
import { loadProfile, saveProfile, isProfileConfigured } from '../lib/profile';
import type { UserProfile } from '../lib/profile';
import { computeRelevance } from '../lib/relevance';
import type { InboxFilters } from '../lib/router';
import type { SavedActionSummary } from '../lib/types';
import { useActionDelete } from '../lib/useActionDelete';
import { useActionDetail } from '../lib/useActionDetail';
import { useActionFilters } from '../lib/useActionFilters';
import { usePagedList } from '../lib/usePagedList';
import { ActionDetailPanel } from './ActionDetailPanel';
import { ActionSummaryCard } from './ActionSummaryCard';
import { ConfirmDialog } from './ConfirmDialog';
import { ProfileSettings } from './ProfileSettings';
import { SkeletonCard } from './SkeletonCard';

type InboxViewProps = Readonly<{
  initialActionId: string | null;
  initialFilters: InboxFilters;
  onActionSelect: (id: string | null) => void;
}>;

export function InboxView({ initialActionId, initialFilters, onActionSelect }: InboxViewProps): ReactElement {
  const [profile, setProfile] = useState<UserProfile>(() => loadProfile());
  const [showRelevantOnly, setShowRelevantOnly] = useState<boolean>(false);
  const [csvExporting, setCsvExporting] = useState<boolean>(false);
  const [csvError, setCsvError] = useState<string | null>(null);

  useEffect(() => {
    if (csvError === null) return;
    const timer = setTimeout(() => { setCsvError(null); }, 4000);
    return () => { clearTimeout(timer); };
  }, [csvError]);

  const detail = useActionDetail({ initialActionId, onActionSelect });
  const filters = useActionFilters({ initialFilters, selectedId: detail.selectedId });

  const fetchPage = useCallback(
    (page: number) =>
      fetchActionList(filters.sort, page, filters.currentSearch)
        .then((r) => ({ items: [...r.actions], hasNext: r.hasNext })),
    [filters.sort, filters.currentSearch],
  );

  const list = usePagedList<SavedActionSummary>({
    fetchPage,
    deps: [filters.sort, filters.currentSearch],
  });

  const del = useActionDelete({
    actions: list.items,
    onItemsChange: list.setItems,
    selectedId: detail.selectedId,
    clearSelection: detail.clearSelection,
  });

  function handleProfileChange(newProfile: UserProfile): void {
    saveProfile(newProfile);
    setProfile(newProfile);
  }

  async function handleCsvExport(): Promise<void> {
    setCsvExporting(true);
    try {
      const allActions = await fetchAllMatchingActions(filters.sort, filters.currentSearch);
      const csv = generateActionsCsv(allActions);
      downloadCsv(csv, 'notice2action-actions.csv');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'CSV 내보내기에 실패했습니다';
      setCsvError(message);
    } finally {
      setCsvExporting(false);
    }
  }

  const actionsWithRelevance = useMemo(() => {
    return list.items.map((action) => ({
      action,
      relevance: computeRelevance(action.eligibility, profile),
    }));
  }, [list.items, profile]);

  const filteredActions = useMemo(() => {
    if (!showRelevantOnly) return actionsWithRelevance;
    return actionsWithRelevance.filter(
      ({ relevance }) => relevance.level === 'relevant' || relevance.level === 'unknown',
    );
  }, [actionsWithRelevance, showRelevantOnly]);

  if (list.loading) {
    return (
      <div className="card-list" role="status" aria-label="로딩 중">
        <SkeletonCard lines={2} />
        <SkeletonCard lines={3} />
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

  if (del.deleteError !== null) {
    return (
      <div className="error-banner" role="alert">
        {del.deleteError}
        <button className="retry-btn" onClick={list.retry}>다시 시도</button>
      </div>
    );
  }

  if (list.items.length === 0 && !filters.hasActiveSearch) {
    return (
      <div className="inbox-state">
        <span className="state-icon" aria-hidden="true">&#128203;</span>
        <p className="state-title">저장된 액션이 없습니다</p>
        <p className="state-desc">&quot;액션 추출&quot; 탭에서 텍스트를 입력하면 여기에 저장됩니다.</p>
      </div>
    );
  }

  const profileConfigured = isProfileConfigured(profile);

  return (
    <section className={`inbox-layout${detail.selectedId !== null ? ' inbox-has-selection' : ''}`}>
      <div className="inbox-list">
        <div className="panel-header">
          <p className="eyebrow">저장된 액션</p>
          <h2>{filteredActions.length}개</h2>

          <ProfileSettings profile={profile} onProfileChange={handleProfileChange} />

          <div className="export-row">
            <a
              className="calendar-btn"
              href={filters.calendarUrl}
              download="notice2action.ics"
            >
              캘린더 내보내기
            </a>
            <button
              className="calendar-btn"
              onClick={() => { void handleCsvExport(); }}
              disabled={list.items.length === 0 || csvExporting}
            >
              {csvExporting ? '내보내는 중...' : 'CSV 내보내기'}
            </button>
          </div>

          {csvError !== null ? (
            <div className="error-banner" role="alert">
              {csvError}
              <button className="retry-btn" onClick={() => { setCsvError(null); }}>닫기</button>
            </div>
          ) : null}

          <div className="filter-row">
            <input
              type="search"
              className="search-bar"
              placeholder="제목 또는 요약 검색..."
              aria-label="검색"
              value={filters.searchInput}
              onChange={(e) => { filters.setSearchInput(e.target.value); }}
            />
            <select
              className="filter-select"
              value={filters.categoryFilter}
              onChange={(e) => { filters.setCategoryFilter(e.target.value as Parameters<typeof filters.setCategoryFilter>[0]); }}
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
              className={`sort-btn${filters.sort === 'due' ? ' sort-btn-active' : ''}`}
              onClick={() => { filters.setSort('due'); }}
              aria-pressed={filters.sort === 'due'}
            >마감순</button>
            <button
              className={`sort-btn${filters.sort === 'recent' ? ' sort-btn-active' : ''}`}
              onClick={() => { filters.setSort('recent'); }}
              aria-pressed={filters.sort === 'recent'}
            >최신순</button>
          </div>

          <div className="date-range-row">
            {(['all', 'this-week', 'this-month', 'overdue', 'custom'] as const).map((preset) => (
              <button
                key={preset}
                className={`sort-btn${filters.dateRangePreset === preset ? ' sort-btn-active' : ''}`}
                onClick={() => { filters.setDateRangePreset(preset); }}
                aria-pressed={filters.dateRangePreset === preset}
              >
                {getPresetLabel(preset)}
              </button>
            ))}
          </div>

          {filters.dateRangePreset === 'custom' ? (
            <div className="date-range-custom">
              <input
                type="date"
                className="date-input"
                value={filters.customFrom}
                onChange={(e) => { filters.setCustomFrom(e.target.value); }}
                aria-label="시작일"
              />
              <span className="date-range-sep">~</span>
              <input
                type="date"
                className="date-input"
                value={filters.customTo}
                onChange={(e) => { filters.setCustomTo(e.target.value); }}
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
          {list.items.length === 0 && filters.hasActiveSearch ? (
            <div className="inbox-state">
              <span className="state-icon" aria-hidden="true">&#128269;</span>
              <p className="state-title">검색 결과가 없습니다</p>
            </div>
          ) : null}
          {filteredActions.map(({ action, relevance }) => (
            <ActionSummaryCard
              key={action.id}
              action={action}
              selected={action.id === detail.selectedId}
              onSelect={detail.handleSelect}
              onDelete={del.requestDelete}
              isDeleting={del.deletingId === action.id}
              relevance={relevance}
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
        {detail.detailError !== null ? (
          <div className="error-banner" role="alert">
            {detail.detailError}
          </div>
        ) : detail.detail !== null ? (
          <>
            <button
              className="mobile-back-btn"
              onClick={detail.clearSelection}
            >
              &larr; 목록으로
            </button>
            <ActionDetailPanel
              detail={detail.detail}
              profile={profile}
              onActionUpdated={(updated) => {
                detail.setDetail(updated);
                list.setItems((prev) => prev.map((a) =>
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
            <span className="state-icon" aria-hidden="true">&#128203;</span>
            <p className="state-title">목록에서 액션을 선택하세요</p>
          </div>
        )}
      </div>
      <ConfirmDialog
        open={del.pendingDeleteId !== null}
        title="액션 삭제"
        message={`"${list.items.find((a) => a.id === del.pendingDeleteId)?.title ?? ''}" 액션을 삭제하시겠습니까?`}
        confirmLabel="삭제"
        cancelLabel="취소"
        danger={true}
        onConfirm={() => { void del.confirmDelete(); }}
        onCancel={del.cancelDelete}
      />
      {del.deleteToast !== null ? (
        <div className="toast" role="status" aria-live="polite">
          {del.deleteToast}
        </div>
      ) : null}
    </section>
  );
}
