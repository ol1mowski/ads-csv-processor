import { ProgressBar } from '@/components/ui/ProgressBar.component';
import { formatBytes } from '@/utils/format.util';

interface UploadProgressProps {
  readonly sentBytes: number;
  readonly totalBytes: number;
}

export function UploadProgress({ sentBytes, totalBytes }: UploadProgressProps) {
  const sent = Math.min(sentBytes, totalBytes);
  const percent = totalBytes === 0 ? 100 : Math.floor((sent / totalBytes) * 100);
  const valueText = `${formatBytes(sent)} z ${formatBytes(totalBytes)}`;
  return (
    <div className="space-y-2">
      <ProgressBar percent={percent} label="Postęp wysyłania" valueText={valueText} />
      <p className="text-sm text-slate-600 dark:text-slate-400">
        {sent === totalBytes ? 'Serwer zapisuje plik…' : `Wysłano ${valueText}`}
      </p>
    </div>
  );
}
