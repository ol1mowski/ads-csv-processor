import { useState, type SubmitEvent } from 'react';
import type { Job } from '@/api/types.api';
import { Button } from '@/components/ui/Button.component';
import { Card } from '@/components/ui/Card.component';
import { useUpload } from '@/hooks/useUpload.hook';
import { formatBytes } from '@/utils/format.util';
import { FileDropZone } from './FileDropZone.component';
import { UploadFeedback } from './UploadFeedback.component';

export function UploadPanel({ onJobAccepted }: { readonly onJobAccepted: (job: Job) => void }) {
  const [file, setFile] = useState<File | null>(null);
  const upload = useUpload((job) => {
    setFile(null);
    onJobAccepted(job);
  });

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (file !== null) {
      void upload.start(file);
    }
  }

  return (
    <Card title="Wyślij plik CSV">
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <FileDropZone disabled={upload.busy} onFileChosen={setFile} />
        {file !== null && (
          <p className="text-sm break-all">
            Wybrany plik: <span className="font-medium">{file.name}</span> ({formatBytes(file.size)}
            )
          </p>
        )}
        <div className="flex flex-wrap gap-3">
          <Button type="submit" disabled={file === null || upload.busy}>
            Wyślij
          </Button>
          {upload.canAbort && (
            <Button variant="secondary" onClick={upload.abort}>
              Przerwij wysyłanie
            </Button>
          )}
        </div>
        <UploadFeedback state={upload.state} />
      </form>
    </Card>
  );
}
