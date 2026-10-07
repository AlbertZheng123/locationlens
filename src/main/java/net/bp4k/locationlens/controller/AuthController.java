package net.bp4k.locationlens.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.ObjectMapper;

import net.bp4k.locationlens.dto.ErrorResponse;
import net.bp4k.locationlens.dto.MeResponse;
import net.bp4k.locationlens.security.IdpTokenRelaySuccessHandler;

/**
 * Exposes the two REST endpoints this application adds on top of the
 * pre-existing session-based OIDC demo ({@code /} and
 * {@code /secured}, see {@link SampleController}):
 *
 * <ul>
 *   <li>{@code GET /api/v1/login} - starts the OIDC login and, once
 *       it completes, returns the identity provider's own access
 *       token as JSON.</li>
 *   <li>{@code GET /api/v1/me} - returns the caller's profile,
 *       reading it straight out of the bearer access token that was
 *       validated by Spring Security's OAuth2 resource server
 *       support before this method is ever invoked.</li>
 * </ul>
 *
 * <p>Both paths fall under {@code /api/v1/**}, which is matched by
 * the stateless {@code apiSecurityFilterChain} defined in
 * {@code SecurityConfig} rather than the session-based chain used by
 * {@link SampleController}.
 */
@RestController
public class AuthController {

    /**
     * Registration id, from {@code application.properties}
     * ({@code spring.security.oauth2.client.registration.common-idp
     * .*}), of the single OIDC provider this app is configured
     * against. Spring Security exposes a login-triggering endpoint
     * for it automatically at {@code /oauth2/authorization
     * /{registrationId}} - we only need to redirect the browser
     * there, not reimplement the authorization request ourselves.
     */
    private static final String IDP_REGISTRATION_ID = "common-idp";

    /**
     * The set of URIs {@code target} is allowed to name, parsed once
     * at startup from the comma-separated
     * {@code net.bp4k.locationlens.redirect-uri} application
     * property. Matching is exact (character-for-character): this is
     * a security control against open-redirect attacks, so a near
     * miss - a trailing slash, a different scheme, a different host -
     * is deliberately rejected rather than "helpfully" normalized.
     */
    private final Set<String> allowedRedirectUris;

    /** Serializes {@link ErrorResponse} to the JSON response body. */
    private final ObjectMapper objectMapper;

    /**
     * Creates the controller.
     *
     * @param redirectUriConfig the raw, comma-separated
     *                          {@code net.bp4k.locationlens
     *                          .redirect-uri} property value; blank
     *                          or absent means no {@code target} will
     *                          ever be accepted
     * @param objectMapper      the application's auto-configured
     *                          Jackson mapper, reused here instead of
     *                          constructing a new one
     */
    public AuthController(
            @Value("${net.bp4k.locationlens.redirect-uri:}")
            String redirectUriConfig,
            ObjectMapper objectMapper) {
        this.allowedRedirectUris = parseAllowedRedirectUris(redirectUriConfig);
        this.objectMapper = objectMapper;
    }

    /**
     * Splits the comma-separated configuration value into a set of
     * trimmed, non-empty URIs.
     *
     * @param redirectUriConfig the raw property value
     * @return the parsed set, empty if the input is blank
     */
    private static Set<String> parseAllowedRedirectUris(
            String redirectUriConfig) {
        if (redirectUriConfig == null || redirectUriConfig.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(redirectUriConfig.split(","))
                .map(String::trim)
                .filter(uri -> !uri.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Begins the OIDC login flow and arranges for the eventual result
     * to reach the caller automatically, without a human copying a
     * token by hand.
     *
     * <p>The actual redirect-to-IDP, callback handling, and code
     * exchange are all performed by Spring Security's own
     * {@code oauth2Login()} filters; this method's own job is to (1)
     * validate the optional {@code target} query parameter against
     * {@link #allowedRedirectUris}, (2) record the outcome in the
     * session so {@link IdpTokenRelaySuccessHandler} knows how to
     * respond once the login finishes, and (3) send the browser to
     * the standard authorization endpoint Spring Security already
     * exposes.
     *
     * <p>Three outcomes are possible:
     *
     * <ul>
     *   <li>{@code target} is missing or blank: unchanged from
     *       before - the eventual response is the raw JSON access
     *       token, suited to manual testing.</li>
     *   <li>{@code target} exactly matches one of
     *       {@link #allowedRedirectUris}: the browser will eventually
     *       be redirected there with the token appended as query
     *       parameters (see {@link IdpTokenRelaySuccessHandler}), a
     *       real frontend's callback route can consume automatically.
     *       </li>
     *   <li>{@code target} does not match any allowed URI: rejected
     *       immediately, before any redirect to the IDP happens, with
     *       {@code 400 Bad Request}. Without this check, this
     *       endpoint would be an open redirect - it would hand a
     *       genuine access token to any {@code target} URL the caller
     *       named, including an attacker-controlled one.</li>
     * </ul>
     *
     * @param target   the caller-requested redirect target, or
     *                 {@code null}/blank to request the plain JSON
     *                 response instead
     * @param request  used to obtain/create the HTTP session that
     *                 carries the login's outcome across the redirect
     *                 to the IDP and back
     * @param response used to issue the redirect to the IDP, or to
     *                 write the {@code 400} error body
     * @throws IOException if the redirect or error body cannot be
     *                     written
     */
    @GetMapping("/api/v1/login")
    public void login(
            @RequestParam(name = "target", required = false) String target,
            HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        boolean targetRequested = target != null && !target.isBlank();

        if (targetRequested && !allowedRedirectUris.contains(target)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    new ErrorResponse("400", "Invalid redirect URI"));
            return;
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(
                IdpTokenRelaySuccessHandler.API_LOGIN_SESSION_ATTRIBUTE,
                Boolean.TRUE);
        if (targetRequested) {
            session.setAttribute(
                    IdpTokenRelaySuccessHandler
                            .REDIRECT_TARGET_SESSION_ATTRIBUTE,
                    target);
        }

        response.sendRedirect(
                "/oauth2/authorization/" + IDP_REGISTRATION_ID);
    }

    /**
     * Returns the authenticated caller's profile.
     *
     * <p>{@code jwt} is resolved by Spring Security's standard
     * {@code @AuthenticationPrincipal} support: by the time this
     * method runs, the {@code apiSecurityFilterChain}'s
     * {@code oauth2ResourceServer(oauth2 -> oauth2.jwt(...))}
     * configuration has already verified the caller's
     * {@code Authorization: Bearer <token>} header against the IDP's
     * published signing keys (its JWKS) and rejected the request with
     * {@code 401 Unauthorized} if it was missing, expired, or
     * otherwise invalid - this method only ever sees a token that is
     * already known to be genuine.
     *
     * @param jwt the caller's validated access token
     * @return the caller's first name, last name, and email, read
     *         directly from the token's standard OIDC claims
     */
    @GetMapping("/api/v1/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("email"));
    }
}
