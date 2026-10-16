import { screen } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import {
  completedJob,
  inSequence,
  JOB_ID,
  OTHER_JOB_ID,
  problem,
  processingJob,
  renderWithClient,
  server,
} from '@/test/utils';
import {
  JobStatusCard,
  NO_JOB_SELECTED_MESSAGE,
  RETRYING_MESSAGE,
} from '../JobStatusCard.component';

const jobPath = '/api/v1/jobs/:id';

describe('JobStatusCard', () => {
  it('invites the user to pick a job while none is selected', () => {
    const { calls, resolver } = inSequence(() => HttpResponse.json(completedJob()));
    server.use(http.get(jobPath, resolver));
    renderWithClient(<JobStatusCard jobId={null} />);

    expect(screen.getByText(NO_JOB_SELECTED_MESSAGE)).toBeInTheDocument();
    expect(calls.count).toBe(0);
  });

  it('shows a loading state until the first answer arrives', async () => {
    server.use(
      http.get(jobPath, async () => {
        await delay('infinite');
        return HttpResponse.json(completedJob());
      }),
    );
    renderWithClient(<JobStatusCard jobId={JOB_ID} />);

    expect(await screen.findByText('Ładowanie statusu…')).toBeInTheDocument();
  });

  it('shows the job with its status badge and announces the status', async () => {
    server.use(http.get(jobPath, () => HttpResponse.json(completedJob())));
    renderWithClient(<JobStatusCard jobId={JOB_ID} />);

    expect(await screen.findByText('sample_ads_data.csv')).toBeInTheDocument();
    expect(screen.getByText('Zakończone')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Status zadania sample_ads_data.csv: Zakończone',
    );
    expect(screen.getByRole('link', { name: 'Pobierz wynik CSV' })).toBeInTheDocument();
  });

  it('switches to another job when the selection changes', async () => {
    server.use(
      http.get(jobPath, ({ params }) =>
        HttpResponse.json(
          params.id === JOB_ID
            ? completedJob()
            : processingJob({ id: OTHER_JOB_ID, fileName: 'other.csv' }),
        ),
      ),
    );
    const { rerender } = renderWithClient(<JobStatusCard jobId={JOB_ID} />);
    await screen.findByText('sample_ads_data.csv');

    rerender(<JobStatusCard jobId={OTHER_JOB_ID} />);

    expect(await screen.findByText('other.csv')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Status zadania other.csv: Przetwarzanie');
  });

  it('reports a job that does not exist', async () => {
    server.use(http.get(jobPath, () => problem(404, 'JOB_NOT_FOUND')));
    renderWithClient(<JobStatusCard jobId={JOB_ID} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Nie znaleziono zadania.');
    expect(screen.queryByText(RETRYING_MESSAGE)).not.toBeInTheDocument();
  });

  it('says it keeps retrying when the server fails', async () => {
    server.use(http.get(jobPath, () => problem(503, 'INTERNAL_ERROR')));
    renderWithClient(<JobStatusCard jobId={JOB_ID} />);

    expect(await screen.findByText(RETRYING_MESSAGE)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('says it keeps retrying when the network is down', async () => {
    server.use(http.get(jobPath, () => HttpResponse.error()));
    renderWithClient(<JobStatusCard jobId={JOB_ID} />);

    expect(await screen.findByText(RETRYING_MESSAGE)).toBeInTheDocument();
  });
});
