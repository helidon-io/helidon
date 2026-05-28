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

package io.helidon.webclient.api;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SniConfigTest {
    @Test
    void defaultUsesUriHost() {
        SniConfig config = SniConfig.create();

        assertThat(config.mode(), is(SniMode.URI_HOST));
        assertThat(config.host().isEmpty(), is(true));
    }

    @Test
    void explicitModeRequiresHost() {
        assertThrows(IllegalArgumentException.class,
                     () -> SniConfig.builder()
                             .mode(SniMode.EXPLICIT)
                             .build());
    }

    @Test
    void explicitModeValidatesHost() {
        assertThrows(IllegalArgumentException.class,
                     () -> SniConfig.builder()
                             .mode(SniMode.EXPLICIT)
                             .host("bad_host")
                             .build());
    }

    @Test
    void explicitModeAcceptsBracketedIpv6Host() {
        SniConfig config = SniConfig.builder()
                .mode(SniMode.EXPLICIT)
                .host("[::1]")
                .build();

        assertThat(config.host().orElseThrow(), is("::1"));
    }

    @Test
    void explicitModeRejectsPort() {
        assertThrows(IllegalArgumentException.class,
                     () -> SniConfig.builder()
                             .mode(SniMode.EXPLICIT)
                             .host("authority.example:443")
                             .build());
        assertThrows(IllegalArgumentException.class,
                     () -> SniConfig.builder()
                             .mode(SniMode.EXPLICIT)
                             .host("[::1]:443")
                             .build());
    }

    @Test
    void hostIsOnlyAllowedForExplicitMode() {
        assertThrows(IllegalArgumentException.class,
                     () -> SniConfig.builder()
                             .mode(SniMode.HOST_HEADER)
                             .host("authority.example")
                             .build());
    }

    @Test
    void configUsesHyphenatedModeNames() {
        SniConfig hostHeader = SniConfig.create(Config.just(ConfigSources.create(Map.of("mode", "host-header"))));
        SniConfig explicit = SniConfig.create(Config.just(ConfigSources.create(Map.of("mode", "explicit",
                                                                                      "host", "authority.example"))));

        assertThat(hostHeader.mode(), is(SniMode.HOST_HEADER));
        assertThat(explicit.mode(), is(SniMode.EXPLICIT));
        assertThat(explicit.host().orElseThrow(), is("authority.example"));
    }
}
