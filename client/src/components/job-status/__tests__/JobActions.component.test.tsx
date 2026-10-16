import { screen, waitFor } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { JobStatus } from '@/api/types.api';
import {
  buildJob,
  completedJob,
  JOB_ID,
  problem,
  processingJob,
  renderWithClient,
  server,
} from '@/test/utils';
import { queryKeys } from '@/utils/queryKeys.util';
import { JobActions } from '../JobActions.component';

const cancelPath = '/api/v1/jobs/:id/cancel';

async function cancelWith(response: () => Response | Promise<Response>) {
  server.use(http.post(cancelPath, response));
  const view = renderWithClient(<JobActions job={processingJob()} />);
  await view.user.click(screen.getByRole('button', { name: 'Anuluj przetwarzanie' }));
  return view;
}

describe('JobActions', () => {
  it.each<[JobStatus, boolean]>([
    ['QUEUED', true],
    ['PROCESSING', true],
    ['COMPLETED', false],
    ['FAILED', false],
    ['CANCELLED', false],
  ])('for %s offers cancelling: %s', (status, cancellable) => {
    renderWithClient(<JobActions job={buildJob({ status })} />);

    expect(screen.queryByRole('button', { name: 'Anuluj przetwarzanie' }) !== null).toBe(
      cancellable,
    );
  });

  it('offers a same-origin download of the result only when there is one', () => {
    const { rerender } = renderWithClient(<JobActions job={completedJob()} />);

    const link = screen.getByRole('link', { name: 'Pobierz wynik CSV' });
    expect(link).toHaveAttribute('href', `/api/v1/jobs/${JOB_ID}/result`);
    expect(link).toHaveAttribute('download');

    rerender(<JobActions job={processingJob()} />);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('cancels once, shows the pending state and stores the cancelled job', async () => {
    let requests = 0;
    const { queryClient } = await cancelWith(async () => {
      requests += 1;
      await delay(20);
      return HttpResponse.json(buildJob({ status: 'CANCELLED' }));
    });

    expect(screen.getByRole('button', { name: 'Anulowanie…' })).toBeDisabled();
    await waitFor(() => {
      expect(queryClient.getQueryData(queryKeys.job(JOB_ID))).toMatchObject({
        status: 'CANCELLED',
      });
    });
    expect(requests).toBe(1);
  });

  it.each([
    [
      () => problem(409, 'JOB_NOT_CANCELLABLE'),
      'Zadanie już się zakończyło — nie można go anulować.',
    ],
    [() => problem(403, 'CSRF_INVALID'), 'Sesja wygasła — odśwież stronę'],
    [() => problem(500, 'INTERNAL_ERROR'), 'Nie udało się anulować zadania. Spróbuj ponownie.'],
    [() => HttpResponse.error(), 'Brak połączenia z serwerem — spróbuj ponownie.'],
  ])('explains a failed cancel (%#)', async (response, message) => {
    await cancelWith(response);

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
  });
});
