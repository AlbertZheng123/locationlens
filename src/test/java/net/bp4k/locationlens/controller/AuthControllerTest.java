package net.bp4k.locationlens.controller;

import java.io.IOException;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

import net.bp4k.locationlens.dto.ErrorResponse;
import net.bp4k.locationlens.security.IdpTokenRelaySuccessHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AuthController#login}, covering the
 * {@code ?target=} redirect-allow-list behavior: an unrecognized
 * target is rejected with {@code 400}, an exact allow-list match is
 * accepted and recorded on the session for
 * {@link IdpTokenRelaySuccessHandler} to use later, and a missing
 * target retains the original plain-JSON-response behavior.
 *
 * <p>These are plain unit tests rather than a Spring context test.
 * The project's full application context cannot be loaded in this
 * environment without a real, network-reachable identity provider
 * (see {@code LocationlensApplicationTests}), and none of the
 * behavior exercised here needs a running Spring container - it is
 * exercised by constructing {@link AuthController} directly and
 * calling its handler method with Spring's mock servlet request and
 * response objects.
 */
class AuthControllerTest {

    /** First allow-listed URI, matching the task's Example 1. */
    private static final String ALLOWED_HTTP_TARGET =
            "http://example.com/login-redirect";

    /**
     * Second allow-listed URI, a custom (non-http) scheme such as a
     * native app might register - allow-list matching must not
     * assume an http(s) URL.
     */
    private static final String ALLOWED_CUSTOM_SCHEME_TARGET =
            "example-app://login";

    /** Mirrors the task's Example 1 configuration value. */
    private static final String REDIRECT_URI_CONFIG =
            ALLOWED_HTTP_TARGET + "," + ALLOWED_CUSTOM_SCHEME_TARGET;

    private final AuthController controller = new AuthController(
            REDIRECT_URI_CONFIG, JsonMapper.builder().build());

    @Test
    @DisplayName("a target absent from the allow-list is rejected"
            + " with 400 Bad Request")
    void rejectsUnknownTarget() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login(
                "http://evil.example.com", request, response);

        assertInvalidRedirectUriResponse(response);
    }

    @Test
    @DisplayName("a trailing slash on an otherwise-allowed target is"
            + " rejected, since matching is exact")
    void rejectsTargetWithTrailingSlash() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login(
                ALLOWED_HTTP_TARGET + "/", request, response);

        assertInvalidRedirectUriResponse(response);
    }

    @Test
    @DisplayName("an exact allow-listed http(s) target is accepted"
            + " and remembered on the session")
    void acceptsAllowedHttpTarget() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login(ALLOWED_HTTP_TARGET, request, response);

        assertRedirectedToIdpWithTarget(
                request, response, ALLOWED_HTTP_TARGET);
    }

    @Test
    @DisplayName("an exact allow-listed custom-scheme target is"
            + " accepted and remembered on the session")
    void acceptsAllowedCustomSchemeTarget() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login(
                ALLOWED_CUSTOM_SCHEME_TARGET, request, response);

        assertRedirectedToIdpWithTarget(
                request, response, ALLOWED_CUSTOM_SCHEME_TARGET);
    }

    @Test
    @DisplayName("no target retains the original plain-JSON behavior")
    void noTargetRetainsJsonBehavior() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login(null, request, response);

        assertEquals("/oauth2/authorization/common-idp",
                response.getRedirectedUrl());

        HttpSession session = request.getSession(false);
        assertTrue((Boolean) session.getAttribute(
                IdpTokenRelaySuccessHandler.API_LOGIN_SESSION_ATTRIBUTE));
        assertNull(session.getAttribute(IdpTokenRelaySuccessHandler
                .REDIRECT_TARGET_SESSION_ATTRIBUTE));
    }

    @Test
    @DisplayName("a blank target is treated the same as no target")
    void blankTargetRetainsJsonBehavior() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login("   ", request, response);

        assertEquals("/oauth2/authorization/common-idp",
                response.getRedirectedUrl());
        HttpSession session = request.getSession(false);
        assertNull(session.getAttribute(IdpTokenRelaySuccessHandler
                .REDIRECT_TARGET_SESSION_ATTRIBUTE));
    }

    /**
     * Asserts that {@code response} is the {@code 400} error body
     * described in the task: {@code {"error": "400", "message":
     * "Invalid redirect URI"}}.
     *
     * @param response the response {@link AuthController#login}
     *                 wrote to
     */
    private void assertInvalidRedirectUriResponse(
            MockHttpServletResponse response) throws IOException {
        assertEquals(400, response.getStatus());

        JsonMapper mapper = JsonMapper.builder().build();
        ErrorResponse error = mapper.readValue(
                response.getContentAsByteArray(), ErrorResponse.class);

        assertEquals("400", error.error());
        assertEquals("Invalid redirect URI", error.message());
    }

    /**
     * Asserts that a valid {@code target} led to (1) a redirect to
     * the standard OIDC authorization endpoint, and (2) both session
     * attributes {@link IdpTokenRelaySuccessHandler} relies on being
     * set correctly, ready for the eventual login callback.
     *
     * @param request        the request {@link AuthController#login}
     *                       was called with
     * @param response       the response it wrote to
     * @param expectedTarget the {@code target} value that should now
     *                       be recorded on the session
     */
    private void assertRedirectedToIdpWithTarget(
            MockHttpServletRequest request,
            MockHttpServletResponse response, String expectedTarget) {
        assertEquals("/oauth2/authorization/common-idp",
                response.getRedirectedUrl());

        HttpSession session = request.getSession(false);
        assertTrue((Boolean) session.getAttribute(
                IdpTokenRelaySuccessHandler.API_LOGIN_SESSION_ATTRIBUTE));
        assertEquals(expectedTarget, session.getAttribute(
                IdpTokenRelaySuccessHandler
                        .REDIRECT_TARGET_SESSION_ATTRIBUTE));
    }
}
