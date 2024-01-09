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

package io.helidon.inject.tests.configbeans;

import java.util.Collection;
import java.util.Objects;

import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.yaml.YamlConfigParser;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

class NestedConfigBeanTest extends AbstractConfigBeanTest {

    @Test
    void rootServerConfigPlusOneSocket() {
        Config cfg = io.helidon.config.Config.builder(createRootPlusOneSocketTestingConfigSource())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        FakeServerConfig serverConfig = FakeServerConfig.create(cfg.get(FAKE_SERVER_CONFIG));

        assertThat(serverConfig.name(),
                   equalTo("root"));
        assertThat(serverConfig.port(),
                   equalTo(8080));

        // validate the map
        assertThat(serverConfig.sockets(),
                   Matchers.hasEntry("first",
                                     FakeSocketConfig.builder()
                                    .name("first")
                                    .port(8081)
                                    .build()));
        assertThat(serverConfig.sockets().get("first").tls(),
                   optionalEmpty());
    }

    @Test
    void nestedServerConfigPlusOneSocketAndOneTls() {
        Config cfg = io.helidon.config.Config.builder(createNestedPlusOneSocketAndOneTlsTestingConfigSource())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        FakeServerConfig serverConfig = FakeServerConfig.create(cfg.get(NESTED + "." + FAKE_SERVER_CONFIG));

        assertThat(serverConfig.name(),
                   equalTo("nested"));
        assertThat(serverConfig.port(),
                   equalTo(8080));

        // validate the map
        FakeWebServerTlsConfig tls = serverConfig.sockets().get("first").tls().orElseThrow();
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
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        FakeServerConfig serverConfig = FakeServerConfig.create(cfg.get(FAKE_SERVER_CONFIG));

        assertThat(serverConfig.name(),
                   equalTo("@default"));

        // validate the map
        FakeSocketConfig zero = Objects.requireNonNull(serverConfig.namedSocket("0").orElse(null),
                                                       serverConfig.sockets().toString());
        assertThat(zero.bindAddress(), optionalValue(is("127.0.0.1")));
        assertThat(zero.port(),
                   equalTo(8086));
        assertThat(zero.tls(),
                   optionalEmpty());
        FakeSocketConfig one = Objects.requireNonNull(serverConfig.sockets().get("1"),
                                                      serverConfig.sockets().toString());
        assertThat(one.bindAddress(),
                   optionalValue(is("localhost")));
        assertThat(one.port(),
                   equalTo(8087));
        FakeWebServerTlsConfig tls = one.tls().orElseThrow();
        assertThat(tls.enabled(),
                   is(true));
        assertThat(tls.cipherSuite(),
                   containsInAnyOrder("cipher-1"));
        assertThat(tls.enabledTlsProtocols(),
                   containsInAnyOrder(FakeWebServerTlsConfig.PROTOCOL));

        Collection<FakeSocketConfig> values = serverConfig.sockets().values();
        // validate the list
        assertThat(values,
                   contains(FakeSocketConfig.builder()
                                    .bindAddress("127.0.0.1")
                                    .port(8086)
                                    .build(),
                            FakeSocketConfig.builder()
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
        FakeServerConfig serverConfig = FakeServerConfig.create(cfg.get(FAKE_SERVER_CONFIG));

        // validate the map
        assertThat(serverConfig.name(),
                   equalTo("@default"));
        FakeSocketConfig admin = serverConfig.namedSocket("admin").orElseThrow();
        assertThat(admin.port(),
                   equalTo(8086));
        assertThat(admin.name(),
                   equalTo("admin"));

        // the name is always taken from the values, even if not the same as the name from object config node
        FakeSocketConfig secure = serverConfig.namedSocket("obscure").orElseThrow();
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
