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

package io.helidon.pico.configdriven.configuredby.test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.builder.config.spi.ConfigBeanRegistryHolder;
import io.helidon.builder.config.testsubjects.fakes.FakeServerConfig;
import io.helidon.builder.config.testsubjects.fakes.FakeSocketConfig;
import io.helidon.config.ConfigSources;
import io.helidon.config.MapConfigSource;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.configdriven.services.ConfigBeanRegistry;
import io.helidon.pico.configdriven.services.ConfiguredServiceProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/**
 * Executes the tests from the base.
 */
class ConfiguredByTest extends AbstractConfiguredByTest {

    protected MapConfigSource.Builder createNested8080TestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        "nested." + FAKE_SERVER_CONFIG + ".0.name", "nested",
                        "nested." + FAKE_SERVER_CONFIG + ".0.port", "8080",
                        "nested." + FAKE_SERVER_CONFIG + ".0.worker-count", "1"
                ), "config-nested-default-8080");
    }

    public MapConfigSource.Builder createRootPlusOneSocketTestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        FAKE_SERVER_CONFIG + ".name", "root",
                        FAKE_SERVER_CONFIG + ".port", "8080",
                        FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".0.name", "first",
                        FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".0.port", "8081"
                ), "config-root-plus-one-socket");
    }

    @Test
    void onlyRootConfigBeansAreCreated() {
        resetWith(io.helidon.config.Config.builder(createBasicTestingConfigSource(),
                                                   createRootDefault8080TestingConfigSource(),
                                                   createNested8080TestingConfigSource())
                          .disableEnvironmentVariablesSource()
                          .disableSystemPropertiesSource()
                          .build());

        ConfigBeanRegistry cbr = (ConfigBeanRegistry) ConfigBeanRegistryHolder.configBeanRegistry().orElseThrow();
        assertThat(cbr.ready(),
                   is(true));

        Set<String> set = cbr.allConfigBeans().keySet();
        assertThat(set, containsInAnyOrder(
                "@default",
                "fake-server"
        ));
    }

    @Test
    void serverConfigWithOneSocketConfigNested() {
        resetWith(io.helidon.config.Config.builder(createBasicTestingConfigSource(),
                                                   createRootPlusOneSocketTestingConfigSource())
                          .disableEnvironmentVariablesSource()
                          .disableSystemPropertiesSource()
                          .build());

        ConfigBeanRegistry cbr = (ConfigBeanRegistry) ConfigBeanRegistryHolder.configBeanRegistry().orElseThrow();
        assertThat(cbr.ready(),
                   is(true));

        Set<String> set = cbr.allConfigBeans().keySet();
        assertThat(set,
                   containsInAnyOrder("@default",
                                      "fake-server"
        ));

        Set<?> configBeans = cbr.configBeansByConfigKey("fake-server");
        assertThat(configBeans.toString(), configBeans.size(),
                   is(1));

        List<ConfiguredServiceProvider<?, ?>> list = cbr.configuredServiceProvidersConfiguredBy("fake-server");
        List<String> desc = list.stream().map(ServiceProvider::description).collect(Collectors.toList());
        assertThat(desc,
                   contains("FakeWebServer{root}:ACTIVE",
                            "FakeWebServerNotDrivenAndHavingConfiguredByOverrides{root}:PENDING"));

        FakeServerConfig cfg = (FakeServerConfig) configBeans.iterator().next();
        Map<String, FakeSocketConfig> sockets = cfg.sockets();
        assertThat(sockets.toString(), sockets.size(),
                   is(1));
    }

}
