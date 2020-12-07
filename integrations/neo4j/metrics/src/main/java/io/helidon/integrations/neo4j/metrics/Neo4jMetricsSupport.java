/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.integrations.neo4j.metrics;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.metrics.RegistryFactory;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.neo4j.driver.ConnectionPoolMetrics;
import org.neo4j.driver.Driver;

import static java.util.Map.entry;

/**
 * Neo4j helper class to support metrics. Provided as a separate package to be included as a dependency.
 */
public class Neo4jMetricsSupport {

    private static final String NEO4J_METRIC_NAME_PREFIX = "neo4j";

    private final AtomicReference<Collection<ConnectionPoolMetrics>> lastPoolMetrics = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> reinitFuture = new AtomicReference<>();
    private final AtomicReference<Boolean> metricsInitialized = new AtomicReference<>(false);
    private final LazyValue<MetricRegistry> metricRegistry;
    private final Driver driver;

    private Neo4jMetricsSupport(Builder builder) {
        this.driver = builder.driver;
        // Assuming for the moment that VENDOR is the correct registry to use.
        metricRegistry = LazyValue.create(() -> RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.VENDOR));
    }

    /**
     * Following the builder pattern.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Register metrics and start monitoring.
     */
    public void initialize() {
        this.lastPoolMetrics.set(driver.metrics().connectionPoolMetrics());

        reinit();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "neo4j-metrics-init"));
        reinitFuture.set(executor.scheduleAtFixedRate(() -> maybeRefreshMetrics(executor), 10, 10, TimeUnit.SECONDS));
    }

    private void maybeRefreshMetrics(ScheduledExecutorService executor) {
        Collection<ConnectionPoolMetrics> currentPoolMetrics = driver.metrics().connectionPoolMetrics();

        if (!metricsInitialized.get() && currentPoolMetrics.size() >= 1) {
            lastPoolMetrics.set(currentPoolMetrics);
            reinit();

            if (metricsInitialized.get()) {
                reinitFuture.get().cancel(false);
                executor.shutdown();
            }
        }
    }

    private void reinit() {
        Map<String, Function<ConnectionPoolMetrics, Long>> counters = Map.ofEntries(
                entry("acquired", ConnectionPoolMetrics::acquired),
                entry("closed", ConnectionPoolMetrics::closed),
                entry("created", ConnectionPoolMetrics::created),
                entry("failedToCreate", ConnectionPoolMetrics::failedToCreate),
                entry("timedOutToAcquire", ConnectionPoolMetrics::timedOutToAcquire),
                entry("totalAcquisitionTime", ConnectionPoolMetrics::totalAcquisitionTime),
                entry("totalConnectionTime", ConnectionPoolMetrics::totalConnectionTime),
                entry("totalInUseCount", ConnectionPoolMetrics::totalInUseCount),
                entry("totalInUseTime", ConnectionPoolMetrics::totalInUseTime));

        Map<String, Function<ConnectionPoolMetrics, Integer>> gauges = Map.ofEntries(
                entry("acquiring", ConnectionPoolMetrics::acquiring),
                entry("creating", ConnectionPoolMetrics::creating),
                entry("idle", ConnectionPoolMetrics::idle),
                entry("inUse", ConnectionPoolMetrics::inUse)
        );
        for (ConnectionPoolMetrics it : lastPoolMetrics.get()) {
            //String poolPrefix = NEO4J_METRIC_NAME_PREFIX + "-" + it.id() + "-";
            String poolPrefix = NEO4J_METRIC_NAME_PREFIX + "-";
            counters.forEach((name, supplier) -> registerCounter(metricRegistry.get(), it, poolPrefix, name, supplier));
            gauges.forEach((name, supplier) -> registerGauge(metricRegistry.get(), it, poolPrefix, name, supplier));
            // we only care about the first one
            metricsInitialized.set(true);
            break;
        }
    }

    private void registerCounter(MetricRegistry metricRegistry,
                                 ConnectionPoolMetrics cpm,
                                 String poolPrefix,
                                 String name,
                                 Function<ConnectionPoolMetrics, Long> fn) {

        String counterName = poolPrefix + name;
        if (metricRegistry.getCounters().get(new MetricID(counterName)) == null) {
            Metadata metadata = Metadata.builder()
                    .withName(counterName)
                    .withType(MetricType.COUNTER)
                    .notReusable()
                    .build();
            Neo4JCounterWrapper wrapper = new Neo4JCounterWrapper(() -> fn.apply(cpm));
            metricRegistry.register(metadata, wrapper);
        }
    }

    private void registerGauge(MetricRegistry metricRegistry,
                               ConnectionPoolMetrics cpm,
                               String poolPrefix,
                               String name,
                               Function<ConnectionPoolMetrics, Integer> fn) {

        String gaugeName = poolPrefix + name;
        if (metricRegistry.getGauges().get(new MetricID(gaugeName)) == null) {
            Metadata metadata = Metadata.builder()
                    .withName(poolPrefix + name)
                    .withType(MetricType.GAUGE)
                    .notReusable()
                    .build();
            Neo4JGaugeWrapper<Integer> wrapper =
                    new Neo4JGaugeWrapper<>(() -> fn.apply(cpm));
            metricRegistry.register(metadata, wrapper);
        }
    }

    /**
     * Fluent API builder for Neo4jMetricsSupport.
     */
    public static class Builder implements io.helidon.common.Builder<Neo4jMetricsSupport> {

        private Driver driver;

        private Builder() {
        }

        /**
         * Builder for the wrapper class.
         *
         * @return wrapper
         */
        public Neo4jMetricsSupport build() {
            Objects.requireNonNull(driver, "Must set driver before building");
            return new Neo4jMetricsSupport(this);
        }

        /**
         * Submit the Neo4j driver.
         *
         * @param driver from the support class
         * @return Builder
         */
        public Builder driver(Driver driver) {
            this.driver = driver;
            return this;
        }
    }

    private static class Neo4JCounterWrapper implements Counter {

        private final Supplier<Long> fn;

        private Neo4JCounterWrapper(Supplier<Long> fn) {
            this.fn = fn;
        }

        @Override
        public void inc() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void inc(long n) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getCount() {
            return fn.get();
        }
    }

    private static class Neo4JGaugeWrapper<T> implements Gauge<T> {

        private final Supplier<T> supplier;

        private Neo4JGaugeWrapper(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T getValue() {
            return supplier.get();
        }
    }
}
