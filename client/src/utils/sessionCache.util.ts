import type { QueryClient } from '@tanstack/react-query';
import type { User } from '@/api/types.api';
import { queryKeys } from './queryKeys.util';

export function forgetUserData(queryClient: QueryClient): void {
  queryClient.removeQueries({
    predicate: (query) => query.queryKey[0] !== queryKeys.session[0],
  });
}

export function startSession(queryClient: QueryClient, user: User): void {
  void queryClient.cancelQueries({ queryKey: queryKeys.session });
  forgetUserData(queryClient);
  queryClient.setQueryData(queryKeys.session, user);
}

export function endSession(queryClient: QueryClient): void {
  void queryClient.cancelQueries({ queryKey: queryKeys.session });
  queryClient.setQueryData(queryKeys.session, null);
  forgetUserData(queryClient);
}

export function recheckSession(queryClient: QueryClient): void {
  void queryClient.invalidateQueries({ queryKey: queryKeys.session });
}
