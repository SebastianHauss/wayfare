import type { LinkResponse, MeResponse, MessageResponse } from './types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
const CSRF_HEADER = 'X-Requested-With';
const CSRF_HEADER_VALUE = 'WayfareApp';

const ANONYMOUS_LINKS_KEY = 'wayfare.anonymousLinks';

export class ApiError extends Error {
  status: number;
  code: string | null;

  constructor(message: string, status: number, code: string | null = null) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function parseError(res: Response): Promise<{ message: string; code: string | null }> {
  const body = await res.json().catch(() => ({}));
  return { message: body.error || `Request failed (${res.status})`, code: body.code ?? null };
}

async function request<T>(path: string, options: RequestInit = {}, retry = true): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    credentials: 'include',
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      [CSRF_HEADER]: CSRF_HEADER_VALUE,
      ...options.headers,
    },
  });

  if (res.status === 401 && retry) {
    const refreshed = await refresh();
    if (refreshed) return request<T>(path, options, false);
  }

  if (!res.ok) {
    const { message, code } = await parseError(res);
    throw new ApiError(message, res.status, code);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

async function refresh(): Promise<boolean> {
  const res = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
    headers: { [CSRF_HEADER]: CSRF_HEADER_VALUE },
  });
  return res.ok;
}

export async function logout(): Promise<void> {
  await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: 'POST',
    credentials: 'include',
    headers: { [CSRF_HEADER]: CSRF_HEADER_VALUE },
  });
}

export function register(email: string, password: string): Promise<MessageResponse> {
  return request<MessageResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

// The backend sets the auth cookies on success and returns the current user,
// so these resolve to the signed-in user without a separate /me round-trip.
export function login(email: string, password: string): Promise<MeResponse> {
  return request<MeResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export function verifyEmail(token: string): Promise<MeResponse> {
  return request<MeResponse>('/api/auth/verify-email', {
    method: 'POST',
    body: JSON.stringify({ token }),
  });
}

export function resendVerification(email: string): Promise<MessageResponse> {
  return request<MessageResponse>('/api/auth/resend-verification', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export function reactivate(email: string, password: string): Promise<MeResponse> {
  return request<MeResponse>('/api/auth/reactivate', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
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

export function getQrCodeUrl(link: LinkResponse): string {
  return `${link.shortUrl.replace(/\/$/, '')}/qr`;
}

export async function deleteAccount(password: string): Promise<void> {
  await request<void>('/api/auth/me', {
    method: 'DELETE',
    body: JSON.stringify({ password }),
  });
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
