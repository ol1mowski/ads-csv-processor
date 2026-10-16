import { useState } from 'react';
import { Alert } from '@/components/ui/Alert.component';
import { Card } from '@/components/ui/Card.component';
import { useJobHistory } from '@/hooks/useJobHistory.hook';
import type { HistoryFilter } from '@/utils/queryKeys.util';
import { HistoryFilterControl } from './HistoryFilterControl.component';
import { HistoryPagination } from './HistoryPagination.component';
import { HistoryTable } from './HistoryTable.component';

const EMPTY_MESSAGES: Readonly<Record<HistoryFilter, string>> = {
  all: 'Nie wysłano jeszcze żadnego pliku.',
  finished: 'Brak zakończonych zadań.',
};

interface JobHistoryProps {
  readonly selectedJobId: string | null;
  readonly onSelect: (jobId: string) => void;
}

export function JobHistory({ selectedJobId, onSelect }: JobHistoryProps) {
  const [filter, setFilter] = useState<HistoryFilter>('all');
  const [page, setPage] = useState(0);
  const history = useJobHistory(filter, page);
  const jobs = history.data?.items ?? [];

  function changeFilter(next: HistoryFilter) {
    setFilter(next);
    setPage(0);
  }

  return (
    <Card
      title="Historia przetwarzania"
      actions={<HistoryFilterControl value={filter} onChange={changeFilter} />}
    >
      {history.isError && <Alert tone="error">Nie udało się pobrać historii przetwarzania.</Alert>}
      {history.isPending && (
        <p className="text-sm text-slate-600 dark:text-slate-400">Ładowanie historii…</p>
      )}
      {history.isSuccess && jobs.length === 0 && (
        <p className="text-sm text-slate-600 dark:text-slate-400">{EMPTY_MESSAGES[filter]}</p>
      )}
      {jobs.length > 0 && (
        <HistoryTable jobs={jobs} selectedJobId={selectedJobId} onSelect={onSelect} />
      )}
      {history.data !== undefined && (
        <HistoryPagination
          page={page}
          totalPages={history.data.totalPages}
          loading={history.isPlaceholderData}
          onPageChange={setPage}
        />
      )}
    </Card>
  );
}
