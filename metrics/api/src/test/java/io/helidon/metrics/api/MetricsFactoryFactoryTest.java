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
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.service.registry.Services;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        MetricsFactory currentFactory = Services.get(MetricsFactory.class);
        MetricsFactory serviceResolvedFactory = Services.get(MetricsFactory.class);

        assertThat(serviceResolvedFactory, sameInstance(currentFactory));
        assertThat(Services.get(MetricsFactory.class), sameInstance(currentFactory));
    }

    @Test
    void directFactoryCreatesIndependentInstances() {
        MetricsFactoryFactory serviceFactory = new MetricsFactoryFactory(ROOT_CONFIG);

        MetricsFactory firstFactory = serviceFactory.get();
        MetricsFactory secondFactory = serviceFactory.get();

        assertThat(secondFactory, not(sameInstance(firstFactory)));
    }

    @Test
    void factoryReceivesRootConfig() {
        MetricsFactoryFactory serviceFactory = new MetricsFactoryFactory(ROOT_CONFIG) {
            @Override
            MetricsFactory createMetricsFactory(Config rootConfig) {
                assertThat(rootConfig.get("metrics.app-name").asString().get(), is("metrics-app"));
                assertThat(rootConfig.get("server.features.observe.observers.metrics.app-name").asString().get(),
                           is("observe-app"));
                return new CloseTrackingMetricsFactory();
            }
        };

        serviceFactory.get();
    }

    @Test
    void deprecatedConfigInstanceRejectsNullConfig() {
        assertThrows(NullPointerException.class, () -> MetricsFactory.getInstance((Config) null));
    }

    @Test
    void preDestroyClosesCreatedFactories() {
        AtomicInteger nextFactory = new AtomicInteger();
        CloseTrackingMetricsFactory firstFactory = new CloseTrackingMetricsFactory();
        CloseTrackingMetricsFactory secondFactory = new CloseTrackingMetricsFactory();
        CloseTrackingMetricsFactory[] factories = {firstFactory, secondFactory};
        MetricsFactoryFactory serviceFactory = new MetricsFactoryFactory(ROOT_CONFIG) {
            @Override
            MetricsFactory createMetricsFactory(Config rootConfig) {
                return factories[nextFactory.getAndIncrement()];
            }
        };

        serviceFactory.get();
        serviceFactory.get();

        serviceFactory.preDestroy();

        assertThat(firstFactory.closeCount(), is(1));
        assertThat(secondFactory.closeCount(), is(1));

        serviceFactory.preDestroy();

        assertThat(firstFactory.closeCount(), is(1));
        assertThat(secondFactory.closeCount(), is(1));
    }

    @Test
    void servicesLookupKeepsSingletonAfterCloseAll() {
        MetricsFactory firstFactory = Services.get(MetricsFactory.class);

        MetricsFactory.closeAll();
        MetricsFactory secondFactory = Services.get(MetricsFactory.class);

        assertThat(secondFactory, sameInstance(firstFactory));
    }

    private static class CloseTrackingMetricsFactory extends NoOpMetricsFactory {
        private int closeCount;

        @Override
        public void close() {
            closeCount++;
        }

        private int closeCount() {
            return closeCount;
        }
    }
}
