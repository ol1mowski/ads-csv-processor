import type { Job } from '@/api/types.api';
import { Alert } from '@/components/ui/Alert.component';
import { Button } from '@/components/ui/Button.component';
import { buttonClass } from '@/components/ui/buttonClass.util';
import { useCancelJob } from '@/hooks/useCancelJob.hook';
import { errorMessage } from '@/utils/errorMessage.util';
import { isActive } from '@/utils/jobStatus.util';

export function JobActions({ job }: { readonly job: Job }) {
  const cancel = useCancelJob(job.id);
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-3">
        {isActive(job.status) && (
          <Button
            variant="danger"
            disabled={cancel.isPending}
            onClick={() => {
              cancel.mutate();
            }}
          >
            {cancel.isPending ? 'Anulowanie…' : 'Anuluj przetwarzanie'}
          </Button>
        )}
        {job.resultUrl !== null && (
          <a href={job.resultUrl} download className={buttonClass()}>
            Pobierz wynik CSV
          </a>
        )}
      </div>
      {cancel.isError && (
        <Alert tone="error">
          {errorMessage(cancel.error, 'Nie udało się anulować zadania. Spróbuj ponownie.')}
        </Alert>
      )}
    </div>
  );
}
