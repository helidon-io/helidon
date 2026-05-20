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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
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

        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(), DEFAULT_TENANT_ID, state), is(1));
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
                   is(1));
        assertThat(authenticationHandler.redirectAttempt(requestWithCookies(attemptCookie(oidcConfig,
                                                                                         "tenant-a",
                                                                                         state,
                                                                                         4)),
                                                        DEFAULT_TENANT_ID,
                                                        state),
                   is(1));
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
    public void testSuccessfulAuthenticationClearsCookieCounter() {
        OidcConfig oidcConfig = oidcConfig(COOKIE);
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);
        ProviderRequest providerRequest = requestWithCookies();

        assertThat(authenticationHandler.successCookies(List.of("other=value"), providerRequest, DEFAULT_TENANT_ID),
                   hasItem(startsWith(RedirectAttemptCookie.name(oidcConfig, DEFAULT_TENANT_ID, "/test") + "=;")));
    }

    private static OidcConfig oidcConfig(RedirectAttemptCounterStrategy strategy) {
        return OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .redirectAttemptCounterStrategy(strategy)
                .build();
    }

    private static TenantAuthenticationHandler authenticationHandler(OidcConfig oidcConfig) {
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        return new TenantAuthenticationHandler(oidcConfig, tenant, false, true);
    }

    private static String attemptCookie(OidcConfig oidcConfig, String tenantId, String state, int attempt) {
        return RedirectAttemptCookie.name(oidcConfig, tenantId, state) + "=" + attempt;
    }

    private static ProviderRequest requestWithCookies(String... cookies) {
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityEnvironment.Builder securityEnvironmentBuilder = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:1234/test"));
        for (String cookie : cookies) {
            securityEnvironmentBuilder.header("Cookie", cookie);
        }
        when(providerRequest.env()).thenReturn(securityEnvironmentBuilder.build());
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
