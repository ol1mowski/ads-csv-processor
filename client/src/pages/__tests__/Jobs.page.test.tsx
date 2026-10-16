import { act, screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  buildJob,
  buildPage,
  completedJob,
  csvFile,
  inSequence,
  OTHER_JOB_ID,
  processingJob,
  renderWithClient,
  SAMPLE_CSV,
  server,
} from '@/test/utils';
import { JobsPage } from '../Jobs.page';

afterEach(() => {
  vi.useRealTimers();
});

describe('JobsPage', () => {
  it('uploads a file, follows it to completion and refreshes the history', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const job = buildJob({ fileName: 'campaign.csv' });
    const history = inSequence(
      () => HttpResponse.json(buildPage([])),
      () => HttpResponse.json(buildPage([job])),
      () => HttpResponse.json(buildPage([completedJob({ fileName: 'campaign.csv' })])),
    );
    server.use(
      http.post('/api/v1/jobs', () => HttpResponse.json(job, { status: 202 })),
      http.get(
        '/api/v1/jobs/:id',
        inSequence(
          () => HttpResponse.json(processingJob({ fileName: 'campaign.csv' })),
          () => HttpResponse.json(completedJob({ fileName: 'campaign.csv' })),
        ).resolver,
      ),
      http.get('/api/v1/jobs', history.resolver),
    );
    const { user } = renderWithClient(<JobsPage username="alice" />, {
      advanceTimers: vi.advanceTimersByTime,
      applyAccept: false,
    });
    expect(await screen.findByText('Nie wysłano jeszcze żadnego pliku.')).toBeInTheDocument();

    await user.upload(
      screen.getByLabelText(/Przeciągnij plik tutaj/),
      csvFile(SAMPLE_CSV, 'campaign.csv'),
    );
    await user.click(screen.getByRole('button', { name: 'Wyślij' }));
    expect(await screen.findByText(/został przyjęty/)).toBeInTheDocument();

    await act(() => vi.advanceTimersByTimeAsync(2000));

    const statusCard = screen.getByRole('region', { name: 'Status zadania' });
    expect(await within(statusCard).findByText('Zakończone')).toBeInTheDocument();
    expect(within(statusCard).getByText('campaign.csv')).toBeInTheDocument();
    const historyCard = screen.getByRole('region', { name: 'Historia przetwarzania' });
    expect(await within(historyCard).findByText('Zakończone')).toBeInTheDocument();
  });

  it('shows the status of a job picked from the history', async () => {
    server.use(
      http.get('/api/v1/jobs', () =>
        HttpResponse.json(buildPage([completedJob({ id: OTHER_JOB_ID, fileName: 'old.csv' })])),
      ),
      http.get('/api/v1/jobs/:id', () =>
        HttpResponse.json(completedJob({ id: OTHER_JOB_ID, fileName: 'old.csv' })),
      ),
    );
    const { user } = renderWithClient(<JobsPage username="alice" />);

    await user.click(await screen.findByRole('button', { name: 'Pokaż status pliku old.csv' }));

    const statusCard = screen.getByRole('region', { name: 'Status zadania' });
    expect(await within(statusCard).findByText('old.csv')).toBeInTheDocument();
    expect(screen.getByText('old.csv', { selector: 'button' }).closest('tr')).toHaveAttribute(
      'aria-current',
      'true',
    );
  });
});
