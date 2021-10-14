/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Common test methods for oidc config tests.
 */
abstract class OidcConfigAbstractTest {
    abstract OidcConfig getConfig();

    @Test
    void testExplicitValues() {
        OidcConfig config = getConfig();
        assertAll("All values explicitly configured either in yaml or by hand",
                  () -> assertThat("Identity URI", config.identityUri(), is(URI.create("https://identity.oracle.com"))),
                  () -> assertThat("Scope audience", config.scopeAudience(), is("http://localhost:7987/test-application/")),
                  () -> assertThat("Client ID", config.clientId(), is("client-id-value")),
                  () -> assertThat("Validate JWT with JWK", config.validateJwtWithJwk(), is(false)),
                  () -> assertThat("Token endpoint",
                                   config.tokenEndpoint().getUri(),
                                   is(URI.create("http://identity.oracle.com/tokens"))),
                  () -> assertThat("Authorization endpoint",
                                   config.authorizationEndpointUri(),
                                   is("http://identity.oracle.com/authorization")),
                  () -> assertThat("Introspect endpoint",
                                   config.introspectEndpoint().getUri(),
                                   is(URI.create("http://identity.oracle.com/introspect")))
        );
    }

    @Test
    void testDefaultValues() {
        OidcConfig config = getConfig();
        assertAll("All values using defaults",
                  () -> assertThat("Redirect URI", config.redirectUri(), is("/oidc/redirect")),
                  () -> assertThat("Use Parameter", config.useParam(), is(OidcConfig.DEFAULT_PARAM_USE)),
                  () -> assertThat("Use Cookie", config.useCookie(), is(OidcConfig.DEFAULT_COOKIE_USE)),
                  () -> assertThat("Use Header", config.useHeader(), is(OidcConfig.DEFAULT_HEADER_USE)),
                  () -> assertThat("Base scopes to use", config.baseScopes(), is(OidcConfig.DEFAULT_BASE_SCOPES)),
                  () -> assertThat("Cookie value prefix", config.cookieValuePrefix(), is("JSESSIONID=")),
                  () -> assertThat("Cookie name", config.cookieName(), is(OidcConfig.DEFAULT_COOKIE_NAME)),
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
    void testComputedValues() {
        OidcConfig config = getConfig();
        assertAll("All values computed either from configured or default values",
                  () -> assertThat("Redirect URI with host",
                                   config.redirectUriWithHost(),
                                   is("http://something:7001/oidc/redirect"))
        );
    }
}
