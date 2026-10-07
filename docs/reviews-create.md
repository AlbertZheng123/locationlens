# Reviews: backend creation (part 1 of 8)

## API contract

`POST /api/places/{placeId}/reviews`

The place ID is the Google Places identifier (1–255 characters). This endpoint
stores the supplied identifier; verification against Google Places belongs to
the place-search integration.

Request:

```json
{"rating": 5, "text": "Great place!"}
```

Rating is required and must be 1–5. Text is required, must not be blank, and is
limited to 2,000 characters before trimming. Leading/trailing whitespace is
removed. The author is always `Principal.getName()` from Spring Security;
clients cannot choose an author. Unknown JSON fields are ignored by the existing
Jackson configuration, including any client-supplied author ID.

A successful request returns `201 Created` with:

```json
{
  "id": "generated-uuid",
  "placeId": "google-place-id",
  "authorId": "authenticated-principal-name",
  "rating": 5,
  "text": "Great place!",
  "createdAt": "2026-10-07T18:00:00Z",
  "updatedAt": "2026-10-07T18:00:00Z"
}
```

Invalid review fields/place IDs return `400`; an existing review by the same user
for the same place returns `409`. Controller validation and conflicts use
Problem Detail responses. Unauthenticated requests are rejected by Spring
Security; requests without a valid CSRF token are rejected with `403` before
reaching the controller. Security-filter error bodies follow Spring defaults.
Read, update, and delete endpoints are reserved for later PRs.

## Database and local setup

Use Java 21 and MySQL 8.0.16 or newer. Create an empty `locationlens` database
and a database user with privileges to create the migration table and reviews
table, create indexes, and read/write application data.

Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in your environment, then run:

```sh
./mvnw spring-boot:run
```

The default URL is
`jdbc:mysql://localhost:3306/locationlens?connectionTimeZone=UTC` and the default
username is `locationlens`. Flyway runs `V1__create_reviews.sql` at startup.
The MySQL table uses a binary collation so opaque place/author IDs are compared
case-sensitively. A database unique constraint prevents duplicate reviews even
when requests arrive simultaneously. No users table or foreign key is introduced
because account persistence has not been implemented in this checkout.

## Authentication integration

This PR retains the existing Spring Security configuration and CSRF protection.
The authentication owner must configure a stable, unique principal name that
matches the team's user identity, including for OAuth2 providers. There are no
new signup/login endpoints, OAuth registrations, or CSRF-token retrieval endpoint.
The frontend must send the authenticated session and CSRF token using the team's
shared authentication flow. OAuth2 identities must not collide across providers.

## Verification

```sh
./mvnw test
```

Integration tests use H2 in MySQL compatibility mode and execute the Flyway
migration. They cover persisted creation, trusted author identity, validation,
unauthenticated requests, CSRF, duplicate prevention, separate authors/places,
and simultaneous submissions. A live MySQL instance is still needed to verify
MySQL-specific collation/table options and deployment configuration.
