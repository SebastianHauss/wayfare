export interface MeResponse {
  id: number;
  email: string;
  createdAt: string;
}

export interface LinkResponse {
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  createdAt: string;
  clickCount: number | null;
  expiresAt: string | null;
  maxClicks: number | null;
}

export interface ErrorResponse {
  error: string;
  code: string | null;
}

export interface MessageResponse {
  message: string;
}
