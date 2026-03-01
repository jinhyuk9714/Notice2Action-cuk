import { describe, it, expect } from 'vitest';
import { generateActionsCsv } from './csv';
import type { SavedActionSummary } from './types';

const SAMPLE: SavedActionSummary = {
  id: '1',
  title: '공결 신청',
  actionSummary: 'TRINITY에서 신청',
  dueAtIso: '2026-03-12T18:00:00',
  dueAtLabel: '3월 12일 18시',
  eligibility: '재학생',
  sourceCategory: 'NOTICE',
  sourceTitle: '학사공지',
  confidenceScore: 0.92,
  createdAt: '2026-03-01T12:00:00',
};

describe('generateActionsCsv', () => {
  it('starts with BOM', () => {
    const csv = generateActionsCsv([]);
    expect(csv.charCodeAt(0)).toBe(0xFEFF);
  });

  it('includes header row', () => {
    const csv = generateActionsCsv([]);
    expect(csv).toContain('제목,요약,마감일');
  });

  it('generates correct data row', () => {
    const csv = generateActionsCsv([SAMPLE]);
    const lines = csv.split('\n');
    expect(lines).toHaveLength(2);
    expect(lines[1]).toContain('공결 신청');
    expect(lines[1]).toContain('92');
  });

  it('escapes fields with commas', () => {
    const action: SavedActionSummary = { ...SAMPLE, title: '장학금, 신청 안내' };
    const csv = generateActionsCsv([action]);
    expect(csv).toContain('"장학금, 신청 안내"');
  });

  it('escapes fields with double quotes', () => {
    const action: SavedActionSummary = { ...SAMPLE, title: '"긴급" 공지' };
    const csv = generateActionsCsv([action]);
    expect(csv).toContain('"""긴급"" 공지"');
  });

  it('handles null optional fields gracefully', () => {
    const action: SavedActionSummary = {
      ...SAMPLE,
      dueAtIso: null,
      dueAtLabel: null,
      eligibility: null,
      sourceTitle: null,
      sourceCategory: null,
    };
    const csv = generateActionsCsv([action]);
    expect(csv).not.toContain('null');
  });

  it('handles multiple actions', () => {
    const csv = generateActionsCsv([SAMPLE, { ...SAMPLE, id: '2', title: '두번째' }]);
    const lines = csv.split('\n');
    expect(lines).toHaveLength(3);
  });
});
