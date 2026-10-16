import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Card } from '../Card.component';

describe('Card', () => {
  it('is a region named by its heading with the actions next to it', () => {
    render(
      <Card title="Status zadania" actions={<button type="button">Akcja</button>}>
        Treść
      </Card>,
    );

    const region = screen.getByRole('region', { name: 'Status zadania' });
    expect(region).toHaveTextContent('Treść');
    expect(screen.getByRole('button', { name: 'Akcja' })).toBeInTheDocument();
  });
});
