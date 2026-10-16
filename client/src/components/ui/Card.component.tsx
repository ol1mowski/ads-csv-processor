import { useId, type ReactNode } from 'react';

interface CardProps {
  readonly title: string;
  readonly actions?: ReactNode;
  readonly children: ReactNode;
}

export function Card({ title, actions, children }: CardProps) {
  const headingId = useId();
  return (
    <section
      aria-labelledby={headingId}
      className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6 dark:border-slate-800 dark:bg-slate-900"
    >
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h2 id={headingId} className="text-lg font-semibold tracking-tight">
          {title}
        </h2>
        {actions}
      </header>
      {children}
    </section>
  );
}
