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

import io.helidon.config.Config;
import io.helidon.metrics.RegistryFactory;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.neo4j.driver.ConnectionPoolMetrics;
import org.neo4j.driver.Driver;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Map.entry;

public class Neo4JSupport implements Service {

    private static final String NEO4J_METRIC_NAME_PREFIX = "neo4j.";

    private Optional<Driver> driver = Optional.empty();

    private Optional<ConnectionPoolMetrics> connectionPoolMetrics = Optional.empty();

    private Neo4JSupport(Builder builder) {
        initNeo4JMetrics();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Neo4JSupport create() {
        return builder().build();
    }

    public static Neo4JSupport create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public void update(Routing.Rules rules) {
        // If Neo4J support in Helidon adds no new endpoints,
        // then we do not need to do anything here.
    }

    public static class Builder implements io.helidon.common.Builder<Neo4JSupport> {

        private Config config; //

        private Builder() {

        }


        @Override
        public Neo4JSupport build() {
            return new Neo4JSupport(this);
        }

        public Builder config(Config config) {
            // harvest Neo4J config information from Helidon config.
            this.config = config;
            return this;
        }
    }

    private void initNeo4JMetrics() {
        // Assuming for the moment that VENDOR is the correct registry to use.
        MetricRegistry neo4JMetricRegistry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.VENDOR);

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

        counters.forEach((name, supplier) -> registerCounter(neo4JMetricRegistry, name, supplier));
        gauges.forEach((name, supplier) -> registerGauge(neo4JMetricRegistry, name, supplier, 0));
    }

    private synchronized Optional<ConnectionPoolMetrics> getConnectionPoolMetrics() {
        if (!connectionPoolMetrics.isPresent()) {
            // TODO - remove the driver.isPresent check once driver is set using config
            if (driver.isPresent()) {
                connectionPoolMetrics = driver
                        .get()
                        .metrics()
                        .connectionPoolMetrics()
                        .stream()
                        .findFirst();
            }
        }
        return connectionPoolMetrics;
    }

    private void registerCounter(MetricRegistry metricRegistry, String name, Function<ConnectionPoolMetrics, Long> fn) {
        Metadata metadata = Metadata.builder()
                .withName(NEO4J_METRIC_NAME_PREFIX + name)
                .withType(MetricType.COUNTER)
                .notReusable()
                .build();
        Neo4JCounterWrapper wrapper = new Neo4JCounterWrapper(() -> getConnectionPoolMetrics().map(fn).orElse(0L));
        metricRegistry.register(metadata, wrapper);
    }

    private void registerGauge(MetricRegistry metricRegistry, String name, Function<ConnectionPoolMetrics, Integer> fn,
                               int defaultValue) {
        Metadata metadata = Metadata.builder()
                .withName(NEO4J_METRIC_NAME_PREFIX + name)
                .withType(MetricType.GAUGE)
                .notReusable()
                .build();
        Neo4JGaugeWrapper wrapper =
                new Neo4JGaugeWrapper(() -> getConnectionPoolMetrics().map(fn).orElse(defaultValue));
        metricRegistry.register(metadata, wrapper);
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

    private static class Neo4JGaugeWrapper implements Gauge<Integer> {

        private final Supplier<Integer> supplier;

        private Neo4JGaugeWrapper(Supplier<Integer> supplier) {
            this.supplier = supplier;
        }

        @Override
        public Integer getValue() {
            return supplier.get();
        }
    }
}