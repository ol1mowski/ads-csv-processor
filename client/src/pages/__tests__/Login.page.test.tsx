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
import { LoginPage } from '../Login.page';

const loginPath = '/api/v1/auth/login';

function recordLogins(respond: () => Response | Promise<Response>) {
  const logins: { body: unknown; xsrfHeader: string | null }[] = [];
  server.use(
    http.post(loginPath, async ({ request }) => {
      logins.push({ body: await request.json(), xsrfHeader: xsrfHeaderOf(request) });
      return respond();
    }),
  );
  return logins;
}

function renderLoginPage() {
  const view = renderWithClient(<LoginPage />);
  return {
    ...view,
    signIn: async (username: string, password: string) => {
      if (username !== '') {
        await view.user.type(screen.getByLabelText('Login'), username);
      }
      if (password !== '') {
        await view.user.type(screen.getByLabelText('Hasło'), password);
      }
      await view.user.click(screen.getByRole('button', { name: 'Zaloguj się' }));
    },
  };
}

describe('LoginPage', () => {
  it('sends nothing while a field is empty', async () => {
    const logins = recordLogins(() => HttpResponse.json({ username: 'alice' }));
    const { signIn } = renderLoginPage();

    await signIn('alice', '');

    expect(screen.getByLabelText('Hasło')).toBeInvalid();
    expect(logins).toHaveLength(0);
  });

  it('limits both fields to what the server accepts', () => {
    renderLoginPage();

    expect(screen.getByLabelText('Login')).toHaveAttribute('maxlength', '64');
    expect(screen.getByLabelText('Hasło')).toHaveAttribute('maxlength', '128');
  });

  it('signs in with the XSRF token, stores the session and drops data cached for anyone else', async () => {
    setXsrfCookie();
    const logins = recordLogins(() => HttpResponse.json({ username: 'alice' }));
    const { signIn, queryClient } = renderLoginPage();
    queryClient.setQueryData(queryKeys.jobList('all', 0), { items: ['previous user'] });

    await signIn('alice', 'secret');

    await waitFor(() => {
      expect(queryClient.getQueryData(queryKeys.session)).toEqual({ username: 'alice' });
    });
    expect(queryClient.getQueryData(queryKeys.jobList('all', 0))).toBeUndefined();
    expect(logins).toEqual([
      { body: { username: 'alice', password: 'secret' }, xsrfHeader: TEST_XSRF_TOKEN },
    ]);
  });

  it.each([
    [() => problem(401, 'BAD_CREDENTIALS'), 'Nieprawidłowy login lub hasło'],
    [() => problem(500, 'INTERNAL_ERROR'), 'Nie udało się zalogować — spróbuj ponownie.'],
    [() => HttpResponse.error(), 'Brak połączenia z serwerem — spróbuj ponownie.'],
  ])('explains a failed sign-in (%#)', async (response, message) => {
    recordLogins(response);
    const { signIn, queryClient } = renderLoginPage();

    await signIn('alice', 'guess');

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(queryClient.getQueryData(queryKeys.session)).toBeUndefined();
  });

  it('asks for a session re-check, which brings a fresh CSRF cookie, when the login token is rejected', async () => {
    recordLogins(() => problem(403, 'CSRF_INVALID'));
    const { signIn, queryClient } = renderLoginPage();
    queryClient.setQueryData(queryKeys.session, null);

    await signIn('alice', 'secret');

    expect(await screen.findByRole('alert')).toHaveTextContent('Sesja wygasła — odśwież stronę');
    expect(queryClient.getQueryState(queryKeys.session)?.isInvalidated).toBe(true);
  });

  it('blocks a second submit while the first one is in flight', async () => {
    const logins = recordLogins(async () => {
      await delay(50);
      return HttpResponse.json({ username: 'alice' });
    });
    const { signIn, user } = renderLoginPage();

    await signIn('alice', 'secret');
    expect(screen.getByRole('button', { name: 'Logowanie…' })).toBeDisabled();
    await user.type(screen.getByLabelText('Hasło'), '{Enter}');

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Zaloguj się' })).toBeEnabled();
    });
    expect(logins).toHaveLength(1);
  });
});
