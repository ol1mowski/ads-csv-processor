import { Alert } from '@/components/ui/Alert.component';
import { Button } from '@/components/ui/Button.component';
import { useLogout } from '@/hooks/useLogout.hook';
import { errorMessage } from '@/utils/errorMessage.util';

export function UserMenu({ username }: { readonly username: string }) {
  const logout = useLogout();
  return (
    <div className="flex flex-col items-end gap-2">
      <div className="flex flex-wrap items-center justify-end gap-3">
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Zalogowano jako{' '}
          <span className="font-semibold break-all text-slate-900 dark:text-slate-100">
            {username}
          </span>
        </p>
        <Button
          variant="secondary"
          disabled={logout.isPending}
          onClick={() => {
            logout.mutate();
          }}
        >
          {logout.isPending ? 'Wylogowywanie…' : 'Wyloguj'}
        </Button>
      </div>
      {logout.isError && (
        <Alert tone="error">
          {errorMessage(logout.error, 'Nie udało się wylogować — spróbuj ponownie.')}
        </Alert>
      )}
    </div>
  );
}
