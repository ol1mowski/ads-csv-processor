import { useState, type SubmitEvent } from 'react';
import { LoginField } from '@/components/auth/LoginField.component';
import { Alert } from '@/components/ui/Alert.component';
import { Button } from '@/components/ui/Button.component';
import { Card } from '@/components/ui/Card.component';
import { useLogin } from '@/hooks/useLogin.hook';
import { errorMessage } from '@/utils/errorMessage.util';

export function LoginPage() {
  const login = useLogin();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (event.currentTarget.checkValidity() && !login.isPending) {
      login.mutate({ username, password });
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm space-y-6">
        <header className="text-center">
          <h1 className="text-2xl font-bold tracking-tight">Procesor CSV kampanii reklamowych</h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Zaloguj się, aby wysyłać pliki i przeglądać swoje zadania.
          </p>
        </header>
        <Card title="Logowanie">
          <form onSubmit={submit} className="space-y-4">
            <LoginField
              label="Login"
              type="text"
              autoComplete="username"
              maxLength={64}
              value={username}
              onChange={setUsername}
            />
            <LoginField
              label="Hasło"
              type="password"
              autoComplete="current-password"
              maxLength={128}
              value={password}
              onChange={setPassword}
            />
            {login.isError && (
              <Alert tone="error">
                {errorMessage(login.error, 'Nie udało się zalogować — spróbuj ponownie.')}
              </Alert>
            )}
            <Button type="submit" disabled={login.isPending}>
              {login.isPending ? 'Logowanie…' : 'Zaloguj się'}
            </Button>
          </form>
        </Card>
      </div>
    </div>
  );
}
