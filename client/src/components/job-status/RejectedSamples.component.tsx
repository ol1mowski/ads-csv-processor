import type { RejectedSample } from '@/api/types.api';
import { formatCount } from '@/utils/format.util';
import { rejectReasonLabel } from '@/utils/jobStatus.util';

export function RejectedSamples({ samples }: { readonly samples: readonly RejectedSample[] }) {
  if (samples.length === 0) {
    return null;
  }
  return (
    <div className="text-sm">
      <h3 className="mb-1 font-medium">Przykłady odrzuconych wierszy</h3>
      <ul className="list-disc space-y-0.5 pl-5 text-slate-700 dark:text-slate-300">
        {samples.map((sample) => (
          <li key={`${sample.line}-${sample.reason}`}>
            Linia {formatCount(sample.line)}: {rejectReasonLabel(sample.reason)}
          </li>
        ))}
      </ul>
    </div>
  );
}
