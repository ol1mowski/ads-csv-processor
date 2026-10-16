import { useState } from 'react';
import { UserMenu } from '@/components/auth/UserMenu.component';
import { JobHistory } from '@/components/history/JobHistory.component';
import { JobStatusCard } from '@/components/job-status/JobStatusCard.component';
import { UploadPanel } from '@/components/upload/UploadPanel.component';

export function JobsPage({ username }: { readonly username: string }) {
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  return (
    <div className="min-h-dvh">
      <header className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div className="mx-auto flex max-w-6xl flex-wrap items-start justify-between gap-4 px-4 py-6 sm:px-6">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Procesor CSV kampanii reklamowych</h1>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Wyślij plik CSV (do 5 GiB), śledź przetwarzanie i pobierz wynik z kolumną CTR.
            </p>
          </div>
          <UserMenu username={username} />
        </div>
      </header>
      <main className="mx-auto grid max-w-6xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-2">
        <UploadPanel
          onJobAccepted={(job) => {
            setSelectedJobId(job.id);
          }}
        />
        <JobStatusCard jobId={selectedJobId} />
        <div className="min-w-0 lg:col-span-2">
          <JobHistory selectedJobId={selectedJobId} onSelect={setSelectedJobId} />
        </div>
      </main>
    </div>
  );
}
