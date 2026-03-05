import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { ActionCard } from './components/ActionCard';
import { InboxView } from './components/InboxView';
import { PersonalizedFeedView } from './components/PersonalizedFeedView';
import { ProfileSettings } from './components/ProfileSettings';
import { SavedNoticeView } from './components/SavedNoticeView';
import { SourceIngestionForm } from './components/SourceIngestionForm';
import { SourceListView } from './components/SourceListView';
import { ThemeToggle } from './components/ThemeToggle';
import {
  requestActionExtraction,
  requestEmailExtraction,
  requestPdfExtraction,
  requestScreenshotExtraction,
} from './lib/api';
import {
  cleanupNoticePreferences,
  loadNoticePreferences,
  saveNoticePreferences,
  toggleNoticeHidden,
  toggleNoticeSaved,
  unhideNotice,
  type NoticePreferences,
} from './lib/noticePrefs';
import { loadProfile, saveProfile, type UserProfile } from './lib/profile';
import type { ActionExtractionRequest, ActionExtractionResponse, ExtractedAction } from './lib/types';
import { useHashRoute } from './lib/useHashRoute';
import { useReminderCheck } from './lib/useReminderCheck';

export default function App(): ReactElement {
  useReminderCheck();

  const [route, navigate] = useHashRoute();
  const [profile, setProfile] = useState<UserProfile>(() => loadProfile());
  const [noticePreferences, setNoticePreferences] = useState<NoticePreferences>(() => loadNoticePreferences());
  const [actions, setActions] = useState<readonly ExtractedAction[]>([]);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const lastSubmitRef = useRef<(() => Promise<void>) | null>(null);

  useEffect(() => {
    if (toastMessage === null) return;
    const timer = setTimeout(() => { setToastMessage(null); }, 3000);
    return () => { clearTimeout(timer); };
  }, [toastMessage]);

  const actionCountLabel = useMemo(() => `${actions.length}개 액션`, [actions.length]);

  const handleProfileChange = useCallback((nextProfile: UserProfile) => {
    setProfile(nextProfile);
    saveProfile(nextProfile);
  }, []);

  const persistPreferences = useCallback((updater: (current: NoticePreferences) => NoticePreferences) => {
    setNoticePreferences((current) => {
      const next = updater(current);
      saveNoticePreferences(next);
      return next;
    });
  }, []);

  const handleToggleSaved = useCallback((id: string) => {
    persistPreferences((current) => toggleNoticeSaved(current, id));
  }, [persistPreferences]);

  const handleHideNotice = useCallback((id: string) => {
    persistPreferences((current) => toggleNoticeHidden(current, id));
    if ((route.view === 'feed' || route.view === 'saved') && route.noticeId === id) {
      navigate({ view: route.view, noticeId: null });
    }
  }, [navigate, persistPreferences, route]);

  const handleUnhideNotice = useCallback((id: string) => {
    persistPreferences((current) => unhideNotice(current, id));
  }, [persistPreferences]);

  const handleCleanupPreferences = useCallback((ids: readonly string[]) => {
    persistPreferences((current) => cleanupNoticePreferences(current, ids));
  }, [persistPreferences]);

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

  function handleRetry(): void {
    setError(null);
    if (lastSubmitRef.current !== null) {
      void lastSubmitRef.current();
    }
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
          <h1>성심교정 개인화 공지 피드</h1>
          <p className="hero-copy">
            내 학과, 학년, 신분, 관심 키워드에 맞춰 <strong>중요한 공지</strong>를 먼저 보여주고,
            상세에서 행동 블록과 근거를 함께 확인합니다.
          </p>

          <nav className="tab-nav">
            <button className={route.view === 'feed' ? 'tab tab-active' : 'tab'} onClick={() => { navigate({ view: 'feed', noticeId: null }); }}>
              중요 공지
            </button>
            <button className={route.view === 'saved' ? 'tab tab-active' : 'tab'} onClick={() => { navigate({ view: 'saved', noticeId: null }); }}>
              저장한 공지
            </button>
            <button className={route.view === 'profile' ? 'tab tab-active' : 'tab'} onClick={() => { navigate({ view: 'profile' }); }}>
              프로필
            </button>
          </nav>
        </section>

        {route.view === 'feed' ? (
          <PersonalizedFeedView
            profile={profile}
            preferences={noticePreferences}
            initialNoticeId={route.noticeId}
            onNoticeSelect={(id) => { navigate({ view: 'feed', noticeId: id }); }}
            onToggleSaved={handleToggleSaved}
            onHide={handleHideNotice}
          />
        ) : null}

        {route.view === 'saved' ? (
          <SavedNoticeView
            profile={profile}
            savedIds={noticePreferences.savedIds}
            hiddenIds={noticePreferences.hiddenIds}
            initialNoticeId={route.noticeId}
            onNoticeSelect={(id) => { navigate({ view: 'saved', noticeId: id }); }}
            onToggleSaved={handleToggleSaved}
            onUnhide={handleUnhideNotice}
          />
        ) : null}

        {route.view === 'profile' ? (
          <section className="layout">
            <ProfileSettings profile={profile} onProfileChange={handleProfileChange} />
            <div className="panel">
              <p className="eyebrow">안내</p>
              <h2>개인화 기준 설명</h2>
              <p>학과, 학년, 신분, 관심 키워드를 설정하면 관련도가 높은 공지가 피드 상단으로 올라옵니다.</p>
              <p>저장/숨김 상태는 이 브라우저에만 저장됩니다.</p>
            </div>
          </section>
        ) : null}

        {route.view === 'extract' ? (
          <section className="layout">
            <SourceIngestionForm onSubmit={handleSubmit} onFileSubmit={handleFileSubmit} onEmailSubmit={handleEmailSubmit} isSubmitting={isSubmitting} />
            <div className="panel">
              <div className="panel-header">
                <div>
                  <p className="eyebrow">디버그 추출 결과</p>
                  <h2>{actionCountLabel}</h2>
                </div>
              </div>

              {error !== null ? (
                <div className="error-banner" role="alert">
                  {error}
                  <button className="retry-btn" onClick={handleRetry}>다시 시도</button>
                </div>
              ) : null}

              <div className="card-list">
                {actions.length > 0 ? actions.map((action, index) => (
                  <ActionCard key={action.id ?? `${action.title}-${index}`} action={action} />
                )) : <p>추출 결과가 없습니다.</p>}
              </div>
            </div>
          </section>
        ) : null}

        {route.view === 'inbox' ? (
          <InboxView
            initialActionId={route.actionId}
            initialFilters={route.filters}
            onActionSelect={(id) => { navigate({ view: 'inbox', actionId: id, filters: route.filters }); }}
          />
        ) : null}

        {route.view === 'sources' ? (
          <SourceListView
            initialSourceId={route.sourceId}
            onSourceSelect={(id) => { navigate({ view: 'sources', sourceId: id }); }}
          />
        ) : null}

        {toastMessage !== null ? (
          <div className="toast" role="status" aria-live="polite">
            {toastMessage}
          </div>
        ) : null}
      </main>
    </>
  );
}
