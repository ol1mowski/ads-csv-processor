import { useMutation, useQueryClient } from '@tanstack/react-query';
import { logout } from '@/api/auth.api';
import { endSession, recheckSession } from '@/utils/sessionCache.util';

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      endSession(queryClient);
      recheckSession(queryClient);
    },
  });
}
