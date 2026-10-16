import { act, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { problem, renderHookWithClient, server } from '@/test/utils';
import { queryKeys } from '@/utils/queryKeys.util';
import { useSession } from '../useSession.hook';

function serveSession(...answers: (() => Response)[]) {
  let calls = 0;
  server.use(
    http.get('/api/v1/auth/me', () => {
      const answer = answers[Math.min(calls, answers.length - 1)];
      calls += 1;
      return answer?.() ?? HttpResponse.error();
    }),
  );
}

describe('useSession', () => {
  it('returns the signed-in user', async () => {
    serveSession(() => HttpResponse.json({ username: 'alice' }));
    const { result } = renderHookWithClient(() => useSession());

    await waitFor(() => {
      expect(result.current.data).toEqual({ username: 'alice' });
    });
  });

  it('returns null when nobody is signed in', async () => {
    serveSession(() => problem(401, 'UNAUTHENTICATED'));
    const { result } = renderHookWithClient(() => useSession());

    await waitFor(() => {
      expect(result.current.data).toBeNull();
    });
  });

  it('fails when the server cannot tell', async () => {
    serveSession(() => problem(503, 'INTERNAL_ERROR'));
    const { result } = renderHookWithClient(() => useSession());

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
  });

  it('drops the previous user its data when a re-check finds someone else', async () => {
    serveSession(
      () => HttpResponse.json({ username: 'alice' }),
      () => HttpResponse.json({ username: 'bob' }),
    );
    const { result, queryClient } = renderHookWithClient(() => useSession());
    await waitFor(() => {
      expect(result.current.data).toEqual({ username: 'alice' });
    });
    queryClient.setQueryData(queryKeys.jobList('all', 0), { items: ['alice data'] });

    await act(() => result.current.refetch());

    await waitFor(() => {
      expect(result.current.data).toEqual({ username: 'bob' });
    });
    expect(queryClient.getQueryData(queryKeys.jobList('all', 0))).toBeUndefined();
  });

  it('keeps the cached data when a re-check finds the same user', async () => {
    serveSession(() => HttpResponse.json({ username: 'alice' }));
    const { result, queryClient } = renderHookWithClient(() => useSession());
    await waitFor(() => {
      expect(result.current.data).toEqual({ username: 'alice' });
    });
    queryClient.setQueryData(queryKeys.jobList('all', 0), { items: ['alice data'] });

    await act(() => result.current.refetch());

    expect(queryClient.getQueryData(queryKeys.jobList('all', 0))).toEqual({
      items: ['alice data'],
    });
  });
});
