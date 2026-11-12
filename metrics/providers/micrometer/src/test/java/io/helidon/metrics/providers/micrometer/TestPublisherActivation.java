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

package io.helidon.metrics.providers.micrometer;

import java.util.List;
import java.util.Set;

import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsPublisher;

import io.micrometer.core.instrument.Metrics;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPublisherActivation {
    @Test
    void configurationAndFactoryConstructionDoNotActivatePublishers() {
        var publisher = new TestPublisher(null, false);
        var config = MetricsConfig.builder().addPublisher(publisher).build();
        var factory = MicrometerMetricsFactory.create(config, List.of());
        try {
            assertThat(publisher.starts, is(0));
            var registry = factory.createMeterRegistry(config);
            assertThat(publisher.starts, is(1));
            registry.close();
        } finally {
            publisher.close();
            factory.close();
        }
    }

    @Test
    void failedActivationCleansUpPublishersWithoutMaskingTheStartupFailure() {
        var startupFailure = new IllegalStateException("Simulated publisher startup failure");
        var first = new TestPublisher(null, true);
        var failing = new TestPublisher(startupFailure, false);
        var config = MetricsConfig.builder().addPublisher(PrometheusPublisher.create())
                .addPublisher(first).addPublisher(failing).build();
        var registriesBefore = Set.copyOf(Metrics.globalRegistry.getRegistries());
        var factory = MicrometerMetricsFactory.create(config, List.of());
        try {
            var failure = assertThrows(IllegalStateException.class, () -> factory.createMeterRegistry(config));
            assertThat(failure, sameInstance(startupFailure));
            assertThat(failure.getSuppressed().length, is(1));
            assertThat(first.closes, is(1));
            assertThat(failing.closes, is(1));
            assertThat(Metrics.globalRegistry.getRegistries(), is(registriesBefore));
        } finally {
            factory.close();
        }
    }

    private static class TestPublisher implements MetricsPublisher, AutoCloseable {
        private final RuntimeException startupFailure;
        private final boolean failClose;
        private int starts;
        private int closes;

        private TestPublisher(RuntimeException startupFailure, boolean failClose) {
            this.startupFailure = startupFailure;
            this.failClose = failClose;
        }

        @Override
        public void startPublishing() {
            starts++;
            if (startupFailure != null) {
                throw startupFailure;
            }
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public String type() {
            return "test";
        }

        @Override
        public void close() {
            closes++;
            if (failClose) {
                throw new IllegalStateException("Simulated publisher close failure");
            }
        }
    }
}
