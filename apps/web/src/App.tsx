import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { ActionCard } from './components/ActionCard';
import { InboxView } from './components/InboxView';
import { SourceIngestionForm } from './components/SourceIngestionForm';
import { SourceListView } from './components/SourceListView';
import { ThemeToggle } from './components/ThemeToggle';
import { requestActionExtraction, requestEmailExtraction, requestPdfExtraction, requestScreenshotExtraction } from './lib/api';
import type { ActionExtractionRequest, ActionExtractionResponse, ExtractedAction } from './lib/types';
import { useHashRoute } from './lib/useHashRoute';
import { useReminderCheck } from './lib/useReminderCheck';

export default function App(): ReactElement {
  useReminderCheck();
  const [route, navigate] = useHashRoute();
  const [actions, setActions] = useState<readonly ExtractedAction[]>([]);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const lastSubmitRef = useRef<(() => Promise<void>) | null>(null);

  const activeView = route.view;

  const handleRetry = useCallback(() => {
    setError(null);
    if (lastSubmitRef.current !== null) {
      void lastSubmitRef.current();
    }
  }, []);

  useEffect(() => {
    if (toastMessage === null) return;
    const timer = setTimeout(() => { setToastMessage(null); }, 3000);
    return () => { clearTimeout(timer); };
  }, [toastMessage]);

  const actionCountLabel = useMemo(() => `${actions.length}개 액션`, [actions.length]);

  function applyExtractionResult(result: ActionExtractionResponse): void {
    setActions(result.actions);
    setToastMessage(result.duplicate
      ? '이미 추출된 소스입니다. 기존 결과를 표시합니다.'
      : `${result.actions.length}개 액션이 인박스에 저장되었습니다`);
  }

  async function runSubmission(
    submit: () => Promise<ActionExtractionResponse>,
    retry: () => Promise<void>,
  ): Promise<void> {
    lastSubmitRef.current = retry;
    setIsSubmitting(true);
    setError(null);
    try {
      const result = await submit();
      applyExtractionResult(result);
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : '알 수 없는 에러가 발생했습니다.';
      setError(message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleSubmit(payload: ActionExtractionRequest): Promise<void> {
    await runSubmission(
      () => requestActionExtraction(payload),
      () => handleSubmit(payload),
    );
  }

  async function handleEmailSubmit(emailBody: string, subject: string | null): Promise<void> {
    await runSubmission(
      () => requestEmailExtraction(emailBody, subject),
      () => handleEmailSubmit(emailBody, subject),
    );
  }

  async function handleFileSubmit(file: File, sourceTitle: string | null): Promise<void> {
    await runSubmission(
      () => {
        const isImage = /\.(png|jpe?g|webp)$/i.test(file.name);
        return isImage
          ? requestScreenshotExtraction(file, sourceTitle)
          : requestPdfExtraction(file, sourceTitle);
      },
      () => handleFileSubmit(file, sourceTitle),
    );
  }

  return (
    <>
    <a className="skip-link" href="#main-content">본문으로 건너뛰기</a>
    <main className="page-shell" id="main-content">
      <section className="hero">
        <div className="hero-top">
          <p className="eyebrow">Notice2Action CUK</p>
          <ThemeToggle />
        </div>
        <h1>성심교정용 Campus Action Inbox</h1>
        <p className="hero-copy">
          공지, 메일, PDF, 스크린샷에서 <strong>내가 지금 해야 할 일</strong>만 구조화합니다.
          단순 요약이 아니라 마감, 준비물, 시스템 경로, 근거 snippet까지 보여주는 것이 목표입니다.
        </p>

        <nav className="tab-nav">
          <button
            className={activeView === 'extract' ? 'tab tab-active' : 'tab'}
            onClick={() => { navigate({ view: 'extract' }); }}
          >
            액션 추출
          </button>
          <button
            className={activeView === 'inbox' ? 'tab tab-active' : 'tab'}
            onClick={() => { navigate({ view: 'inbox', actionId: null, filters: {} }); }}
          >
            액션 인박스
          </button>
          <button
            className={activeView === 'sources' ? 'tab tab-active' : 'tab'}
            onClick={() => { navigate({ view: 'sources', sourceId: null }); }}
          >
            소스 히스토리
          </button>
        </nav>
      </section>

      <div className="view-transition" key={activeView}>
        {activeView === 'extract' ? (
          <section className="layout">
            <SourceIngestionForm onSubmit={handleSubmit} onFileSubmit={handleFileSubmit} onEmailSubmit={handleEmailSubmit} isSubmitting={isSubmitting} />

            <div className="panel">
              <div className="panel-header">
                <div>
                  <p className="eyebrow">추출 결과</p>
                  <h2>{actionCountLabel}</h2>
                </div>
              </div>

              {error !== null ? (
                <div className="error-banner">
                  {error}
                  <button className="retry-btn" onClick={handleRetry}>다시 시도</button>
                </div>
              ) : null}

              <div className="card-list">
                {actions.length > 0 ? (
                  actions.map((action, idx) => (
                    <ActionCard key={action.id ?? `${action.title}-${idx}`} action={action} />
                  ))
                ) : (
                  <div className="inbox-state">
                    <span className="state-icon" aria-hidden="true">&#9998;</span>
                    <p className="state-title">추출 결과가 없습니다</p>
                    <p className="state-desc">텍스트를 입력하고 추출하면 결과가 여기에 표시됩니다.</p>
                  </div>
                )}
              </div>
            </div>
          </section>
        ) : activeView === 'inbox' ? (
          <InboxView
            initialActionId={route.view === 'inbox' ? route.actionId : null}
            initialFilters={route.view === 'inbox' ? route.filters : {}}
            onActionSelect={(id) => {
              const filters = route.view === 'inbox' ? route.filters : {};
              navigate({ view: 'inbox', actionId: id, filters });
            }}
          />
        ) : (
          <SourceListView
            initialSourceId={route.view === 'sources' ? route.sourceId : null}
            onSourceSelect={(id) => { navigate({ view: 'sources', sourceId: id }); }}
          />
        )}
      </div>

      {toastMessage !== null ? (
        <div className="toast" role="status" aria-live="polite">
          {toastMessage}
        </div>
      ) : null}
    </main>
    </>
  );
}
