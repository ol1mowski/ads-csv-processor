import { act, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { problem, renderHookWithClient, server } from '@/test/utils';
import { queryKeys } from '@/utils/queryKeys.util';
import { useLogin } from '../useLogin.hook';

const credentials = { username: 'alice', password: 'secret' };

describe('useLogin', () => {
  it('starts the session with the returned user and drops older cached data', async () => {
    server.use(http.post('/api/v1/auth/login', () => HttpResponse.json({ username: 'alice' })));
    const { result, queryClient } = renderHookWithClient(() => useLogin());
    queryClient.setQueryData(queryKeys.jobList('all', 0), { items: ['stale'] });

    await act(() => result.current.mutateAsync(credentials));

    expect(queryClient.getQueryData(queryKeys.session)).toEqual({ username: 'alice' });
    expect(queryClient.getQueryData(queryKeys.jobList('all', 0))).toBeUndefined();
  });

  it('leaves the session alone after wrong credentials', async () => {
    server.use(http.post('/api/v1/auth/login', () => problem(401, 'BAD_CREDENTIALS')));
    const { result, queryClient } = renderHookWithClient(() => useLogin());
    queryClient.setQueryData(queryKeys.session, null);

    act(() => {
      result.current.mutate(credentials);
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
    expect(queryClient.getQueryData(queryKeys.session)).toBeNull();
    expect(queryClient.getQueryState(queryKeys.session)?.isInvalidated).toBe(false);
  });

  it('re-checks the session to get a fresh CSRF cookie after CSRF_INVALID', async () => {
    server.use(http.post('/api/v1/auth/login', () => problem(403, 'CSRF_INVALID')));
    const { result, queryClient } = renderHookWithClient(() => useLogin());
    queryClient.setQueryData(queryKeys.session, null);

    act(() => {
      result.current.mutate(credentials);
    });

    await waitFor(() => {
      expect(queryClient.getQueryState(queryKeys.session)?.isInvalidated).toBe(true);
    });
  });
});
