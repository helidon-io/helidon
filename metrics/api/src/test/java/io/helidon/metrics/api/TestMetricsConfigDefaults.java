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
package io.helidon.metrics.api;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

@Testing.Test(perMethod = true)
class TestMetricsConfigDefaults {

    @BeforeEach
    void rejectServiceLookups() {
        var registry = (ServiceRegistry) Proxy.newProxyInstance(ServiceRegistry.class.getClassLoader(),
                                                               new Class<?>[] {ServiceRegistry.class},
                                                               (_, method, _) -> {
                                                                   throw new AssertionError("Unexpected service registry access: "
                                                                                                    + method.getName());
                                                               });
        Services.registry(registry);
    }

    @Test
    void defaultsUseEmptyConfigWithoutServices() {
        for (MetricsConfig config : List.of(MetricsConfig.create(),
                                           MetricsConfig.create(Config.empty()),
                                           MetricsConfig.builder().build())) {
            assertThat(config.config(), sameInstance(Config.empty()));
            assertThat(config.enabled(), is(true));
            assertThat(config.appName(), is(Optional.empty()));
            assertThat(config.tags(), empty());
        }
    }

    @Test
    void explicitConfigIsAppliedWithoutServices() {
        Config suppliedConfig = Config.just(ConfigSources.create(Map.of("enabled", "false",
                                                                        "app-name", "caller-app")));
        MetricsConfig config = MetricsConfig.create(suppliedConfig);

        assertThat(config.config(), sameInstance(suppliedConfig));
        assertThat(config.enabled(), is(false));
        assertThat(config.appName(), is(Optional.of("caller-app")));
    }

    @Test
    void programmaticSettingsArePreservedWithoutServices() {
        MetricsConfig config = MetricsConfig.builder()
                .enabled(false)
                .appName("builder-app")
                .build();

        assertThat(config.config(), sameInstance(Config.empty()));
        assertThat(config.enabled(), is(false));
        assertThat(config.appName(), is(Optional.of("builder-app")));
    }
}
