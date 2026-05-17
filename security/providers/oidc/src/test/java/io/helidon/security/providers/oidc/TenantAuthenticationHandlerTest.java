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
import io.helidon.security.providers.oidc.common.Tenant;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link TenantAuthenticationHandler}.
 */
class TenantAuthenticationHandlerTest {

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
