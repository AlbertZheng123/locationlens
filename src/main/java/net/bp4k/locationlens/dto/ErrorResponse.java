package net.bp4k.locationlens.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON error body returned when a request cannot be fulfilled for a
 * reason the caller can fix - e.g. an unrecognized {@code target}
 * redirect URI on {@code GET /api/v1/login}.
 *
 * @param error   the HTTP status code, as a string, e.g. {@code "400"}
 * @param message a short, human-readable explanation of what was
 *                wrong with the request
 */
public record ErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("message") String message) {
}
