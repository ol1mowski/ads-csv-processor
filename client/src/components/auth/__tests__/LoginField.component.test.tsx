import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { LoginField } from '../LoginField.component';

function ControlledField() {
  const [value, setValue] = useState('');
  return (
    <LoginField
      label="Login"
      type="text"
      autoComplete="username"
      maxLength={5}
      value={value}
      onChange={setValue}
    />
  );
}

describe('LoginField', () => {
  it('is a required, labelled input that never accepts more than the limit', async () => {
    render(<ControlledField />);
    const input = screen.getByLabelText('Login');

    await userEvent.type(input, 'alice-and-more');

    expect(input).toBeRequired();
    expect(input).toHaveAttribute('autocomplete', 'username');
    expect(input).toHaveValue('alice');
  });
});
