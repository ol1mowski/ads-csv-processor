import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { HistoryFilterControl } from '../HistoryFilterControl.component';

describe('HistoryFilterControl', () => {
  it('checks the current filter and reports a change', async () => {
    const onChange = vi.fn();
    render(<HistoryFilterControl value="all" onChange={onChange} />);

    expect(screen.getByLabelText('Wszystkie')).toBeChecked();
    await userEvent.click(screen.getByLabelText('Tylko zakończone'));

    expect(onChange).toHaveBeenCalledWith('finished');
  });
});
