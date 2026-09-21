package net.bp4k.locationlens.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON response body returned by {@code GET /api/v1/me}.
 *
 * <p>Every field here is read directly from claims embedded in the
 * caller's already-validated bearer access token - see
 * {@link net.bp4k.locationlens.controller.AuthController#me}. No
 * further network call back to the identity provider is made to
 * answer this endpoint.
 *
 * @param firstName the caller's given name, sourced from the OIDC
 *                  standard {@code given_name} claim
 * @param lastName  the caller's family name, sourced from the OIDC
 *                  standard {@code family_name} claim
 * @param email     the caller's email address, sourced from the OIDC
 *                  standard {@code email} claim
 */
public record MeResponse(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("email") String email) {
}
