import { fireEvent, screen } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import sampleCsv from '@/test/fixtures/sample_ads_data.csv?raw';
import {
  buildJob,
  csvFile,
  problem,
  renderWithClient,
  SAMPLE_CSV,
  server,
  setXsrfCookie,
  TEST_XSRF_TOKEN,
  xsrfHeaderOf,
} from '@/test/utils';
import { UPLOAD_ABORTED_MESSAGE } from '@/utils/uploadResult.util';
import { UploadPanel } from '../UploadPanel.component';

interface RecordedUpload {
  readonly body: string;
  readonly fileName: string | null;
  readonly contentType: string | null;
  readonly xsrfHeader: string | null;
}

function recordUploads(respond: () => Response | Promise<Response>) {
  const uploads: RecordedUpload[] = [];
  server.use(
    http.post('/api/v1/jobs', async ({ request }) => {
      uploads.push({
        body: await request.text(),
        fileName: new URL(request.url).searchParams.get('fileName'),
        contentType: request.headers.get('Content-Type'),
        xsrfHeader: xsrfHeaderOf(request),
      });
      return respond();
    }),
  );
  return uploads;
}

function renderPanel() {
  const onJobAccepted = vi.fn();
  const view = renderWithClient(<UploadPanel onJobAccepted={onJobAccepted} />, {
    applyAccept: false,
  });
  return { ...view, onJobAccepted, fileInput: screen.getByLabelText(/Przeciągnij plik tutaj/) };
}

describe('UploadPanel', () => {
  it('keeps the send button disabled until a file is chosen', () => {
    renderPanel();

    expect(screen.getByRole('button', { name: 'Wyślij' })).toBeDisabled();
  });

  it('shows the chosen file with its size', async () => {
    const { user, fileInput } = renderPanel();

    await user.upload(fileInput, csvFile(SAMPLE_CSV, 'campaign.csv'));

    expect(screen.getByText('campaign.csv')).toBeInTheDocument();
    expect(screen.getByText(/Wybrany plik:/)).toHaveTextContent(/\d+\sB\)/);
    expect(screen.getByRole('button', { name: 'Wyślij' })).toBeEnabled();
  });

  it('uploads the sample file as raw text/csv with its name and the XSRF token', async () => {
    setXsrfCookie();
    const job = buildJob();
    const uploads = recordUploads(() => HttpResponse.json(job, { status: 202 }));
    const { user, onJobAccepted, fileInput } = renderPanel();

    await user.upload(fileInput, csvFile(sampleCsv, 'sample_ads_data.csv'));
    await user.click(screen.getByRole('button', { name: 'Wyślij' }));

    expect(
      await screen.findByText(
        'Plik „sample_ads_data.csv” został przyjęty — przetwarzanie trwa w tle.',
      ),
    ).toBeInTheDocument();
    expect(onJobAccepted).toHaveBeenCalledWith(job);
    expect(uploads).toEqual([
      {
        body: sampleCsv,
        fileName: 'sample_ads_data.csv',
        contentType: 'text/csv',
        xsrfHeader: TEST_XSRF_TOKEN,
      },
    ]);
    expect(screen.getByRole('button', { name: 'Wyślij' })).toBeDisabled();
  });

  it('rejects a file without the .csv extension before sending anything', async () => {
    const uploads = recordUploads(() => HttpResponse.json(buildJob(), { status: 202 }));
    const { user, fileInput } = renderPanel();

    await user.upload(fileInput, csvFile(SAMPLE_CSV, 'notes.txt', 'text/plain'));
    await user.click(screen.getByRole('button', { name: 'Wyślij' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Plik nie spełnia wymagań:');
    expect(screen.getByText('Plik musi mieć rozszerzenie .csv')).toBeInTheDocument();
    expect(uploads).toEqual([]);
  });

  it('lets the same file be picked again after it was rejected', async () => {
    const uploads = recordUploads(() => HttpResponse.json(buildJob(), { status: 202 }));
    const { user, fileInput } = renderPanel();

    await user.upload(fileInput, csvFile('a;b;c\n'));
    expect(fileInput).toHaveProperty('files.length', 0);
    await user.click(screen.getByRole('button', { name: 'Wyślij' }));
    expect(await screen.findByText(/oczekiwanym nagłówkiem/)).toBeInTheDocument();

    await user.upload(fileInput, csvFile(SAMPLE_CSV));
    await user.click(screen.getByRole('button', { name: 'Wyślij' }));

    expect(await screen.findByText(/został przyjęty/)).toBeInTheDocument();
    expect(uploads.map((upload) => upload.body)).toEqual([SAMPLE_CSV]);
  });

  it.each([
    [507, 'INSUFFICIENT_STORAGE', 'Na serwerze zabrakło miejsca na dysku — spróbuj później.'],
    [413, 'FILE_TOO_LARGE', 'Plik przekracza limit 5 GiB.'],
    [
      422,
      'INVALID_CSV_HEADER',
      'Serwer odrzucił plik: pierwsza linia nie jest oczekiwanym nagłówkiem CSV.',
    ],
  ])('explains a %d %s from the server', async (status, code, message) => {
    recordUploads(() => problem(status, code));
    const { user, fileInput } = renderPanel();

    await user.upload(fileInput, csvFile(SAMPLE_CSV));
    await user.click(screen.getByRole('button', { name: 'Wyślij' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('button', { name: 'Wyślij' })).toBeEnabled();
  });

  it('lets the user abort an upload in progress', async () => {
    recordUploads(async () => {
      await delay('infinite');
      return HttpResponse.json(buildJob(), { status: 202 });
    });
    const { user, fileInput } = renderPanel();
    await user.upload(fileInput, csvFile(SAMPLE_CSV));
    await user.click(screen.getByRole('button', { name: 'Wyślij' }));

    await user.click(await screen.findByRole('button', { name: 'Przerwij wysyłanie' }));

    expect(await screen.findByText(UPLOAD_ABORTED_MESSAGE)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Przerwij wysyłanie' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Wyślij' })).toBeEnabled();
  });

  it('accepts a file dropped onto the drop zone', () => {
    renderPanel();

    fireEvent.drop(screen.getByText(/Przeciągnij plik tutaj/), {
      dataTransfer: { files: [csvFile(SAMPLE_CSV, 'dropped.csv')] },
    });

    expect(screen.getByText('dropped.csv')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Wyślij' })).toBeEnabled();
  });
});
