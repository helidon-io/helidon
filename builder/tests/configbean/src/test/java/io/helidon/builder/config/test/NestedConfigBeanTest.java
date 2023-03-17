/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.config.test;

import java.util.Objects;

import io.helidon.builder.config.testsubjects.fakes.DefaultFakeServerConfig;
import io.helidon.builder.config.testsubjects.fakes.DefaultFakeSocketConfig;
import io.helidon.builder.config.testsubjects.fakes.FakeSocketConfig;
import io.helidon.builder.config.testsubjects.fakes.FakeWebServerTlsConfig;
import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.yaml.YamlConfigParser;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;

class NestedConfigBeanTest extends AbstractConfigBeanTest {

    @Test
    void rootServerConfigPlusOneSocket() {
        Config cfg = io.helidon.config.Config.create(createRootPlusOneSocketTestingConfigSource());
        DefaultFakeServerConfig serverConfig = DefaultFakeServerConfig
                .toBuilder(cfg.get(FAKE_SERVER_CONFIG)).build();

        assertThat(serverConfig.name(),
                   equalTo("root"));
        assertThat(serverConfig.port(),
                   equalTo(8080));
        assertThat(serverConfig.sockets(),
                   hasEntry("first",
                                     DefaultFakeSocketConfig.builder()
                                             .name("first")
                                             .port(8081)
                                             .build()));
        assertThat(serverConfig.sockets().get("first").tls(),
                   optionalEmpty());
    }

    @Test
    void nestedServerConfigPlusOneSocketAndOneTls() {
        Config cfg = io.helidon.config.Config.create(createNestedPlusOneSocketAndOneTlsTestingConfigSource());
        DefaultFakeServerConfig serverConfig = DefaultFakeServerConfig
                .toBuilder(cfg.get(NESTED + "." + FAKE_SERVER_CONFIG)).build();

        assertThat(serverConfig.name(),
                   equalTo("nested"));
        assertThat(serverConfig.port(),
                   equalTo(8080));
        FakeWebServerTlsConfig tls = serverConfig.sockets().get("first").tls().orElseThrow();
        assertThat(tls.enabled(), is(true));
        assertThat(tls.cipherSuite(), containsInAnyOrder("cipher-1"));
        assertThat(tls.enabledTlsProtocols(), containsInAnyOrder(FakeWebServerTlsConfig.PROTOCOL));
    }

    @Test
    void fakeServerConfigFromYaml() {
        Config cfg = io.helidon.config.Config.builder()
                .sources(ConfigSources.classpath("io/helidon/builder/config/test/FakeServerConfigPlusTwoSockets.yaml"))
                .addParser(YamlConfigParser.create())
                .build();
        DefaultFakeServerConfig serverConfig = DefaultFakeServerConfig
                .toBuilder(cfg.get(FAKE_SERVER_CONFIG)).build();

        assertThat(serverConfig.name(),
                   equalTo("@default"));
        FakeSocketConfig admin = Objects.requireNonNull(serverConfig.sockets().get("admin"), serverConfig.sockets().toString());
        assertThat(admin.port(), equalTo(8086));
        FakeSocketConfig secure = Objects.requireNonNull(serverConfig.sockets().get("secure"));
        FakeWebServerTlsConfig tls = secure.tls().orElseThrow();
        assertThat(tls.enabled(), is(true));
        assertThat(tls.cipherSuite(), containsInAnyOrder("cipher-1"));
        assertThat(tls.enabledTlsProtocols(), containsInAnyOrder(FakeWebServerTlsConfig.PROTOCOL));
    }

}
