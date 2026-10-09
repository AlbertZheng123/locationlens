import { forwardRef, useCallback, useEffect, useId, useImperativeHandle, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import type { ReviewApiClient } from './ReviewApiClient';
import { ReviewApiError } from './ReviewApiClient';
import type { ReviewModel, ReviewTarget } from './ReviewModel';
import './ReviewView.css';

export interface ReviewViewHandle {
  displayPlaceReviews(placeId: string): Promise<void>;
  displayUserReviewHistory(userId: string): Promise<void>;
  submitReview(placeId: string, rating: number, text: string): Promise<void>;
}

export interface ReviewViewProps {
  apiClient: ReviewApiClient;
  /** Set this from PlaceDetailsView or the user-history page. */
  target?: ReviewTarget;
}

interface ReadState {
  target?: ReviewTarget;
  reviews: ReviewModel[];
  status: 'idle' | 'loading' | 'ready' | 'error';
  error?: string;
}

function targetKey(target?: ReviewTarget): string {
  if (!target) return '';
  return target.kind === 'place' ? `place:${target.placeId}` : `user:${target.userId}`;
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ReviewApiError) {
    if (error.status === 401) return 'Sign in to access reviews.';
    if (error.status === 403) return 'You do not have permission to perform this action.';
    if (error.status === 409) return 'You have already reviewed this place.';
    return error.message;
  }
  return fallback;
}

/** React implementation of the diagram's ReviewView, with an injectable API boundary. */
export const ReviewView = forwardRef<ReviewViewHandle, ReviewViewProps>(function ReviewView({ apiClient, target }, ref) {
  const [read, setRead] = useState<ReadState>({ reviews: [], status: 'idle' });
  const [rating, setRating] = useState(0);
  const [text, setText] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string>();
  const [confirmation, setConfirmation] = useState<string>();
  const selected = useRef<ReviewTarget | undefined>(undefined);
  const readSequence = useRef(0);
  const alive = useRef(true);
  const inFlight = useRef(false);
  const id = useId();

  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; readSequence.current += 1; };
  }, []);

  const display = useCallback(async (next: ReviewTarget) => {
    const sequence = ++readSequence.current;
    if (targetKey(selected.current) !== targetKey(next)) {
      setRating(0);
      setText('');
      setSubmitError(undefined);
      setConfirmation(undefined);
    }
    selected.current = next;
    setRead({ target: next, reviews: [], status: 'loading' });
    const identifier = next.kind === 'place' ? next.placeId : next.userId;
    try {
      if (!identifier.trim()) throw new Error('Missing identifier');
      const reviews = next.kind === 'place'
        ? await apiClient.getPlaceReviews(next.placeId)
        : await apiClient.getUserReviewHistory(next.userId);
      if (alive.current && sequence === readSequence.current) {
        setRead({ target: next, reviews, status: 'ready' });
      }
    } catch (error) {
      if (alive.current && sequence === readSequence.current) {
        setRead({ target: next, reviews: [], status: 'error',
          error: errorMessage(error, 'Unable to load reviews. Please try again.') });
      }
    }
  }, [apiClient]);

  const kind = target?.kind;
  const identifier = target?.kind === 'place' ? target.placeId : target?.userId;
  useEffect(() => {
    if (kind && identifier !== undefined) {
      void display(kind === 'place' ? { kind, placeId: identifier } : { kind, userId: identifier });
    } else {
      ++readSequence.current;
      selected.current = undefined;
      setRead({ reviews: [], status: 'idle' });
    }
    return () => { ++readSequence.current; };
  }, [kind, identifier, display]);

  const submitReview = useCallback(async (placeId: string, value: number, reviewText: string) => {
    if (inFlight.current) return;
    setSubmitError(undefined);
    setConfirmation(undefined);
    if (!placeId.trim()) {
      setSubmitError('Select a place before submitting a review.');
      return;
    }
    if (!Number.isInteger(value) || value < 1 || value > 5) {
      setSubmitError('Choose a rating from 1 to 5.');
      return;
    }
    if (!reviewText.trim() || reviewText.length > 2000) {
      setSubmitError('Enter a review between 1 and 2,000 characters.');
      return;
    }
    const selectionAtStart = targetKey(selected.current);
    inFlight.current = true;
    setSubmitting(true);
    try {
      const review = await apiClient.submitReview(placeId, value, reviewText.trim());
      if (!alive.current) return;
      if (targetKey(selected.current) === selectionAtStart) {
        setRating(0);
        setText('');
        setConfirmation('Your review was submitted.');
        if (selected.current?.kind === 'place' && selected.current.placeId === placeId) {
          // Invalidate older reads so they cannot remove the just-created review.
          ++readSequence.current;
          setRead(current => ({ target: current.target, status: 'ready',
            reviews: [review, ...current.reviews.filter(item => item.reviewId !== review.reviewId)] }));
        }
      }
    } catch (error) {
      if (alive.current && targetKey(selected.current) === selectionAtStart) {
        setSubmitError(errorMessage(error, 'Unable to submit your review. Your text has been kept; please try again.'));
      }
    } finally {
      inFlight.current = false;
      if (alive.current) setSubmitting(false);
    }
  }, [apiClient]);

  useImperativeHandle(ref, () => ({
    displayPlaceReviews: placeId => display({ kind: 'place', placeId }),
    displayUserReviewHistory: userId => display({ kind: 'user', userId }),
    submitReview,
  }), [display, submitReview]);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (read.target?.kind === 'place') void submitReview(read.target.placeId, rating, text);
  }

  const history = read.target?.kind === 'user';
  return (
    <section className="review-view" aria-labelledby={`${id}-heading`}>
      <h2 id={`${id}-heading`}>{history ? 'Review history' : 'Place reviews'}</h2>
      {read.status === 'idle' && <p>Select a place to see its reviews.</p>}
      {read.status === 'loading' && <p role="status">Loading reviews…</p>}
      {read.status === 'error' && <div role="alert">
        <p>{read.error}</p>
        <button type="button" onClick={() => read.target && void display(read.target)}>Try again</button>
      </div>}
      {read.status === 'ready' && read.reviews.length === 0 &&
        <p>{history ? 'This user has not written any reviews yet.' : 'No reviews yet. Be the first to share your experience.'}</p>}
      {read.status === 'ready' && read.reviews.length > 0 && <ul className="review-list" aria-label={history ? 'User reviews' : 'Place reviews'}>
        {read.reviews.map(review => <li key={review.reviewId}>
          <article>
            <p className="review-rating" aria-label={`${review.rating} out of 5 stars`}>{review.rating} / 5 stars</p>
            <p className="review-text">{review.text}</p>
            <p className="review-meta">{history ? `Place: ${review.placeId}` : `Reviewer: ${review.userId}`} · <time dateTime={review.createdAt}>{review.createdAt.slice(0, 10)}</time></p>
          </article>
        </li>)}
      </ul>}
      {read.target?.kind === 'place' && <form onSubmit={onSubmit} noValidate>
        <h3>Write a review</h3>
        <fieldset disabled={submitting}>
          <legend className="review-sr-only">Your review</legend>
          <label htmlFor={`${id}-rating`}>Rating</label>
          <select id={`${id}-rating`} value={rating} onChange={event => setRating(Number(event.target.value))}>
            <option value={0}>Choose a rating</option>
            {[1, 2, 3, 4, 5].map(value => <option key={value} value={value}>{value} {value === 1 ? 'star' : 'stars'}</option>)}
          </select>
          <label htmlFor={`${id}-text`}>Your experience</label>
          <textarea id={`${id}-text`} value={text} maxLength={2000} rows={5}
            aria-describedby={`${id}-limit`} onChange={event => setText(event.target.value)} />
          <p id={`${id}-limit`} className="review-meta">{text.length} / 2,000 characters</p>
          <button type="submit">{submitting ? 'Submitting…' : 'Submit review'}</button>
        </fieldset>
        {submitError && <p role="alert">{submitError}</p>}
        {confirmation && <p role="status">{confirmation}</p>}
      </form>}
    </section>
  );
});
