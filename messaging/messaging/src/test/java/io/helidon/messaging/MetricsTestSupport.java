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

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

/**
 * One test-owned registry and clock, with explicit graph and recorder lifetimes.
 */
final class MetricsTestSupport implements AutoCloseable {
    static final String DURATION = "messaging.processing.duration";
    static final String FAILURES = "messaging.processing.failures";

    final TestClock clock = new TestClock();
    final HelidonMetricsFactory factory;
    final MeterRegistry meters;
    final MeterRegistry registry;
    private final ServiceRegistryManager manager;
    private final List<MessagingMetrics> observations = new ArrayList<>();

    MetricsTestSupport() {
        this(MetricsConfig.create());
    }

    MetricsTestSupport(MetricsConfig config) {
        this(config, HelidonMetricsFactory.create());
    }

    MetricsTestSupport(HelidonMetricsFactory factory) {
        this(MetricsConfig.create(), factory);
    }

    MetricsTestSupport(MetricsConfig config, HelidonMetricsFactory factory) {
        this(config, factory, UnaryOperator.identity());
    }

    MetricsTestSupport(MetricsConfig config, UnaryOperator<MeterRegistry> decorate) {
        this(config, HelidonMetricsFactory.create(), decorate);
    }

    private MetricsTestSupport(MetricsConfig config,
                               HelidonMetricsFactory factory,
                               UnaryOperator<MeterRegistry> decorate) {
        this.factory = factory;
        meters = factory.createMeterRegistry(clock, config);
        registry = decorate.apply(meters);
        manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                .discoverServices(false)
                .discoverServicesFromServiceLoader(false)
                .putContractInstance(MeterRegistry.class, registry)
                .build());
    }

    @Override
    public void close() {
        try {
            observations.forEach(MessagingMetrics::close);
        } finally {
            try {
                manager.shutdown();
            } finally {
                factory.close();
            }
        }
    }

    MessagingConfig.Builder builder() {
        return MessagingGraph.builder().serviceRegistry(manager.registry()).health(h -> h.enabled(false));
    }

    MessagingGraph graph(MessagingConfig.Builder config) {
        return new MessagingGraphAssembler(config.serviceRegistry(manager.registry()).buildPrototype(),
                                           manager.registry()).graph();
    }

    MessagingGraph graph(MessagingChannel<String> channel) {
        return graph(builder().channel(channel).payloadSink(channel, payload -> {
            if (payload.equals("fail")) {
                throw new IllegalStateException("Processing failed");
            }
        })).start();
    }

    MessagingMetrics observe() {
        MessagingMetrics metrics = MessagingMetrics.create(builder().buildPrototype(), manager.registry());
        observations.add(metrics);
        return metrics;
    }

    List<Tag> tags(String channel) {
        return List.of(factory.tagCreate("channel", channel));
    }

    Timer timer(String channel) {
        return meters.timer(DURATION, tags(channel)).orElseThrow();
    }

    Counter counter(String channel) {
        return meters.counter(FAILURES, tags(channel)).orElseThrow();
    }

    static final class TestClock implements Clock {
        final AtomicInteger reads = new AtomicInteger();
        private final AtomicLong now = new AtomicLong();
        boolean fail;

        @Override
        public long wallTime() {
            return TimeUnit.NANOSECONDS.toMillis(now.get());
        }

        @Override
        public long monotonicTime() {
            reads.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("Clock unavailable");
            }
            return now.get();
        }

        void advance(long nanos) {
            now.addAndGet(nanos);
        }
    }
}
