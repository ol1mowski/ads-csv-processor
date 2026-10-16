import { httpClient, httpStatus } from './httpClient.api';
import type { User } from './types.api';

export async function getCurrentUser(): Promise<User | null> {
  try {
    return (await httpClient.get<User>('/auth/me')).data;
  } catch (error) {
    if (httpStatus(error) === 401) {
      return null;
    }
    throw error;
  }
}

export async function login(username: string, password: string): Promise<User> {
  return (await httpClient.post<User>('/auth/login', { username, password })).data;
}

export async function logout(): Promise<void> {
  await httpClient.post('/auth/logout');
}
