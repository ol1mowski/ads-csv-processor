import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { errorMessage, NO_CONNECTION_MESSAGE, problemCode } from '../errorMessage.util';

const FALLBACK = 'Coś poszło nie tak.';

function httpError(status: number, data: unknown = '') {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, null, {
    status,
    statusText: '',
    headers: {},
    config,
    data,
  });
}

describe('errorMessage', () => {
  it.each([
    [401, 'BAD_CREDENTIALS', 'Nieprawidłowy login lub hasło'],
    [403, 'CSRF_INVALID', 'Sesja wygasła — odśwież stronę'],
    [409, 'JOB_NOT_CANCELLABLE', 'Zadanie już się zakończyło — nie można go anulować.'],
    [
      422,
      'INVALID_CSV_HEADER',
      'Serwer odrzucił plik: pierwsza linia nie jest oczekiwanym nagłówkiem CSV.',
    ],
  ])('explains %d %s', (status, code, message) => {
    expect(errorMessage(httpError(status, { status, code }), FALLBACK)).toBe(message);
  });

  it('falls back to the HTTP status when a proxy answers without a problem body', () => {
    expect(errorMessage(httpError(413, '<html>Too large</html>'), FALLBACK)).toBe(
      'Plik przekracza limit 5 GiB.',
    );
    expect(problemCode(httpError(403))).toBe('FORBIDDEN');
  });

  it('reports a request that got no answer as a connection problem', () => {
    expect(errorMessage(new AxiosError('Network Error', 'ERR_NETWORK'), FALLBACK)).toBe(
      NO_CONNECTION_MESSAGE,
    );
  });

  it('uses the fallback for unknown codes and non-HTTP errors', () => {
    expect(errorMessage(httpError(500, { code: 'INTERNAL_ERROR' }), FALLBACK)).toBe(FALLBACK);
    expect(errorMessage(new TypeError('boom'), FALLBACK)).toBe(FALLBACK);
  });
});
