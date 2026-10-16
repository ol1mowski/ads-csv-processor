import type { Job } from '@/api/types.api';
import { Alert } from '@/components/ui/Alert.component';
import { ProgressBar } from '@/components/ui/ProgressBar.component';
import {
  formatBytes,
  formatCount,
  formatDateTime,
  formatDecimalCount,
  formatRatioAsPercent,
} from '@/utils/format.util';
import { FAILURE_REASON_LABELS } from '@/utils/jobStatus.util';
import { RejectedSamples } from './RejectedSamples.component';

function detailRows(job: Job): readonly (readonly [string, string])[] {
  return [
    ['Rozmiar', formatBytes(job.sizeBytes)],
    ['Przetworzone wiersze', formatCount(job.rowsProcessed)],
    ['Odrzucone wiersze', formatCount(job.rowsRejected)],
    ['Suma wyświetleń', formatDecimalCount(job.totals.impressions)],
    ['Suma kliknięć', formatDecimalCount(job.totals.clicks)],
    ['CTR całości', formatRatioAsPercent(job.totals.ctr)],
    ['Próby', formatCount(job.attempts)],
    ['Utworzono', formatDateTime(job.createdAt)],
    ['Rozpoczęto', formatDateTime(job.startedAt)],
    ['Zakończono', formatDateTime(job.finishedAt)],
  ];
}

export function JobDetails({ job }: { readonly job: Job }) {
  return (
    <div className="space-y-4">
      <p className="font-medium break-all">{job.fileName}</p>
      <ProgressBar
        percent={job.progressPercent}
        label="Postęp przetwarzania"
        valueText={`${formatBytes(job.bytesProcessed)} z ${formatBytes(job.sizeBytes)}`}
      />
      {job.failureReason !== null && (
        <Alert tone="error">{FAILURE_REASON_LABELS[job.failureReason]}</Alert>
      )}
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-2 xl:grid-cols-3">
        {detailRows(job).map(([term, value]) => (
          <div key={term} className="rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-800/60">
            <dt className="text-xs text-slate-500 dark:text-slate-400">{term}</dt>
            <dd className="mt-0.5 text-sm font-semibold tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
      <RejectedSamples samples={job.rejectedSamples} />
    </div>
  );
}
