import { describe, it, expect } from 'vitest';
import { categoryLabel, evidenceFieldLabel, inferredLabel } from './labels';

describe('categoryLabel', () => {
  it('maps NOTICE to 공지', () => {
    expect(categoryLabel('NOTICE')).toBe('공지');
  });

  it('maps SYLLABUS to 강의계획서', () => {
    expect(categoryLabel('SYLLABUS')).toBe('강의계획서');
  });

  it('maps EMAIL to 이메일', () => {
    expect(categoryLabel('EMAIL')).toBe('이메일');
  });

  it('maps PDF to PDF', () => {
    expect(categoryLabel('PDF')).toBe('PDF');
  });

  it('maps SCREENSHOT to 스크린샷', () => {
    expect(categoryLabel('SCREENSHOT')).toBe('스크린샷');
  });

  it('returns empty string for null', () => {
    expect(categoryLabel(null)).toBe('');
  });

  it('returns raw value for unknown category', () => {
    expect(categoryLabel('UNKNOWN')).toBe('UNKNOWN');
  });
});

describe('evidenceFieldLabel', () => {
  it('maps dueAtLabel to 마감일', () => {
    expect(evidenceFieldLabel('dueAtLabel')).toBe('마감일');
  });

  it('maps systemHint to 시스템', () => {
    expect(evidenceFieldLabel('systemHint')).toBe('시스템');
  });

  it('maps actionVerb to 행동', () => {
    expect(evidenceFieldLabel('actionVerb')).toBe('행동');
  });

  it('maps requiredItems to 준비물', () => {
    expect(evidenceFieldLabel('requiredItems')).toBe('준비물');
  });

  it('maps eligibility to 대상자', () => {
    expect(evidenceFieldLabel('eligibility')).toBe('대상자');
  });

  it('returns raw value for unknown field', () => {
    expect(evidenceFieldLabel('unknownField')).toBe('unknownField');
  });
});

describe('inferredLabel', () => {
  it('returns 추론됨 for true', () => {
    expect(inferredLabel(true)).toBe('추론됨');
  });

  it('returns 확인됨 for false', () => {
    expect(inferredLabel(false)).toBe('확인됨');
  });
});
