import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { setUnauthorizedHandler } from '@/api/httpClient.api';
import { endSession } from '@/utils/sessionCache.util';

export function useEndSessionOnUnauthorized(): void {
  const queryClient = useQueryClient();
  useEffect(
    () =>
      setUnauthorizedHandler(() => {
        endSession(queryClient);
      }),
    [queryClient],
  );
}
