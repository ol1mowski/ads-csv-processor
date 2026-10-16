import { Alert } from '@/components/ui/Alert.component';
import { Button } from '@/components/ui/Button.component';
import { CenteredScreen } from '@/components/ui/CenteredScreen.component';
import { useEndSessionOnUnauthorized } from '@/hooks/useEndSessionOnUnauthorized.hook';
import { useSession } from '@/hooks/useSession.hook';
import { JobsPage } from '@/pages/Jobs.page';
import { LoginPage } from '@/pages/Login.page';

export function App() {
  const session = useSession();
  useEndSessionOnUnauthorized();

  if (session.data === undefined) {
    return session.isError ? (
      <CenteredScreen>
        <Alert tone="error">Nie udało się połączyć z serwerem.</Alert>
        <Button onClick={() => void session.refetch()}>Spróbuj ponownie</Button>
      </CenteredScreen>
    ) : (
      <CenteredScreen>
        <p role="status" className="text-sm text-slate-600 dark:text-slate-400">
          Sprawdzanie sesji…
        </p>
      </CenteredScreen>
    );
  }

  if (session.data === null) {
    return <LoginPage />;
  }

  return <JobsPage key={session.data.username} username={session.data.username} />;
}
