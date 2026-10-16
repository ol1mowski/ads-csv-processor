export type HistoryFilter = 'all' | 'finished';

export const queryKeys = {
  session: ['session'] as const,
  job: (id: string | null) => ['job', id] as const,
  jobLists: ['jobs'] as const,
  jobList: (filter: HistoryFilter, page: number) => ['jobs', filter, page] as const,
};
