/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc.common;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Base64Value;
import io.helidon.common.crypto.CryptoException;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link OidcConfig}.
 */
class OidcConfigFromBuilderTest extends OidcConfigAbstractTest {
    private static final String COOKIE_VALUE = "cookieValue";
    private static final String COOKIE_ENCRYPTION_PASSWORD = "test-password";
    private static final byte CURRENT_VERSION = 1;
    private static final byte[] CURRENT_VERSION_HEADER = {CURRENT_VERSION};
    private static final int CURRENT_NUMBER_OF_ITERATIONS = 600_000;
    private static final int LEGACY_NUMBER_OF_ITERATIONS = 10_000;
    private static final String LEGACY_ENCRYPTED_COOKIE =
            "9WmBEiNX4CF9l4lj+1axdgAAAAySayWBmiIG5e2hIYy7ilR2iML6S+qvr2M4U7593tCWI/SjCZsZ2XQ=";

    private OidcConfig oidcConfig;
    private boolean isCommunicationWithProxy = true;
    private String httpHostPort;
    private boolean relativeUris;
    private String cookieEncryptionPasswordValue;

    OidcConfigFromBuilderTest() {
        oidcConfig = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .scopeAudience("http://localhost:7987/test-application")
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .frontendUri("http://something:7001")
                .validateJwtWithJwk(false)
                .oidcMetadataWellKnown(false)
                .tokenEndpointUri(URI.create("http://identity.oracle.com/tokens"))
                .authorizationEndpointUri(URI.create("http://identity.oracle.com/authorization"))
                .introspectEndpointUri(URI.create("http://identity.oracle.com/introspect"))
                .relativeUris(true)
                .fallbackToDefaultTenantEnabled(true)
                .build();
    }

    @Override
    OidcConfig getConfig() {
        return oidcConfig;
    }

    @Test
    void testDefaultValues() {
        OidcConfig config = OidcConfig.builder()
                // The next 3 parameters need to be set or config builder will fail
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                // Set to false so it will not load metadata
                .oidcMetadataWellKnown(false)
                .build();
        assertAll("All values using defaults",
                () -> assertThat("Redirect URI", config.redirectUri(), is(OidcConfig.DEFAULT_REDIRECT_URI)),
                () -> assertThat("Should Redirect", config.shouldRedirect(), is(OidcConfig.DEFAULT_REDIRECT)),
                () -> assertThat("Logout URI", config.logoutUri(), is(OidcConfig.DEFAULT_LOGOUT_URI)),
                () -> assertThat("Use Parameter", config.useParam(), is(OidcConfig.DEFAULT_PARAM_USE)),
                () -> assertThat("Parameter Name", config.paramName(), is(OidcConfig.DEFAULT_PARAM_NAME)),
                () -> assertThat("Relative URIs", config.relativeUris(), is(OidcConfig.DEFAULT_RELATIVE_URIS)),
                () -> assertThat("Use Cookie", config.useCookie(), is(OidcConfig.DEFAULT_COOKIE_USE)),
                () -> assertThat("Use Header", config.useHeader(), is(OidcConfig.DEFAULT_HEADER_USE)),
                () -> assertThat("Legacy state parameter", config.legacyStateParam(), is(false)),
                () -> assertThat("Legacy state fallback", config.legacyStateFallback(), is(false)),
                () -> assertThat("Legacy query parameter handoff", config.legacyQueryParamHandoff(), is(false)),
                () -> assertThat("Base scopes to use", config.baseScopes(), is(OidcConfig.Builder.DEFAULT_BASE_SCOPES)),
                () -> assertThat("Cookie value prefix", config.cookieValuePrefix(), is(OidcConfig.DEFAULT_COOKIE_NAME + "=")),
                () -> assertThat("Cookie name", config.cookieName(), is(OidcConfig.DEFAULT_COOKIE_NAME)),
                () -> assertThat("Realm", config.realm(), is(OidcConfig.Builder.DEFAULT_REALM)),
                () -> assertThat("Redirect Attempt Parameter", config.redirectAttemptParam(), is(OidcConfig.DEFAULT_ATTEMPT_PARAM)),
                () -> assertThat("Max Redirects", config.maxRedirects(), is(OidcConfig.DEFAULT_MAX_REDIRECTS)),
                () -> assertThat("Client Timeout", config.clientTimeout(),
                                 is(Duration.ofSeconds(OidcConfig.Builder.DEFAULT_TIMEOUT_SECONDS))),
                () -> assertThat("Force HTTPS Redirects", config.forceHttpsRedirects(), is(OidcConfig.DEFAULT_FORCE_HTTPS_REDIRECTS)),
                () -> assertThat("Fallback to default tenant", config.fallbackToDefaultTenantEnabled(), is(false)),
                () -> assertThat("Token Refresh Skew", config.tokenRefreshSkew(), is(OidcConfig.DEFAULT_TOKEN_REFRESH_SKEW)),
                // cookie options should be separated by space as defined by the specification
                () -> assertThat("Cookie options", config.cookieOptions(), is("; Path=/; HttpOnly; SameSite=Lax")),
                () -> assertThat("Audience", config.audience(), is("https://identity.oracle.com")),
                () -> assertThat("Parameter name", config.paramName(), is("accessToken")),
                () -> assertThat("Issuer", config.issuer(), nullValue()),
                () -> assertThat("Client without authentication", config.generalClient(), notNullValue()),
                () -> assertThat("Client with authentication", config.appClient(), notNullValue()),
                () -> assertThat("JWK Keys", config.signJwk(), notNullValue())
        );
    }

    @Test
    void testLegacyRollingUpdateFlags() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .legacyStateParam(true)
                .legacyStateFallback(true)
                .legacyQueryParamHandoff(true)
                .build();

        assertAll("Legacy rolling update flags",
                  () -> assertThat("Legacy state parameter", config.legacyStateParam(), is(true)),
                  () -> assertThat("Legacy state fallback", config.legacyStateFallback(), is(true)),
                  () -> assertThat("Legacy query parameter handoff", config.legacyQueryParamHandoff(), is(true)));
    }

    @Test
    void testLegacyRollingUpdateFlagsFromConfig() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .config(Config.create(ConfigSources.create(Map.of(
                        "legacy-state-param", "true",
                        "legacy-state-fallback", "true",
                        "legacy-query-param-handoff", "true"))))
                .build();

        assertAll("Legacy rolling update flags from config",
                  () -> assertThat("Legacy state parameter", config.legacyStateParam(), is(true)),
                  () -> assertThat("Legacy state fallback", config.legacyStateFallback(), is(true)),
                  () -> assertThat("Legacy query parameter handoff", config.legacyQueryParamHandoff(), is(true)));
    }

    @Test
    void testRequestUrisWithProxy() {
        httpHostPort = "";                   // This will be set once the server is up
        isCommunicationWithProxy = true;     // initial request is with a proxy
        // This server will simulate a Proxy on the 1st request and Identity Server on the 2nd request
        WebServer proxyAndIdentityServer = WebServer.builder()
                .host("localhost")
                .routing(Routing.builder()
                        .any((req, res) -> {
                            // Simulate a successful Proxy response
                            if (isCommunicationWithProxy) {
                                // Flip to false so next request will simulate Identity Server  interaction
                                isCommunicationWithProxy = false;
                                res.send();
                            }
                            // Simulate a failed Identity response if relativeURIs=false but the request URI is relative
                            else if (!relativeUris && !req.uri().toASCIIString().startsWith(httpHostPort)) {
                                res.status(500);
                                res.send("URI must be absolute");
                            }
                            // Simulate a failed Identity response if relativeURIs=true but the request URI is absolute
                            else if (relativeUris && req.uri().toASCIIString().startsWith(httpHostPort)) {
                                res.status(500);
                                res.send("URI must be relative");
                            }
                            // Simulate a successful Identity response
                            else {
                                res.send("{}");
                            }
                        }))
                .build();
        proxyAndIdentityServer.start().await(Duration.ofSeconds(10));
        httpHostPort = "http://localhost:" + proxyAndIdentityServer.port();

        // 1st test will simulate relativeUris=false and will fail if URI is relative
        OidcConfig.builder()
                // The next 3 parameters need to be set or config builder will fail
                .identityUri(URI.create(httpHostPort + "/identity"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .proxyProtocol("http")
                .proxyHost("localhost")
                .proxyPort(proxyAndIdentityServer.port())
                .build();

        // 2nd test will simulate relativeUris=true and will fail if URI is absolute
        relativeUris = true;
        OidcConfig.builder()
                // The next 3 parameters need to be set or config builder will fail
                .identityUri(URI.create(httpHostPort + "/identity"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .proxyProtocol("http")
                .proxyHost("localhost")
                .proxyPort(proxyAndIdentityServer.port())
                .relativeUris(relativeUris)
                .build();
        proxyAndIdentityServer.shutdown();
    }

    @Test
    void testCookieEncryptionPasswordFromBuilderConfig() {
        OidcConfig.Builder builder = new TestOidcConfigBuilder();
        for (String passwordValue : Arrays.asList("PasswordString", "", "   ")) {
            builder.config(Config.builder()
                    .sources(ConfigSources.create(Map.of("cookie-encryption-password", passwordValue)))
                    .build()
            );
            assertThat(cookieEncryptionPasswordValue, is(passwordValue));
            // reset the value
            cookieEncryptionPasswordValue = null;
        }
    }

    @Test
    void testLegacyCookieEncryptionFromBuilderConfig() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .logoutEnabled(true)
                .postLogoutUri(URI.create("https://identity.oracle.com/logout"))
                .config(Config.builder()
                                .sources(ConfigSources.create(Map.of("cookie-encryption-enabled", "true",
                                                                     "cookie-encryption-password",
                                                                     COOKIE_ENCRYPTION_PASSWORD,
                                                                     "legacy-cookie-encryption", "true")))
                                .build())
                .build();

        for (OidcCookieHandler cookieHandler : cookieHandlers(config)) {
            String encrypted = cookieValue(cookieHandler.createCookie(COOKIE_VALUE).await().build().toString());

            assertAll("legacy cookie encryption from config for " + cookieHandler.cookieName(),
                      () -> assertThat(legacyCipher()
                                               .decrypt(Base64Value.createFromEncoded(encrypted))
                                               .toDecodedString(),
                                       is(COOKIE_VALUE)),
                      () -> assertThrows(CryptoException.class,
                                         () -> currentCipher().decrypt(Base64Value.createFromEncoded(encrypted))));
        }
    }

    @Test
    void testLegacyCookieFallbackFromBuilderConfig() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .logoutEnabled(true)
                .postLogoutUri(URI.create("https://identity.oracle.com/logout"))
                .config(Config.builder()
                                .sources(ConfigSources.create(Map.of("cookie-encryption-enabled", "true",
                                                                     "cookie-encryption-password",
                                                                     COOKIE_ENCRYPTION_PASSWORD,
                                                                     "legacy-cookie-fallback", "true")))
                                .build())
                .build();

        for (OidcCookieHandler cookieHandler : cookieHandlers(config)) {
            Optional<String> cookie = cookieHandler
                    .findCookie(Map.of("Cookie", List.of(cookieHandler.cookieName() + "=" + LEGACY_ENCRYPTED_COOKIE)))
                    .map(it -> it.await());

            assertThat(cookieHandler.cookieName(), cookie, is(Optional.of(COOKIE_VALUE)));
        }
    }

    @Test
    void testTokenCookieEncryptedByDefault() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .cookieEncryptionPassword(COOKIE_ENCRYPTION_PASSWORD.toCharArray())
                .build();
        OidcCookieHandler cookieHandler = config.tokenCookieHandler();
        String cookieValue = cookieValue(cookieHandler.createCookie(COOKIE_VALUE).await().build().toString());
        String cookieHeader = cookieHandler.cookieName() + "=" + cookieValue;

        assertAll("token cookie encrypted by default",
                  () -> assertThat("Encrypted cookie should not expose the token value",
                                   cookieValue,
                                   not(COOKIE_VALUE)),
                  () -> assertThat(cookieHandler.findCookie(Map.of("Cookie", List.of(cookieHeader)))
                                           .orElseThrow()
                                           .await(),
                                   is(COOKIE_VALUE)));
    }

    @Test
    void testTokenCookieEncryptionCanBeDisabled() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .cookieEncryptionPassword(COOKIE_ENCRYPTION_PASSWORD.toCharArray())
                .cookieEncryptionEnabled(false)
                .build();
        OidcCookieHandler cookieHandler = config.tokenCookieHandler();
        String cookieValue = cookieValue(cookieHandler.createCookie(COOKIE_VALUE).await().build().toString());
        String cookieHeader = cookieHandler.cookieName() + "=" + cookieValue;

        assertAll("token cookie encryption opt-out",
                  () -> assertThat("Unencrypted cookie should preserve existing opt-out behavior",
                                   cookieValue,
                                   is(COOKIE_VALUE)),
                  () -> assertThat(cookieHandler.findCookie(Map.of("Cookie", List.of(cookieHeader)))
                                           .orElseThrow()
                                           .await(),
                                   is(COOKIE_VALUE)));
    }

    @Test
    void testTenantCookieEncryptedByDefault() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .cookieEncryptionPassword(COOKIE_ENCRYPTION_PASSWORD.toCharArray())
                .build();
        OidcCookieHandler cookieHandler = config.tenantCookieHandler();
        String cookieValue = cookieValue(cookieHandler.createCookie(COOKIE_VALUE).await().build().toString());
        String cookieHeader = cookieHandler.cookieName() + "=" + cookieValue;

        assertAll("tenant cookie encrypted by default",
                  () -> assertThat("Encrypted cookie should not expose the tenant value",
                                   cookieValue,
                                   not(COOKIE_VALUE)),
                  () -> assertThat(cookieHandler.findCookie(Map.of("Cookie", List.of(cookieHeader)))
                                           .orElseThrow()
                                           .await(),
                                   is(COOKIE_VALUE)));
    }

    @Test
    void testTenantCookieEncryptionCanBeDisabled() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .cookieEncryptionPassword(COOKIE_ENCRYPTION_PASSWORD.toCharArray())
                .cookieEncryptionEnabledTenantName(false)
                .build();
        OidcCookieHandler cookieHandler = config.tenantCookieHandler();
        String cookieValue = cookieValue(cookieHandler.createCookie(COOKIE_VALUE).await().build().toString());
        String cookieHeader = cookieHandler.cookieName() + "=" + cookieValue;

        assertAll("tenant cookie encryption opt-out",
                  () -> assertThat("Unencrypted cookie should preserve existing opt-out behavior",
                                   cookieValue,
                                   is(COOKIE_VALUE)),
                  () -> assertThat(cookieHandler.findCookie(Map.of("Cookie", List.of(cookieHeader)))
                                           .orElseThrow()
                                           .await(),
                                           is(COOKIE_VALUE)));
    }

    @Test
    void testConfigCookieEncryptionOptOutDisablesTokenAndTenantCookies() {
        OidcConfig config = OidcConfig.builder()
                .config(Config.create(ConfigSources.create(Map.of(
                        "identity-uri", "https://identity.oracle.com",
                        "client-id", "client-id-value",
                        "client-secret", "client-secret-value",
                        "oidc-metadata-well-known", "false",
                        "cookie-encryption-enabled", "false"))))
                .build();
        OidcCookieHandler tokenCookieHandler = config.tokenCookieHandler();
        String tokenCookieValue = cookieValue(tokenCookieHandler.createCookie(COOKIE_VALUE).await().build().toString());
        OidcCookieHandler tenantCookieHandler = config.tenantCookieHandler();
        String tenantCookieValue = cookieValue(tenantCookieHandler.createCookie(COOKIE_VALUE).await().build().toString());

        assertAll("cookie encryption config opt-out",
                  () -> assertThat("Token cookie should be unencrypted",
                                   tokenCookieValue,
                                   is(COOKIE_VALUE)),
                  () -> assertThat("Tenant cookie should be unencrypted",
                                   tenantCookieValue,
                                   is(COOKIE_VALUE)));
    }

    @Test
    void testClientCredentialsSentOnlyToConfiguredTargets() {
        AtomicReference<String> authorization = new AtomicReference<>();
        WebServer server = authCapturingServer(authorization);
        AtomicReference<String> otherAuthorization = new AtomicReference<>();
        WebServer otherServer = authCapturingServer(otherAuthorization);
        String baseUri = "http://127.0.0.1:" + server.port();
        String otherBaseUri = "http://127.0.0.1:" + otherServer.port();

        try {
            OidcConfig config = OidcConfig.builder()
                    .identityUri(URI.create(baseUri + "/identity"))
                    .clientSecret("client-secret")
                    .clientId("client-id")
                    .oidcMetadataWellKnown(false)
                    .validateJwtWithJwk(false)
                    .tokenEndpointUri(URI.create(baseUri + "/tokens.v1"))
                    .authorizationEndpointUri(URI.create(baseUri + "/authorization"))
                    .introspectEndpointUri(URI.create(baseUri + "/introspect.v1"))
                    .serverType("idcs")
                    .build();

            String expectedAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));

            post(config, URI.create(baseUri + "/tokens.v1"));
            assertThat(authorization.get(), is(expectedAuthorization));

            authorization.set(null);
            post(config, URI.create(baseUri + "/introspect.v1"));
            assertThat(authorization.get(), is(expectedAuthorization));

            authorization.set(null);
            post(config, URI.create(baseUri + "/other"));
            assertThat(authorization.get(), nullValue());

            authorization.set(null);
            get(config, URI.create(baseUri + "/tokens.v1"));
            assertThat(authorization.get(), nullValue());

            authorization.set(null);
            post(config, URI.create(baseUri + "/tokens.v1?other=true"));
            assertThat(authorization.get(), nullValue());

            authorization.set(null);
            post(config, URI.create(baseUri + "/introspect.v1?other=true"));
            assertThat(authorization.get(), nullValue());

            post(config, URI.create(otherBaseUri + "/tokens.v1"));
            assertThat(otherAuthorization.get(), nullValue());

            authorization.set(null);
            jaxRsPost(config, URI.create(baseUri + "/tokens.v1"));
            assertThat(authorization.get(), is(expectedAuthorization));

            authorization.set(null);
            jaxRsPost(config, URI.create(baseUri + "/introspect.v1"));
            assertThat(authorization.get(), is(expectedAuthorization));

            authorization.set(null);
            jaxRsPost(config, URI.create(baseUri + "/other"));
            assertThat(authorization.get(), nullValue());

            authorization.set(null);
            jaxRsGet(config, URI.create(baseUri + "/tokens.v1"));
            assertThat(authorization.get(), nullValue());

            authorization.set(null);
            jaxRsPost(config, URI.create(baseUri + "/tokens.v1?other=true"));
            assertThat(authorization.get(), nullValue());

            authorization.set(null);
            jaxRsPost(config, URI.create(baseUri + "/introspect.v1?other=true"));
            assertThat(authorization.get(), nullValue());

            otherAuthorization.set(null);
            jaxRsPost(config, URI.create(otherBaseUri + "/tokens.v1"));
            assertThat(otherAuthorization.get(), nullValue());

            OidcConfig httpsTokenConfig = OidcConfig.builder()
                    .identityUri(URI.create("https://127.0.0.1:" + server.port() + "/identity"))
                    .clientSecret("client-secret")
                    .clientId("client-id")
                    .oidcMetadataWellKnown(false)
                    .tokenEndpointUri(URI.create("https://127.0.0.1:" + server.port() + "/tokens.v1"))
                    .authorizationEndpointUri(URI.create("https://127.0.0.1:" + server.port() + "/authorization"))
                    .serverType("idcs")
                    .build();

            authorization.set(null);
            post(httpsTokenConfig, URI.create(baseUri + "/tokens.v1"));
            assertThat(authorization.get(), nullValue());

            authorization.set(null);
            jaxRsPost(httpsTokenConfig, URI.create(baseUri + "/tokens.v1"));
            assertThat(authorization.get(), nullValue());
        } finally {
            otherServer.shutdown().await(Duration.ofSeconds(10));
            server.shutdown().await(Duration.ofSeconds(10));
        }
    }

    @Test
    void testClientCredentialsCompareRawEndpointPath() {
        AtomicReference<String> authorization = new AtomicReference<>();
        WebServer server = authCapturingServer(authorization);
        String baseUri = "http://127.0.0.1:" + server.port();

        try {
            OidcConfig config = OidcConfig.builder()
                    .identityUri(URI.create(baseUri + "/identity"))
                    .clientSecret("client-secret")
                    .clientId("client-id")
                    .oidcMetadataWellKnown(false)
                    .validateJwtWithJwk(false)
                    .tokenEndpointUri(URI.create(baseUri + "/tokens%2Fv1"))
                    .authorizationEndpointUri(URI.create(baseUri + "/authorization"))
                    .introspectEndpointUri(URI.create(baseUri + "/introspect.v1"))
                    .serverType("idcs")
                    .build();

            String expectedAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));

            post(config, URI.create(baseUri + "/tokens%2Fv1"));
            assertThat(authorization.get(), is(expectedAuthorization));

            authorization.set(null);
            post(config, URI.create(baseUri + "/tokens/v1"));
            assertThat(authorization.get(), nullValue());

            authorization.set(null);
            jaxRsPost(config, URI.create(baseUri + "/tokens%2Fv1"));
            assertThat(authorization.get(), is(expectedAuthorization));

            authorization.set(null);
            jaxRsPost(config, URI.create(baseUri + "/tokens/v1"));
            assertThat(authorization.get(), nullValue());
        } finally {
            server.shutdown().await(Duration.ofSeconds(10));
        }
    }

    private static WebServer authCapturingServer(AtomicReference<String> authorization) {
        WebServer server = WebServer.builder()
                .host("127.0.0.1")
                .routing(Routing.builder()
                                 .any((req, res) -> {
                                     authorization.set(req.headers()
                                                               .first(Http.Header.AUTHORIZATION)
                                                               .orElse(null));
                                     res.headers().contentType(MediaType.APPLICATION_JSON);
                                     res.send("{}");
                                 }))
                .build();
        server.start().await(Duration.ofSeconds(10));
        return server;
    }

    private static String cookieValue(String setCookie) {
        int begin = setCookie.indexOf('=') + 1;
        int end = setCookie.indexOf(';');
        return setCookie.substring(begin, end == -1 ? setCookie.length() : end);
    }

    private static void post(OidcConfig config, URI uri) {
        WebClientResponse response = config.appWebClient()
                .post()
                .uri(uri)
                .submit("")
                .await(10, TimeUnit.SECONDS);
        response.content().as(String.class).await(10, TimeUnit.SECONDS);
    }

    private static void get(OidcConfig config, URI uri) {
        config.appWebClient()
                .get()
                .uri(uri)
                .request(String.class)
                .await(10, TimeUnit.SECONDS);
    }

    private static void jaxRsPost(OidcConfig config, URI uri) {
        try (Response response = config.appClient()
                .target(uri)
                .request()
                .post(Entity.text(""))) {
            response.readEntity(String.class);
        }
    }

    private static List<OidcCookieHandler> cookieHandlers(OidcConfig config) {
        return List.of(config.tokenCookieHandler(),
                       config.idTokenCookieHandler(),
                       config.tenantCookieHandler());
    }

    private static SymmetricCipher currentCipher() {
        return SymmetricCipher.builder()
                .password(COOKIE_ENCRYPTION_PASSWORD.toCharArray())
                .numberOfIterations(CURRENT_NUMBER_OF_ITERATIONS)
                .additionalAuthenticatedData(CURRENT_VERSION_HEADER)
                .build();
    }

    private static SymmetricCipher legacyCipher() {
        return SymmetricCipher.builder()
                .password(COOKIE_ENCRYPTION_PASSWORD.toCharArray())
                .numberOfIterations(LEGACY_NUMBER_OF_ITERATIONS)
                .build();
    }

    private static void jaxRsGet(OidcConfig config, URI uri) {
        try (Response response = config.appClient()
                .target(uri)
                .request()
                .get()) {
            response.readEntity(String.class);
        }
    }

    // Stub the Builder class to be able to retrieve the cookie-encryption-password value
    private class TestOidcConfigBuilder extends OidcConfig.Builder {
        // Stub the method to be able to store the cookie-encryption-password to a variable for later retrieval
        @Override
        public OidcConfig.Builder cookieEncryptionPassword(char[] cookieEncryptionPassword) {
            cookieEncryptionPasswordValue = String.valueOf(cookieEncryptionPassword);
            super.cookieEncryptionPassword(cookieEncryptionPassword);
            return this;
        }
    }
}
