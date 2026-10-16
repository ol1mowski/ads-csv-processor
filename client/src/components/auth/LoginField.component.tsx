import { useId } from 'react';

const INPUT_CLASSES =
  'mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500 dark:border-slate-700 dark:bg-slate-900';

interface LoginFieldProps {
  readonly label: string;
  readonly type: 'text' | 'password';
  readonly autoComplete: 'username' | 'current-password';
  readonly maxLength: number;
  readonly value: string;
  readonly onChange: (value: string) => void;
}

export function LoginField({
  label,
  type,
  autoComplete,
  maxLength,
  value,
  onChange,
}: LoginFieldProps) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        type={type}
        required
        maxLength={maxLength}
        autoComplete={autoComplete}
        autoCapitalize="none"
        spellCheck={false}
        value={value}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        className={INPUT_CLASSES}
      />
    </div>
  );
}
