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

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Base64Value;
import io.helidon.common.Errors;
import io.helidon.common.configurable.Resource;
import io.helidon.common.crypto.CryptoException;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.oidc.common.BaseBuilder.DEFAULT_BASE_SCOPES;
import static io.helidon.security.providers.oidc.common.BaseBuilder.DEFAULT_TIMEOUT_SECONDS;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_ATTEMPT_PARAM;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_COOKIE_NAME;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_COOKIE_USE;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_FORCE_HTTPS_REDIRECTS;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_HEADER_USE;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_LOGOUT_URI;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_MAX_REDIRECTS;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_PARAM_NAME;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_PARAM_USE;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_REDIRECT;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_REDIRECT_URI;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_TOKEN_REFRESH_SKEW;
import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.COOKIE;
import static io.helidon.security.providers.oidc.common.RedirectAttemptCounterStrategy.PARAM;
import static org.hamcrest.CoreMatchers.is;
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

    private final OidcConfig oidcConfig;

    OidcConfigFromBuilderTest() {
        oidcConfig = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .scopeAudience("https://something:7987/test-application")
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .frontendUri("https://something:7001")
                .validateJwtWithJwk(false)
                .oidcMetadataWellKnown(false)
                .tokenEndpointUri(URI.create("https://identity.oracle.com/tokens"))
                .authorizationEndpointUri(URI.create("https://identity.oracle.com/authorization"))
                .introspectEndpointUri(URI.create("https://identity.oracle.com/introspect"))
                .webclient(it -> it.relativeUris(true))
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
        OidcCookieHandler tokenCookieHandler = config.tokenCookieHandler();
        assertAll("All values using defaults",
                () -> assertThat("Redirect URI", config.redirectUri(), is(DEFAULT_REDIRECT_URI)),
                () -> assertThat("Should Redirect", config.shouldRedirect(), is(DEFAULT_REDIRECT)),
                () -> assertThat("Logout URI", config.logoutUri(), is(DEFAULT_LOGOUT_URI)),
                () -> assertThat("Use Parameter", config.useParam(), is(DEFAULT_PARAM_USE)),
                () -> assertThat("Parameter Name", config.paramName(), is(DEFAULT_PARAM_NAME)),
                () -> assertThat("Use Cookie", config.useCookie(), is(DEFAULT_COOKIE_USE)),
                () -> assertThat("Use Header", config.useHeader(), is(DEFAULT_HEADER_USE)),
                () -> assertThat("Base scopes to use", config.baseScopes(), is(DEFAULT_BASE_SCOPES)),
                () -> assertThat("Cookie value prefix", tokenCookieHandler.cookieValuePrefix(), is(DEFAULT_COOKIE_NAME + "=")),
                () -> assertThat("Cookie name", tokenCookieHandler.cookieName(), is(DEFAULT_COOKIE_NAME)),
                () -> assertThat("Realm", config.realm(), is(OidcConfig.Builder.DEFAULT_REALM)),
                () -> assertThat("Redirect Attempt Parameter", config.redirectAttemptParam(), is(DEFAULT_ATTEMPT_PARAM)),
                () -> assertThat("Redirect Attempt Counter Strategy",
                                 config.redirectAttemptCounterStrategy(), is(PARAM)),
                () -> assertThat("Max Redirects", config.maxRedirects(), is(DEFAULT_MAX_REDIRECTS)),
                () -> assertThat("Client Timeout", config.clientTimeout(), is(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))),
                () -> assertThat("Force HTTPS Redirects", config.forceHttpsRedirects(), is(DEFAULT_FORCE_HTTPS_REDIRECTS)),
                () -> assertThat("Token Refresh Skew", config.tokenRefreshSkew(), is(DEFAULT_TOKEN_REFRESH_SKEW)),
                // cookie options should be separated by space as defined by the specification
                () -> assertThat("Cookie options", tokenCookieHandler.createCookieOptions(), is("; Path=/; HttpOnly; SameSite=Lax")),
                () -> assertThat("Audience", config.audience(), is("https://identity.oracle.com")),
                () -> assertThat("Parameter name", config.paramName(), is("accessToken")),
                () -> assertThat("Issuer", config.issuer(), nullValue()),
                () -> assertThat("Client without authentication", config.generalWebClient(), notNullValue()),
                () -> assertThat("Client with authentication", config.appWebClient(), notNullValue()),
                () -> assertThat("JWK Keys", config.signJwk(), notNullValue()));
    }

    @Test
    void testRedirectAttemptCounterStrategyFromBuilder() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .redirectAttemptCounterStrategy(COOKIE)
                .build();

        assertThat(config.redirectAttemptCounterStrategy(), is(COOKIE));
    }

    @Test
    void testCookieStrategyRejectsInvalidRedirectAttemptCookiePrefix() {
        assertThrows(Errors.ErrorMessagesException.class,
                     () -> OidcConfig.builder()
                             .identityUri(URI.create("https://identity.oracle.com"))
                             .clientId("client-id-value")
                             .clientSecret("client-secret-value")
                             .oidcMetadataWellKnown(false)
                             .redirectAttemptParam("foo[]")
                             .redirectAttemptCounterStrategy(COOKIE)
                             .build());
    }

    @Test
    void testParamStrategyAllowsQueryStyleRedirectAttemptParam() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .redirectAttemptParam("foo[]")
                .redirectAttemptCounterStrategy(PARAM)
                .build();

        assertThat(config.redirectAttemptParam(), is("foo[]"));
    }

    @Test
    void testRequestUrisWithProxy() {
        boolean[] isProxy = new boolean[]{true};
        boolean[] relativeUris = new boolean[]{false};

        // This server will simulate a Proxy on the 1st request
        // and Identity Server on the 2nd request
        WebServer server = WebServer.builder()
                .host("localhost")
                .routing(routing -> routing
                        .any((req, res) -> {
                            // Simulate a successful Proxy response
                            if (isProxy[0]) {
                                // Flip to false so next request will simulate Identity Server  interaction
                                isProxy[0] = false;
                                res.send();
                            } else {
                                String reqUri = req.requestedUri().toUri().toASCIIString();
                                if (!relativeUris[0] && !reqUri.startsWith("http://localhost")) {
                                    // Simulate a failed Identity response if relativeURIs=false but the request URI is relative
                                    res.status(Status.INTERNAL_SERVER_ERROR_500);
                                    res.send("URI must be absolute");
                                } else if (relativeUris[0] && reqUri.startsWith("http://localhost")) {
                                    // Simulate a failed Identity response if relativeURIs=true but the request URI is absolute
                                    res.status(Status.INTERNAL_SERVER_ERROR_500);
                                    res.send("URI must be relative");
                                } else {
                                    // Simulate a successful Identity response
                                    res.send("{}");
                                }
                            }
                        }))
                .build()
                .start();

        String baseUri = "http://localhost:" + server.port();

        // 1st test will simulate relativeUris=false and will fail if URI is relative
        OidcConfig.builder()
                // The next 3 parameters need to be set or config builder will fail
                .identityUri(URI.create(baseUri + "/identity"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .webclient(it -> it.proxy(Proxy.builder()
                                               .type(Proxy.ProxyType.HTTP)
                                               .host("localhost")
                                               .port(server.port())
                                               .build()))
                .build();

        // 2nd test will simulate relativeUris=true and will fail if URI is absolute
        relativeUris[0] = true;
        OidcConfig.builder()
                // The next 3 parameters need to be set or config builder will fail
                .identityUri(URI.create(baseUri + "/identity"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .webclient(it -> it.proxy(Proxy.builder()
                                               .type(Proxy.ProxyType.HTTP)
                                               .host("localhost")
                                               .port(server.port())
                                               .build())
                        .relativeUris(relativeUris[0]))
                .build();
        server.stop();
    }

    @Test
    void testCookieEncryptionPasswordFromBuilderConfig() {
        String[] cookieEncryptionPasswordValue = new String[1];
        OidcConfig.Builder builder = new TestOidcConfigBuilder(cookieEncryptionPasswordValue);
        for (String passwordValue : Arrays.asList("PasswordString", "", "   ")) {
            builder.config(Config.builder()
                    .sources(ConfigSources.create(Map.of("cookie-encryption-password", passwordValue)))
                    .build());
            assertThat(cookieEncryptionPasswordValue[0], is(passwordValue));
            // reset the value
            cookieEncryptionPasswordValue[0] = null;
        }
    }

    @Test
    void testLegacyCookieEncryptionFromBuilderConfig() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .oidcMetadataWellKnown(false)
                .config(Config.builder()
                                .sources(ConfigSources.create(Map.of("cookie-encryption-enabled", "true",
                                                                     "cookie-encryption-password",
                                                                     COOKIE_ENCRYPTION_PASSWORD,
                                                                     "legacy-cookie-encryption", "true")))
                                .build())
                .build();

        for (OidcCookieHandler cookieHandler : cookieHandlers(config)) {
            String encrypted = cookieHandler.createCookie(COOKIE_VALUE).build().value();

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
                .config(Config.builder()
                                .sources(ConfigSources.create(Map.of("cookie-encryption-enabled", "true",
                                                                     "cookie-encryption-password",
                                                                     COOKIE_ENCRYPTION_PASSWORD,
                                                                     "legacy-cookie-fallback", "true")))
                                .build())
                .build();

        for (OidcCookieHandler cookieHandler : cookieHandlers(config)) {
            Optional<String> cookie = cookieHandler
                    .findCookie(Map.of("Cookie", List.of(cookieHandler.cookieName() + "=" + LEGACY_ENCRYPTED_COOKIE)));

            assertThat(cookieHandler.cookieName(), cookie, is(Optional.of(COOKIE_VALUE)));
        }
    }

    @Test
    void testOptionalAudience() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("http://localhost/identity"))
                .clientSecret("top-secret")
                .clientId("client-id")
                .optionalAudience(true)
                .build();
        String audience = config.audience();
        assertThat(audience, nullValue());
    }

    @Test
    void testCheckAudience() {
        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("http://localhost/identity"))
                .clientSecret("top-secret")
                .clientId("client-id")
                .checkAudience(false)
                .build();
        assertThat(config.checkAudience(), is(false));
    }

    @Test
    void testClientCredentialsSentOnlyToConfiguredHost() {
        AtomicReference<String> expectedHostAuthorization = new AtomicReference<>();
        AtomicReference<String> introspectHostAuthorization = new AtomicReference<>();
        AtomicReference<String> otherHostAuthorization = new AtomicReference<>();
        WebServer expectedServer = null;
        WebServer introspectServer = null;
        WebServer otherServer = null;

        try {
            expectedServer = authCapturingServer(expectedHostAuthorization);
            introspectServer = authCapturingServer(introspectHostAuthorization);
            otherServer = authCapturingServer(otherHostAuthorization);

            String expectedBaseUri = "http://identity.example.test:" + expectedServer.port();
            String introspectBaseUri = "http://introspect.example.test:" + introspectServer.port();
            String otherBaseUri = "http://other.example.test:" + otherServer.port();

            OidcConfig config = OidcConfig.builder()
                    .identityUri(URI.create(expectedBaseUri + "/identity"))
                    .clientSecret("client-secret")
                    .clientId("client-id")
                    .oidcMetadataWellKnown(false)
                    .validateJwtWithJwk(false)
                    .tokenEndpointUri(URI.create(expectedBaseUri + "/tokens.v1"))
                    .authorizationEndpointUri(URI.create(expectedBaseUri + "/authorization"))
                    .introspectEndpointUri(URI.create(introspectBaseUri + "/introspect.v1"))
                    .serverType("idcs")
                    .webclient(it -> it.dnsResolver((hostname, dnsAddressLookup) -> InetAddress.getLoopbackAddress()))
                    .build();

            post(config, URI.create(expectedBaseUri + "/tokens.v1"));
            post(config, URI.create(introspectBaseUri + "/introspect.v1"));
            post(config, URI.create(otherBaseUri + "/tokens"));

            String expectedAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));

            assertThat(expectedHostAuthorization.get(), is(expectedAuthorization));
            assertThat(introspectHostAuthorization.get(), is(expectedAuthorization));
            assertThat(otherHostAuthorization.get(), nullValue());

            expectedHostAuthorization.set(null);
            introspectHostAuthorization.set(null);

            post(config, URI.create(expectedBaseUri + "/tokensXv1"));
            post(config, URI.create(expectedBaseUri + "/introspect.v1"));
            post(config, URI.create(expectedBaseUri + "/other"));
            post(config, URI.create(introspectBaseUri + "/introspectXv1"));
            post(config, URI.create(introspectBaseUri + "/tokens"));

            assertThat(expectedHostAuthorization.get(), nullValue());
            assertThat(introspectHostAuthorization.get(), nullValue());

            OidcConfig httpsTokenConfig = OidcConfig.builder()
                    .identityUri(URI.create("https://identity.example.test:" + expectedServer.port() + "/identity"))
                    .clientSecret("client-secret")
                    .clientId("client-id")
                    .oidcMetadataWellKnown(false)
                    .tokenEndpointUri(URI.create("https://identity.example.test:" + expectedServer.port() + "/tokens"))
                    .authorizationEndpointUri(URI.create("https://identity.example.test:" + expectedServer.port()
                                                                 + "/authorization"))
                    .serverType("idcs")
                    .webclient(it -> it.dnsResolver((hostname, dnsAddressLookup) -> InetAddress.getLoopbackAddress()))
                    .build();

            expectedHostAuthorization.set(null);

            post(httpsTokenConfig, URI.create(expectedBaseUri + "/tokens"));

            assertThat(expectedHostAuthorization.get(), nullValue());
        } finally {
            stop(expectedServer);
            stop(introspectServer);
            stop(otherServer);
        }
    }

    @Test
    void testClientCredentialsSentToMetadataIntrospectionHost() {
        AtomicReference<String> tokenHostAuthorization = new AtomicReference<>();
        AtomicReference<String> introspectHostAuthorization = new AtomicReference<>();
        AtomicReference<String> otherHostAuthorization = new AtomicReference<>();
        JsonObject[] metadataHolder = new JsonObject[1];
        WebServer tokenServer = null;
        WebServer introspectServer = null;
        WebServer otherServer = null;
        WebServer metadataServer = null;

        try {
            tokenServer = authCapturingServer(tokenHostAuthorization);
            introspectServer = authCapturingServer(introspectHostAuthorization);
            otherServer = authCapturingServer(otherHostAuthorization);
            metadataServer = WebServer.builder()
                    .host(InetAddress.getLoopbackAddress().getHostAddress())
                    .routing(routing -> routing
                            .get("/.well-known/openid-configuration", (req, res) -> res.send(metadataHolder[0])))
                    .build()
                    .start();

            String metadataBaseUri = "http://metadata.example.test:" + metadataServer.port();
            String tokenBaseUri = "http://token.example.test:" + tokenServer.port();
            String introspectBaseUri = "http://introspect.example.test:" + introspectServer.port();
            String otherBaseUri = "http://other.example.test:" + otherServer.port();

            metadataHolder[0] = JsonParser.create("{"
                                                          + "\"token_endpoint\":\"" + tokenBaseUri + "/tokens\","
                                                          + "\"authorization_endpoint\":\"" + tokenBaseUri + "/authorization\","
                                                          + "\"end_session_endpoint\":\"" + tokenBaseUri + "/logout\","
                                                          + "\"issuer\":\"" + tokenBaseUri + "\","
                                                          + "\"introspection_endpoint\":\"" + introspectBaseUri
                                                          + "/introspect\""
                                                          + "}")
                    .readJsonObject();

            OidcConfig config = OidcConfig.builder()
                    .identityUri(URI.create(metadataBaseUri))
                    .clientSecret("client-secret")
                    .clientId("client-id")
                    .validateJwtWithJwk(false)
                    .serverType("idcs")
                    .webclient(it -> it.dnsResolver((hostname, dnsAddressLookup) -> InetAddress.getLoopbackAddress()))
                    .build();

            post(config, URI.create(tokenBaseUri + "/tokens"));
            post(config, URI.create(introspectBaseUri + "/introspect"));
            post(config, URI.create(otherBaseUri + "/tokens"));

            String expectedAuthorization = "Basic " + Base64.getEncoder()
                    .encodeToString("client-id:client-secret".getBytes(StandardCharsets.UTF_8));

            assertThat(tokenHostAuthorization.get(), is(expectedAuthorization));
            assertThat(introspectHostAuthorization.get(), is(expectedAuthorization));
            assertThat(otherHostAuthorization.get(), nullValue());

            tokenHostAuthorization.set(null);
            introspectHostAuthorization.set(null);

            post(config, URI.create(tokenBaseUri + "/introspect"));
            post(config, URI.create(introspectBaseUri + "/tokens"));

            assertThat(tokenHostAuthorization.get(), nullValue());
            assertThat(introspectHostAuthorization.get(), nullValue());
        } finally {
            stop(metadataServer);
            stop(tokenServer);
            stop(introspectServer);
            stop(otherServer);
        }
    }

    @Test
    void testHelidonJsonMetadataConfigured() {
        JsonObject metadata = JsonObject.builder()
                .set("issuer", "https://identity.oracle.com")
                .build();

        OidcConfig config = OidcConfig.builder()
                .identityUri(URI.create("https://identity.oracle.com"))
                .clientSecret("top-secret")
                .clientId("client-id")
                .oidcMetadataJsonObject(metadata)
                .build();

        assertAll("Helidon JSON metadata is retained",
                  () -> assertThat(config.oidcMetadataJsonObject(), is(metadata)),
                  () -> assertThat(config.oidcMetadataJsonObject().stringValue("issuer").orElse(null),
                                   is("https://identity.oracle.com")));
    }

    private static WebServer authCapturingServer(AtomicReference<String> authorization) {
        return WebServer.builder()
                .host(InetAddress.getLoopbackAddress().getHostAddress())
                .routing(routing -> routing
                        .any((req, res) -> {
                            authorization.set(req.headers()
                                                      .first(HeaderNames.AUTHORIZATION)
                                                      .orElse(null));
                            res.send("{}");
                        }))
                .build()
                .start();
    }

    private static void stop(WebServer server) {
        if (server != null) {
            server.stop();
        }
    }

    private static void post(OidcConfig config, URI uri) {
        try (HttpClientResponse response = config.appWebClient()
                .post()
                .uri(uri)
                .submit("")) {
            response.as(String.class);
        }
    }

    private static List<OidcCookieHandler> cookieHandlers(OidcConfig config) {
        return List.of(config.tokenCookieHandler(),
                       config.idTokenCookieHandler(),
                       config.tenantCookieHandler(),
                       config.refreshTokenCookieHandler(),
                       config.stateCookieHandler());
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

    @Test
    void testResourceMetadataUsesHelidonJsonHook() {
        MetadataHookBuilder builder = new MetadataHookBuilder();

        OidcConfig config = builder.identityUri(URI.create("https://identity.oracle.com"))
                .clientSecret("top-secret")
                .clientId("client-id")
                .oidcMetadata(Resource.create("oidc-metadata", "{\"issuer\":\"https://identity.oracle.com\"}"))
                .build();

        assertAll("Resource metadata must flow through the Helidon JSON override path",
                  () -> assertThat(builder.metadataHookCalled(), is(true)),
                  () -> assertThat(builder.capturedMetadata().stringValue("issuer").orElse(null),
                                   is("https://identity.oracle.com")),
                  () -> assertThat(config.oidcMetadataJsonObject().stringValue("issuer").orElse(null),
                                   is("https://identity.oracle.com")));
    }

    @Test
    void testWellKnownMetadataAndJwkReadThroughHelidonJson() {
        JsonObject[] metadataHolder = new JsonObject[1];
        JsonObject jwk = JsonParser.create("""
                {
                    "keys": [
                        {
                            "kty": "oct",
                            "kid": "test-key",
                            "alg": "HS256",
                            "key_ops": [
                                "sign",
                                "verify"
                            ],
                            "k": "FdFYFzERwC2uCBB46pZQi4GG85LujR8obt-KWRBICVQ"
                        }
                    ]
                }
                """).readJsonObject();

        WebServer server = WebServer.builder()
                .host("localhost")
                .routing(routing -> routing
                        .get("/.well-known/openid-configuration", (req, res) -> res.send(metadataHolder[0]))
                        .get("/jwk", (req, res) -> res.send(jwk)))
                .build()
                .start();

        String baseUri = "http://localhost:" + server.port();
        metadataHolder[0] = JsonParser.create("""
                {
                    "token_endpoint": "%1$s/tokens",
                    "authorization_endpoint": "%1$s/authorization",
                    "end_session_endpoint": "%1$s/logout",
                    "issuer": "%1$s",
                    "jwks_uri": "%1$s/jwk"
                }
                """.formatted(baseUri)).readJsonObject();

        try {
            OidcConfig config = OidcConfig.builder()
                    .identityUri(URI.create(baseUri))
                    .clientSecret("top-secret")
                    .clientId("client-id")
                    .build();

            JsonObject jwkJson = config.generalWebClient()
                    .get()
                    .uri(URI.create(baseUri + "/jwk"))
                    .requestEntity(JsonObject.class);

            assertAll("Well known metadata and JWK are loaded using Helidon JSON",
                      () -> assertThat(config.tokenEndpointUri(), is(URI.create(baseUri + "/tokens"))),
                      () -> assertThat(config.authorizationEndpointUri(), is(baseUri + "/authorization")),
                      () -> assertThat(config.issuer(), is(baseUri)),
                      () -> assertThat(jwkJson.arrayValue("keys").orElseThrow().values().size(), is(1)),
                      () -> assertThat(config.signJwk().forKeyId("test-key").isPresent(), is(true)));
        } finally {
            server.stop();
        }
    }

    // Stub the Builder class to be able to retrieve the cookie-encryption-password value
    private static class TestOidcConfigBuilder extends OidcConfig.Builder {

        private final String[] cookieEncryptionPasswordValue;

        private TestOidcConfigBuilder(String[] cookieEncryptionPasswordValue) {
            this.cookieEncryptionPasswordValue = cookieEncryptionPasswordValue;
        }

        // Stub the method to be able to store the cookie-encryption-password to a variable for later retrieval
        @Override
        public OidcConfig.Builder cookieEncryptionPassword(char[] cookieEncryptionPassword) {
            cookieEncryptionPasswordValue[0] = String.valueOf(cookieEncryptionPassword);
            super.cookieEncryptionPassword(cookieEncryptionPassword);
            return this;
        }
    }

    private static final class MetadataHookBuilder extends OidcConfig.Builder {
        private boolean metadataHookCalled;
        private JsonObject capturedMetadata;

        @Override
        public OidcConfig.Builder oidcMetadataJsonObject(JsonObject metadata) {
            this.metadataHookCalled = true;
            this.capturedMetadata = metadata;
            return super.oidcMetadataJsonObject(metadata);
        }

        private boolean metadataHookCalled() {
            return metadataHookCalled;
        }

        private JsonObject capturedMetadata() {
            return capturedMetadata;
        }
    }
}
