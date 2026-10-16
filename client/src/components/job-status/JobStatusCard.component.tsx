import { isClientError } from '@/api/httpClient.api';
import { Alert } from '@/components/ui/Alert.component';
import { Card } from '@/components/ui/Card.component';
import { StatusBadge } from '@/components/ui/StatusBadge.component';
import { useJob } from '@/hooks/useJob.hook';
import { errorMessage } from '@/utils/errorMessage.util';
import { STATUS_LABELS } from '@/utils/jobStatus.util';
import { JobActions } from './JobActions.component';
import { JobDetails } from './JobDetails.component';

export const NO_JOB_SELECTED_MESSAGE =
  'Wyślij plik albo wybierz zadanie z historii, aby zobaczyć jego status.';
export const RETRYING_MESSAGE = 'Nie udało się odświeżyć statusu — ponawiam…';

export function JobStatusCard({ jobId }: { readonly jobId: string | null }) {
  const query = useJob(jobId);
  const job = query.data;

  return (
    <Card title="Status zadania" actions={job && <StatusBadge status={job.status} />}>
      <p role="status" className="sr-only">
        {job === undefined ? '' : `Status zadania ${job.fileName}: ${STATUS_LABELS[job.status]}`}
      </p>
      {query.isError &&
        (isClientError(query.error) ? (
          <Alert tone="error">
            {errorMessage(query.error, 'Nie udało się pobrać statusu zadania.')}
          </Alert>
        ) : (
          <Alert tone="warning">{RETRYING_MESSAGE}</Alert>
        ))}
      {jobId === null && (
        <p className="text-sm text-slate-600 dark:text-slate-400">{NO_JOB_SELECTED_MESSAGE}</p>
      )}
      {jobId !== null && job === undefined && !query.isError && (
        <p className="text-sm text-slate-600 dark:text-slate-400">Ładowanie statusu…</p>
      )}
      {job !== undefined && (
        <>
          <JobDetails job={job} />
          <JobActions key={job.id} job={job} />
        </>
      )}
    </Card>
  );
}
