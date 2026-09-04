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
package io.helidon.dbclient.metrics.hikari;

import java.util.ArrayList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.spi.MeterBuilderCustomizer;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class DropwizardMetricsListenerTest {
    private static final String PREFIX = "db.pool.";
    private static final Tag CUSTOM_TAG = new TestTag("customized", "true");

    @Test
    void removesMetersUsingCustomizedIdentity() {
        List<Class<?>> origins = new ArrayList<>();
        MeterBuilderCustomizer customizer = new MeterBuilderCustomizer() {
            @Override
            public void customize(Meter.Builder<?, ?> builder, Class<?> origin) {
                if (builder.name().startsWith(PREFIX)) {
                    origins.add(origin);
                    builder.addTag(CUSTOM_TAG);
                }
            }
        };
        ServiceRegistryConfig registryConfig = ServiceRegistryConfig.builder()
                .putContractInstance(MeterBuilderCustomizer.class, customizer)
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(registryConfig);
        try {
            Services.registry(manager.registry());
            MeterRegistry registry = manager.registry().get(MeterRegistry.class);
            MetricRegistry dropwizardRegistry = new MetricRegistry();
            dropwizardRegistry.addListener(DropwizardMetricsListener.create(Config.empty()));

            dropwizardRegistry.register("gauge", (Gauge<Integer>) () -> 1);
            dropwizardRegistry.counter("counter");

            assertThat(origins, contains(DropwizardMetricsListener.class, DropwizardMetricsListener.class));
            assertThat(registry.gauge(PREFIX + "gauge", List.of(CUSTOM_TAG)).isPresent(), is(true));
            assertThat(registry.gauge(PREFIX + "counter", List.of(CUSTOM_TAG)).isPresent(), is(true));

            assertThat(dropwizardRegistry.remove("gauge"), is(true));
            assertThat(dropwizardRegistry.remove("counter"), is(true));

            assertThat(registry.gauge(PREFIX + "gauge", List.of(CUSTOM_TAG)).isEmpty(), is(true));
            assertThat(registry.gauge(PREFIX + "counter", List.of(CUSTOM_TAG)).isEmpty(), is(true));
        } finally {
            manager.shutdown();
        }
    }

    private record TestTag(String key, String value) implements Tag {
        @Override
        public <T> T unwrap(Class<? extends T> type) {
            return type.cast(this);
        }
    }
}
