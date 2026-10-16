import { act, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import {
  buildJob,
  completedJob,
  JOB_ID,
  problem,
  processingJob,
  renderHookWithClient,
  server,
} from '@/test/utils';
import { queryKeys } from '@/utils/queryKeys.util';
import { useCancelJob } from '../useCancelJob.hook';

const cancelPath = '/api/v1/jobs/:id/cancel';

function renderCancel() {
  const view = renderHookWithClient(() => useCancelJob(JOB_ID));
  view.queryClient.setQueryData(queryKeys.job(JOB_ID), processingJob());
  view.queryClient.setQueryData(queryKeys.jobList('all', 0), { items: [] });
  return view;
}

describe('useCancelJob', () => {
  it('stores the cancelled job and refreshes the history', async () => {
    server.use(http.post(cancelPath, () => HttpResponse.json(buildJob({ status: 'CANCELLED' }))));
    const { result, queryClient } = renderCancel();

    await act(() => result.current.mutateAsync());

    expect(queryClient.getQueryData(queryKeys.job(JOB_ID))).toMatchObject({ status: 'CANCELLED' });
    expect(queryClient.getQueryState(queryKeys.jobList('all', 0))?.isInvalidated).toBe(true);
  });

  it('fetches the real status when the job finished in the meantime', async () => {
    server.use(
      http.post(cancelPath, () => problem(409, 'JOB_NOT_CANCELLABLE')),
      http.get('/api/v1/jobs/:id', () => HttpResponse.json(completedJob())),
    );
    const { result, queryClient } = renderCancel();

    act(() => {
      result.current.mutate();
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
    expect(queryClient.getQueryState(queryKeys.job(JOB_ID))?.isInvalidated).toBe(true);
  });

  it('keeps the cached job untouched when cancelling fails for another reason', async () => {
    server.use(http.post(cancelPath, () => problem(403, 'CSRF_INVALID')));
    const { result, queryClient } = renderCancel();

    act(() => {
      result.current.mutate();
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
    expect(queryClient.getQueryData(queryKeys.job(JOB_ID))).toEqual(processingJob());
    expect(queryClient.getQueryState(queryKeys.job(JOB_ID))?.isInvalidated).toBe(false);
  });
});
