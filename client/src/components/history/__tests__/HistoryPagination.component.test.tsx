import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { HistoryPagination } from '../HistoryPagination.component';

function renderPagination(page: number, totalPages: number, loading = false) {
  const onPageChange = vi.fn();
  render(
    <HistoryPagination
      page={page}
      totalPages={totalPages}
      loading={loading}
      onPageChange={onPageChange}
    />,
  );
  return {
    onPageChange,
    previous: screen.getByRole('button', { name: 'Poprzednia' }),
    next: screen.getByRole('button', { name: 'Następna' }),
  };
}

describe('HistoryPagination', () => {
  it('disables both directions on the only page and counts an empty history as one page', () => {
    const { previous, next } = renderPagination(0, 0);

    expect(previous).toBeDisabled();
    expect(next).toBeDisabled();
    expect(screen.getByText('Strona 1 z 1')).toBeInTheDocument();
  });

  it('moves to the neighbouring pages', async () => {
    const { previous, next, onPageChange } = renderPagination(1, 3);

    await userEvent.click(previous);
    await userEvent.click(next);

    expect(onPageChange.mock.calls).toEqual([[0], [2]]);
  });

  it('disables moving while the next page is loading', () => {
    const { previous, next } = renderPagination(1, 3, true);

    expect(previous).toBeDisabled();
    expect(next).toBeDisabled();
    expect(screen.getByText(/ładowanie/)).toBeInTheDocument();
  });
});
