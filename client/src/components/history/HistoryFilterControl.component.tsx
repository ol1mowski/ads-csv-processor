import { useId } from 'react';
import type { HistoryFilter } from '@/utils/queryKeys.util';

export const HISTORY_FILTERS: readonly { readonly value: HistoryFilter; readonly label: string }[] =
  [
    { value: 'all', label: 'Wszystkie' },
    { value: 'finished', label: 'Tylko zakończone' },
  ];

interface HistoryFilterControlProps {
  readonly value: HistoryFilter;
  readonly onChange: (filter: HistoryFilter) => void;
}

export function HistoryFilterControl({ value, onChange }: HistoryFilterControlProps) {
  const name = useId();
  return (
    <fieldset className="flex rounded-lg bg-slate-100 p-1 text-sm dark:bg-slate-800">
      <legend className="sr-only">Filtr historii</legend>
      {HISTORY_FILTERS.map((filter) => (
        <label
          key={filter.value}
          className="cursor-pointer rounded-md px-3 py-1 font-medium text-slate-600 has-checked:bg-white has-checked:text-slate-900 has-checked:shadow-sm has-focus-visible:outline-2 has-focus-visible:outline-indigo-500 dark:text-slate-300 dark:has-checked:bg-slate-700 dark:has-checked:text-white"
        >
          <input
            type="radio"
            name={name}
            value={filter.value}
            checked={value === filter.value}
            onChange={() => {
              onChange(filter.value);
            }}
            className="sr-only"
          />
          {filter.label}
        </label>
      ))}
    </fieldset>
  );
}
