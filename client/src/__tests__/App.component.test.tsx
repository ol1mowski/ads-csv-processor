import type { QueryClient } from '@tanstack/react-query';
import { act, screen, waitFor, within } from '@testing-library/react';
import type { UserEvent } from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { App } from '@/App.component';
import {
  buildPage,
  completedJob,
  problem,
  renderWithClient,
  server,
  setXsrfCookie,
  TEST_XSRF_TOKEN,
  xsrfHeaderOf,
} from '@/test/utils';

const PASSWORD = 'alice-test-password';

interface FakeAuthServer {
  user: string | null;
  sessionChecks: number;
  readonly logins: { readonly body: unknown; readonly xsrfHeader: string | null }[];
  readonly logoutXsrfHeaders: (string | null)[];
}

function startFakeAuthServer(user: string | null = null): FakeAuthServer {
  const state: FakeAuthServer = { user, sessionChecks: 0, logins: [], logoutXsrfHeaders: [] };
  server.use(
    http.get('/api/v1/auth/me', () => {
      state.sessionChecks += 1;
      return state.user === null
        ? problem(401, 'UNAUTHENTICATED')
        : HttpResponse.json({ username: state.user });
    }),
    http.post('/api/v1/auth/login', async ({ request }) => {
      const body = (await request.json()) as { username: string; password: string };
      state.logins.push({ body, xsrfHeader: xsrfHeaderOf(request) });
      if (body.username !== 'alice' || body.password !== PASSWORD) {
        return problem(401, 'BAD_CREDENTIALS');
      }
      state.user = 'alice';
      return HttpResponse.json({ username: 'alice' });
    }),
    http.post('/api/v1/auth/logout', ({ request }) => {
      state.logoutXsrfHeaders.push(xsrfHeaderOf(request));
      state.user = null;
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return state;
}

function serveHistoryOf(owner: string) {
  server.use(
    http.get('/api/v1/jobs', () =>
      HttpResponse.json(buildPage([completedJob({ fileName: `${owner}-campaign.csv` })])),
    ),
  );
}

async function signIn(user: UserEvent, username: string, password: string) {
  await user.type(await screen.findByLabelText('Login'), username);
  await user.type(screen.getByLabelText('Hasło'), password);
  await user.click(screen.getByRole('button', { name: 'Zaloguj się' }));
}

function cachedUserData(queryClient: QueryClient) {
  return queryClient.getQueryCache().findAll({ predicate: (q) => q.queryKey[0] !== 'session' });
}

describe('App', () => {
  it('shows the login page when nobody is signed in', async () => {
    startFakeAuthServer();
    renderWithClient(<App />);

    expect(await screen.findByRole('heading', { name: 'Logowanie' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Wyślij plik CSV' })).not.toBeInTheDocument();
  });

  it('shows the jobs page with the username and a logout button for a signed-in user', async () => {
    startFakeAuthServer('alice');
    serveHistoryOf('alice');
    renderWithClient(<App />);

    const header = (await screen.findByRole('heading', { name: /Procesor CSV/ })).closest('header');
    expect(header).not.toBeNull();
    expect(within(header as HTMLElement).getByText('alice')).toBeInTheDocument();
    expect(within(header as HTMLElement).getByRole('button', { name: 'Wyloguj' })).toBeVisible();
    expect(await screen.findByText('alice-campaign.csv')).toBeInTheDocument();
  });

  it('signs in with the XSRF token and opens the jobs page', async () => {
    setXsrfCookie();
    const auth = startFakeAuthServer();
    serveHistoryOf('alice');
    const { user } = renderWithClient(<App />);

    await signIn(user, 'alice', PASSWORD);

    expect(await screen.findByText('alice-campaign.csv')).toBeInTheDocument();
    expect(auth.logins).toEqual([
      { body: { username: 'alice', password: PASSWORD }, xsrfHeader: TEST_XSRF_TOKEN },
    ]);
  });

  it('sends nothing while a field is empty', async () => {
    const auth = startFakeAuthServer();
    const { user } = renderWithClient(<App />);

    await user.type(await screen.findByLabelText('Login'), 'alice');
    await user.click(screen.getByRole('button', { name: 'Zaloguj się' }));

    expect(screen.getByLabelText('Hasło')).toBeInvalid();
    expect(auth.logins).toHaveLength(0);
  });

  it('reports wrong credentials with one message for both fields', async () => {
    startFakeAuthServer();
    const { user } = renderWithClient(<App />);

    await signIn(user, 'mallory', 'guess');

    expect(await screen.findByRole('alert')).toHaveTextContent('Nieprawidłowy login lub hasło');
  });

  it('asks to refresh and fetches a fresh CSRF cookie when the login token is rejected', async () => {
    const auth = startFakeAuthServer();
    server.use(http.post('/api/v1/auth/login', () => problem(403, 'CSRF_INVALID')));
    const { user } = renderWithClient(<App />);

    await signIn(user, 'alice', PASSWORD);

    expect(await screen.findByRole('alert')).toHaveTextContent('Sesja wygasła — odśwież stronę');
    await waitFor(() => {
      expect(auth.sessionChecks).toBe(2);
    });
  });

  it('signs out with the XSRF token and forgets the cached jobs', async () => {
    setXsrfCookie();
    const auth = startFakeAuthServer('alice');
    serveHistoryOf('alice');
    const { user, queryClient } = renderWithClient(<App />);
    expect(await screen.findByText('alice-campaign.csv')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Wyloguj' }));

    expect(await screen.findByRole('heading', { name: 'Logowanie' })).toBeInTheDocument();
    expect(cachedUserData(queryClient)).toHaveLength(0);
    expect(auth.logoutXsrfHeaders).toEqual([TEST_XSRF_TOKEN]);
    await waitFor(() => {
      expect(auth.sessionChecks).toBe(2);
    });
  });

  it('returns to the login page when an API call reports an expired session', async () => {
    const auth = startFakeAuthServer('alice');
    server.use(
      http.get('/api/v1/jobs', () => {
        auth.user = null;
        return problem(401, 'UNAUTHENTICATED');
      }),
    );
    const { queryClient } = renderWithClient(<App />);

    expect(await screen.findByRole('heading', { name: 'Logowanie' })).toBeInTheDocument();
    expect(cachedUserData(queryClient)).toHaveLength(0);
  });

  it('never shows the previous user its jobs when the session check finds another user', async () => {
    const auth = startFakeAuthServer('alice');
    server.use(
      http.get('/api/v1/jobs', () =>
        HttpResponse.json(
          buildPage(
            auth.user === 'alice' ? [completedJob({ fileName: 'alice-campaign.csv' })] : [],
          ),
        ),
      ),
    );
    const { queryClient } = renderWithClient(<App />);
    expect(await screen.findByText('alice-campaign.csv')).toBeInTheDocument();

    auth.user = 'bob';
    await act(() => queryClient.refetchQueries({ queryKey: ['session'] }));

    expect(await screen.findByText('bob')).toBeInTheDocument();
    expect(screen.queryByText('alice-campaign.csv')).not.toBeInTheDocument();
  });

  it('offers a retry when the session cannot be checked', async () => {
    let checks = 0;
    server.use(
      http.get('/api/v1/auth/me', () => {
        checks += 1;
        return checks === 1 ? problem(503, 'INTERNAL_ERROR') : problem(401, 'UNAUTHENTICATED');
      }),
    );
    const { user } = renderWithClient(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Nie udało się połączyć z serwerem.',
    );
    await user.click(screen.getByRole('button', { name: 'Spróbuj ponownie' }));

    expect(await screen.findByRole('heading', { name: 'Logowanie' })).toBeInTheDocument();
  });
});
