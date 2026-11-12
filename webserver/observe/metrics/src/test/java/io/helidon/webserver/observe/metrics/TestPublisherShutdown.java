/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.observe.metrics;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.MetricsPublisher;
import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPublisherShutdown {
    @BeforeEach
    void resetKpiMetrics() {
        MetricsFactory.closeAll();
        KeyPerformanceIndicatorMetricsImpls.close();
    }

    @AfterEach
    void closeMetrics() {
        MetricsFactory.closeAll();
    }

    @Test
    void failedPublisherCloseDoesNotPreventIndependentCleanup() {
        var failing = new TestPublisher(false);
        var healthy = new TestPublisher(false) {
            @Override
            public void close() {
                closes.incrementAndGet();
            }
        };
        var registry = Metrics.globalRegistry();
        var config = MetricsObserverConfig.builder()
                .meterRegistry(registry)
                .metricsConfig(MetricsConfig.builder().addPublisher(failing).addPublisher(healthy).build())
                .buildPrototype();
        var routing = HttpRouting.builder();
        new MetricsFeature(config).register(routing, "/metrics");
        assertThat(registry.meters().stream().anyMatch(meter -> meter.id().name().startsWith("requests.")), is(true));

        var failure = assertThrows(IllegalStateException.class, routing.build()::afterStop);
        assertThat(failure.getSuppressed().length, is(1));
        assertThat(failing.closes.get(), is(1));
        assertThat(healthy.closes.get(), is(1));
        assertThat(registry.meters().stream().anyMatch(meter -> meter.id().name().startsWith("requests.")), is(false));
    }

    @Test
    void interruptedPublisherClosePreservesInterruption() {
        var publisher = new TestPublisher(true);
        var config = MetricsObserverConfig.builder()
                .meterRegistry(Metrics.globalRegistry())
                .metricsConfig(MetricsConfig.builder().addPublisher(publisher).build())
                .buildPrototype();
        var routing = HttpRouting.builder();
        new MetricsFeature(config).register(routing, "/metrics");
        try {
            assertThrows(IllegalStateException.class, routing.build()::afterStop);
            assertThat(Thread.currentThread().isInterrupted(), is(true));
        } finally {
            Thread.interrupted();
        }
    }

    private static class TestPublisher implements MetricsPublisher, AutoCloseable {
        final AtomicInteger closes = new AtomicInteger();
        private final boolean interrupt;

        private TestPublisher(boolean interrupt) {
            this.interrupt = interrupt;
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
        public void close() throws InterruptedException {
            closes.incrementAndGet();
            if (interrupt) {
                throw new InterruptedException("Simulated publisher close interruption");
            }
            throw new IllegalStateException("Simulated publisher close failure");
        }
    }
}
