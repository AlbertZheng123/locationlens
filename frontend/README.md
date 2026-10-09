# ReviewView

This is the React/TypeScript ReviewView from the project class diagram. The
checkout previously contained only Spring Boot, so this directory introduces
an isolated frontend component, its API client, and a test/build setup. It does
not introduce a separate login flow or change the Java backend.

## Verify

Use Node 22.12+ (or a newer supported Node version) and npm:

```sh
cd frontend
npm ci
npm test
npm run build
```

The build creates an ES module and stylesheet under `dist/`. React is an external
runtime dependency. Tests use Vitest, React Testing Library, and jsdom; they do
not require Keycloak or a database.

## Preview

Run `npm run dev`, then open `http://localhost:5173/examples/reviews.html`.
This isolated example uses clearly labeled sample data in memory and never
calls the backend. It lets you inspect place reviews, user history, and creation.
Sample submissions are lost on reload. Production exports contain no mock data.

## Integrate with the future React application

Keep the API client stable across renders. Obtain the token from the team's
AuthClient; this component does not store tokens, parse login callbacks, or
infer a database user UUID from Keycloak's subject.

```tsx
import { useMemo, useRef } from 'react';
import { createReviewApiClient, ReviewView } from './index';
import type { ReviewViewHandle } from './index';

// Example placed alongside src/index.ts.
interface PlaceReviewsProps {
  placeId: string;
  authClient: { getAccessToken(): string | null };
}
function PlaceReviews({ placeId, authClient }: PlaceReviewsProps) {
  const reviews = useRef<ReviewViewHandle>(null);
  const apiClient = useMemo(() => createReviewApiClient({
    baseUrl: 'http://localhost:8080',
    getAccessToken: () => authClient.getAccessToken(),
  }), [authClient]);

  return <ReviewView ref={reviews} apiClient={apiClient}
    target={{ kind: 'place', placeId }} />;
}
```

The `target` prop responds to place/user selection changes. Use
`{ kind: 'user', userId }` to show user review history. Alternatively, call the
three operations through the component ref:

- `displayPlaceReviews(placeId)`
- `displayUserReviewHistory(userId)`
- `submitReview(placeId, rating, text)`

Operations return `Promise<void>` so callers/tests can await completion. Errors
are shown in the view; rejected requests preserve the form draft. Selecting a
different place or user clears the previous draft. History has no creation form.
No update/delete operations are included in this PR.

## Proposed backend contract — not implemented in this checkout

The diagram specifies operation names and models but no URLs. The adapter uses
these proposed routes, following the existing `/api/v1/**` bearer-token chain:

| Operation | Request | Response |
| --- | --- | --- |
| Place reviews | `GET /api/v1/places/{placeId}/reviews` | `ReviewModel[]` |
| User history | `GET /api/v1/users/{userId}/reviews` | `ReviewModel[]` |
| Submit | `POST /api/v1/places/{placeId}/reviews`, JSON `{ rating, text }` | Created `ReviewModel` |

Every request sends `Authorization: Bearer <current token>`. Cookie credentials
are omitted, and redirect responses are rejected. The existing stateless API
chain does not require CSRF tokens. Existing security currently requires
sign-in for reads as well as writes. Errors support the backend's
`{ error, message }` format and Problem Detail's `detail` field.

`ReviewModel` follows the diagram: `reviewId`, `rating`, `text`, `createdAt`,
`userId`, and `placeId`. UUIDs and ISO-8601 timestamps are serialized as strings.
The backend must resolve the author's local user ID from authenticated identity;
the submission request deliberately contains no `userId`. Since the diagram
provides no author/place display names, the view currently shows their IDs.

The UI validates integer ratings 1–5 and nonblank text up to 2,000 characters
before trimming. These are proposed product rules and must also be enforced
server-side. A `409` response is displayed as an existing-review conflict;
the backend owner still needs to establish the one-review-per-user/place rule.

Until the backend implements these endpoints, use an injected `ReviewApiClient`
for tests/previews. These tests establish frontend behavior, not live backend
integration. This contract is independent of the abandoned earlier JDBC PR.
