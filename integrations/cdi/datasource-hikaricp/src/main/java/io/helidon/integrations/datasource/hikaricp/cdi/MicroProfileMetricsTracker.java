/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.datasource.hikaricp.cdi;

import java.time.Duration;
import java.util.Objects;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.PoolStats;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;

final class MicroProfileMetricsTracker implements IMetricsTracker {

    private static final String HIKARI_METRIC_NAME_PREFIX = "hikaricp";

    private static final String METRIC_CATEGORY = "pool";

    private static final String METRIC_NAME_WAIT = HIKARI_METRIC_NAME_PREFIX + ".connections.acquisition";

    private static final String METRIC_NAME_USAGE = HIKARI_METRIC_NAME_PREFIX + ".connections.usage";

    private static final String METRIC_NAME_CONNECT = HIKARI_METRIC_NAME_PREFIX + ".connections.creation";

    private static final String METRIC_NAME_TIMEOUT_RATE = HIKARI_METRIC_NAME_PREFIX + ".connections.timeout";

    private static final String METRIC_NAME_TOTAL_CONNECTIONS = HIKARI_METRIC_NAME_PREFIX + ".connections";

    private static final String METRIC_NAME_IDLE_CONNECTIONS = HIKARI_METRIC_NAME_PREFIX + ".connections.idle";

    private static final String METRIC_NAME_ACTIVE_CONNECTIONS = HIKARI_METRIC_NAME_PREFIX + ".connections.active";

    private static final String METRIC_NAME_PENDING_CONNECTIONS = HIKARI_METRIC_NAME_PREFIX + ".connections.pending";

    private static final String METRIC_NAME_MAX_CONNECTIONS = HIKARI_METRIC_NAME_PREFIX + ".connections.max";

    private static final String METRIC_NAME_MIN_CONNECTIONS = HIKARI_METRIC_NAME_PREFIX + ".connections.min";

    private final Tag metricCategoryTag;

    private final MetricRegistry registry;

    private final SimpleTimer connectionAcquisitionTimer;

    private final SimpleTimer connectionCreationTimer;

    private final Histogram connectionUsageHistogram;

    private final Counter connectionTimeoutCounter;

    MicroProfileMetricsTracker(final String poolName, final PoolStats poolStats, final MetricRegistry registry) {
        super();
        Objects.requireNonNull(poolStats, "poolStats");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.metricCategoryTag = new Tag(METRIC_CATEGORY, Objects.requireNonNull(poolName, "poolName"));
        this.connectionAcquisitionTimer =
            registry.simpleTimer(Metadata.builder()
                                 .withName(METRIC_NAME_WAIT)
                                 .withDescription("Connection acquire time")
                                 .withUnit(MetricUnits.NANOSECONDS)
                                 .build(),
                                 this.metricCategoryTag);
        this.connectionCreationTimer =
            registry.simpleTimer(Metadata.builder()
                                 .withName(METRIC_NAME_CONNECT)
                                 .withDescription("Connection creation time")
                                 .withUnit(MetricUnits.MILLISECONDS)
                                 .build(),
                                 this.metricCategoryTag);
        this.connectionUsageHistogram =
            registry.histogram(Metadata.builder()
                               .withName(METRIC_NAME_USAGE)
                               .withDescription("Connection usage time")
                               .withUnit(MetricUnits.MILLISECONDS)
                               .build(),
                               this.metricCategoryTag);
        this.connectionTimeoutCounter =
            registry.counter(Metadata.builder()
                             .withName(METRIC_NAME_TIMEOUT_RATE)
                             .withDescription("Connection timeout total count")
                             .withUnit("connections")
                             .build(),
                             this.metricCategoryTag);
        registry.<Gauge<Integer>>register(Metadata.builder()
                                          .withName(METRIC_NAME_TOTAL_CONNECTIONS)
                                          .withDescription("Total connections")
                                          .withUnit("connections")
                                          .build(),
                                          poolStats::getTotalConnections,
                                          metricCategoryTag);
        registry.<Gauge<Integer>>register(Metadata.builder()
                                          .withName(METRIC_NAME_IDLE_CONNECTIONS)
                                          .withDescription("Idle connections")
                                          .withUnit("connections")
                                          .build(),
                                          poolStats::getIdleConnections,
                                          metricCategoryTag);
        registry.<Gauge<Integer>>register(Metadata.builder()
                                          .withName(METRIC_NAME_ACTIVE_CONNECTIONS)
                                          .withDescription("Active connections")
                                          .withUnit("connections")
                                          .build(),
                                          poolStats::getActiveConnections,
                                          metricCategoryTag);
        // All of the pre-existing Hikari metrics implementations call
        // this "Pending connections" even though
        // PoolStats#getPendingThreads() is referenced.  We follow suit.
        registry.<Gauge<Integer>>register(Metadata.builder()
                                          .withName(METRIC_NAME_PENDING_CONNECTIONS)
                                          .withDescription("Pending connections")
                                          .withUnit("connections")
                                          .build(),
                                          poolStats::getPendingThreads,
                                          metricCategoryTag);
        registry.<Gauge<Integer>>register(Metadata.builder()
                                          .withName(METRIC_NAME_MAX_CONNECTIONS)
                                          .withDescription("Max connections")
                                          .withUnit("connections")
                                          .build(),
                                          poolStats::getMaxConnections,
                                          metricCategoryTag);
        registry.<Gauge<Integer>>register(Metadata.builder()
                                          .withName(METRIC_NAME_MIN_CONNECTIONS)
                                          .withDescription("Min connections")
                                          .withUnit("connections")
                                          .build(),
                                          poolStats::getMinConnections,
                                          metricCategoryTag);
    }

    @Override
    public void recordConnectionAcquiredNanos(final long elapsedAcquiredNanos) {
        this.connectionAcquisitionTimer.update(Duration.ofNanos(elapsedAcquiredNanos));
    }

    @Override
    public void recordConnectionCreatedMillis(final long connectionCreatedMillis) {
        this.connectionCreationTimer.update(Duration.ofMillis(connectionCreatedMillis));
    }

    @Override
    public void recordConnectionUsageMillis(final long elapsedBorrowedMillis) {
        this.connectionUsageHistogram.update(elapsedBorrowedMillis);
    }

    @Override
    public void recordConnectionTimeout() {
        this.connectionTimeoutCounter.inc();
    }

    @Override
    public void close() {
        this.registry.remove(new MetricID(METRIC_NAME_WAIT, this.metricCategoryTag));
        this.registry.remove(new MetricID(METRIC_NAME_CONNECT, this.metricCategoryTag));
        this.registry.remove(new MetricID(METRIC_NAME_USAGE, this.metricCategoryTag));
        this.registry.remove(new MetricID(METRIC_NAME_TIMEOUT_RATE, this.metricCategoryTag));

        this.registry.remove(new MetricID(METRIC_NAME_TOTAL_CONNECTIONS, this.metricCategoryTag));
        this.registry.remove(new MetricID(METRIC_NAME_IDLE_CONNECTIONS, this.metricCategoryTag));
        this.registry.remove(new MetricID(METRIC_NAME_ACTIVE_CONNECTIONS, this.metricCategoryTag));
        this.registry.remove(new MetricID(METRIC_NAME_PENDING_CONNECTIONS, this.metricCategoryTag));
        this.registry.remove(new MetricID(METRIC_NAME_MAX_CONNECTIONS, this.metricCategoryTag));
        this.registry.remove(new MetricID(METRIC_NAME_MIN_CONNECTIONS, this.metricCategoryTag));
    }

}
