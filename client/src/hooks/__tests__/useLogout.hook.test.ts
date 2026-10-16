import { act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { problem, renderHookWithClient, server } from '@/test/utils';
import { queryKeys } from '@/utils/queryKeys.util';
import { useLogout } from '../useLogout.hook';

describe('useLogout', () => {
  it('ends the session and forgets the user data', async () => {
    server.use(http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })));
    const { result, queryClient } = renderHookWithClient(() => useLogout());
    queryClient.setQueryData(queryKeys.session, { username: 'alice' });
    queryClient.setQueryData(queryKeys.job('x'), { id: 'x' });

    await act(() => result.current.mutateAsync());

    expect(queryClient.getQueryData(queryKeys.session)).toBeNull();
    expect(queryClient.getQueryData(queryKeys.job('x'))).toBeUndefined();
    expect(queryClient.getQueryState(queryKeys.session)?.isInvalidated).toBe(true);
  });

  it('keeps the session when the server refuses', async () => {
    server.use(http.post('/api/v1/auth/logout', () => problem(403, 'CSRF_INVALID')));
    const { result, queryClient } = renderHookWithClient(() => useLogout());
    queryClient.setQueryData(queryKeys.session, { username: 'alice' });

    await act(() => result.current.mutateAsync().catch(() => undefined));

    expect(queryClient.getQueryData(queryKeys.session)).toEqual({ username: 'alice' });
  });
});
