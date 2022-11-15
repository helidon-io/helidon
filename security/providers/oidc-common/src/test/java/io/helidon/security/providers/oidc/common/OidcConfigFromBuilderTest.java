/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

/**
 * Unit test for {@link OidcConfig}.
 */
class OidcConfigFromBuilderTest extends OidcConfigAbstractTest {
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
                () -> assertThat("Base scopes to use", config.baseScopes(), is(OidcConfig.Builder.DEFAULT_BASE_SCOPES)),
                () -> assertThat("Cookie value prefix", config.cookieValuePrefix(), is(OidcConfig.DEFAULT_COOKIE_NAME + "=")),
                () -> assertThat("Cookie name", config.cookieName(), is(OidcConfig.DEFAULT_COOKIE_NAME)),
                () -> assertThat("Realm", config.realm(), is(OidcConfig.Builder.DEFAULT_REALM)),
                () -> assertThat("Redirect Attempt Parameter", config.redirectAttemptParam(), is(OidcConfig.DEFAULT_ATTEMPT_PARAM)),
                () -> assertThat("Max Redirects", config.maxRedirects(), is(OidcConfig.DEFAULT_MAX_REDIRECTS)),
                () -> assertThat("Client Timeout", config.clientTimeout(),
                                 is(Duration.ofSeconds(OidcConfig.Builder.DEFAULT_TIMEOUT_SECONDS))),
                () -> assertThat("Force HTTPS Redirects", config.forceHttpsRedirects(), is(OidcConfig.DEFAULT_FORCE_HTTPS_REDIRECTS)),
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
