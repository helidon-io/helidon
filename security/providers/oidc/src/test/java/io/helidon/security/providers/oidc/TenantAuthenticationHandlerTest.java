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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import io.helidon.common.http.HashParameters;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.jwt.jwk.JwkOctet;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.Tenant;
import io.helidon.security.providers.oidc.common.TenantConfig;

import org.junit.jupiter.api.Test;

import jakarta.json.Json;

import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    public void testRawQueryParamUsesFirstValueByDefault() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);

        assertThat(authenticationHandler.rawQueryParamAccessToken(requestWithQuery("old", "new"))
                           .orElseThrow(),
                   is("old"));
    }

    @Test
    public void testRawQueryParamSkipsExpiredEncryptedQueryResultByDefault() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);
        String expiredQueryResult = OidcState.createQueryResult("access-token",
                                                                Json.createObjectBuilder().build(),
                                                                oidcConfig,
                                                                Instant.now().getEpochSecond() - 120);

        assertThat(authenticationHandler.rawQueryParamAccessToken(requestWithQuery(expiredQueryResult)).isPresent(),
                   is(false));
    }

    @Test
    public void testRawQueryParamUsesFirstNonEncryptedQueryResultByDefault() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);
        String expiredQueryResult = OidcState.createQueryResult("access-token",
                                                                Json.createObjectBuilder().build(),
                                                                oidcConfig,
                                                                Instant.now().getEpochSecond() - 120);

        assertThat(authenticationHandler.rawQueryParamAccessToken(requestWithQuery(expiredQueryResult, "raw-token"))
                           .orElseThrow(),
                   is("raw-token"));
    }

    @Test
    public void testOversizedQueryResultIsNotTreatedAsRawToken() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);
        String oversizedQueryResult = OidcState.queryResultPrefix() + "x".repeat(17 * 1024);
        ProviderRequest request = requestWithQuery(oversizedQueryResult);

        assertThat(authenticationHandler.queryResultAccessToken(request).isPresent(), is(false));
        assertThat(authenticationHandler.rawQueryParamAccessToken(request).isPresent(), is(false));
    }

    @Test
    public void testQueryResultRequiresMatchingNonceCookieWhenCookiesEnabled() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);
        String nonce = "nonce";
        String queryResult = OidcState.createQueryResult("access-token",
                                                         Json.createObjectBuilder().build(),
                                                         oidcConfig,
                                                         nonce);
        String nonceCookie = OidcState.queryResultNonceCookieName(oidcConfig.tokenCookieHandler().cookieName())
                + "="
                + nonce;

        assertThat(authenticationHandler.queryResultAccessToken(requestWithQueryAndCookies(queryResult, nonceCookie))
                           .orElseThrow(),
                   is("access-token"));
    }

    @Test
    public void testQueryResultRejectsMissingNonceCookieWhenCookiesEnabled() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);
        String queryResult = OidcState.createQueryResult("access-token",
                                                         Json.createObjectBuilder().build(),
                                                         oidcConfig,
                                                         "nonce");

        assertThat(authenticationHandler.queryResultAccessToken(requestWithQuery(queryResult)).isPresent(),
                   is(false));
    }

    @Test
    public void testLegacyHandoffAcceptsNonceBoundQueryResultWhenCookieIsPresent() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .legacyQueryParamHandoff(true)
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);
        String nonce = "nonce";
        String queryResult = OidcState.createQueryResult("access-token",
                                                         Json.createObjectBuilder().build(),
                                                         oidcConfig,
                                                         nonce);
        String nonceCookie = OidcState.queryResultNonceCookieName(oidcConfig.tokenCookieHandler().cookieName())
                + "="
                + nonce;

        assertThat(authenticationHandler.queryResultAccessToken(requestWithQueryAndCookies(queryResult, nonceCookie))
                           .orElseThrow(),
                   is("access-token"));
    }

    @Test
    public void testQueryResultSkipsDecryptWhenNonceCookieIsMissing() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();
        TenantConfig tenantConfig = mock(TenantConfig.class);
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(tenantConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);

        assertThat(authenticationHandler.queryResultAccessToken(requestWithQuery(OidcState.queryResultPrefix() + "cipher"))
                           .isPresent(),
                   is(false));
        verify(tenantConfig, never()).clientSecret();
    }

    @Test
    public void testQueryResultDoesNotReadNonceCookieWithoutQueryResultCandidate() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityEnvironment env = mock(SecurityEnvironment.class);
        when(providerRequest.env()).thenReturn(env);
        when(env.queryParams()).thenReturn(HashParameters.create());

        assertThat(authenticationHandler.queryResultAccessToken(providerRequest).isPresent(), is(false));
        verify(env, never()).headers();
    }

    @Test
    public void testQueryResultRejectsMismatchedNonceCookieWhenCookiesEnabled() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);
        String queryResult = OidcState.createQueryResult("access-token",
                                                         Json.createObjectBuilder().build(),
                                                         oidcConfig,
                                                         "nonce");
        String nonceCookie = OidcState.queryResultNonceCookieName(oidcConfig.tokenCookieHandler().cookieName())
                + "=other";

        assertThat(authenticationHandler.queryResultAccessToken(requestWithQueryAndCookies(queryResult, nonceCookie))
                           .isPresent(),
                   is(false));
    }

    @Test
    public void testExpiredQueryResultDoesNotBlockCookieAuthentication() {
        JwkKeys signingKeys = signingKeys();
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .useHeader(false)
                .useParam(true)
                .useCookie(true)
                .redirect(false)
                .cookieEncryptionPassword("test-password".toCharArray())
                .signJwk(signingKeys)
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        when(tenant.signJwk()).thenReturn(signingKeys);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            false);
        String expiredQueryResult = OidcState.createQueryResult("query-token",
                                                                Json.createObjectBuilder().build(),
                                                                oidcConfig,
                                                                Instant.now().getEpochSecond() - 120);
        String cookieHeader = cookieHeader(oidcConfig.tokenCookieHandler()
                                             .createCookie(validToken(signingKeys))
                                             .await()
                                             .build()
                                             .toString());

        AuthenticationResponse response = authenticationHandler.authenticate(DEFAULT_TENANT_ID,
                                                                            requestWithQueryAndCookies(expiredQueryResult,
                                                                                                       cookieHeader))
                .toCompletableFuture()
                .join();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.user().orElseThrow().principal().getName(), is("user"));
    }

    @Test
    public void testTamperedQueryResultDoesNotBlockCookieAuthentication() {
        JwkKeys signingKeys = signingKeys();
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .useHeader(false)
                .useParam(true)
                .useCookie(true)
                .redirect(false)
                .cookieEncryptionPassword("test-password".toCharArray())
                .signJwk(signingKeys)
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        when(tenant.signJwk()).thenReturn(signingKeys);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            false);
        String queryResult = OidcState.createQueryResult("query-token",
                                                         Json.createObjectBuilder().build(),
                                                         oidcConfig);
        String tamperedQueryResult = queryResult + "x";
        String cookieHeader = cookieHeader(oidcConfig.tokenCookieHandler()
                                             .createCookie(validToken(signingKeys))
                                             .await()
                                             .build()
                                             .toString());

        AuthenticationResponse response = authenticationHandler.authenticate(DEFAULT_TENANT_ID,
                                                                            requestWithQueryAndCookies(tamperedQueryResult,
                                                                                                       cookieHeader))
                .toCompletableFuture()
                .join();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.user().orElseThrow().principal().getName(), is("user"));
    }

    @Test
    public void testDuplicateQueryResultsUseOnlyLastCandidateBeforeCookieAuthentication() {
        JwkKeys signingKeys = signingKeys();
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .useHeader(false)
                .useParam(true)
                .useCookie(true)
                .redirect(false)
                .cookieEncryptionPassword("test-password".toCharArray())
                .signJwk(signingKeys)
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        when(tenant.signJwk()).thenReturn(signingKeys);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            false);
        String nonce = "nonce";
        String queryResult = OidcState.createQueryResult("query-token",
                                                         Json.createObjectBuilder().build(),
                                                         oidcConfig,
                                                         nonce);
        String tamperedQueryResult = queryResult + "x";
        String cookieHeader = cookieHeader(oidcConfig.tokenCookieHandler()
                                             .createCookie(validToken(signingKeys))
                                             .await()
                                             .build()
                                             .toString())
                + "; "
                + OidcState.queryResultNonceCookieName(oidcConfig.tokenCookieHandler().cookieName())
                + "="
                + nonce;

        AuthenticationResponse response = authenticationHandler.authenticate(
                        DEFAULT_TENANT_ID,
                        requestWithQueryAndCookies(List.of(queryResult, tamperedQueryResult), cookieHeader))
                .toCompletableFuture()
                .join();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.user().orElseThrow().principal().getName(), is("user"));
    }

    @Test
    public void testExpiredQueryResultDoesNotBlockCookieAuthenticationWithLegacyHandoff() {
        JwkKeys signingKeys = signingKeys();
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .oidcMetadataWellKnown(false)
                .useHeader(false)
                .useParam(true)
                .useCookie(true)
                .redirect(false)
                .cookieEncryptionPassword("test-password".toCharArray())
                .legacyQueryParamHandoff(true)
                .signJwk(signingKeys)
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        when(tenant.signJwk()).thenReturn(signingKeys);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            false);
        String expiredQueryResult = OidcState.createQueryResult("query-token",
                                                                Json.createObjectBuilder().build(),
                                                                oidcConfig,
                                                                Instant.now().getEpochSecond() - 120);
        String cookieHeader = cookieHeader(oidcConfig.tokenCookieHandler()
                                             .createCookie(validToken(signingKeys))
                                             .await()
                                             .build()
                                             .toString());

        AuthenticationResponse response = authenticationHandler.authenticate(DEFAULT_TENANT_ID,
                                                                            requestWithQueryAndCookies(expiredQueryResult,
                                                                                                       cookieHeader))
                .toCompletableFuture()
                .join();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        assertThat(response.user().orElseThrow().principal().getName(), is("user"));
    }

    @Test
    public void testLegacyRawQueryParamHandoffUsesLastValue() {
        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("test")
                .clientSecret("123")
                .identityUri(URI.create("http://localhost:1234"))
                .legacyQueryParamHandoff(true)
                .build();
        Tenant tenant = mock(Tenant.class);
        when(tenant.tenantConfig()).thenReturn(oidcConfig);
        TenantAuthenticationHandler authenticationHandler = new TenantAuthenticationHandler(oidcConfig,
                                                                                            tenant,
                                                                                            false,
                                                                                            true);

        assertThat(authenticationHandler.rawQueryParamAccessToken(requestWithQuery("old", "new"))
                           .orElseThrow(),
                   is("new"));
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

    private static ProviderRequest requestWithQuery(String... tokens) {
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityEnvironment securityEnvironment = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:1234/protected"))
                .queryParam("accessToken", List.of(tokens))
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);
        return providerRequest;
    }

    private static ProviderRequest requestWithQueryAndCookies(String token, String cookieHeader) {
        return requestWithQueryAndCookies(List.of(token), cookieHeader);
    }

    private static ProviderRequest requestWithQueryAndCookies(List<String> tokens, String cookieHeader) {
        ProviderRequest providerRequest = mock(ProviderRequest.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityEnvironment securityEnvironment = SecurityEnvironment.builder()
                .targetUri(URI.create("http://localhost:1234/protected"))
                .queryParam("accessToken", tokens)
                .header("Cookie", cookieHeader)
                .build();
        when(providerRequest.env()).thenReturn(securityEnvironment);
        when(providerRequest.securityContext()).thenReturn(securityContext);
        when(providerRequest.endpointConfig()).thenReturn(EndpointConfig.builder().build());
        when(securityContext.executorService()).thenReturn(ForkJoinPool.commonPool());
        return providerRequest;
    }

    private static String validToken(JwkKeys signingKeys) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.builder()
                .subject("user")
                .preferredUsername("user")
                .algorithm(JwkOctet.ALG_HS256)
                .keyId("test-key")
                .issueTime(now)
                .expirationTime(now.plus(1, ChronoUnit.HOURS))
                .addAudience("http://localhost:1234")
                .build();

        return SignedJwt.sign(jwt, signingKeys).tokenContent();
    }

    private static JwkKeys signingKeys() {
        return JwkKeys.builder()
                .addKey(JwkOctet.create(Json.createObjectBuilder()
                                           .add(Jwk.PARAM_KEY_TYPE, Jwk.KEY_TYPE_OCT)
                                           .add(Jwk.PARAM_KEY_ID, "test-key")
                                           .add(Jwk.PARAM_ALGORITHM, JwkOctet.ALG_HS256)
                                           .add(JwkOctet.PARAM_OCTET_KEY,
                                                Base64.getUrlEncoder()
                                                        .encodeToString("test-secret".getBytes(StandardCharsets.UTF_8)))
                                           .build()))
                .build();
    }

    private static String cookieHeader(String setCookieHeader) {
        int attributesStart = setCookieHeader.indexOf(';');
        if (attributesStart < 0) {
            return setCookieHeader;
        }
        return setCookieHeader.substring(0, attributesStart);
    }

}
