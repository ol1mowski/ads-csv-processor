import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { listJobs } from '@/api/jobs.api';
import { FINISHED_STATUSES, isActive } from '@/utils/jobStatus.util';
import { queryKeys, type HistoryFilter } from '@/utils/queryKeys.util';

export const HISTORY_PAGE_SIZE = 10;
export const HISTORY_POLL_MS = 3000;

export function useJobHistory(filter: HistoryFilter, page: number) {
  return useQuery({
    queryKey: queryKeys.jobList(filter, page),
    queryFn: ({ signal }) =>
      listJobs(page, HISTORY_PAGE_SIZE, filter === 'finished' ? FINISHED_STATUSES : null, signal),
    placeholderData: keepPreviousData,
    refetchInterval: ({ state }) =>
      filter === 'finished' || state.data?.items.some((job) => isActive(job.status))
        ? HISTORY_POLL_MS
        : false,
  });
}
