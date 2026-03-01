import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { ActionCard } from './components/ActionCard';
import { InboxView } from './components/InboxView';
import { SourceIngestionForm } from './components/SourceIngestionForm';
import { requestActionExtraction, requestPdfExtraction, requestScreenshotExtraction } from './lib/api';
import type { ActionExtractionRequest, ExtractedAction } from './lib/types';
import { useReminderCheck } from './lib/useReminderCheck';

type ActiveView = 'extract' | 'inbox';

export default function App(): ReactElement {
  useReminderCheck();
  const [activeView, setActiveView] = useState<ActiveView>('extract');
  const [actions, setActions] = useState<readonly ExtractedAction[]>([]);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  useEffect(() => {
    if (toastMessage === null) return;
    const timer = setTimeout(() => { setToastMessage(null); }, 3000);
    return () => { clearTimeout(timer); };
  }, [toastMessage]);

  const actionCountLabel = useMemo(() => `${actions.length}개 액션`, [actions.length]);

  async function handleSubmit(payload: ActionExtractionRequest): Promise<void> {
    setIsSubmitting(true);
    setError(null);

    try {
      const result = await requestActionExtraction(payload);
      setActions(result.actions);
      setToastMessage(`${result.actions.length}개 액션이 인박스에 저장되었습니다`);
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : '알 수 없는 에러가 발생했습니다.';
      setError(message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleFileSubmit(file: File, sourceTitle: string | null): Promise<void> {
    setIsSubmitting(true);
    setError(null);

    try {
      const isImage = /\.(png|jpe?g|webp)$/i.test(file.name);
      const result = isImage
        ? await requestScreenshotExtraction(file, sourceTitle)
        : await requestPdfExtraction(file, sourceTitle);
      setActions(result.actions);
      setToastMessage(`${result.actions.length}개 액션이 인박스에 저장되었습니다`);
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : '알 수 없는 에러가 발생했습니다.';
      setError(message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="page-shell">
      <section className="hero">
        <p className="eyebrow">Notice2Action CUK</p>
        <h1>성심교정용 Campus Action Inbox</h1>
        <p className="hero-copy">
          공지, 메일, PDF, 스크린샷에서 <strong>내가 지금 해야 할 일</strong>만 구조화합니다.
          단순 요약이 아니라 마감, 준비물, 시스템 경로, 근거 snippet까지 보여주는 것이 목표입니다.
        </p>

        <nav className="tab-nav">
          <button
            className={activeView === 'extract' ? 'tab tab-active' : 'tab'}
            onClick={() => { setActiveView('extract'); }}
          >
            액션 추출
          </button>
          <button
            className={activeView === 'inbox' ? 'tab tab-active' : 'tab'}
            onClick={() => { setActiveView('inbox'); }}
          >
            액션 인박스
          </button>
        </nav>
      </section>

      {activeView === 'extract' ? (
        <section className="layout">
          <SourceIngestionForm onSubmit={handleSubmit} onFileSubmit={handleFileSubmit} isSubmitting={isSubmitting} />

          <div className="panel">
            <div className="panel-header">
              <div>
                <p className="eyebrow">추출 결과</p>
                <h2>{actionCountLabel}</h2>
              </div>
            </div>

            {error !== null ? <div className="error-banner">{error}</div> : null}

            <div className="card-list">
              {actions.length > 0 ? (
                actions.map((action) => (
                  <ActionCard key={`${action.title}-${action.dueAtIso ?? 'none'}`} action={action} />
                ))
              ) : (
                <p className="empty-hint">텍스트를 입력하고 추출하면 결과가 여기에 표시됩니다.</p>
              )}
            </div>
          </div>
        </section>
      ) : (
        <InboxView />
      )}

      {toastMessage !== null ? (
        <div className="toast" role="status" aria-live="polite">
          {toastMessage}
        </div>
      ) : null}
    </main>
  );
}
