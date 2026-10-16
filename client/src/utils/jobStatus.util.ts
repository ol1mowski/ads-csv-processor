import type { FailureReason, JobStatus, RejectReason } from '@/api/types.api';

export const FINISHED_STATUSES: readonly JobStatus[] = ['COMPLETED', 'FAILED', 'CANCELLED'];

export function isActive(status: JobStatus): boolean {
  return status === 'QUEUED' || status === 'PROCESSING';
}

export const STATUS_LABELS: Readonly<Record<JobStatus, string>> = {
  QUEUED: 'W kolejce',
  PROCESSING: 'Przetwarzanie',
  COMPLETED: 'Zakończone',
  FAILED: 'Błąd',
  CANCELLED: 'Anulowane',
};

export const FAILURE_REASON_LABELS: Readonly<Record<FailureReason, string>> = {
  INVALID_HEADER: 'Plik nie ma oczekiwanego nagłówka CSV.',
  NO_VALID_ROWS: 'Plik nie zawiera żadnego poprawnego wiersza — sprawdź przykłady poniżej.',
  TOO_MANY_INVALID_ROWS:
    'Zbyt wiele niepoprawnych wierszy — wynik odrzucono. Popraw wiersze wskazane poniżej i wyślij plik ponownie.',
  INPUT_MISSING: 'Plik wejściowy nie jest już dostępny na serwerze.',
  INTERRUPTED: 'Przetwarzanie przerywano zbyt wiele razy (np. przez restart serwera).',
  PROCESSING_ERROR: 'Wystąpił nieoczekiwany błąd podczas przetwarzania.',
};

const REJECT_REASON_LABELS: Readonly<Record<RejectReason, string>> = {
  LINE_TOO_LONG: 'wiersz dłuższy niż 1024 bajty',
  FIELD_COUNT: 'zła liczba kolumn — oczekiwano 8 wartości rozdzielonych średnikiem',
  INVALID_ADVERTISER_ID:
    'kolumna advertiserId — oczekiwano liczby całkowitej (same cyfry, np. 5342)',
  INVALID_CAMPAIGN_ID: 'kolumna campaignId — oczekiwano liczby całkowitej (same cyfry, np. 11380)',
  INVALID_CREATIVE_ID: 'kolumna creativeId — oczekiwano liczby całkowitej (same cyfry, np. 526779)',
  INVALID_AD_BOX_UUID:
    'kolumna adBoxUUID — oczekiwano identyfikatora UUID, np. 287d06ca-6f4c-469a-8b22-d3081c8eaee9',
  INVALID_IMPRESSIONS:
    'kolumna impressions — oczekiwano nieujemnej liczby całkowitej (same cyfry, np. 591)',
  INVALID_CLICKS: 'kolumna clicks — oczekiwano nieujemnej liczby całkowitej (same cyfry, np. 22)',
  INVALID_REVENUE:
    'kolumna revenue — oczekiwano liczby z kropką dziesiętną, np. 2.9798 (nie 2,9798)',
  INVALID_ADVERTISER_MARGIN:
    'kolumna advertiserMargin — oczekiwano liczby z kropką dziesiętną, np. 0.1927 (nie 0,1927)',
  CLICKS_EXCEED_IMPRESSIONS:
    'więcej kliknięć (clicks) niż wyświetleń (impressions) — CTR nie może przekroczyć 100%',
};

export function rejectReasonLabel(reason: string): string {
  return Object.hasOwn(REJECT_REASON_LABELS, reason)
    ? REJECT_REASON_LABELS[reason as RejectReason]
    : reason;
}
