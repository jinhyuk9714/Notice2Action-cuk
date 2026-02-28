import { useState, type ReactElement } from 'react';
import type { ActionExtractionRequest, SourceCategory } from '../lib/types';

type SourceIngestionFormProps = Readonly<{
  onSubmit: (payload: ActionExtractionRequest) => Promise<void>;
  isSubmitting: boolean;
}>;

type InputMode = 'text' | 'url';

const DEFAULT_TEXT = `2026년 3월 12일 18시까지 TRINITY에서 공결 신청을 완료하고 증빙서류를 업로드해야 합니다.
신청 대상은 재학생이며, 필요 시 재학증명서를 제출해야 합니다.`;

export function SourceIngestionForm({
  onSubmit,
  isSubmitting
}: SourceIngestionFormProps): ReactElement {
  const [inputMode, setInputMode] = useState<InputMode>('text');
  const [sourceTitle, setSourceTitle] = useState<string>('공결 신청 안내');
  const [sourceText, setSourceText] = useState<string>(DEFAULT_TEXT);
  const [sourceUrl, setSourceUrl] = useState<string>('');
  const [sourceCategory, setSourceCategory] = useState<SourceCategory>('NOTICE');

  const isInputEmpty = inputMode === 'text'
    ? sourceText.trim().length === 0
    : sourceUrl.trim().length === 0;

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();

    await onSubmit({
      sourceText: inputMode === 'text' ? sourceText : '',
      sourceUrl: inputMode === 'url' ? sourceUrl : null,
      sourceTitle: sourceTitle || null,
      sourceCategory,
      focusProfile: ['성심교정', '복학생']
    });
  }

  return (
    <form className="card form-card" onSubmit={handleSubmit}>
      <div className="form-row">
        <label htmlFor="sourceTitle">제목</label>
        <input
          id="sourceTitle"
          value={sourceTitle}
          onChange={(event) => { setSourceTitle(event.target.value); }}
          placeholder={inputMode === 'url' ? '비워두면 페이지 제목을 자동으로 가져옵니다' : '예: 장학 신청 안내'}
        />
      </div>

      <div className="form-row">
        <label htmlFor="sourceCategory">카테고리</label>
        <select
          id="sourceCategory"
          value={sourceCategory}
          onChange={(event) => { setSourceCategory(event.target.value as SourceCategory); }}
        >
          <option value="NOTICE">공지</option>
          <option value="SYLLABUS">강의계획서</option>
          <option value="EMAIL">이메일</option>
          <option value="PDF">PDF</option>
          <option value="SCREENSHOT">스크린샷</option>
        </select>
      </div>

      <div className="form-row">
        <label>입력 방식</label>
        <div className="toggle-group">
          <button
            type="button"
            className={inputMode === 'text' ? 'toggle toggle-active' : 'toggle'}
            onClick={() => { setInputMode('text'); }}
          >
            직접 입력
          </button>
          <button
            type="button"
            className={inputMode === 'url' ? 'toggle toggle-active' : 'toggle'}
            onClick={() => { setInputMode('url'); }}
          >
            URL 입력
          </button>
        </div>
      </div>

      {inputMode === 'text' ? (
        <div className="form-row">
          <label htmlFor="sourceText">원문 텍스트</label>
          <textarea
            id="sourceText"
            value={sourceText}
            onChange={(event) => { setSourceText(event.target.value); }}
            rows={10}
          />
        </div>
      ) : (
        <div className="form-row">
          <label htmlFor="sourceUrl">공지 URL</label>
          <input
            id="sourceUrl"
            type="url"
            value={sourceUrl}
            onChange={(event) => { setSourceUrl(event.target.value); }}
            placeholder="https://www.catholic.ac.kr/..."
          />
          <p className="url-hint">로그인이 필요한 페이지는 직접 텍스트를 붙여넣어 주세요.</p>
        </div>
      )}

      <button className="primary-button" type="submit" disabled={isSubmitting || isInputEmpty}>
        {isSubmitting ? '추출 중...' : '액션 추출'}
      </button>
    </form>
  );
}
