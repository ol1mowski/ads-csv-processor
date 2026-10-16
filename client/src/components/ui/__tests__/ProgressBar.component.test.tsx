import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ProgressBar } from '../ProgressBar.component';

describe('ProgressBar', () => {
  it('exposes the value, a label and a readable value text', () => {
    render(<ProgressBar percent={42} label="Postęp" valueText="42 z 100" />);

    const bar = screen.getByRole('progressbar', { name: 'Postęp' });
    expect(bar).toHaveAttribute('value', '42');
    expect(bar).toHaveAttribute('aria-valuetext', '42 z 100');
    expect(screen.getByText('42%')).toBeInTheDocument();
  });
});
