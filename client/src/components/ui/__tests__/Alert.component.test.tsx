import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Alert, type AlertTone } from '../Alert.component';

describe('Alert', () => {
  it.each<[AlertTone, 'alert' | 'status']>([
    ['error', 'alert'],
    ['warning', 'status'],
    ['success', 'status'],
  ])('renders a %s as role %s', (tone, role) => {
    render(<Alert tone={tone}>Komunikat</Alert>);

    expect(screen.getByRole(role)).toHaveTextContent('Komunikat');
  });
});
