import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Job } from '@/api/types.api';
import { buildJob, completedJob, JOB_ID, processingJob } from '@/test/utils';
import { HistoryRow } from '../HistoryRow.component';

function renderRow(job: Job, selected = false) {
  const onSelect = vi.fn();
  render(
    <table>
      <tbody>
        <HistoryRow job={job} selected={selected} onSelect={onSelect} />
      </tbody>
    </table>,
  );
  return { onSelect, row: screen.getByRole('row') };
}

describe('HistoryRow', () => {
  it('shows the status, counters and CTR of a completed job with a download link', () => {
    const { row } = renderRow(completedJob({ fileName: 'done.csv', rowsRejected: 3 }));

    expect(row).toHaveTextContent('Zakończone');
    expect(row).toHaveTextContent('2,6251%');
    expect(row).toHaveTextContent('1000');
    expect(row).toHaveTextContent('3');
    expect(screen.getByRole('link', { name: 'Pobierz wynik done.csv' })).toHaveAttribute(
      'href',
      `/api/v1/jobs/${JOB_ID}/result`,
    );
  });

  it('shows the progress of a running job and no download', () => {
    const { row } = renderRow(processingJob());

    expect(row).toHaveTextContent('Przetwarzanie');
    expect(row).toHaveTextContent('42%');
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('marks the selected row with a visible label, not only a colour', () => {
    const { row } = renderRow(buildJob(), true);

    expect(row).toHaveAttribute('aria-current', 'true');
    expect(screen.getByText('Wybrane')).toBeInTheDocument();
  });

  it('reports a click on the file name', async () => {
    const { onSelect } = renderRow(buildJob({ fileName: 'queued.csv' }));

    await userEvent.click(screen.getByRole('button', { name: 'Pokaż status pliku queued.csv' }));

    expect(onSelect).toHaveBeenCalledWith(JOB_ID);
  });
});
