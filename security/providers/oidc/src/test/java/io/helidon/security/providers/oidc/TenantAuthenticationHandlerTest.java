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
import java.util.List;

import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy;
import io.helidon.security.providers.oidc.common.Tenant;

import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.COOKIE;
import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.NONE;
import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.PARAM;
import static org.hamcrest.CoreMatchers.hasItem;
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

        assertThat(authenticationHandler.redirectAttempt(request(), "/test?someUri=value"), is(1));
        assertThat(authenticationHandler.redirectAttempt(request(), "/test?someUri=value&"
                + ATTEMPT_PARAM + "=4"), is(4));
    }

    @Test
    public void testRedirectAttemptCookieStrategy() {
        OidcConfig oidcConfig = oidcConfig(COOKIE);
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);

        assertThat(authenticationHandler.redirectAttempt(request(), "/test?someUri=value"), is(1));
        assertThat(authenticationHandler.redirectAttempt(request(ATTEMPT_PARAM + "=4"), "/test?someUri=value"),
                   is(4));
    }

    @Test
    public void testRedirectAttemptNoneStrategy() {
        OidcConfig oidcConfig = oidcConfig(NONE);
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig);

        assertThat(authenticationHandler.redirectAttempt(request(ATTEMPT_PARAM + "=4"),
                                                         "/test?someUri=value&" + ATTEMPT_PARAM + "=4"),
                   is(0));
    }

    @Test
    public void testSuccessfulAuthenticationClearsCookieCounter() {
        TenantAuthenticationHandler authenticationHandler = authenticationHandler(oidcConfig(COOKIE));

        assertThat(authenticationHandler.successCookies(List.of("other=value")),
                   hasItem(startsWith(ATTEMPT_PARAM + "=;")));
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

    private static ProviderRequest request(String... cookies) {
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityEnvironment.Builder securityEnvironmentBuilder = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:1234/test"));
        for (String cookie : cookies) {
            securityEnvironmentBuilder.header("Cookie", cookie);
        }
        when(providerRequest.env()).thenReturn(securityEnvironmentBuilder.build());
        return providerRequest;
    }

}
