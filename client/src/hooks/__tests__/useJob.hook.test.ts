import { act, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  completedJob,
  inSequence,
  JOB_ID,
  problem,
  processingJob,
  renderHookWithClient,
  server,
} from '@/test/utils';
import { queryKeys } from '@/utils/queryKeys.util';
import { JOB_POLL_MS, JOB_RETRY_MS, useJob } from '../useJob.hook';

const jobPath = '/api/v1/jobs/:id';

function serveJob(...responses: (() => Response)[]) {
  const sequence = inSequence(...responses);
  server.use(http.get(jobPath, sequence.resolver));
  return sequence.calls;
}

async function advance(ms: number) {
  await act(() => vi.advanceTimersByTimeAsync(ms));
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useJob', () => {
  it('asks for nothing while no job is selected', async () => {
    const calls = serveJob(() => HttpResponse.json(completedJob()));
    const { result } = renderHookWithClient(() => useJob(null));

    await advance(5 * JOB_POLL_MS);

    expect(result.current.data).toBeUndefined();
    expect(calls.count).toBe(0);
  });

  it('polls a running job every second and stops once it finishes', async () => {
    const calls = serveJob(
      () => HttpResponse.json(processingJob()),
      () => HttpResponse.json(processingJob({ progressPercent: 80 })),
      () => HttpResponse.json(completedJob()),
    );
    const { result } = renderHookWithClient(() => useJob(JOB_ID));
    await waitFor(() => {
      expect(result.current.data?.progressPercent).toBe(42);
    });

    await advance(JOB_POLL_MS);
    await waitFor(() => {
      expect(result.current.data?.progressPercent).toBe(80);
    });
    await advance(JOB_POLL_MS);
    await waitFor(() => {
      expect(result.current.data?.status).toBe('COMPLETED');
    });
    await advance(10 * JOB_POLL_MS);

    expect(calls.count).toBe(3);
  });

  it('refreshes the history once the job finishes', async () => {
    serveJob(() => HttpResponse.json(completedJob()));
    const { queryClient } = renderHookWithClient(() => useJob(JOB_ID));
    queryClient.setQueryData(queryKeys.jobList('all', 0), { items: [] });

    await waitFor(() => {
      expect(queryClient.getQueryState(queryKeys.jobList('all', 0))?.isInvalidated).toBe(true);
    });
  });

  it('gives up on a 4xx answer', async () => {
    const calls = serveJob(() => problem(404, 'JOB_NOT_FOUND'));
    const { result } = renderHookWithClient(() => useJob(JOB_ID));
    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    await advance(3 * JOB_RETRY_MS);

    expect(calls.count).toBe(1);
  });

  it('keeps retrying every few seconds while the server fails and recovers afterwards', async () => {
    const calls = serveJob(
      () => problem(503, 'INTERNAL_ERROR'),
      () => HttpResponse.error(),
      () => HttpResponse.json(processingJob()),
    );
    const { result } = renderHookWithClient(() => useJob(JOB_ID));
    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    await advance(JOB_POLL_MS);
    expect(calls.count).toBe(1);
    await advance(JOB_RETRY_MS - JOB_POLL_MS);
    expect(calls.count).toBe(2);
    await advance(JOB_RETRY_MS);

    await waitFor(() => {
      expect(result.current.data?.status).toBe('PROCESSING');
    });
    expect(result.current.isError).toBe(false);
    expect(calls.count).toBe(3);
  });
});
