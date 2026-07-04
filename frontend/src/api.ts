import type { AuthResponse, LinkResponse, MeResponse } from './types';

const ACCESS_TOKEN_KEY = 'wayfare.accessToken';
const REFRESH_TOKEN_KEY = 'wayfare.refreshToken';
const ANONYMOUS_LINKS_KEY = 'wayfare.anonymousLinks';

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

function setSession(auth: AuthResponse) {
  localStorage.setItem(ACCESS_TOKEN_KEY, auth.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken);
}

function clearSession() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

async function parseErrorMessage(res: Response) {
  const body = await res.json().catch(() => ({}));
  return body.error || `Request failed (${res.status})`;
}

async function request<T>(path: string, options: RequestInit = {}, retry = true): Promise<T> {
  const accessToken = getAccessToken();
  const res = await fetch(path, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...options.headers,
    },
  });

  if (res.status === 401 && retry && getRefreshToken()) {
    const refreshed = await refresh();
    if (refreshed) return request<T>(path, options, false);
    clearSession();
  }

  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  if (res.status === 204) return undefined as T;
  return res.json();
}

async function refresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  const res = await fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) return false;
  setSession(await res.json());
  return true;
}

export async function login(email: string, password: string): Promise<MeResponse> {
  const auth = await request<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  setSession(auth);
  return request<MeResponse>('/api/auth/me');
}

export async function register(email: string, password: string): Promise<MeResponse> {
  const auth = await request<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  setSession(auth);
  return request<MeResponse>('/api/auth/me');
}

export async function reactivate(email: string, password: string): Promise<MeResponse> {
  const auth = await request<AuthResponse>('/api/auth/reactivate', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  setSession(auth);
  return request<MeResponse>('/api/auth/me');
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  try {
    await fetch('/api/auth/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
  } finally {
    clearSession();
  }
}

export function getCurrentUser(): Promise<MeResponse> {
  return request<MeResponse>('/api/auth/me');
}

export function getMyLinks(): Promise<LinkResponse[]> {
  return request<LinkResponse[]>('/api/links');
}

interface ShortenApiResponse {
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
}

export async function shorten(url: string): Promise<LinkResponse> {
  const created = await request<ShortenApiResponse>('/api/shorten', {
    method: 'POST',
    body: JSON.stringify({ url }),
  });
  return { ...created, createdAt: new Date().toISOString(), clickCount: 0, expiresAt: null, maxClicks: null };
}

export function deleteLink(shortCode: string): Promise<void> {
  return request<void>(`/api/links/${shortCode}`, { method: 'DELETE' });
}

export async function deleteAccount(password: string): Promise<void> {
  await request<void>('/api/auth/me', {
    method: 'DELETE',
    body: JSON.stringify({ password }),
  });
  clearSession();
}

export function getAnonymousLinks(): LinkResponse[] {
  try {
    return JSON.parse(localStorage.getItem(ANONYMOUS_LINKS_KEY) ?? '[]');
  } catch {
    return [];
  }
}

export function saveAnonymousLink(link: LinkResponse): LinkResponse[] {
  const links = [link, ...getAnonymousLinks()];
  localStorage.setItem(ANONYMOUS_LINKS_KEY, JSON.stringify(links));
  return links;
}

export function removeAnonymousLink(shortCode: string): LinkResponse[] {
  const links = getAnonymousLinks().filter((link) => link.shortCode !== shortCode);
  localStorage.setItem(ANONYMOUS_LINKS_KEY, JSON.stringify(links));
  return links;
}
