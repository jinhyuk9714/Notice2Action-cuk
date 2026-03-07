import { useMemo, useState, type ReactElement } from 'react';
import type { PersonalizedNoticeDetail } from '../lib/types';

const BODY_COLLAPSE_LINE_THRESHOLD = 12;
const BODY_COLLAPSE_CHAR_THRESHOLD = 700;
const BODY_COLLAPSE_VISIBLE_LINES = 18;
const TABLE_HEAVY_LINE_THRESHOLD = 3;

type BodyBlock = Readonly<{
  id: string;
  kind: 'text' | 'heading' | 'table';
  lines: string[];
}>;

function shouldCollapseBody(body: string): boolean {
  const lines = body.split(/\r?\n/);
  const tableHeavyLineCount = lines.filter((line) => line.includes(' | ')).length;
  return (
    lines.length > BODY_COLLAPSE_LINE_THRESHOLD
    || body.length > BODY_COLLAPSE_CHAR_THRESHOLD
    || tableHeavyLineCount >= TABLE_HEAVY_LINE_THRESHOLD
  );
}

function isHeadingLine(line: string): boolean {
  return (
    /^STEP\s+\d+/u.test(line)
    || /^참고\d*/u.test(line)
    || /^첨부파일:?/u.test(line)
    || /^<.+>$/u.test(line)
  );
}

function splitBodyIntoBlocks(body: string): BodyBlock[] {
  const lines = body.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.length > 0);
  const blocks: BodyBlock[] = [];
  let textLines: string[] = [];
  let tableLines: string[] = [];

  const flushText = () => {
    if (textLines.length === 0) {
      return;
    }
    blocks.push({
      id: `text-${blocks.length}`,
      kind: 'text',
      lines: textLines,
    });
    textLines = [];
  };

  const flushTable = () => {
    if (tableLines.length === 0) {
      return;
    }
    blocks.push({
      id: `table-${blocks.length}`,
      kind: 'table',
      lines: tableLines,
    });
    tableLines = [];
  };

  for (const line of lines) {
    if (line.includes(' | ')) {
      flushText();
      tableLines.push(line);
      continue;
    }

    flushTable();

    if (isHeadingLine(line)) {
      flushText();
      blocks.push({
        id: `heading-${blocks.length}`,
        kind: 'heading',
        lines: [line],
      });
      continue;
    }

    textLines.push(line);
  }

  flushTable();
  flushText();

  return blocks;
}

function getCollapsedBlocks(blocks: BodyBlock[]): BodyBlock[] {
  const visibleBlocks: BodyBlock[] = [];
  let remainingLines = BODY_COLLAPSE_VISIBLE_LINES;

  for (const block of blocks) {
    if (remainingLines <= 0) {
      break;
    }

    if (block.kind === 'table') {
      visibleBlocks.push(block);
      remainingLines -= Math.min(2, remainingLines);
      continue;
    }

    const visibleLines = block.lines.slice(0, remainingLines);
    if (visibleLines.length === 0) {
      break;
    }
    visibleBlocks.push({
      ...block,
      lines: visibleLines,
    });
    remainingLines -= visibleLines.length;
  }

  return visibleBlocks;
}

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
  const [isBodyExpanded, setIsBodyExpanded] = useState(false);
  const [expandedTableIds, setExpandedTableIds] = useState<string[]>([]);
  const bodyIsCollapsible = useMemo(() => shouldCollapseBody(detail.body), [detail.body]);
  const bodyBlocks = useMemo(() => splitBodyIntoBlocks(detail.body), [detail.body]);
  const visibleBlocks = useMemo(() => {
    if (!bodyIsCollapsible || isBodyExpanded) {
      return bodyBlocks;
    }
    return getCollapsedBlocks(bodyBlocks);
  }, [bodyBlocks, bodyIsCollapsible, isBodyExpanded]);

  const toggleTable = (blockId: string) => {
    setExpandedTableIds((current) => (
      current.includes(blockId)
        ? current.filter((id) => id !== blockId)
        : [...current, blockId]
    ));
  };

  return (
    <section className="panel detail-panel panel-scroll-shell" data-testid="detail-panel-shell">
      <div className="panel-header panel-scroll-header detail-panel-header" data-testid="detail-panel-header">
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

      <div className="panel-scroll-body detail-panel-body" data-testid="detail-panel-body">
        <div className="chip-row">
          {detail.boardLabel !== null ? <span className="badge">{detail.boardLabel}</span> : null}
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
                  {block.evidence.length > 0 ? (
                    <div className="detail-block-evidence">
                      <p className="detail-block-evidence-title">근거</p>
                      <ul>
                        {block.evidence.map((evidence, index) => (
                          <li key={`${detail.id}-${block.title}-evidence-${index}`}>{evidence.snippet}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                </article>
              ))}
            </div>
          )}
        </div>

        <div className="detail-section">
          <h3>원문</h3>
          <div className={`detail-body${bodyIsCollapsible && !isBodyExpanded ? ' detail-body-collapsed' : ''}`}>
            {visibleBlocks.map((block) => {
              if (block.kind === 'table') {
                const isExpanded = expandedTableIds.includes(block.id);
                const headerRow = block.lines[0] ?? '';
                return (
                  <div key={block.id} className="detail-body-block detail-table-block">
                    {isExpanded ? (
                      <pre className="detail-table-rows">{block.lines.join('\n')}</pre>
                    ) : (
                      <div className="detail-table-summary">
                        <pre className="detail-table-header">{headerRow}</pre>
                        <p className="detail-table-meta">총 {block.lines.length}행</p>
                      </div>
                    )}
                    <button
                      className="secondary-btn detail-table-toggle"
                      onClick={() => { toggleTable(block.id); }}
                    >
                      {isExpanded ? '표 접기' : '표 펼치기'}
                    </button>
                  </div>
                );
              }

              if (block.kind === 'heading') {
                return (
                  <pre key={block.id} className="detail-body-block detail-body-heading">
                    {block.lines.join('\n')}
                  </pre>
                );
              }

              return (
                <pre key={block.id} className="detail-body-block detail-body-text">
                  {block.lines.join('\n')}
                </pre>
              );
            })}
          </div>
          {bodyIsCollapsible ? (
            <button
              className="secondary-btn detail-body-toggle"
              onClick={() => { setIsBodyExpanded((current) => !current); }}
            >
              {isBodyExpanded ? '본문 접기' : '본문 더보기'}
            </button>
          ) : null}
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
      </div>
    </section>
  );
}
