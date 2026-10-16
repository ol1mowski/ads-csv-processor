import { act, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { JobPage } from '@/api/types.api';
import { buildJob, buildPage, completedJob, renderHookWithClient, server } from '@/test/utils';
import type { HistoryFilter } from '@/utils/queryKeys.util';
import { HISTORY_PAGE_SIZE, HISTORY_POLL_MS, useJobHistory } from '../useJobHistory.hook';

function serveHistory(...pages: JobPage[]) {
  const requests: URLSearchParams[] = [];
  server.use(
    http.get('/api/v1/jobs', ({ request }) => {
      requests.push(new URL(request.url).searchParams);
      return HttpResponse.json(pages[Math.min(requests.length - 1, pages.length - 1)]);
    }),
  );
  return requests;
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

describe('useJobHistory', () => {
  it.each<[HistoryFilter, string | null]>([
    ['all', null],
    ['finished', 'COMPLETED,FAILED,CANCELLED'],
  ])('asks for the %s filter with status=%s', async (filter, status) => {
    const requests = serveHistory(buildPage([]));
    const { result } = renderHookWithClient(() => useJobHistory(filter, 3));

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(requests[0]?.get('page')).toBe('3');
    expect(requests[0]?.get('size')).toBe(String(HISTORY_PAGE_SIZE));
    expect(requests[0]?.get('status')).toBe(status);
  });

  it('refreshes only while the page lists unfinished jobs', async () => {
    const requests = serveHistory(buildPage([buildJob()]), buildPage([completedJob()]));
    const { result } = renderHookWithClient(() => useJobHistory('all', 0));
    await waitFor(() => {
      expect(result.current.data?.items[0]?.status).toBe('QUEUED');
    });

    await advance(HISTORY_POLL_MS);
    await waitFor(() => {
      expect(result.current.data?.items[0]?.status).toBe('COMPLETED');
    });
    await advance(5 * HISTORY_POLL_MS);

    expect(requests).toHaveLength(2);
  });

  it('keeps refreshing the finished filter to catch jobs that finish later', async () => {
    const requests = serveHistory(buildPage([completedJob()]));
    const { result } = renderHookWithClient(() => useJobHistory('finished', 0));
    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    await advance(2 * HISTORY_POLL_MS);

    await waitFor(() => {
      expect(requests).toHaveLength(3);
    });
  });

  it('keeps the previous page on screen while the next one loads', async () => {
    serveHistory(
      buildPage([completedJob({ fileName: 'first.csv' })], { totalPages: 2 }),
      buildPage([completedJob({ fileName: 'second.csv' })], { page: 1, totalPages: 2 }),
    );
    let page = 0;
    const { result, rerender } = renderHookWithClient(() => useJobHistory('all', page));
    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    page = 1;
    rerender();

    expect(result.current.isPlaceholderData).toBe(true);
    expect(result.current.data?.items[0]?.fileName).toBe('first.csv');
    await waitFor(() => {
      expect(result.current.data?.items[0]?.fileName).toBe('second.csv');
    });
  });
});
