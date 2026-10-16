import { Alert } from '@/components/ui/Alert.component';
import type { UploadState } from '@/hooks/useUpload.hook';
import { UploadProgress } from './UploadProgress.component';

export function UploadFeedback({ state }: { readonly state: UploadState }) {
  switch (state.phase) {
    case 'idle':
      return null;
    case 'validating':
      return <p className="text-sm text-slate-600 dark:text-slate-400">Sprawdzanie pliku…</p>;
    case 'uploading':
      return <UploadProgress sentBytes={state.sentBytes} totalBytes={state.totalBytes} />;
    case 'done': {
      const { tone, message, issues } = state.result;
      return (
        <Alert tone={tone}>
          <p className={issues === undefined ? undefined : 'font-medium'}>{message}</p>
          {issues !== undefined && (
            <ul className="mt-1 list-disc pl-5">
              {issues.map((issue) => (
                <li key={issue}>{issue}</li>
              ))}
            </ul>
          )}
        </Alert>
      );
    }
  }
}
