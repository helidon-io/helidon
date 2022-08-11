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
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;

/**
 * Unit test for {@link OidcConfig}.
 */
class OidcConfigFromBuilderTest extends OidcConfigAbstractTest {
    private OidcConfig oidcConfig;
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
                .build();
    }

    @Override
    OidcConfig getConfig() {
        return oidcConfig;
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
