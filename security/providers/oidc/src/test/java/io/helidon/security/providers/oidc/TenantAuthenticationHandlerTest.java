/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.Tenant;

import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link TenantAuthenticationHandler}.
 */
public class TenantAuthenticationHandlerTest {

    @Test
    public void testPlainAccessTokenCookieWithEncryptedDefaultFailsAuthentication() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .useCookie(true)
                .useHeader(false)
                .useParam(false)
                .redirect(false)
                .cookieEncryptionPassword("test-password".toCharArray())
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            false);
        String tokenJson = "{\"accessToken\":\"dummy\",\"remotePeer\":\"127.0.0.1\"}";
        String oldPlainCookie = oidcConfig.tokenCookieHandler().cookieName() + "="
                + Base64.getEncoder().encodeToString(tokenJson.getBytes(StandardCharsets.UTF_8));

        AuthenticationResponse response = authenticationHandler.authenticate(DEFAULT_TENANT_ID,
                                                                            requestWithCookies(oldPlainCookie))
                .toCompletableFuture()
                .join();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.statusCode().orElseThrow(), is(401));
        assertThat(response.description().orElseThrow(), is("Invalid access token"));
    }

    @Test
    public void testPlainTenantAndAccessTokenCookiesWithEncryptedDefaultFailAuthentication() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .useCookie(true)
                .useHeader(false)
                .useParam(false)
                .redirect(false)
                .cookieEncryptionPassword("test-password".toCharArray())
                .build();
        OidcProvider provider = OidcProvider.builder()
                .config(Config.create(ConfigSources.create(Map.of("multi-tenant", "true"))))
                .oidcConfig(oidcConfig)
                .build();
        String tokenJson = "{\"accessToken\":\"dummy\",\"remotePeer\":\"127.0.0.1\"}";
        String oldPlainTenantCookie = oidcConfig.tenantCookieHandler().cookieName() + "=tenant-one";
        String oldPlainTokenCookie = oidcConfig.tokenCookieHandler().cookieName() + "="
                + Base64.getEncoder().encodeToString(tokenJson.getBytes(StandardCharsets.UTF_8));
        String cookieHeader = oldPlainTenantCookie + "; " + oldPlainTokenCookie;

        AuthenticationResponse response = provider.authenticate(requestWithCookies(cookieHeader))
                .toCompletableFuture()
                .join();

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
                .targetUri(URI.create("http://localhost:1234/raw%2Fpath?return=https%3A%2F%2Fexample.com%2Ftest"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest),
                   is("/raw%2Fpath?return=https%3A%2F%2Fexample.com%2Ftest"));

        securityEnvironment = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:1234/%2f%2fexample.com/test"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);

        assertThat(authenticationHandler.origUri(providerRequest), is("/index.html"));

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

    private static ProviderRequest requestWithCookies(String cookieHeader) {
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityEnvironment securityEnvironment = SecurityEnvironment.builder()
                .header("Cookie", cookieHeader)
                .targetUri(URI.create("http://localhost:1234/protected"))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);
        when(providerRequest.securityContext()).thenReturn(securityContext);
        when(securityContext.executorService()).thenReturn(ForkJoinPool.commonPool());
        return providerRequest;
    }

}
