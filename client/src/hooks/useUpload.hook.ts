import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { uploadJob } from '@/api/jobs.api';
import type { Job } from '@/api/types.api';
import { queryKeys } from '@/utils/queryKeys.util';
import {
  acceptedResult,
  invalidFileResult,
  UNREADABLE_FILE_MESSAGE,
  uploadFailureResult,
  type UploadResult,
} from '@/utils/uploadResult.util';
import { validateCsvFile } from '@/utils/validateCsvFile.util';
import { useBeforeUnloadWarning } from './useBeforeUnloadWarning.hook';

export type UploadState =
  | { readonly phase: 'idle' }
  | { readonly phase: 'validating' }
  | { readonly phase: 'uploading'; readonly sentBytes: number; readonly totalBytes: number }
  | { readonly phase: 'done'; readonly result: UploadResult };

export function useUpload(onAccepted: (job: Job) => void) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<UploadState>({ phase: 'idle' });
  const controllerRef = useRef<AbortController | null>(null);

  useBeforeUnloadWarning(state.phase === 'uploading');

  useEffect(
    () => () => {
      controllerRef.current?.abort();
    },
    [],
  );

  async function send(file: File, signal: AbortSignal): Promise<void> {
    let sentAllBytes = false;
    setState({ phase: 'uploading', sentBytes: 0, totalBytes: file.size });
    try {
      const job = await uploadJob(file, signal, (sentBytes) => {
        sentAllBytes = sentBytes >= file.size;
        setState({ phase: 'uploading', sentBytes, totalBytes: file.size });
      });
      queryClient.setQueryData(queryKeys.job(job.id), job);
      setState({ phase: 'done', result: acceptedResult(job) });
      onAccepted(job);
    } catch (error) {
      setState({ phase: 'done', result: uploadFailureResult(error, sentAllBytes) });
    } finally {
      void queryClient.invalidateQueries({ queryKey: queryKeys.jobLists });
    }
  }

  async function start(file: File): Promise<void> {
    if (controllerRef.current !== null) {
      return;
    }
    const controller = new AbortController();
    controllerRef.current = controller;
    try {
      setState({ phase: 'validating' });
      const issues = await validateCsvFile(file).catch(() => [UNREADABLE_FILE_MESSAGE]);
      if (issues.length > 0) {
        setState({ phase: 'done', result: invalidFileResult(issues) });
      } else if (!controller.signal.aborted) {
        await send(file, controller.signal);
      }
    } finally {
      controllerRef.current = null;
    }
  }

  const canAbort = state.phase === 'uploading' && state.sentBytes < state.totalBytes;

  function abort(): void {
    if (canAbort) {
      controllerRef.current?.abort();
    }
  }

  const busy = state.phase === 'validating' || state.phase === 'uploading';
  return { state, start, abort, canAbort, busy };
}
