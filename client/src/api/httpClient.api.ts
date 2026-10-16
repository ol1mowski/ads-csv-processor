import axios from 'axios';

export const httpClient = axios.create({
  baseURL: '/api/v1',
  adapter: 'xhr',
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler): () => void {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) {
      unauthorizedHandler = null;
    }
  };
}

httpClient.interceptors.response.use(undefined, (error: unknown) => {
  if (httpStatus(error) === 401 && !isAuthRequest(error)) {
    unauthorizedHandler?.();
  }
  throw error;
});

function isAuthRequest(error: unknown): boolean {
  return axios.isAxiosError(error) && (error.config?.url?.startsWith('/auth/') ?? false);
}

export function httpStatus(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined;
}

export function isClientError(error: unknown): boolean {
  const status = httpStatus(error);
  return status !== undefined && status < 500;
}

export function isAborted(error: unknown): boolean {
  return axios.isCancel(error);
}
