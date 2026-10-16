import { httpClient } from './httpClient.api';
import type { Job, JobPage, JobStatus } from './types.api';

export async function uploadJob(
  file: File,
  signal: AbortSignal,
  onProgress: (sentBytes: number) => void,
): Promise<Job> {
  const response = await httpClient.post<Job>('/jobs', file, {
    params: { fileName: file.name },
    headers: { 'Content-Type': 'text/csv' },
    signal,
    onUploadProgress: (event) => {
      onProgress(event.loaded);
    },
  });
  return response.data;
}

export async function getJob(id: string, signal?: AbortSignal): Promise<Job> {
  return (await httpClient.get<Job>(`/jobs/${id}`, { signal })).data;
}

export async function listJobs(
  page: number,
  size: number,
  statuses: readonly JobStatus[] | null,
  signal?: AbortSignal,
): Promise<JobPage> {
  const params = statuses === null ? { page, size } : { page, size, status: statuses.join(',') };
  return (await httpClient.get<JobPage>('/jobs', { params, signal })).data;
}

export async function cancelJob(id: string): Promise<Job> {
  return (await httpClient.post<Job>(`/jobs/${id}/cancel`)).data;
}
