/*
 * Copyright (c) 2026 Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.jwt.jwk.JwkKeys;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OidcConfigRedirectOptionsTest {
    @Test
    void secureDefaults() {
        assertOptions(builder().build(), false);
    }

    @Test
    void legacyOptionsFromBuilder() {
        assertOptions(builder().legacyStateParam(true).legacyStateFallback(true).legacyQueryParamHandoff(true).build(), true);
    }

    @Test
    void legacyOptionsFromConfiguration() {
        Map<String, String> values = new HashMap<>();
        values.put("legacy-state-param", "true");
        values.put("legacy-state-fallback", "true");
        values.put("legacy-query-param-handoff", "true");
        assertOptions(builder().config(Config.create(ConfigSources.create(values))).build(), true);
    }

    private static OidcConfig.Builder builder() {
        return OidcConfig.builder()
                .clientId("test-client")
                .clientSecret("test-secret")
                .identityUri(URI.create("https://issuer.example"))
                .tokenEndpointUri(URI.create("https://issuer.example/token"))
                .authorizationEndpointUri(URI.create("https://issuer.example/authorize"))
                .signJwk(JwkKeys.builder().build())
                .oidcMetadataWellKnown(false);
    }

    private static void assertOptions(OidcConfig config, boolean legacy) {
        try {
            assertAll(() -> assertThat(config.legacyStateParam(), is(legacy)),
                      () -> assertThat(config.legacyStateFallback(), is(legacy)),
                      () -> assertThat(config.legacyQueryParamHandoff(), is(legacy)),
                      () -> assertThat(config.clientSecret(), is("test-secret")),
                      () -> assertThat(config.useParam(), is(false)),
                      () -> assertThat(config.useCookie(), is(true)));
        } finally {
            config.appClient().close();
            config.generalClient().close();
        }
    }
}
