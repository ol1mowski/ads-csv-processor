import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, renderHook } from '@testing-library/react';
import { userEvent, type Options as UserEventOptions } from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { File as NodeFile } from 'node:buffer';
import type { ReactElement, ReactNode } from 'react';
import type { Job, JobPage } from '@/api/types.api';

export const server = setupServer();

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Infinity },
      mutations: { retry: false },
    },
  });
}

function wrapperFor(queryClient: QueryClient) {
  return function TestQueryClientProvider({ children }: { readonly children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

export function renderWithClient(
  ui: ReactElement,
  userEventOptions?: UserEventOptions,
  queryClient: QueryClient = createTestQueryClient(),
) {
  const user = userEvent.setup(userEventOptions);
  return { ...render(ui, { wrapper: wrapperFor(queryClient) }), user, queryClient };
}

export function renderHookWithClient<Result>(
  hook: () => Result,
  queryClient: QueryClient = createTestQueryClient(),
) {
  return { ...renderHook(hook, { wrapper: wrapperFor(queryClient) }), queryClient };
}

export function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return HttpResponse.json(
    { type: 'about:blank', title: 'Error', status, detail: 'Problem', code, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

export function csvFile(content: string, name = 'ads.csv', type = 'text/csv'): File {
  return new NodeFile([content], name, { type }) as unknown as File;
}

export function fileOfSize(size: number, name = 'big.csv'): File {
  return { name, size, slice: () => new Blob(['']) } as unknown as File;
}

export const TEST_XSRF_TOKEN = '6d3f8a2e-test-xsrf-token';

export function setXsrfCookie(token: string = TEST_XSRF_TOKEN): void {
  document.cookie = `XSRF-TOKEN=${token}; path=/`;
}

export function clearXsrfCookie(): void {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
}

export function xsrfHeaderOf(request: Request): string | null {
  return request.headers.get('X-XSRF-TOKEN');
}

export const JOB_ID = '0b9c3e0e-5d2a-4c51-9a63-8f1e2b7d4c10';
export const OTHER_JOB_ID = '7c1f5d2a-3b4e-4f60-8a71-92b3c4d5e6f7';

export const SAMPLE_HEADER =
  'advertiserId;campaignId;creativeId;adBoxUUID;impressions;clicks;revenue;advertiserMargin';
export const SAMPLE_CSV = `${SAMPLE_HEADER}\n5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927\n`;

export function buildJob(overrides: Partial<Job> = {}): Job {
  return {
    id: JOB_ID,
    fileName: 'sample_ads_data.csv',
    status: 'QUEUED',
    sizeBytes: 77752,
    bytesProcessed: 0,
    progressPercent: 0,
    rowsProcessed: 0,
    rowsRejected: 0,
    rejectedSamples: [],
    totals: { impressions: '0', clicks: '0', ctr: null },
    failureReason: null,
    attempts: 0,
    createdAt: '2026-10-14T10:15:30.123Z',
    startedAt: null,
    finishedAt: null,
    resultUrl: null,
    ...overrides,
  };
}

export function processingJob(overrides: Partial<Job> = {}): Job {
  return buildJob({
    status: 'PROCESSING',
    bytesProcessed: 32656,
    progressPercent: 42,
    rowsProcessed: 420,
    totals: { impressions: '2828023', clicks: '74237', ctr: '0.026251' },
    attempts: 1,
    startedAt: '2026-10-14T10:15:30.410Z',
    ...overrides,
  });
}

export function completedJob(overrides: Partial<Job> = {}): Job {
  const id = overrides.id ?? JOB_ID;
  return buildJob({
    status: 'COMPLETED',
    bytesProcessed: 77752,
    progressPercent: 100,
    rowsProcessed: 1000,
    totals: { impressions: '6733389', clicks: '176755', ctr: '0.026251' },
    attempts: 1,
    startedAt: '2026-10-14T10:15:30.410Z',
    finishedAt: '2026-10-14T10:15:30.502Z',
    resultUrl: `/api/v1/jobs/${id}/result`,
    ...overrides,
  });
}

export function buildPage(items: readonly Job[], overrides: Partial<JobPage> = {}): JobPage {
  return {
    items,
    page: 0,
    size: 10,
    totalElements: items.length,
    totalPages: items.length === 0 ? 0 : 1,
    ...overrides,
  };
}

export function inSequence(...responses: (() => Response | Promise<Response>)[]) {
  const calls = { count: 0 };
  const resolver = () => {
    const respond = responses[Math.min(calls.count, responses.length - 1)];
    calls.count += 1;
    if (respond === undefined) {
      throw new Error('inSequence() needs at least one response');
    }
    return respond();
  };
  return { calls, resolver };
}
