import { skipToken, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { isClientError } from '@/api/httpClient.api';
import { getJob } from '@/api/jobs.api';
import { isActive } from '@/utils/jobStatus.util';
import { queryKeys } from '@/utils/queryKeys.util';

export const JOB_POLL_MS = 1000;
export const JOB_RETRY_MS = 5000;

export function useJob(jobId: string | null) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: queryKeys.job(jobId),
    queryFn: jobId === null ? skipToken : ({ signal }) => getJob(jobId, signal),
    networkMode: 'always',
    retry: false,
    refetchInterval: ({ state }) => {
      if (state.status === 'error') {
        return isClientError(state.error) ? false : JOB_RETRY_MS;
      }
      return state.data !== undefined && !isActive(state.data.status) ? false : JOB_POLL_MS;
    },
  });
  const status = query.data?.status;

  useEffect(() => {
    if (status !== undefined && !isActive(status)) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.jobLists });
    }
  }, [status, queryClient]);

  return query;
}
