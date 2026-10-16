import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { UploadProgress } from '../UploadProgress.component';

const NBSP = String.fromCodePoint(0xa0);

describe('UploadProgress', () => {
  it('shows how much has been sent so far', () => {
    render(<UploadProgress sentBytes={512} totalBytes={2048} />);

    const bar = screen.getByRole('progressbar', { name: 'Postęp wysyłania' });
    expect(bar).toHaveAttribute('value', '25');
    expect(bar).toHaveAttribute('aria-valuetext', `512${NBSP}B z 2${NBSP}KiB`);
    expect(screen.getByText('Wysłano 512 B z 2 KiB')).toBeInTheDocument();
  });

  it('says the server is storing the file once every byte is out', () => {
    render(<UploadProgress sentBytes={4096} totalBytes={2048} />);

    expect(screen.getByRole('progressbar')).toHaveAttribute('value', '100');
    expect(screen.getByText('Serwer zapisuje plik…')).toBeInTheDocument();
  });
});
