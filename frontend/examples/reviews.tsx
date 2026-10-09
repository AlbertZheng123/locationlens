import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ReviewView } from '../src';
import type { ReviewApiClient, ReviewModel, ReviewTarget } from '../src';

// Isolated preview only. Production callers inject the HTTP ReviewApiClient.
const reviews: ReviewModel[] = [{
  reviewId: 'preview-review-1', placeId: 'preview-park', userId: 'preview-user',
  rating: 4, text: 'A peaceful place to walk. Plenty of shade along the paths.',
  createdAt: '2026-10-09T12:00:00Z',
}];
const apiClient: ReviewApiClient = {
  getPlaceReviews: async placeId => reviews.filter(review => review.placeId === placeId),
  getUserReviewHistory: async userId => reviews.filter(review => review.userId === userId),
  submitReview: async (placeId, rating, text) => {
    const review = { reviewId: crypto.randomUUID(), placeId, userId: 'preview-user', rating, text,
      createdAt: new Date().toISOString() };
    reviews.unshift(review);
    return review;
  },
};

function Preview() {
  const [target, setTarget] = useState<ReviewTarget>({ kind: 'place', placeId: 'preview-park' });
  return <main style={{ maxWidth: '42rem', margin: '3rem auto', padding: '0 1.25rem', fontFamily: 'system-ui' }}>
    <p>LocationLens · Component preview · Sample data</p>
    <h1>Reviews</h1>
    <nav aria-label="Preview mode" style={{ display: 'flex', gap: '1rem', marginBottom: '2rem' }}>
      <button onClick={() => setTarget({ kind: 'place', placeId: 'preview-park' })}>Place reviews</button>
      <button onClick={() => setTarget({ kind: 'user', userId: 'preview-user' })}>User history</button>
    </nav>
    <ReviewView apiClient={apiClient} target={target} />
  </main>;
}

createRoot(document.getElementById('root')!).render(<Preview />);
