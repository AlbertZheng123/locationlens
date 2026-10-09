import type { ReviewModel } from './ReviewModel';

/** Matches the API-client operations in the project diagram. */
export interface ReviewApiClient {
  getPlaceReviews(placeId: string): Promise<ReviewModel[]>;
  getUserReviewHistory(userId: string): Promise<ReviewModel[]>;
  submitReview(placeId: string, rating: number, text: string): Promise<ReviewModel>;
}

export class ReviewApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = 'ReviewApiError';
  }
}

interface ClientOptions {
  /** Obtain the current token from AuthClient; do not cache it in this client. */
  getAccessToken: () => string | null | undefined | Promise<string | null | undefined>;
  baseUrl?: string;
  fetch?: typeof globalThis.fetch;
}

export function createReviewApiClient({ getAccessToken, baseUrl = '', fetch: fetchOverride }: ClientOptions): ReviewApiClient {
  const root = baseUrl.replace(/\/$/, '');

  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const token = await getAccessToken();
    if (!token?.trim()) {
      throw new ReviewApiError(401, 'Sign in to access reviews.');
    }
    const fetcher = fetchOverride ?? globalThis.fetch;
    const response = await fetcher(`${root}/api/v1${path}`, {
      ...init,
      credentials: 'omit',
      redirect: 'error',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      },
    });
    if (!response.ok) {
      // The existing backend uses { error, message }; also accept Problem Detail.
      const body: unknown = await response.json().catch(() => null);
      const error = body && typeof body === 'object' ? body as Record<string, unknown> : {};
      const detail = typeof error.message === 'string' ? error.message
        : typeof error.detail === 'string' ? error.detail : `Review request failed (${response.status}).`;
      throw new ReviewApiError(response.status, detail);
    }
    return await response.json() as T;
  }

  return {
    getPlaceReviews: (placeId) => request(`/places/${encodeURIComponent(placeId)}/reviews`),
    getUserReviewHistory: (userId) => request(`/users/${encodeURIComponent(userId)}/reviews`),
    submitReview: (placeId, rating, text) => request(`/places/${encodeURIComponent(placeId)}/reviews`, {
      method: 'POST',
      body: JSON.stringify({ rating, text }),
    }),
  };
}
