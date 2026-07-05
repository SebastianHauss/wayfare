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

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ErrorResponse {
  error: string;
  code: string | null;
}

export interface MessageResponse {
  message: string;
}

export interface StatBucket {
  label: string;
  count: number;
}

export interface DailyCount {
  day: string;
  count: number;
}

export interface LinkStats {
  totalClicks: number;
  clicksByDay: DailyCount[];
  topReferrers: StatBucket[];
  topCountries: StatBucket[];
  devices: StatBucket[];
}
