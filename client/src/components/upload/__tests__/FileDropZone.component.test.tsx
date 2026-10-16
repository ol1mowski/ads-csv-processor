import { fireEvent, render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { csvFile, SAMPLE_CSV } from '@/test/utils';
import { FileDropZone } from '../FileDropZone.component';

const LABEL = /Przeciągnij plik tutaj/;

describe('FileDropZone', () => {
  it('reports a file picked with the file dialog and clears the input', async () => {
    const onFileChosen = vi.fn();
    const user = userEvent.setup({ applyAccept: false });
    render(<FileDropZone disabled={false} onFileChosen={onFileChosen} />);
    const file = csvFile(SAMPLE_CSV, 'picked.csv');

    await user.upload(screen.getByLabelText(LABEL), file);

    expect(onFileChosen).toHaveBeenCalledWith(file);
    expect(screen.getByLabelText(LABEL)).toHaveProperty('files.length', 0);
  });

  it('reports a dropped file', () => {
    const onFileChosen = vi.fn();
    render(<FileDropZone disabled={false} onFileChosen={onFileChosen} />);
    const file = csvFile(SAMPLE_CSV, 'dropped.csv');

    fireEvent.drop(screen.getByText(LABEL), { dataTransfer: { files: [file] } });

    expect(onFileChosen).toHaveBeenCalledWith(file);
  });

  it('ignores drops and disables the input while disabled', () => {
    const onFileChosen = vi.fn();
    render(<FileDropZone disabled onFileChosen={onFileChosen} />);

    fireEvent.drop(screen.getByText(LABEL), { dataTransfer: { files: [csvFile(SAMPLE_CSV)] } });

    expect(onFileChosen).not.toHaveBeenCalled();
    expect(screen.getByLabelText(LABEL)).toBeDisabled();
  });

  it('describes the expected format', () => {
    render(<FileDropZone disabled={false} onFileChosen={vi.fn()} />);

    expect(screen.getByLabelText(LABEL)).toHaveAccessibleDescription(/Plik CSV do 5 GiB/);
  });
});
