package net.bp4k.locationlens.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON response body returned to a client once it completes the OIDC
 * login redirect that was started at {@code GET /api/v1/login}.
 *
 * <p>Note that {@code accessToken} is <strong>not</strong> minted by
 * this application. It is the raw OAuth2 access token issued directly
 * by the OpenID Connect identity provider (IDP) during the
 * authorization code exchange, and this application merely relays it
 * back to the caller (a pattern often called "token relay"). Because
 * this app never signs its own tokens, there is no local signing
 * secret to generate, store, or rotate - trust is anchored entirely
 * in the IDP's published signing keys (its JWKS).
 *
 * @param accessToken the bearer token issued by the IDP; callers must
 *                     send this value back as
 *                     {@code Authorization: Bearer <accessToken>} on
 *                     subsequent requests to authenticated endpoints
 *                     such as {@code GET /api/v1/me}
 * @param tokenType    the token scheme identifier, always the literal
 *                     string {@code "Bearer"} per RFC 6750
 * @param expiresIn    how many whole seconds remain before
 *                     {@code accessToken} expires, measured from the
 *                     moment this response was constructed
 */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn) {
}
