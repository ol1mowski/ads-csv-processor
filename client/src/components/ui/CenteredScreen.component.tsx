import type { ReactNode } from 'react';

export function CenteredScreen({ children }: { readonly children: ReactNode }) {
  return (
    <div className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm space-y-4 text-center">{children}</div>
    </div>
  );
}
