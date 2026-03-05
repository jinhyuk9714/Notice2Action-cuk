import type { ReactElement } from 'react';
import type { PersonalizedNoticeDetail } from '../lib/types';

type NoticeDetailContentProps = Readonly<{
  detail: PersonalizedNoticeDetail;
  isSaved: boolean;
  isHidden: boolean;
  onToggleSaved: (id: string) => void;
  onHide?: (id: string) => void;
  onUnhide?: (id: string) => void;
}>;

export function NoticeDetailContent({
  detail,
  isSaved,
  isHidden,
  onToggleSaved,
  onHide,
  onUnhide,
}: NoticeDetailContentProps): ReactElement {
  return (
    <section className="panel detail-panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">공지 상세</p>
          <h2>{detail.title}</h2>
        </div>
        <div className="detail-actions-row">
          <button className="secondary-btn" onClick={() => { onToggleSaved(detail.id); }}>
            {isSaved ? '저장됨' : '저장'}
          </button>
          {isHidden ? (
            <button className="secondary-btn" onClick={() => { onUnhide?.(detail.id); }}>
              숨김 해제
            </button>
          ) : (
            <button className="secondary-btn" onClick={() => { onHide?.(detail.id); }}>
              숨김
            </button>
          )}
        </div>
      </div>

      <div className="chip-row">
        {detail.actionability === 'action_required' ? <span className="badge badge-danger">행동 필요</span> : <span className="badge">정보성 공지</span>}
        {isHidden ? <span className="badge">숨김됨</span> : null}
        {detail.dueHint !== null ? <span className="badge">{detail.dueHint.label}</span> : null}
      </div>

      <div className="detail-section">
        <h3>왜 중요한지</h3>
        <div className="chip-row">
          {detail.importanceReasons.map((reason) => (
            <span key={reason} className="badge">{reason}</span>
          ))}
        </div>
      </div>

      <div className="detail-section">
        <h3>행동 블록</h3>
        {detail.actionBlocks.length === 0 ? (
          <p>행동 없음</p>
        ) : (
          <div className="card-list">
            {detail.actionBlocks.map((block) => (
              <article key={`${detail.id}-${block.title}`} className="action-card">
                <h4>{block.title}</h4>
                <p>{block.summary}</p>
                {block.dueAtLabel !== null ? <p>마감: {block.dueAtLabel}</p> : null}
                {block.systemHint !== null ? <p>시스템: {block.systemHint}</p> : null}
                {block.requiredItems.length > 0 ? <p>준비물: {block.requiredItems.join(', ')}</p> : null}
              </article>
            ))}
          </div>
        )}
      </div>

      <div className="detail-section">
        <h3>원문</h3>
        <pre className="detail-body">{detail.body}</pre>
      </div>

      <div className="detail-section">
        <h3>첨부파일</h3>
        {detail.attachments.length === 0 ? (
          <p>첨부파일 없음</p>
        ) : (
          <ul>
            {detail.attachments.map((attachment) => (
              <li key={attachment.url}><a href={attachment.url} target="_blank" rel="noreferrer">{attachment.name}</a></li>
            ))}
          </ul>
        )}
      </div>

      <div className="detail-section">
        <h3>근거 snippet</h3>
        {detail.actionBlocks.flatMap((block) => block.evidence).length === 0 ? (
          <p>근거 없음</p>
        ) : (
          <ul>
            {detail.actionBlocks.flatMap((block) => block.evidence).map((evidence, index) => (
              <li key={`${detail.id}-evidence-${index}`}>{evidence.snippet}</li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
