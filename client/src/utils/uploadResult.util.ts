import { httpStatus, isAborted } from '@/api/httpClient.api';
import type { Job } from '@/api/types.api';
import { errorMessage } from './errorMessage.util';

export interface UploadResult {
  readonly tone: 'success' | 'warning' | 'error';
  readonly message: string;
  readonly issues?: readonly string[];
}

export const UPLOAD_ABORTED_MESSAGE = 'Wysyłanie przerwane — plik nie został zapisany na serwerze.';
export const MAYBE_ACCEPTED_MESSAGE =
  'Plik mógł zostać przyjęty — sprawdź historię i w razie potrzeby anuluj przetwarzanie.';
export const CONNECTION_LOST_MESSAGE = 'Połączenie przerwane — sprawdź rozmiar i format pliku.';
export const UNREADABLE_FILE_MESSAGE = 'Nie udało się odczytać pliku z dysku.';

export function acceptedResult(job: Job): UploadResult {
  return {
    tone: 'success',
    message: `Plik „${job.fileName}” został przyjęty — przetwarzanie trwa w tle.`,
  };
}

export function invalidFileResult(issues: readonly string[]): UploadResult {
  return { tone: 'error', message: 'Plik nie spełnia wymagań:', issues };
}

export function uploadFailureResult(error: unknown, sentAllBytes: boolean): UploadResult {
  if (isAborted(error)) {
    return {
      tone: 'warning',
      message: sentAllBytes ? MAYBE_ACCEPTED_MESSAGE : UPLOAD_ABORTED_MESSAGE,
    };
  }
  if (httpStatus(error) === undefined) {
    return {
      tone: 'error',
      message: sentAllBytes ? MAYBE_ACCEPTED_MESSAGE : CONNECTION_LOST_MESSAGE,
    };
  }
  return { tone: 'error', message: errorMessage(error, 'Serwer odrzucił plik.') };
}
