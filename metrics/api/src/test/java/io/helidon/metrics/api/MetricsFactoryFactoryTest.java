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

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

class MetricsFactoryFactoryTest {
    private static final Config ROOT_CONFIG = Config.just(ConfigSources.create(Map.of(
            "metrics.app-name", "metrics-app",
            "server.features.observe.observers.metrics.app-name", "observe-app")));

    @BeforeEach
    void setUp() {
        MetricsFactory.closeAll();
    }

    @AfterEach
    void tearDown() {
        MetricsFactory.closeAll();
    }

    @Test
    void reusesCurrentMetricsFactory() {
        MetricsFactory currentFactory =
                MetricsFactory.getInstance(ROOT_CONFIG.get("server.features.observe.observers.metrics"));
        MetricsFactoryFactory serviceFactory = new MetricsFactoryFactory(ROOT_CONFIG);

        MetricsFactory serviceResolvedFactory = serviceFactory.get();

        assertThat(serviceResolvedFactory, sameInstance(currentFactory));
        assertThat(MetricsFactory.getInstance(), sameInstance(currentFactory));
    }

    @Test
    void createsCurrentMetricsFactoryOnceWhenAbsent() {
        MetricsFactoryFactory serviceFactory = new MetricsFactoryFactory(ROOT_CONFIG);

        MetricsFactory firstFactory = serviceFactory.get();
        MetricsFactory secondFactory = serviceFactory.get();

        assertThat(secondFactory, sameInstance(firstFactory));
    }
}
