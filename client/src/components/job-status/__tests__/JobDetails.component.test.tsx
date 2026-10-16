import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { buildJob, completedJob, processingJob } from '@/test/utils';
import { JobDetails } from '../JobDetails.component';

const NBSP = String.fromCodePoint(0xa0);

function valueOf(term: string): string | null {
  return screen.getByText(term).nextElementSibling?.textContent ?? null;
}

describe('JobDetails', () => {
  it('renders progress, counters and totals of a completed job', () => {
    render(<JobDetails job={completedJob()} />);

    const bar = screen.getByRole('progressbar', { name: 'Postęp przetwarzania' });
    expect(bar).toHaveAttribute('value', '100');
    expect(bar).toHaveAttribute('aria-valuetext', `75,9${NBSP}KiB z 75,9${NBSP}KiB`);
    expect(valueOf('Przetworzone wiersze')).toBe('1000');
    expect(valueOf('Suma wyświetleń')).toBe(`6${NBSP}733${NBSP}389`);
    expect(valueOf('Suma kliknięć')).toBe(`176${NBSP}755`);
    expect(valueOf('CTR całości')).toBe('2,6251%');
    expect(valueOf('Próby')).toBe('1');
  });

  it('shows the partial progress of a running job and dashes for missing dates', () => {
    render(<JobDetails job={processingJob()} />);

    expect(screen.getByRole('progressbar')).toHaveAttribute('value', '42');
    expect(valueOf('Zakończono')).toBe('—');
  });

  it('shows a dash for the CTR when there were no impressions', () => {
    render(<JobDetails job={buildJob()} />);

    expect(valueOf('CTR całości')).toBe('—');
  });

  it('explains why a job failed', () => {
    render(<JobDetails job={buildJob({ status: 'FAILED', failureReason: 'NO_VALID_ROWS' })} />);

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Plik nie zawiera żadnego poprawnego wiersza — sprawdź przykłady poniżej.',
    );
  });

  it('lists samples of rejected rows', () => {
    render(
      <JobDetails
        job={buildJob({
          rowsRejected: 1,
          rejectedSamples: [{ line: 7, reason: 'INVALID_CLICKS' }],
        })}
      />,
    );

    expect(
      within(screen.getByRole('list')).getByText(
        'Linia 7: kolumna clicks — oczekiwano nieujemnej liczby całkowitej (same cyfry, np. 22)',
      ),
    ).toBeInTheDocument();
  });
});
