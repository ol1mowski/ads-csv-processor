export type ButtonVariant = 'primary' | 'secondary' | 'danger';

const BASE =
  'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500 disabled:cursor-not-allowed disabled:opacity-50';

const VARIANTS: Readonly<Record<ButtonVariant, string>> = {
  primary:
    'bg-indigo-600 text-white shadow-sm hover:bg-indigo-500 disabled:hover:bg-indigo-600 dark:bg-indigo-500 dark:hover:bg-indigo-400',
  secondary:
    'bg-white text-slate-800 shadow-sm ring-1 ring-slate-300 ring-inset hover:bg-slate-50 dark:bg-slate-800 dark:text-slate-100 dark:ring-slate-600 dark:hover:bg-slate-700',
  danger:
    'bg-red-600 text-white shadow-sm hover:bg-red-500 disabled:hover:bg-red-600 dark:bg-red-500 dark:hover:bg-red-400',
};

export function buttonClass(variant: ButtonVariant = 'primary'): string {
  return `${BASE} ${VARIANTS[variant]}`;
}
