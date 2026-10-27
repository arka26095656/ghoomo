import type {
  AuthResponse,
  ChatMessage,
  ChatSession,
  ItineraryDetail,
  ItineraryPlan,
  ItinerarySummary,
  Profile,
} from '../types';

const BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? '';
const TOKEN_KEY = 'ghoomo.token';

export const tokenStore = {
  get: (): string | null => {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (t: string | null) => {
    try {
      if (t) localStorage.setItem(TOKEN_KEY, t);
      else localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* storage unavailable: session-only login */
    }
  },
};

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public fields?: Record<string, string>,
  ) {
    super(message);
  }
}

/** Fired when the server says the token is no longer valid, so the app can log out. */
export const onUnauthorized = new EventTarget();

function headers(json = true): HeadersInit {
  const h: Record<string, string> = {};
  if (json) h['Content-Type'] = 'application/json';
  const t = tokenStore.get();
  if (t) h['Authorization'] = `Bearer ${t}`;
  return h;
}

export async function toApiError(res: Response): Promise<ApiError> {
  let message = `Request failed (${res.status}).`;
  let fields: Record<string, string> | undefined;
  try {
    const body = await res.json();
    if (body?.message) message = body.message;
    fields = body?.fields ?? undefined;
  } catch {
    if (res.status === 0 || res.status >= 502) message = "Can't reach the Ghoomo server. Check that the backend is running.";
  }
  if (res.status === 401 && tokenStore.get()) onUnauthorized.dispatchEvent(new Event('logout'));
  return new ApiError(res.status, message, fields);
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(BASE + path, {
      method,
      headers: headers(body !== undefined),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "Can't reach the Ghoomo server. Check your connection and that the backend is running.");
  }
  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  base: BASE,
  headers,

  register: (name: string, email: string, password: string) =>
    request<AuthResponse>('POST', '/api/auth/register', { name, email, password }),
  login: (email: string, password: string) => request<AuthResponse>('POST', '/api/auth/login', { email, password }),

  me: () => request<Profile>('GET', '/api/me'),
  updateMe: (p: Omit<Profile, 'id' | 'email'>) => request<Profile>('PUT', '/api/me', p),

  sessions: () => request<ChatSession[]>('GET', '/api/chat/sessions'),
  createSession: () => request<ChatSession>('POST', '/api/chat/sessions'),
  deleteSession: (id: number) => request<void>('DELETE', `/api/chat/sessions/${id}`),
  messages: (id: number) => request<ChatMessage[]>('GET', `/api/chat/sessions/${id}/messages`),

  itineraries: () => request<ItinerarySummary[]>('GET', '/api/itineraries'),
  itinerary: (id: number) => request<ItineraryDetail>('GET', `/api/itineraries/${id}`),
  updateItinerary: (id: number, body: { title: string; startDate: string | null; budgetInr: number | null; plan: ItineraryPlan }) =>
    request<ItineraryDetail>('PUT', `/api/itineraries/${id}`, body),
  deleteItinerary: (id: number) => request<void>('DELETE', `/api/itineraries/${id}`),
};
