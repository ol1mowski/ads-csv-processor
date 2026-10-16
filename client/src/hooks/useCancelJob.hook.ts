import { useMutation, useQueryClient } from '@tanstack/react-query';
import { httpStatus } from '@/api/httpClient.api';
import { cancelJob } from '@/api/jobs.api';
import { queryKeys } from '@/utils/queryKeys.util';

export function useCancelJob(jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => cancelJob(jobId),
    onMutate: () => queryClient.cancelQueries({ queryKey: queryKeys.job(jobId) }),
    onSuccess: (job) => {
      queryClient.setQueryData(queryKeys.job(job.id), job);
    },
    onError: (error) => {
      if (httpStatus(error) === 409) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.job(jobId) });
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.jobLists }),
  });
}
