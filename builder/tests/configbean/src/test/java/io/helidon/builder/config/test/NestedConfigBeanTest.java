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

import io.helidon.builder.config.testsubjects.fakes.FakeServerConfigDefault;
import io.helidon.builder.config.testsubjects.fakes.FakeSocketConfigDefault;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;

class NestedConfigBeanTest extends AbstractConfigBeanTest {

    @Test
    void rootServerConfigPlusOneSocket() {
        Config cfg = io.helidon.config.Config.builder(createRootPlusOneSocketTestingConfigSource())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        FakeServerConfigDefault serverConfig = FakeServerConfigDefault
                .toBuilder(cfg.get(FAKE_SERVER_CONFIG)).build();

        assertThat(serverConfig.name(),
                   equalTo("root"));
        assertThat(serverConfig.port(),
                   equalTo(8080));

        // validate the map
        assertThat(serverConfig.sockets(),
                   hasEntry("1",
                            FakeSocketConfigDefault.builder()
                                    .name("first")
                                    .port(8081)
                                    .build()));
        assertThat(serverConfig.sockets().get("1").tls(),
                   optionalEmpty());
    }

    @Test
    void nestedServerConfigPlusOneSocketAndOneTls() {
        Config cfg = io.helidon.config.Config.builder(createNestedPlusOneSocketAndOneTlsTestingConfigSource())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        FakeServerConfigDefault serverConfig = FakeServerConfigDefault
                .toBuilder(cfg.get(NESTED + "." + FAKE_SERVER_CONFIG)).build();

        assertThat(serverConfig.name(),
                   equalTo("nested"));
        assertThat(serverConfig.port(),
                   equalTo(8080));

        // validate the map
        FakeWebServerTlsConfig tls = serverConfig.sockets().get("1").tls().orElseThrow();
        assertThat(tls.enabled(),
                   is(true));
        assertThat(tls.cipherSuite(),
                   containsInAnyOrder("cipher-1"));
        assertThat(tls.enabledTlsProtocols(),
                   containsInAnyOrder(FakeWebServerTlsConfig.PROTOCOL));
    }

    @Test
    void fakeServerConfigFromUnnamedYaml() {
        Config cfg = io.helidon.config.Config.builder()
                .sources(ConfigSources.classpath("io/helidon/builder/config/test/FakeServerConfigPlusTwoUnnamedSockets.yaml"))
                .addParser(YamlConfigParser.create())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        FakeServerConfigDefault serverConfig = FakeServerConfigDefault
                .toBuilder(cfg.get(FAKE_SERVER_CONFIG)).build();

        assertThat(serverConfig.name(),
                   equalTo("@default"));

        // validate the map
        FakeSocketConfig zero = Objects.requireNonNull(serverConfig.namedSocket("0").orElse(null),
                                                       serverConfig.sockets().toString());
        assertThat(zero.bindAddress(),
                   equalTo("127.0.0.1"));
        assertThat(zero.port(),
                   equalTo(8086));
        assertThat(zero.tls(),
                   optionalEmpty());
        FakeSocketConfig one = Objects.requireNonNull(serverConfig.sockets().get("1"),
                                                      serverConfig.sockets().toString());
        assertThat(one.bindAddress(),
                   equalTo("localhost"));
        assertThat(one.port(),
                   equalTo(8087));
        FakeWebServerTlsConfig tls = one.tls().orElseThrow();
        assertThat(tls.enabled(),
                   is(true));
        assertThat(tls.cipherSuite(),
                   containsInAnyOrder("cipher-1"));
        assertThat(tls.enabledTlsProtocols(),
                   containsInAnyOrder(FakeWebServerTlsConfig.PROTOCOL));

        // validate the list
        assertThat(serverConfig.socketList(),
                   contains(FakeSocketConfigDefault.builder()
                                    .bindAddress("127.0.0.1")
                                    .port(8086)
                                    .build(),
                            FakeSocketConfigDefault.builder()
                                    .bindAddress("localhost")
                                    .port(8087)
                                    .tls(tls)
                                    .build()));

        // validate the set
        assertThat(serverConfig.socketSet(),
                   containsInAnyOrder(FakeSocketConfigDefault.builder()
                                              .bindAddress("127.0.0.1")
                                              .port(8086)
                                              .build(),
                                      FakeSocketConfigDefault.builder()
                                              .bindAddress("localhost")
                                              .port(8087)
                                              .tls(tls)
                                              .build()));
    }

    @Test
    void fakeServerConfigFromNamedYaml() {
        Config cfg = io.helidon.config.Config.builder()
                .sources(ConfigSources.classpath("io/helidon/builder/config/test/FakeServerConfigPlusTwoNamedSockets.yaml"))
                .addParser(YamlConfigParser.create())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        FakeServerConfigDefault serverConfig = FakeServerConfigDefault
                .toBuilder(cfg.get(FAKE_SERVER_CONFIG)).build();

        // validate the map
        assertThat(serverConfig.name(),
                   equalTo("@default"));
        FakeSocketConfig admin = serverConfig.namedSocket("admin").orElseThrow();
        assertThat(admin.port(),
                   equalTo(8086));
        assertThat(admin.name(),
                   equalTo("admin"));

        FakeSocketConfig secure = serverConfig.namedSocket("secure").orElseThrow();
        assertThat(secure.port(),
                   equalTo(8087));
        assertThat(secure.name(),
                   equalTo("obscure"));
        FakeWebServerTlsConfig tls = secure.tls().orElseThrow();
        assertThat(tls.enabled(),
                   is(true));
        assertThat(tls.cipherSuite(),
                   containsInAnyOrder("cipher-1"));
        assertThat(tls.enabledTlsProtocols(),
                   containsInAnyOrder(FakeWebServerTlsConfig.PROTOCOL));
    }

}
