import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button } from '../Button.component';
import { buttonClass } from '../buttonClass.util';

describe('Button', () => {
  it('is a plain button by default so it never submits a form by accident', async () => {
    const onSubmit = vi.fn();
    render(
      <form onSubmit={onSubmit}>
        <Button>Kliknij</Button>
      </form>,
    );

    await userEvent.click(screen.getByRole('button'));

    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('passes clicks and the disabled state through', async () => {
    const onClick = vi.fn();
    const { rerender } = render(<Button onClick={onClick}>Kliknij</Button>);

    await userEvent.click(screen.getByRole('button'));
    rerender(
      <Button onClick={onClick} disabled>
        Kliknij
      </Button>,
    );
    await userEvent.click(screen.getByRole('button'));

    expect(onClick).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('styles each variant differently', () => {
    expect(
      new Set([buttonClass('primary'), buttonClass('secondary'), buttonClass('danger')]).size,
    ).toBe(3);
    expect(buttonClass()).toBe(buttonClass('primary'));
  });
});
