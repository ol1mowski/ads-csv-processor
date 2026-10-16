import { useQuery } from '@tanstack/react-query';
import { getCurrentUser } from '@/api/auth.api';
import type { User } from '@/api/types.api';
import { queryKeys } from '@/utils/queryKeys.util';
import { forgetUserData } from '@/utils/sessionCache.util';

export function useSession() {
  return useQuery({
    queryKey: queryKeys.session,
    queryFn: async ({ client }) => {
      const user = await getCurrentUser();
      if (client.getQueryData<User | null>(queryKeys.session)?.username !== user?.username) {
        forgetUserData(client);
      }
      return user;
    },
  });
}
