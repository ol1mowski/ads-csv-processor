import axios from 'axios';

interface Problem {
  readonly code?: string;
}

export const NO_CONNECTION_MESSAGE = 'Brak połączenia z serwerem — spróbuj ponownie.';

const MESSAGES: Readonly<Record<string, string>> = {
  BAD_CREDENTIALS: 'Nieprawidłowy login lub hasło',
  UNAUTHENTICATED: 'Sesja wygasła — zaloguj się ponownie.',
  CSRF_INVALID: 'Sesja wygasła — odśwież stronę',
  FORBIDDEN: 'Brak uprawnień do tej operacji.',
  JOB_NOT_FOUND: 'Nie znaleziono zadania.',
  JOB_NOT_CANCELLABLE: 'Zadanie już się zakończyło — nie można go anulować.',
  EMPTY_FILE: 'Plik jest pusty.',
  FILE_TOO_LARGE: 'Plik przekracza limit 5 GiB.',
  UNSUPPORTED_MEDIA_TYPE: 'Serwer przyjmuje wyłącznie pliki CSV (text/csv).',
  INVALID_CSV_HEADER: 'Serwer odrzucił plik: pierwsza linia nie jest oczekiwanym nagłówkiem CSV.',
  UPLOAD_INTERRUPTED: 'Serwer otrzymał niekompletny plik — spróbuj ponownie.',
  INSUFFICIENT_STORAGE: 'Na serwerze zabrakło miejsca na dysku — spróbuj później.',
};

const CODES_BY_STATUS: Readonly<Record<number, string>> = {
  401: 'UNAUTHENTICATED',
  403: 'FORBIDDEN',
  413: 'FILE_TOO_LARGE',
  415: 'UNSUPPORTED_MEDIA_TYPE',
};

export function problemCode(error: unknown): string | undefined {
  if (!axios.isAxiosError<Problem>(error) || error.response === undefined) {
    return undefined;
  }
  return error.response.data?.code ?? CODES_BY_STATUS[error.response.status];
}

export function errorMessage(error: unknown, fallback: string): string {
  if (!axios.isAxiosError<Problem>(error)) {
    return fallback;
  }
  if (error.response === undefined) {
    return NO_CONNECTION_MESSAGE;
  }
  const code = problemCode(error);
  return (code === undefined ? undefined : MESSAGES[code]) ?? fallback;
}
