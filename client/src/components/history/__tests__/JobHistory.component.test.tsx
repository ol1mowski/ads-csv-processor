import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import type { JobPage } from '@/api/types.api';
import {
  buildPage,
  completedJob,
  OTHER_JOB_ID,
  problem,
  processingJob,
  renderWithClient,
  server,
} from '@/test/utils';
import { JobHistory } from '../JobHistory.component';

function serveHistory(...pages: JobPage[]) {
  const requests: URLSearchParams[] = [];
  server.use(
    http.get('/api/v1/jobs', ({ request }) => {
      requests.push(new URL(request.url).searchParams);
      return HttpResponse.json(pages[Math.min(requests.length - 1, pages.length - 1)]);
    }),
  );
  return requests;
}

function renderHistory(selectedJobId: string | null = null) {
  const onSelect = vi.fn();
  return {
    ...renderWithClient(<JobHistory selectedJobId={selectedJobId} onSelect={onSelect} />),
    onSelect,
  };
}

describe('JobHistory', () => {
  it('shows a loading state first and then the jobs', async () => {
    serveHistory(
      buildPage([
        completedJob({ fileName: 'done.csv' }),
        processingJob({ id: OTHER_JOB_ID, fileName: 'running.csv' }),
      ]),
    );
    renderHistory();

    expect(screen.getByText('Ładowanie historii…')).toBeInTheDocument();
    expect(await screen.findByText('done.csv')).toBeInTheDocument();
    expect(screen.getByText('running.csv')).toBeInTheDocument();
    expect(screen.queryByText('Ładowanie historii…')).not.toBeInTheDocument();
  });

  it('reports the picked job', async () => {
    serveHistory(buildPage([completedJob({ fileName: 'done.csv' })]));
    const { user, onSelect } = renderHistory();

    await user.click(await screen.findByRole('button', { name: 'Pokaż status pliku done.csv' }));

    expect(onSelect).toHaveBeenCalledWith(completedJob().id);
  });

  it('asks only for finished jobs under the finished filter and starts again at page 1', async () => {
    const requests = serveHistory(
      buildPage([completedJob()], { totalPages: 2 }),
      buildPage([completedJob()], { page: 1, totalPages: 2 }),
      buildPage([completedJob()]),
    );
    const { user } = renderHistory();
    await user.click(await screen.findByRole('button', { name: 'Następna' }));
    await screen.findByText('Strona 2 z 2');

    await user.click(screen.getByLabelText('Tylko zakończone'));
    await screen.findByText('Strona 1 z 1');

    expect(requests.map((params) => [params.get('page'), params.get('status')])).toEqual([
      ['0', null],
      ['1', null],
      ['0', 'COMPLETED,FAILED,CANCELLED'],
    ]);
  });

  it('moves between pages', async () => {
    const requests = serveHistory(
      buildPage([completedJob({ fileName: 'first.csv' })], { totalPages: 2 }),
      buildPage([completedJob({ fileName: 'second.csv' })], { page: 1, totalPages: 2 }),
    );
    const { user } = renderHistory();
    await screen.findByText('first.csv');

    await user.click(screen.getByRole('button', { name: 'Następna' }));

    expect(await screen.findByText('second.csv')).toBeInTheDocument();
    expect(screen.getByText('Strona 2 z 2')).toBeInTheDocument();
    expect(requests.map((params) => params.get('page'))).toEqual(['0', '1']);
  });

  it('shows an empty state per filter', async () => {
    serveHistory(buildPage([]));
    const { user } = renderHistory();

    expect(await screen.findByText('Nie wysłano jeszcze żadnego pliku.')).toBeInTheDocument();
    await user.click(screen.getByLabelText('Tylko zakończone'));

    expect(await screen.findByText('Brak zakończonych zadań.')).toBeInTheDocument();
  });

  it('reports a history that cannot be loaded', async () => {
    server.use(http.get('/api/v1/jobs', () => problem(500, 'INTERNAL_ERROR')));
    renderHistory();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Nie udało się pobrać historii przetwarzania.',
    );
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });
});
