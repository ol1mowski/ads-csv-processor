import { Button } from '@/components/ui/Button.component';

interface HistoryPaginationProps {
  readonly page: number;
  readonly totalPages: number;
  readonly loading: boolean;
  readonly onPageChange: (page: number) => void;
}

export function HistoryPagination({
  page,
  totalPages,
  loading,
  onPageChange,
}: HistoryPaginationProps) {
  const lastPage = Math.max(totalPages, 1);
  return (
    <nav
      aria-label="Strony historii"
      className="flex flex-wrap items-center justify-between gap-3 text-sm"
    >
      <Button
        variant="secondary"
        disabled={page === 0 || loading}
        onClick={() => {
          onPageChange(page - 1);
        }}
      >
        Poprzednia
      </Button>
      <span aria-live="polite" className="text-slate-600 dark:text-slate-400">
        Strona {page + 1} z {lastPage}
        {loading && ' (ładowanie…)'}
      </span>
      <Button
        variant="secondary"
        disabled={page + 1 >= lastPage || loading}
        onClick={() => {
          onPageChange(page + 1);
        }}
      >
        Następna
      </Button>
    </nav>
  );
}
