/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.Status;
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
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_RELATIVE_URIS;
import static io.helidon.security.providers.oidc.common.OidcConfig.DEFAULT_TOKEN_REFRESH_SKEW;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Unit test for {@link OidcConfig}.
 */
class OidcConfigFromBuilderTest extends OidcConfigAbstractTest {

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
                .relativeUris(true)
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
                () -> assertThat("Relative URIs", config.relativeUris(), is(DEFAULT_RELATIVE_URIS)),
                () -> assertThat("Use Cookie", config.useCookie(), is(DEFAULT_COOKIE_USE)),
                () -> assertThat("Use Header", config.useHeader(), is(DEFAULT_HEADER_USE)),
                () -> assertThat("Base scopes to use", config.baseScopes(), is(DEFAULT_BASE_SCOPES)),
                () -> assertThat("Cookie value prefix", tokenCookieHandler.cookieValuePrefix(), is(DEFAULT_COOKIE_NAME + "=")),
                () -> assertThat("Cookie name", tokenCookieHandler.cookieName(), is(DEFAULT_COOKIE_NAME)),
                () -> assertThat("Realm", config.realm(), is(OidcConfig.Builder.DEFAULT_REALM)),
                () -> assertThat("Redirect Attempt Parameter", config.redirectAttemptParam(), is(DEFAULT_ATTEMPT_PARAM)),
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
                .proxyProtocol("http")
                .proxyHost("localhost")
                .proxyPort(server.port())
                .build();

        // 2nd test will simulate relativeUris=true and will fail if URI is absolute
        relativeUris[0] = true;
        OidcConfig.builder()
                // The next 3 parameters need to be set or config builder will fail
                .identityUri(URI.create(baseUri + "/identity"))
                .clientId("client-id-value")
                .clientSecret("client-secret-value")
                .proxyProtocol("http")
                .proxyHost("localhost")
                .proxyPort(server.port())
                .relativeUris(relativeUris[0])
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
}
