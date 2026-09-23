/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.util.Map;

import io.helidon.common.configurable.AllowList;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OidcFrontendUriAllowListTest {
    @Test
    void defaultAndEmptyAllowListDenyOrigins() {
        assertThat(builder().build().frontendUriAllowList().test("https://frontend.example"), is(false));
        assertThat(builder().frontendUriAllowList(AllowList.builder().build())
                           .build().frontendUriAllowList().test("https://frontend.example"), is(false));
    }

    @Test
    void builderConfiguresAllowedOrigins() {
        OidcConfig config = builder()
                .frontendUriAllowList(AllowList.builder().addAllowed("https://frontend.example").build())
                .build();

        assertThat(config.frontendUriAllowList().test("https://frontend.example"), is(true));
        assertThat(config.frontendUriAllowList().test("https://attacker.example"), is(false));
    }

    @Test
    void allowListIsAtSameConfigurationLevelAsFrontendUri() {
        Config options = Config.create(ConfigSources.create(Map.of(
                "frontend-uri", "https://configured.example/application",
                "frontend-uri-allow-list.allow.exact", "https://frontend.example,https://denied.example",
                "frontend-uri-allow-list.deny.exact", "https://denied.example")));
        OidcConfig config = builder().config(options).build();

        assertThat(config.frontendUri().orElseThrow(), is("https://configured.example/application"));
        assertThat(config.frontendUriAllowList().test("https://frontend.example"), is(true));
        assertThat(config.frontendUriAllowList().test("https://denied.example"), is(false));
        assertThat(config.frontendUriAllowList().test("https://attacker.example"), is(false));
    }

    @Test
    void nullAllowListIsRejected() {
        assertThrows(NullPointerException.class, () -> builder().frontendUriAllowList(null));
    }

    private static OidcConfig.Builder builder() {
        return OidcConfig.builder()
                .clientId("test")
                .clientSecret("secret")
                .identityUri(URI.create("https://identity.example"))
                .oidcMetadataWellKnown(false)
                .useCookie(false);
    }
}
