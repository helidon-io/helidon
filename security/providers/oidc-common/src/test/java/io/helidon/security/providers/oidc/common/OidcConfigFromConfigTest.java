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

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link OidcConfig}.
 */
class OidcConfigFromConfigTest extends OidcConfigAbstractTest {
    private OidcConfig oidcConfig;
    private Config config;

    OidcConfigFromConfigTest() {
        config = Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .build();

        oidcConfig = OidcConfig.create(config.get("security.oidc-test"));
    }

    @Override
    OidcConfig getConfig() {
        return oidcConfig;
    }

    @Test
    void testOptionalAudience() {
        OidcConfig oidcConfig = OidcConfig.create(config.get("security.oidc-optional-aud"));
        String audience = oidcConfig.audience();
        assertThat(audience, nullValue());
    }

    @Test
    void testDisabledAudience() {
        OidcConfig oidcConfig = OidcConfig.create(config.get("security.oidc-disabled-aud"));
        assertThat(oidcConfig.checkAudience(), is(false));
    }

}
