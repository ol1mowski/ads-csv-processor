import { screen, waitFor } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import {
  problem,
  renderWithClient,
  server,
  setXsrfCookie,
  TEST_XSRF_TOKEN,
  xsrfHeaderOf,
} from '@/test/utils';
import { queryKeys } from '@/utils/queryKeys.util';
import { UserMenu } from '../UserMenu.component';

describe('UserMenu', () => {
  it('shows who is signed in', () => {
    renderWithClient(<UserMenu username="alice" />);

    expect(screen.getByText('alice')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Wyloguj' })).toBeEnabled();
  });

  it('signs out with the XSRF token, ends the session and forgets the cached jobs', async () => {
    setXsrfCookie();
    const headers: (string | null)[] = [];
    server.use(
      http.post('/api/v1/auth/logout', async ({ request }) => {
        headers.push(xsrfHeaderOf(request));
        await delay(20);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user, queryClient } = renderWithClient(<UserMenu username="alice" />);
    queryClient.setQueryData(queryKeys.session, { username: 'alice' });
    queryClient.setQueryData(queryKeys.jobList('all', 0), { items: [] });

    await user.click(screen.getByRole('button', { name: 'Wyloguj' }));

    expect(screen.getByRole('button', { name: 'Wylogowywanie…' })).toBeDisabled();
    await waitFor(() => {
      expect(queryClient.getQueryData(queryKeys.session)).toBeNull();
    });
    expect(queryClient.getQueryData(queryKeys.jobList('all', 0))).toBeUndefined();
    expect(headers).toEqual([TEST_XSRF_TOKEN]);
  });

  it('keeps the session and asks to refresh the page when the CSRF token is rejected', async () => {
    server.use(http.post('/api/v1/auth/logout', () => problem(403, 'CSRF_INVALID')));
    const { user, queryClient } = renderWithClient(<UserMenu username="alice" />);
    queryClient.setQueryData(queryKeys.session, { username: 'alice' });

    await user.click(screen.getByRole('button', { name: 'Wyloguj' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Sesja wygasła — odśwież stronę');
    expect(queryClient.getQueryData(queryKeys.session)).toEqual({ username: 'alice' });
  });
});
