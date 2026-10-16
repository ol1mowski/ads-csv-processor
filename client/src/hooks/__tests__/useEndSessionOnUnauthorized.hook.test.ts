import { waitFor } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { getJob } from '@/api/jobs.api';
import { JOB_ID, problem, renderHookWithClient, server } from '@/test/utils';
import { queryKeys } from '@/utils/queryKeys.util';
import { useEndSessionOnUnauthorized } from '../useEndSessionOnUnauthorized.hook';

describe('useEndSessionOnUnauthorized', () => {
  it('ends the session when an API call answers 401', async () => {
    server.use(http.get('/api/v1/jobs/:id', () => problem(401, 'UNAUTHENTICATED')));
    const { queryClient } = renderHookWithClient(() => {
      useEndSessionOnUnauthorized();
    });
    queryClient.setQueryData(queryKeys.session, { username: 'alice' });

    await expect(getJob(JOB_ID)).rejects.toThrow();

    await waitFor(() => {
      expect(queryClient.getQueryData(queryKeys.session)).toBeNull();
    });
  });

  it('stops listening once unmounted', async () => {
    server.use(http.get('/api/v1/jobs/:id', () => problem(401, 'UNAUTHENTICATED')));
    const { queryClient, unmount } = renderHookWithClient(() => {
      useEndSessionOnUnauthorized();
    });
    queryClient.setQueryData(queryKeys.session, { username: 'alice' });

    unmount();
    await expect(getJob(JOB_ID)).rejects.toThrow();

    expect(queryClient.getQueryData(queryKeys.session)).toEqual({ username: 'alice' });
  });
});
