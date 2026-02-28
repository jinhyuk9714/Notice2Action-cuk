import { useEffect, useState, type ReactElement } from 'react';
import { fetchActionDetail, fetchActionList } from '../lib/api';
import type { SavedActionDetail, SavedActionSummary } from '../lib/types';
import { ActionDetailPanel } from './ActionDetailPanel';
import { ActionSummaryCard } from './ActionSummaryCard';

export function InboxView(): ReactElement {
  const [actions, setActions] = useState<readonly SavedActionSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<SavedActionDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchActionList()
      .then((result) => {
        setActions(result.actions);
        setLoading(false);
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : 'Failed to load actions';
        setError(message);
        setLoading(false);
      });
  }, []);

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

  return (
    <section className="inbox-layout">
      <div className="inbox-list">
        <div className="panel-header">
          <p className="eyebrow">Saved Actions</p>
          <h2>{actions.length}개</h2>
        </div>
        <div className="card-list">
          {actions.map((action) => (
            <ActionSummaryCard
              key={action.id}
              action={action}
              selected={action.id === selectedId}
              onSelect={handleSelect}
            />
          ))}
        </div>
      </div>

      <div className="inbox-detail">
        {detail !== null ? (
          <ActionDetailPanel detail={detail} />
        ) : (
          <div className="inbox-state">
            <p>목록에서 액션을 선택하세요.</p>
          </div>
        )}
      </div>
    </section>
  );
}
