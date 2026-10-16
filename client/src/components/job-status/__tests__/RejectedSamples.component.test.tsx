import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { RejectedSamples } from '../RejectedSamples.component';

describe('RejectedSamples', () => {
  it('renders nothing when no row was rejected', () => {
    const { container } = render(<RejectedSamples samples={[]} />);

    expect(container).toBeEmptyDOMElement();
  });

  it('lists every sample with its line number, the column and the expected format', () => {
    render(
      <RejectedSamples
        samples={[
          { line: 2, reason: 'FIELD_COUNT' },
          { line: 3, reason: 'INVALID_REVENUE' },
          { line: 4, reason: 'CLICKS_EXCEED_IMPRESSIONS' },
          { line: 12_345, reason: 'LINE_TOO_LONG' },
        ]}
      />,
    );

    expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Linia 2: zła liczba kolumn — oczekiwano 8 wartości rozdzielonych średnikiem',
      'Linia 3: kolumna revenue — oczekiwano liczby z kropką dziesiętną, np. 2.9798 (nie 2,9798)',
      'Linia 4: więcej kliknięć (clicks) niż wyświetleń (impressions) — CTR nie może przekroczyć 100%',
      `Linia 12${String.fromCodePoint(0xa0)}345: wiersz dłuższy niż 1024 bajty`,
    ]);
  });

  it('shows the raw code of a reason this client does not know yet', () => {
    const samples = [{ line: 5, reason: 'SOMETHING_NEW' }] as unknown as Parameters<
      typeof RejectedSamples
    >[0]['samples'];

    render(<RejectedSamples samples={samples} />);

    expect(screen.getByRole('listitem')).toHaveTextContent('Linia 5: SOMETHING_NEW');
  });
});
