import { formatPercent } from '@/utils/format.util';

interface ProgressBarProps {
  readonly percent: number;
  readonly label: string;
  readonly valueText: string;
}

export function ProgressBar({ percent, label, valueText }: ProgressBarProps) {
  return (
    <div className="flex items-center gap-3">
      <progress
        max={100}
        value={percent}
        aria-label={label}
        aria-valuetext={valueText}
        className="h-2.5 w-full appearance-none overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800 [&::-moz-progress-bar]:bg-indigo-600 [&::-webkit-progress-bar]:bg-slate-200 dark:[&::-webkit-progress-bar]:bg-slate-800 [&::-webkit-progress-value]:bg-indigo-600 [&::-webkit-progress-value]:transition-all"
      />
      <span aria-hidden="true" className="w-12 shrink-0 text-right text-sm tabular-nums">
        {formatPercent(percent)}
      </span>
    </div>
  );
}
