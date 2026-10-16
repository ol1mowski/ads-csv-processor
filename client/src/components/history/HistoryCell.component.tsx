import type { ReactNode } from 'react';

interface HistoryCellProps {
  readonly label: string;
  readonly children: ReactNode;
}

export function HistoryCell({ label, children }: HistoryCellProps) {
  return (
    <td className="flex items-center justify-between gap-4 py-1 lg:table-cell lg:px-3 lg:py-2.5">
      <span className="text-xs font-medium text-slate-500 lg:hidden dark:text-slate-400">
        {label}
      </span>
      <span className="min-w-0 text-right lg:text-left">{children}</span>
    </td>
  );
}
