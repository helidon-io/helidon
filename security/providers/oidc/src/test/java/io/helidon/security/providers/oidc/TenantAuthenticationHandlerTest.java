/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.security.providers.oidc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.SetCookie;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.Security;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy;
import io.helidon.security.providers.oidc.common.Tenant;

import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.COOKIE;
import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.NONE;
import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.PARAM;
import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link TenantAuthenticationHandler}.
 */
class TenantAuthenticationHandlerTest {
    private static final String ATTEMPT_PARAM = "h_ra";

    @Test
    public void testCustomJwtGroupsPath() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();

        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);

        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            true,
                                                                                            "realm_access/roles",
                                                                                            null,
                                                                                            true);
        Instant now = Instant.now();
        Jwt jwt = Jwt.builder()
                .subject("user1-id")
                .preferredUsername("user1")
                .algorithm("none")
                .issueTime(now)
                .expirationTime(now.plus(1, ChronoUnit.HOURS))
                .addPayloadClaim("realm_access",
                                 JsonObject.create(Map.of("roles",
                                                          JsonArray.createStrings(List.of("inventory:read",
                                                                                          "inventory:write")))))
                .build();
        SignedJwt signedJwt = SignedJwt.sign(jwt, Jwk.NONE_JWK);

        Subject subject = authenticationHandler.buildSubject(jwt, signedJwt, null);

        assertThat(subject.grants(Role.class), hasItems(Role.create("inventory:read"), Role.create("inventory:write")));
    }

    @Test
    public void testConfiguredCustomJwtGroupsPathWithSeparator() {
        OidcProvider provider = OidcProvider.create(Config.create().get("security.oidc-custom-groups"));
        Instant now = Instant.now();
        Jwt jwt = Jwt.builder()
                .subject("user1-id")
                .preferredUsername("user1")
                .algorithm("none")
                .issueTime(now)
                .expirationTime(now.plus(1, ChronoUnit.HOURS))
                .addPayloadClaim("realm_access",
                                 JsonObject.create(Map.of("roles",
                                                          JsonString.create("inventory:read,inventory:write"))))
                .build();
        SignedJwt signedJwt = SignedJwt.sign(jwt, Jwk.NONE_JWK);

        AuthenticationResponse authenticationResponse = provider.authenticate(request(signedJwt.tokenContent()));

        assertThat(authenticationResponse.description().orElse("Unexpected authentication response"),
                   authenticationResponse.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
        Subject subject = authenticationResponse.user().orElseThrow();
        assertThat(subject.grants(Role.class), hasItems(Role.create("inventory:read"), Role.create("inventory:write")));
    }

    @Test
    public void testPlainAccessTokenCookieWithEncryptedDefaultFailsAuthentication() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .useCookie(true)
                .useHeader(false)
                .redirect(false)
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig, tenant, false, false);
        String tokenJson = "{\"accessToken\":\"dummy\",\"remotePeer\":\"127.0.0.1\"}";
        String oldPlainCookie = OidcConfig.DEFAULT_COOKIE_NAME + "="
                + Base64.getEncoder().encodeToString(tokenJson.getBytes(StandardCharsets.UTF_8));

        AuthenticationResponse response = authenticationHandler.authenticate(DEFAULT_TENANT_ID,
                                                                             requestWithCookies(oldPlainCookie));

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElseThrow(), is(401));
        assertThat(response.description().orElseThrow(), is("Invalid access token"));
    }

    @Test
    public void testOriginalUri() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();

        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);

        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig, tenant, false, true);
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityEnvironment securityEnvironment = SecurityEnvironment.builder()
                .header(Security.HEADER_ORIG_URI, "/test?someUri=value")
                .targetUri(URI.create("http://localhost:1234/incorrect"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest), is("/test?someUri=value"));

        securityEnvironment = SecurityEnvironment.builder()
                .header(Security.HEADER_ORIG_URI, "/noQuery")
                .targetUri(URI.create("http://localhost:1234/incorrect"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest), is("/noQuery"));

        securityEnvironment = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:1234/raw%2Fpath?return=https%3A%2F%2Fexample.com%2Ftest"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest),
                   is("/raw%2Fpath?return=https%3A%2F%2Fexample.com%2Ftest"));
    }

    @Test
    public void testOriginalUriMissingHeader() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();

        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);

        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig, tenant, false, true);
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityEnvironment securityEnvironment = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:1234/test?someUri=value"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest), is("/test?someUri=value"));

        securityEnvironment = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:1234/noQuery"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest), is("/noQuery"));
    }

    @Test
    public void testOriginalUriIgnoresNonLocalHeader() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();

        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);

        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig, tenant, false, true);
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityEnvironment securityEnvironment = SecurityEnvironment.builder()
                .header(Security.HEADER_ORIG_URI, "https://example.com/test")
                .targetUri(URI.create("http://localhost:1234/test?someUri=value"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest), is("/test?someUri=value"));

        securityEnvironment = SecurityEnvironment.builder()
                .header(Security.HEADER_ORIG_URI, "//example.com/test")
                .targetUri(URI.create("http://localhost:1234/noQuery"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest), is("/noQuery"));

        securityEnvironment = SecurityEnvironment.builder()
                .header(Security.HEADER_ORIG_URI, "/\\example.com/test")
                .targetUri(URI.create("http://localhost:1234/backslash"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest), is("/backslash"));

        securityEnvironment = SecurityEnvironment.builder()
                .header(Security.HEADER_ORIG_URI, "/%2f%2fexample.com/test")
                .targetUri(URI.create("http://localhost:1234/encodedSlash"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest), is("/encodedSlash"));
    }

    @Test
    public void testRedirectAttemptParamStrategy() {
        OidcConfig oidcConfig = oidcConfig(PARAM);
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);

        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(),
                                                         DEFAULT_TENANT_ID,
                                                         "/test?someUri=value"),
                   is(1));
        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(), DEFAULT_TENANT_ID, "/test?someUri=value&"
                + ATTEMPT_PARAM + "=4"), is(4));
    }

    @Test
    public void testRedirectAttemptCookieStrategy() {
        OidcConfig oidcConfig = oidcConfig(COOKIE);
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);
        String state = "/test?someUri=value";
        String otherState = "/other?someUri=value";

        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(), DEFAULT_TENANT_ID, state), is(0));
        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         DEFAULT_TENANT_ID,
                                                                                         state,
                                                                                         4)),
                                                        DEFAULT_TENANT_ID,
                                                        state),
                   is(4));
        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         DEFAULT_TENANT_ID,
                                                                                         otherState,
                                                                                         4)),
                                                        DEFAULT_TENANT_ID,
                                                        state),
                   is(0));
        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         "tenant-a",
                                                                                         state,
                                                                                         4)),
                                                        DEFAULT_TENANT_ID,
                                                        state),
                   is(0));
        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         DEFAULT_TENANT_ID,
                                                                                         state,
                                                                                         4)),
                                                        DEFAULT_TENANT_ID,
                                                        state + "&accessToken=token&id_token=id-token&h_tenant=tenant"),
                   is(0));
    }

    @Test
    public void testRedirectAttemptCookieStrategyWithQueryParamTokenPropagation() {
        OidcConfig oidcConfig = oidcConfig(COOKIE, true);
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);
        String state = "/test?someUri=value";
        String stateWithTokens = state + "&accessToken=token&id_token=id-token&h_tenant=tenant";
        String stateWithMoreTokens = stateWithTokens + "&accessToken=next-token&id_token=next-id-token";
        String stateWithManyParams = "/test?k548985=1&k764588=1&k641847=1&k970313=1&k64254=1&k814904=1"
                + "&k504434=1&k906606=1&k239978=1&k301748=1&k136569=1&k998473=1";
        String stateWithManyParamsAndTokens = stateWithManyParams
                + "&accessToken=token&id_token=id-token&h_tenant=tenant";

        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         DEFAULT_TENANT_ID,
                                                                                         state,
                                                                                         4)),
                                                        DEFAULT_TENANT_ID,
                                                        stateWithTokens),
                   is(4));
        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         DEFAULT_TENANT_ID,
                                                                                         stateWithManyParams,
                                                                                         4)),
                                                        DEFAULT_TENANT_ID,
                                                        stateWithManyParamsAndTokens),
                   is(4));
        assertThat(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, stateWithMoreTokens),
                   is(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, state)));
        assertThat(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, stateWithManyParamsAndTokens),
                   is(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, stateWithManyParams)));
        assertThat(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, state + "&other=value")
                           .equals(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, state)),
                   is(false));
        assertThat(RedirectAttemptCookie.name(oidcConfig, "tenant-a", state)
                           .equals(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, state)),
                   is(false));
    }

    @Test
    public void testRedirectAttemptCookieStrategyWithInvalidCookieValue() {
        OidcConfig oidcConfig = oidcConfig(COOKIE);
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);
        String state = "/test?someUri=value";

        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         DEFAULT_TENANT_ID,
                                                                                         state,
                                                                                         "invalid")),
                                                        DEFAULT_TENANT_ID,
                                                        state),
                   is(oidcConfig.maxRedirects()));
        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         DEFAULT_TENANT_ID,
                                                                                         state,
                                                                                         "999999999999999999999")),
                                                        DEFAULT_TENANT_ID,
                                                        state),
                   is(oidcConfig.maxRedirects()));
        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         DEFAULT_TENANT_ID,
                                                                                         state,
                                                                                         "-1")),
                                                        DEFAULT_TENANT_ID,
                                                        state),
                   is(oidcConfig.maxRedirects()));
        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         DEFAULT_TENANT_ID,
                                                                                         state,
                                                                                         "0")),
                                                        DEFAULT_TENANT_ID,
                                                        state),
                   is(oidcConfig.maxRedirects()));
    }

    @Test
    public void testRedirectAttemptCookieStrategyWithEncodedQueryParamNames() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .redirectAttemptCounterStrategy(COOKIE)
                .useParam(true)
                .paramName("access token")
                .idTokenParamName("id token")
                .paramTenantName("h tenant")
                .build();
        String state = "/test?someUri=value&h_ra=4";
        String stateWithTokens = state + "&access+token=token"
                + "&id+token=id-token"
                + "&h+tenant=tenant";
        String stateWithPercentEncodedName = state + "&%61ccess+token=token"
                + "&id+token=id-token"
                + "&h+tenant=tenant";

        assertThat(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, stateWithTokens),
                   is(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, state)));
        assertThat(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, stateWithPercentEncodedName),
                   is(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, state)));
        assertThat(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, "/test?someUri=value&h_ra=5")
                           .equals(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, state)),
                   is(false));
    }

    @Test
    public void testRedirectAttemptNoneStrategy() {
        OidcConfig oidcConfig = oidcConfig(NONE);
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);

        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(ATTEMPT_PARAM + "=4"),
                                                         DEFAULT_TENANT_ID,
                                                         "/test?someUri=value&" + ATTEMPT_PARAM + "=4"),
                   is(0));
    }

    @Test
    public void testRedirectAttemptNoneStrategyIgnoresMaxRedirects() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .redirectAttemptCounterStrategy(NONE)
                .maxRedirects(0)
                .build();
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);

        AuthenticationResponse response = authenticationHandler.authenticate(DEFAULT_TENANT_ID, requestWithCookies());

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE_FINISH));
    }

    @Test
    public void testSuccessfulAuthenticationClearsCookieCounter() {
        OidcConfig oidcConfig = oidcConfig(COOKIE);
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);
        ProviderRequest providerRequest = requestWithUri(URI.create("http://localhost:1234/test"),
                                                         attemptCookie(oidcConfig, DEFAULT_TENANT_ID, "/test", 1));

        assertThat(authenticationHandler.successCookies(List.of("other=value"), providerRequest, DEFAULT_TENANT_ID),
                   hasItem(startsWith(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, "/test") + "=;")));
    }

    @Test
    public void testSuccessfulAuthenticationDoesNotClearMissingCookieCounter() {
        OidcConfig oidcConfig = oidcConfig(COOKIE);
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);
        ProviderRequest providerRequest = requestWithUri(URI.create("http://localhost:1234/test"),
                                                         attemptCookie(oidcConfig, DEFAULT_TENANT_ID, "/other", 1));
        List<String> cookies = List.of("other=value");

        assertThat(authenticationHandler.successCookies(cookies, providerRequest, DEFAULT_TENANT_ID), is(cookies));
    }

    @Test
    public void testRedirectSetsCookieCounter() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .redirectAttemptCounterStrategy(COOKIE)
                .cookieSecure(true)
                .cookieDomain("example.com")
                .cookiePath("/app")
                .cookieSameSite(SetCookie.SameSite.STRICT)
                .cookieHttpOnly(false)
                .build();
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);
        String state = "/app/test?resource=a";
        ProviderRequest providerRequest = requestWithUri(URI.create("http://localhost:1234" + state),
                                                         attemptCookie(oidcConfig, DEFAULT_TENANT_ID, state, 2));

        AuthenticationResponse response = authenticationHandler.authenticate(DEFAULT_TENANT_ID, providerRequest);
        List<String> cookies = response.responseHeaders().get(HeaderNames.SET_COOKIE.defaultCase());
        String cookieName = RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, state);
        String cookieValue = cookies.stream()
                .filter(it -> it.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow();
        SetCookie cookie = SetCookie.parse(cookieValue);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE_FINISH));
        assertThat(cookie.name(), is(cookieName));
        assertThat(cookie.value(), is("3"));
        assertThat(cookie.secure(), is(true));
        assertThat(cookie.httpOnly(), is(false));
        assertThat(cookie.domain().orElseThrow(), is("example.com"));
        assertThat(cookie.path().orElseThrow(), is("/app"));
        assertThat(cookie.sameSite().orElseThrow(), is(SetCookie.SameSite.STRICT));
    }

    private static OidcConfig oidcConfig(RedirectAttemptCounterStrategy strategy) {
        return oidcConfig(strategy, false);
    }

    private static OidcConfig oidcConfig(RedirectAttemptCounterStrategy strategy, boolean useParam) {
        return OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .redirectAttemptCounterStrategy(strategy)
                .useParam(useParam)
                .build();
    }

    private static TenantAuthenticationHandler authenticationHandler(OidcConfig oidcConfig) {
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        when(tenant.authorizationEndpointUri()).thenReturn("http://localhost:1234/authorize");
        return new TenantAuthenticationHandler(oidcConfig, tenant, false, true);
    }

    private static String attemptCookie(OidcConfig oidcConfig, String tenantId, String state, int attempt) {
        return attemptCookie(oidcConfig, tenantId, state, String.valueOf(attempt));
    }

    private static String attemptCookie(OidcConfig oidcConfig, String tenantId, String state, String attempt) {
        return RedirectAttemptCookie.name(oidcConfig, tenantId, state) + "=" + attempt;
    }

    private static ProviderRequest requestWithCookies(String... cookies) {
        return requestWithUri(URI.create("http://localhost:1234/test"), cookies);
    }

    private static ProviderRequest requestWithUri(URI targetUri, String... cookies) {
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityEnvironment.Builder securityEnvironmentBuilder = SecurityEnvironment.builder()
                .targetUri(targetUri)
                .header("Host", "localhost:1234");
        for (String cookie : cookies) {
            securityEnvironmentBuilder.header("Cookie", cookie);
        }
        when(providerRequest.env()).thenReturn(securityEnvironmentBuilder.build());
        EndpointConfig endpointConfig = mock(EndpointConfig.class);
        when(endpointConfig.securityLevels()).thenReturn(List.of());
        when(providerRequest.endpointConfig()).thenReturn(endpointConfig);
        return providerRequest;
    }

    private ProviderRequest request(String token) {
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityEnvironment securityEnvironment = SecurityEnvironment.builder()
                .header("Authorization", "bearer " + token)
                .targetUri(URI.create("http://localhost:1234/test"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        EndpointConfig endpointConfig = mock(EndpointConfig.class);
        when(endpointConfig.securityLevels()).thenReturn(List.of());
        when(providerRequest.endpointConfig()).thenReturn(endpointConfig);
        return providerRequest;
    }

}
