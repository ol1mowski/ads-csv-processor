import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { JobStatus } from '@/api/types.api';
import { StatusBadge } from '../StatusBadge.component';

describe('StatusBadge', () => {
  it.each<[JobStatus, string]>([
    ['QUEUED', 'W kolejce'],
    ['PROCESSING', 'Przetwarzanie'],
    ['COMPLETED', 'Zakończone'],
    ['FAILED', 'Błąd'],
    ['CANCELLED', 'Anulowane'],
  ])('labels %s as %s', (status, label) => {
    render(<StatusBadge status={status} />);

    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
