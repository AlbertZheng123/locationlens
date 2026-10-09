import { createRef } from 'react';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ReviewView } from './ReviewView';
import type { ReviewViewHandle } from './ReviewView';
import { ReviewApiError } from './ReviewApiClient';
import type { ReviewApiClient } from './ReviewApiClient';
import type { ReviewModel } from './ReviewModel';

const existing: ReviewModel = {
  reviewId: 'review-1', rating: 4, text: 'A lovely park.',
  createdAt: '2026-10-09T12:00:00Z', userId: 'user-1', placeId: 'place-1',
};
const created: ReviewModel = { ...existing, reviewId: 'review-2', rating: 5, text: 'Wonderful!' };

function client(): ReviewApiClient {
  return {
    getPlaceReviews: vi.fn().mockResolvedValue([existing]),
    getUserReviewHistory: vi.fn().mockResolvedValue([existing]),
    submitReview: vi.fn().mockResolvedValue(created),
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

async function fillReview() {
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText('Rating'), '5');
  await user.type(screen.getByLabelText('Your experience'), '  Wonderful!  ');
  return user;
}

describe('ReviewView', () => {
  it('waits for a place rather than issuing an empty request', () => {
    const api = client();
    render(<ReviewView apiClient={api} />);
    expect(screen.getByText('Select a place to see its reviews.')).toBeInTheDocument();
    expect(api.getPlaceReviews).not.toHaveBeenCalled();
  });

  it('displays place reviews with rating, text, author and date', async () => {
    const api = client();
    render(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading reviews');
    expect(await screen.findByText('A lovely park.')).toBeInTheDocument();
    expect(api.getPlaceReviews).toHaveBeenCalledWith('place-1');
    expect(screen.getByLabelText('4 out of 5 stars')).toBeInTheDocument();
    expect(screen.getByText(/Reviewer: user-1/)).toHaveTextContent('2026-10-09');
  });

  it('displays user history and place identifiers without a submission form', async () => {
    const api = client();
    render(<ReviewView apiClient={api} target={{ kind: 'user', userId: 'user-1' }} />);
    expect(await screen.findByText('A lovely park.')).toBeInTheDocument();
    expect(api.getUserReviewHistory).toHaveBeenCalledWith('user-1');
    expect(screen.getByRole('heading', { name: 'Review history' })).toBeInTheDocument();
    expect(screen.getByText(/Place: place-1/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Submit review' })).not.toBeInTheDocument();
  });

  it('implements both display operations through its public ref', async () => {
    const api = client();
    const ref = createRef<ReviewViewHandle>();
    render(<ReviewView apiClient={api} ref={ref} />);
    await act(async () => { await ref.current!.displayPlaceReviews('place-1'); });
    expect(api.getPlaceReviews).toHaveBeenCalledWith('place-1');
    await act(async () => { await ref.current!.displayUserReviewHistory('user-1'); });
    expect(api.getUserReviewHistory).toHaveBeenCalledWith('user-1');
    expect(screen.getByRole('heading', { name: 'Review history' })).toBeInTheDocument();
  });

  it('displays distinct empty states for a place and user history', async () => {
    const api = client();
    vi.mocked(api.getPlaceReviews).mockResolvedValue([]);
    vi.mocked(api.getUserReviewHistory).mockResolvedValue([]);
    const { rerender } = render(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    expect(await screen.findByText(/No reviews yet/)).toBeInTheDocument();
    rerender(<ReviewView apiClient={api} target={{ kind: 'user', userId: 'user-1' }} />);
    expect(await screen.findByText('This user has not written any reviews yet.')).toBeInTheDocument();
  });

  it('offers retry after a failed read', async () => {
    const api = client();
    vi.mocked(api.getPlaceReviews).mockRejectedValueOnce(new Error('offline'));
    render(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load reviews');
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('A lovely park.')).toBeInTheDocument();
    expect(api.getPlaceReviews).toHaveBeenCalledTimes(2);
  });

  it('validates rating and blank text before sending a request', async () => {
    const api = client();
    render(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    await screen.findByText('A lovely park.');
    await userEvent.click(screen.getByRole('button', { name: 'Submit review' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Choose a rating');
    await userEvent.selectOptions(screen.getByLabelText('Rating'), '5');
    await userEvent.type(screen.getByLabelText('Your experience'), '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Submit review' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a review');
    expect(api.submitReview).not.toHaveBeenCalled();
  });

  it('also validates direct submissions, including text length and fractional ratings', async () => {
    const api = client();
    const ref = createRef<ReviewViewHandle>();
    render(<ReviewView apiClient={api} ref={ref} target={{ kind: 'place', placeId: 'place-1' }} />);
    await screen.findByText('A lovely park.');
    await act(async () => { await ref.current!.submitReview('place-1', 5, 'x'.repeat(2001)); });
    expect(screen.getByRole('alert')).toHaveTextContent('2,000 characters');
    for (const rating of [0, 6, 2.5]) {
      await act(async () => { await ref.current!.submitReview('place-1', rating, 'Good'); });
    }
    await act(async () => { await ref.current!.submitReview(' ', 5, 'Good'); });
    expect(api.submitReview).not.toHaveBeenCalled();
  });

  it('submits trimmed text, adds the returned review and resets the form', async () => {
    const api = client();
    render(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    await screen.findByText('A lovely park.');
    const user = await fillReview();
    await user.click(screen.getByRole('button', { name: 'Submit review' }));
    expect(await screen.findByText('Your review was submitted.')).toBeInTheDocument();
    expect(api.submitReview).toHaveBeenCalledExactlyOnceWith('place-1', 5, 'Wonderful!');
    expect(screen.getByText('Wonderful!')).toBeInTheDocument();
    expect(screen.getByLabelText('Your experience')).toHaveValue('');
    expect(screen.getByLabelText('Rating')).toHaveValue('0');
  });

  it('prevents duplicate requests while a submission is pending', async () => {
    const api = client();
    const pending = deferred<ReviewModel>();
    vi.mocked(api.submitReview).mockReturnValue(pending.promise);
    const ref = createRef<ReviewViewHandle>();
    render(<ReviewView ref={ref} apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    await screen.findByText('A lovely park.');
    const user = await fillReview();
    await user.click(screen.getByRole('button', { name: 'Submit review' }));
    expect(screen.getByRole('button', { name: 'Submitting…' })).toBeDisabled();
    expect(screen.getByLabelText('Your experience')).toBeDisabled();
    await act(async () => { await ref.current!.submitReview('place-1', 5, 'Again'); });
    expect(api.submitReview).toHaveBeenCalledTimes(1);
    await act(async () => { pending.resolve(created); });
    expect(screen.getByRole('button', { name: 'Submit review' })).toBeEnabled();
  });

  it.each([401, 403, 409, 500])('keeps the draft and exposes errors after HTTP %s', async status => {
    const api = client();
    vi.mocked(api.submitReview).mockRejectedValue(new ReviewApiError(status, 'Service unavailable'));
    render(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    await screen.findByText('A lovely park.');
    const user = await fillReview();
    await user.click(screen.getByRole('button', { name: 'Submit review' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      status === 401 ? 'Sign in' : status === 403 ? 'permission' : status === 409 ? 'already reviewed' : 'Service unavailable');
    expect(screen.getByLabelText('Your experience')).toHaveValue('  Wonderful!  ');
    expect(screen.getByLabelText('Rating')).toHaveValue('5');
  });

  it('ignores stale results when a different place is selected', async () => {
    const api = client();
    const old = deferred<ReviewModel[]>();
    vi.mocked(api.getPlaceReviews).mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce([{ ...existing, placeId: 'place-2', text: 'New place review' }]);
    const { rerender } = render(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    rerender(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-2' }} />);
    expect(await screen.findByText('New place review')).toBeInTheDocument();
    await act(async () => { old.resolve([existing]); });
    expect(screen.queryByText('A lovely park.')).not.toBeInTheDocument();
  });

  it('does not show a submission for the previous place after switching', async () => {
    const api = client();
    const pending = deferred<ReviewModel>();
    vi.mocked(api.submitReview).mockReturnValue(pending.promise);
    vi.mocked(api.getPlaceReviews).mockResolvedValue([]);
    const { rerender } = render(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    await screen.findByText(/No reviews yet/);
    const user = await fillReview();
    await user.click(screen.getByRole('button', { name: 'Submit review' }));
    rerender(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-2' }} />);
    await act(async () => { pending.resolve(created); });
    expect(screen.queryByText('Wonderful!')).not.toBeInTheDocument();
    expect(screen.queryByText('Your review was submitted.')).not.toBeInTheDocument();
  });

  it('does not allow an older read to overwrite a successful submission', async () => {
    const api = client();
    const pendingRead = deferred<ReviewModel[]>();
    vi.mocked(api.getPlaceReviews).mockReturnValue(pendingRead.promise);
    const ref = createRef<ReviewViewHandle>();
    render(<ReviewView ref={ref} apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    await act(async () => { await ref.current!.submitReview('place-1', 5, 'Wonderful!'); });
    expect(screen.getByText('Wonderful!')).toBeInTheDocument();
    await act(async () => { pendingRead.resolve([]); });
    expect(screen.getByText('Wonderful!')).toBeInTheDocument();
  });

  it('renders review text as text rather than executable markup', async () => {
    const api = client();
    const markup = '<img src=x onerror=alert(1)>';
    vi.mocked(api.getPlaceReviews).mockResolvedValue([{ ...existing, text: markup }]);
    render(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    expect(await screen.findByText(markup)).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('safely completes an outstanding read after unmount', async () => {
    const api = client();
    const pending = deferred<ReviewModel[]>();
    vi.mocked(api.getPlaceReviews).mockReturnValue(pending.promise);
    const { unmount } = render(<ReviewView apiClient={api} target={{ kind: 'place', placeId: 'place-1' }} />);
    unmount();
    await act(async () => { pending.resolve([existing]); });
    expect(screen.queryByText('A lovely park.')).not.toBeInTheDocument();
  });
});
