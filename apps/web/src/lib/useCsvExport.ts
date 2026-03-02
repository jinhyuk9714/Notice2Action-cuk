import { useCallback, useEffect, useState } from 'react';
import { fetchAllMatchingActions, type SearchParams } from './api';
import { downloadCsv, generateActionsCsv } from './csv';

export type UseCsvExportOptions = Readonly<{
  sort: 'recent' | 'due';
  search: SearchParams;
  filename?: string;
}>;

export type UseCsvExportResult = Readonly<{
  csvExporting: boolean;
  csvError: string | null;
  exportCsv: () => Promise<void>;
  clearCsvError: () => void;
}>;

export function useCsvExport({
  sort,
  search,
  filename = 'notice2action-actions.csv',
}: UseCsvExportOptions): UseCsvExportResult {
  const [csvExporting, setCsvExporting] = useState(false);
  const [csvError, setCsvError] = useState<string | null>(null);

  useEffect(() => {
    if (csvError === null) return;
    const timer = setTimeout(() => { setCsvError(null); }, 4000);
    return () => { clearTimeout(timer); };
  }, [csvError]);

  const exportCsv = useCallback(async () => {
    setCsvExporting(true);
    try {
      const allActions = await fetchAllMatchingActions(sort, search);
      const csv = generateActionsCsv(allActions);
      downloadCsv(csv, filename);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'CSV 내보내기에 실패했습니다';
      setCsvError(message);
    } finally {
      setCsvExporting(false);
    }
  }, [filename, search, sort]);

  const clearCsvError = useCallback(() => {
    setCsvError(null);
  }, []);

  return {
    csvExporting,
    csvError,
    exportCsv,
    clearCsvError,
  };
}
