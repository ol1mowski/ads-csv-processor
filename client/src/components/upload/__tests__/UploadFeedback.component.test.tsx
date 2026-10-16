import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { UploadFeedback } from '../UploadFeedback.component';

describe('UploadFeedback', () => {
  it('renders nothing before an upload starts', () => {
    const { container } = render(<UploadFeedback state={{ phase: 'idle' }} />);

    expect(container).toBeEmptyDOMElement();
  });

  it('says the file is being checked', () => {
    render(<UploadFeedback state={{ phase: 'validating' }} />);

    expect(screen.getByText('Sprawdzanie pliku…')).toBeInTheDocument();
  });

  it('shows the transfer progress', () => {
    render(<UploadFeedback state={{ phase: 'uploading', sentBytes: 1, totalBytes: 4 }} />);

    expect(screen.getByRole('progressbar')).toHaveAttribute('value', '25');
  });

  it('lists every validation issue in an error', () => {
    render(
      <UploadFeedback
        state={{
          phase: 'done',
          result: { tone: 'error', message: 'Plik nie spełnia wymagań:', issues: ['a', 'b'] },
        }}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Plik nie spełnia wymagań:');
    expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual(['a', 'b']);
  });

  it('announces a success politely', () => {
    render(
      <UploadFeedback state={{ phase: 'done', result: { tone: 'success', message: 'OK' } }} />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('OK');
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });
});
