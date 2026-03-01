import { useState, type ReactElement } from 'react';
import type { ActionExtractionRequest, SourceCategory } from '../lib/types';

type SourceIngestionFormProps = Readonly<{
  onSubmit: (payload: ActionExtractionRequest) => Promise<void>;
  onFileSubmit: (file: File, sourceTitle: string | null) => Promise<void>;
  onEmailSubmit: (emailBody: string, subject: string | null) => Promise<void>;
  isSubmitting: boolean;
}>;

type InputMode = 'text' | 'url' | 'file' | 'email';

const MAX_PDF_SIZE = 10 * 1024 * 1024;
const MAX_IMAGE_SIZE = 5 * 1024 * 1024;
const IMAGE_EXTENSIONS = ['.png', '.jpg', '.jpeg', '.webp'];
const PDF_EXTENSIONS = ['.pdf'];

const DEFAULT_TEXT = `2026년 3월 12일 18시까지 TRINITY에서 공결 신청을 완료하고 증빙서류를 업로드해야 합니다.
신청 대상은 재학생이며, 필요 시 재학증명서를 제출해야 합니다.`;

export function SourceIngestionForm({
  onSubmit,
  onFileSubmit,
  onEmailSubmit,
  isSubmitting
}: SourceIngestionFormProps): ReactElement {
  const [inputMode, setInputMode] = useState<InputMode>('text');
  const [sourceTitle, setSourceTitle] = useState<string>('공결 신청 안내');
  const [sourceText, setSourceText] = useState<string>(DEFAULT_TEXT);
  const [sourceUrl, setSourceUrl] = useState<string>('');
  const [sourceCategory, setSourceCategory] = useState<SourceCategory>('NOTICE');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [emailSubject, setEmailSubject] = useState<string>('');
  const [emailBody, setEmailBody] = useState<string>('');

  const isInputEmpty = inputMode === 'text'
    ? sourceText.trim().length === 0
    : inputMode === 'url'
      ? sourceUrl.trim().length === 0
      : inputMode === 'email'
        ? emailBody.trim().length === 0
        : selectedFile === null;

  function getFileType(fileName: string): 'pdf' | 'image' | null {
    const lower = fileName.toLowerCase();
    if (PDF_EXTENSIONS.some((ext) => lower.endsWith(ext))) return 'pdf';
    if (IMAGE_EXTENSIONS.some((ext) => lower.endsWith(ext))) return 'image';
    return null;
  }

  function handleFileChange(event: React.ChangeEvent<HTMLInputElement>): void {
    setFileError(null);
    const file = event.target.files?.[0] ?? null;

    if (file === null) {
      setSelectedFile(null);
      return;
    }

    const fileType = getFileType(file.name);
    if (fileType === null) {
      setFileError('PDF 또는 이미지 파일만 업로드할 수 있습니다. (PDF, PNG, JPG, JPEG, WEBP)');
      setSelectedFile(null);
      return;
    }

    const maxSize = fileType === 'pdf' ? MAX_PDF_SIZE : MAX_IMAGE_SIZE;
    const maxLabel = fileType === 'pdf' ? '10MB' : '5MB';
    if (file.size > maxSize) {
      setFileError(`파일 크기가 ${maxLabel}를 초과합니다.`);
      setSelectedFile(null);
      return;
    }

    setSelectedFile(file);
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();

    if (inputMode === 'file') {
      if (selectedFile === null) return;
      await onFileSubmit(selectedFile, sourceTitle || null);
      return;
    }

    if (inputMode === 'email') {
      await onEmailSubmit(emailBody, emailSubject || null);
      return;
    }

    await onSubmit({
      sourceText: inputMode === 'text' ? sourceText : '',
      sourceUrl: inputMode === 'url' ? sourceUrl : null,
      sourceTitle: sourceTitle || null,
      sourceCategory,
      focusProfile: []
    });
  }

  const titlePlaceholder = inputMode === 'url'
    ? '비워두면 페이지 제목을 자동으로 가져옵니다'
    : inputMode === 'file'
      ? '비워두면 파일명을 사용합니다'
      : inputMode === 'email'
        ? '비워두면 제목을 사용합니다'
        : '예: 장학 신청 안내';

  return (
    <form className="card form-card" onSubmit={handleSubmit}>
      {inputMode !== 'email' ? (
        <div className="form-row">
          <label htmlFor="sourceTitle">제목</label>
          <input
            id="sourceTitle"
            value={sourceTitle}
            onChange={(event) => { setSourceTitle(event.target.value); }}
            placeholder={titlePlaceholder}
          />
        </div>
      ) : null}

      {inputMode !== 'file' && inputMode !== 'email' ? (
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
      ) : null}

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
          <button
            type="button"
            className={inputMode === 'file' ? 'toggle toggle-active' : 'toggle'}
            onClick={() => { setInputMode('file'); }}
          >
            파일 업로드
          </button>
          <button
            type="button"
            className={inputMode === 'email' ? 'toggle toggle-active' : 'toggle'}
            onClick={() => { setInputMode('email'); }}
          >
            이메일
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
      ) : inputMode === 'url' ? (
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
      ) : inputMode === 'file' ? (
        <div className="form-row">
          <label htmlFor="uploadFile">파일 (PDF / 스크린샷)</label>
          <input
            id="uploadFile"
            type="file"
            accept=".pdf,application/pdf,.png,.jpg,.jpeg,.webp,image/png,image/jpeg,image/webp"
            onChange={handleFileChange}
          />
          {fileError !== null ? <p className="file-error">{fileError}</p> : null}
          {selectedFile !== null ? (
            <p className="file-info">{selectedFile.name} ({(selectedFile.size / 1024).toFixed(0)} KB)</p>
          ) : null}
        </div>
      ) : (
        <>
          <div className="form-row">
            <label htmlFor="emailSubject">제목 (Subject)</label>
            <input
              id="emailSubject"
              value={emailSubject}
              onChange={(event) => { setEmailSubject(event.target.value); }}
              placeholder="비워두면 '이메일'로 저장됩니다"
            />
          </div>
          <div className="form-row">
            <label htmlFor="emailBody">이메일 본문</label>
            <textarea
              id="emailBody"
              value={emailBody}
              onChange={(event) => { setEmailBody(event.target.value); }}
              rows={10}
              placeholder="이메일 본문을 붙여넣으세요"
            />
          </div>
        </>
      )}

      <button className="primary-button" type="submit" disabled={isSubmitting || isInputEmpty}>
        {isSubmitting ? '추출 중...' : '액션 추출'}
      </button>
    </form>
  );
}
