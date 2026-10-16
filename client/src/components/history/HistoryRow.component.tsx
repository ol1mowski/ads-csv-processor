import type { Job } from '@/api/types.api';
import { StatusBadge } from '@/components/ui/StatusBadge.component';
import {
  EMPTY_VALUE,
  formatBytes,
  formatCount,
  formatDateTime,
  formatPercent,
  formatRatioAsPercent,
} from '@/utils/format.util';
import { HistoryCell } from './HistoryCell.component';

interface HistoryRowProps {
  readonly job: Job;
  readonly selected: boolean;
  readonly onSelect: (jobId: string) => void;
}

export function HistoryRow({ job, selected, onSelect }: HistoryRowProps) {
  return (
    <tr
      aria-current={selected ? 'true' : undefined}
      className="block rounded-xl border border-slate-200 p-3 aria-[current=true]:border-indigo-400 aria-[current=true]:bg-indigo-50/60 lg:table-row lg:rounded-none lg:border-0 lg:border-b lg:p-0 dark:border-slate-800 dark:aria-[current=true]:bg-indigo-950/40"
    >
      <HistoryCell label="Plik">
        <span className="inline-flex max-w-full items-center gap-2 align-middle">
          <button
            type="button"
            aria-label={`Pokaż status pliku ${job.fileName}`}
            onClick={() => {
              onSelect(job.id);
            }}
            className="max-w-full min-w-0 truncate rounded text-left font-medium text-indigo-700 underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-indigo-500 lg:max-w-56 dark:text-indigo-300"
          >
            {job.fileName}
          </button>
          {selected && (
            <span
              aria-hidden="true"
              className="shrink-0 rounded-full bg-indigo-600 px-2 py-0.5 text-xs font-semibold text-white dark:bg-indigo-400 dark:text-slate-950"
            >
              Wybrane
            </span>
          )}
        </span>
      </HistoryCell>
      <HistoryCell label="Rozmiar">{formatBytes(job.sizeBytes)}</HistoryCell>
      <HistoryCell label="Status">
        <span className="inline-flex items-center gap-2">
          <StatusBadge status={job.status} />
          {job.status === 'PROCESSING' && (
            <span className="tabular-nums">{formatPercent(job.progressPercent)}</span>
          )}
        </span>
      </HistoryCell>
      <HistoryCell label="Utworzono">{formatDateTime(job.createdAt)}</HistoryCell>
      <HistoryCell label="Zakończono">{formatDateTime(job.finishedAt)}</HistoryCell>
      <HistoryCell label="Wiersze">{formatCount(job.rowsProcessed)}</HistoryCell>
      <HistoryCell label="Odrzucone">{formatCount(job.rowsRejected)}</HistoryCell>
      <HistoryCell label="CTR">{formatRatioAsPercent(job.totals.ctr)}</HistoryCell>
      <HistoryCell label="Wynik">
        {job.resultUrl === null ? (
          EMPTY_VALUE
        ) : (
          <a
            href={job.resultUrl}
            download
            aria-label={`Pobierz wynik ${job.fileName}`}
            className="rounded font-medium text-indigo-700 underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-indigo-500 dark:text-indigo-300"
          >
            Pobierz
          </a>
        )}
      </HistoryCell>
    </tr>
  );
}
