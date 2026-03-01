import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { fetchActionDetail, fetchActionList } from '../lib/api';
import { loadProfile, saveProfile, isProfileConfigured } from '../lib/profile';
import type { UserProfile } from '../lib/profile';
import { computeRelevance } from '../lib/relevance';
import type { SavedActionDetail, SavedActionSummary } from '../lib/types';
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

  useEffect(() => {
    setLoading(true);
    fetchActionList(sort)
      .then((result) => {
        setActions(result.actions);
        setLoading(false);
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : 'Failed to load actions';
        setError(message);
        setLoading(false);
      });
  }, [sort]);

  function handleProfileChange(newProfile: UserProfile): void {
    saveProfile(newProfile);
    setProfile(newProfile);
  }

  function handleSelect(id: string): void {
    setSelectedId(id);
    setDetail(null);

    fetchActionDetail(id)
      .then((result) => {
        setDetail(result);
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : 'Failed to load detail';
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

  if (actions.length === 0) {
    return (
      <div className="inbox-state">
        <p>저장된 액션이 없습니다.</p>
        <p>"액션 추출" 탭에서 텍스트를 입력하면 여기에 저장됩니다.</p>
      </div>
    );
  }

  const profileConfigured = isProfileConfigured(profile);

  return (
    <section className="inbox-layout">
      <div className="inbox-list">
        <div className="panel-header">
          <p className="eyebrow">Saved Actions</p>
          <h2>{filteredActions.length}개</h2>

          <ProfileSettings profile={profile} onProfileChange={handleProfileChange} />

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
          {filteredActions.map(({ action, relevance }) => (
            <ActionSummaryCard
              key={action.id}
              action={action}
              selected={action.id === selectedId}
              onSelect={handleSelect}
              relevance={relevance}
            />
          ))}
        </div>
      </div>

      <div className="inbox-detail">
        {detail !== null ? (
          <ActionDetailPanel detail={detail} profile={profile} />
        ) : (
          <div className="inbox-state">
            <p>목록에서 액션을 선택하세요.</p>
          </div>
        )}
      </div>
    </section>
  );
}
