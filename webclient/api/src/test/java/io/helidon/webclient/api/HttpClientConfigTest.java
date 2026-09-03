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

import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

class HttpClientConfigTest {

    @Test
    void altSvcIsDisabledByDefault() {
        assertThat(HttpClientConfig.create().altSvc().isEmpty(), is(true));
    }

    @Test
    void testUnixBaseAddressKeepsConfiguredPath() {
        Config config = Config.just(ConfigSources.create(Map.of("base-address", "unix:/tmp/client.sock")));

        var socketAddress = (UnixDomainSocketAddress) HttpClientConfig.create(config)
                .baseAddress()
                .orElseThrow();

        assertThat(socketAddress.getPath(), is(Path.of("/tmp/client.sock")));
    }

    @Test
    void programmaticAltSvcConfigurationOptsInWithDefaults() {
        HttpClientConfig config = HttpClientConfig.builder()
                .altSvc(ClientAltSvcConfig.create())
                .build();

        ClientAltSvcConfig altSvc = config.altSvc().orElseThrow();
        assertThat(altSvc.enabled(), is(true));
        assertThat(altSvc.protocols().isEmpty(), is(true));
    }

    @Test
    void parsesAltSvcConfiguration() {
        Config config = Config.just(ConfigSources.create(Map.of("alt-svc.enabled", "false",
                                                               "alt-svc.protocols.0", "h2",
                                                               "alt-svc.protocols.1", "future-protocol")));

        ClientAltSvcConfig altSvc = HttpClientConfig.create(config).altSvc().orElseThrow();

        assertThat(altSvc.enabled(), is(false));
        assertThat(altSvc.protocols(), containsInAnyOrder("h2", "future-protocol"));
    }
}
