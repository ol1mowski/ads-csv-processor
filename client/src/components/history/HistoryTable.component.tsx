import type { Job } from '@/api/types.api';
import { HistoryRow } from './HistoryRow.component';

const COLUMNS = [
  'Plik',
  'Rozmiar',
  'Status',
  'Utworzono',
  'Zakończono',
  'Wiersze',
  'Odrzucone',
  'CTR',
  'Wynik',
] as const;

interface HistoryTableProps {
  readonly jobs: readonly Job[];
  readonly selectedJobId: string | null;
  readonly onSelect: (jobId: string) => void;
}

export function HistoryTable({ jobs, selectedJobId, onSelect }: HistoryTableProps) {
  return (
    <div className="lg:overflow-x-auto">
      <table className="block w-full text-sm lg:table">
        <caption className="sr-only">Historia przetworzonych plików</caption>
        <thead className="hidden border-b border-slate-200 text-left text-xs text-slate-500 uppercase lg:table-header-group dark:border-slate-800 dark:text-slate-400">
          <tr>
            {COLUMNS.map((column) => (
              <th key={column} scope="col" className="px-3 py-2 font-medium">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="block space-y-3 lg:table-row-group lg:space-y-0">
          {jobs.map((job) => (
            <HistoryRow
              key={job.id}
              job={job}
              selected={job.id === selectedJobId}
              onSelect={onSelect}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}
