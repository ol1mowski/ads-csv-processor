import { useMutation, useQueryClient } from '@tanstack/react-query';
import { login } from '@/api/auth.api';
import { problemCode } from '@/utils/errorMessage.util';
import { recheckSession, startSession } from '@/utils/sessionCache.util';

export interface Credentials {
  readonly username: string;
  readonly password: string;
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ username, password }: Credentials) => login(username, password),
    onSuccess: (user) => {
      startSession(queryClient, user);
    },
    onError: (error) => {
      if (problemCode(error) === 'CSRF_INVALID') {
        recheckSession(queryClient);
      }
    },
  });
}
