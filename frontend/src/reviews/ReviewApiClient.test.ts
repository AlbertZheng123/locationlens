import { describe, expect, it, vi } from 'vitest';
import { createReviewApiClient, ReviewApiError } from './ReviewApiClient';

const review = { reviewId: 'r1', rating: 5, text: 'Great', createdAt: '2026-10-09T12:00:00Z', userId: 'u1', placeId: 'p1' };

function setup(response: Response = Response.json([review])) {
  const fetch = vi.fn<typeof globalThis.fetch>().mockImplementation(async () => response.clone());
  const getAccessToken = vi.fn().mockReturnValue('access-token');
  return { fetch, getAccessToken, client: createReviewApiClient({ baseUrl: 'http://localhost:8080/', getAccessToken, fetch }) };
}

describe('ReviewApiClient', () => {
  it('requests place reviews using the bearer API chain and encodes opaque IDs', async () => {
    const { client, fetch } = setup();
    expect(await client.getPlaceReviews('place/a?b')).toEqual([review]);
    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/v1/places/place%2Fa%3Fb/reviews',
      expect.objectContaining({ credentials: 'omit', redirect: 'error', headers: { Accept: 'application/json', Authorization: 'Bearer access-token' } }));
  });

  it('requests a user review history', async () => {
    const { client, fetch } = setup();
    await client.getUserReviewHistory('user/a');
    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/v1/users/user%2Fa/reviews', expect.anything());
  });

  it('posts rating/text without accepting a client-supplied author', async () => {
    const { client, fetch } = setup(Response.json(review, { status: 201 }));
    expect(await client.submitReview('p1', 5, 'Great')).toEqual(review);
    expect(fetch).toHaveBeenCalledWith('http://localhost:8080/api/v1/places/p1/reviews',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ rating: 5, text: 'Great' }),
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }) }));
  });

  it('reads the current access token again for every request', async () => {
    const { client, fetch, getAccessToken } = setup();
    await client.getPlaceReviews('p1');
    getAccessToken.mockReturnValue('refreshed-token');
    await client.getUserReviewHistory('u1');
    expect(fetch.mock.calls[1][1]?.headers).toEqual(expect.objectContaining({ Authorization: 'Bearer refreshed-token' }));
  });

  it('does not send a request without authentication', async () => {
    const { fetch } = setup();
    const client = createReviewApiClient({ fetch, getAccessToken: () => null });
    await expect(client.getPlaceReviews('p1')).rejects.toMatchObject({ status: 401 });
    expect(fetch).not.toHaveBeenCalled();
  });

  it.each([{ message: 'Duplicate review' }, { detail: 'Duplicate review' }])('supports backend error formats: %j', async body => {
    const { client } = setup(Response.json(body, { status: 409 }));
    await expect(client.submitReview('p1', 5, 'Great')).rejects.toMatchObject({ status: 409, message: 'Duplicate review' });
  });

  it('handles non-JSON backend errors', async () => {
    const { client } = setup(new Response('Bad gateway', { status: 502 }));
    await expect(client.getPlaceReviews('p1')).rejects.toEqual(new ReviewApiError(502, 'Review request failed (502).'));
  });

  it('propagates network errors so the view can offer retry', async () => {
    const { client, fetch } = setup();
    fetch.mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(client.getPlaceReviews('p1')).rejects.toThrow('Failed to fetch');
  });
});
