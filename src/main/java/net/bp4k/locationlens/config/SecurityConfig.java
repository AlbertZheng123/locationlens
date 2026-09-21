package net.bp4k.locationlens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders
    .HttpSecurity;
import org.springframework.security.config.annotation.web.configuration
    .EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

import net.bp4k.locationlens.security.IdpTokenRelaySuccessHandler;

/**
 * Wires up two independent {@link SecurityFilterChain}s so that a
 * single application can serve both a classic, session-based OIDC
 * login (the pre-existing {@code /} and {@code /secured} demo) and a
 * stateless, bearer-token-protected REST API ({@code /api/v1/**})
 * side by side, without either one interfering with the other.
 *
 * <p>Spring Security selects between multiple chains using each
 * chain's {@code securityMatcher} and the numeric order given by
 * {@link Order} (lower runs first); the first chain whose matcher
 * accepts the request is the only one used for it.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Protects every {@code /api/v1/**} endpoint. Because
     * {@link Order} makes this chain run before
     * {@link #defaultSecurityFilterChain}, all such requests are
     * handled here and never fall through to the session-based chain
     * below.
     *
     * <p>This chain is intentionally stateless: API clients identify
     * themselves with a bearer token on every request, so there is no
     * need for (and no security benefit from) a server-side HTTP
     * session or a CSRF token here - CSRF protection exists to stop a
     * browser's ambient session cookie from being ridden by a
     * malicious site, and a bearer token in an explicit
     * {@code Authorization} header is not ambient in that way.
     *
     * <p>Token verification itself is handled entirely by
     * {@code .oauth2ResourceServer(oauth2 -> oauth2.jwt(...))}: given
     * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} in
     * {@code application.properties}, Spring Boot auto-configures a
     * {@code JwtDecoder} that fetches the identity provider's public
     * signing keys (its JWKS) and checks each incoming token's
     * signature, issuer, and expiry - no custom filter or decoder
     * needs to be written for this application.
     *
     * @param http the builder Spring Security supplies for
     *             configuring this chain
     * @return the fully configured chain for {@code /api/v1/**}
     * @throws Exception propagated from the underlying
     *                    {@code HttpSecurity} builder methods
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http)
            throws Exception {
        return http
            .securityMatcher("/api/v1/**")
            .sessionManagement(session -> session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests((auth) -> {
                // Starting a login must be reachable by a caller who
                // is not authenticated yet.
                auth.requestMatchers("/api/v1/login").permitAll();
                // Every other /api/v1/** endpoint requires a valid,
                // already-verified bearer access token.
                auth.anyRequest().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
            .build();
    }

    /**
     * Handles every request not claimed by
     * {@link #apiSecurityFilterChain}: the original {@code /} and
     * {@code /secured} sample endpoints, plus the OIDC client
     * endpoints that {@code oauth2Login()} itself registers
     * ({@code /oauth2/authorization/**} to start a login and
     * {@code /login/oauth2/code/**} for the IDP's callback). This is
     * unchanged from the app's original session-based configuration
     * except for the success handler, which now decides between the
     * classic "redirect back to the page you came from" behavior and
     * returning a JSON access token, depending on how the login was
     * started - see {@link IdpTokenRelaySuccessHandler}.
     *
     * @param http                        the builder Spring Security
     *                                    supplies for configuring
     *                                    this chain
     * @param idpTokenRelaySuccessHandler the shared, Spring-managed
     *                                    success handler described
     *                                    above
     * @return the fully configured chain for all remaining requests
     * @throws Exception propagated from the underlying
     *                    {@code HttpSecurity} builder methods
     */
    @Bean
    @Order(2)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
            IdpTokenRelaySuccessHandler idpTokenRelaySuccessHandler)
            throws Exception {
        return http
            .authorizeHttpRequests((auth) -> {
                auth.requestMatchers("/").permitAll(); // no auth
                auth.anyRequest().authenticated(); // require auth
            })
            .oauth2Login(oauth2 -> oauth2
                    .successHandler(idpTokenRelaySuccessHandler))
            .build();
    }
}
