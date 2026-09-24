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

package io.helidon.metrics.publishers.otlp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.metrics.api.Bucket;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.publishers.otlp.OtlpRequest.AnyValue;
import io.helidon.metrics.publishers.otlp.OtlpRequest.HistogramDataPoint;
import io.helidon.metrics.publishers.otlp.OtlpRequest.InstrumentationScope;
import io.helidon.metrics.publishers.otlp.OtlpRequest.KeyValue;
import io.helidon.metrics.publishers.otlp.OtlpRequest.Metric;
import io.helidon.metrics.publishers.otlp.OtlpRequest.NumberDataPoint;
import io.helidon.metrics.publishers.otlp.OtlpRequest.Resource;
import io.helidon.metrics.publishers.otlp.OtlpRequest.ResourceMetrics;
import io.helidon.metrics.publishers.otlp.OtlpRequest.ScopeMetrics;

// Each publishing session serializes access to its encoder.
final class OtlpEncoder implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(OtlpEncoder.class.getName());
    private static final InstrumentationScope SCOPE = new InstrumentationScope("io.helidon.metrics");
    private static final int AGGREGATION_TEMPORALITY_CUMULATIVE = 2;

    private final MeterRegistry registry;
    private final Resource resource;
    private final SystemTagsManager systemTagsManager;
    private final Map<Meter, SeriesState> states = new IdentityHashMap<>();
    private final Map<String, Set<MetricKey>> conflictingIdentities = new HashMap<>();
    private long lastCollectionTime;
    private boolean closed;

    OtlpEncoder(MeterRegistry registry, MetricsConfig metricsConfig, Map<String, String> resourceAttributes) {
        this.registry = Objects.requireNonNull(registry);
        this.systemTagsManager = SystemTagsManager.create(Objects.requireNonNull(metricsConfig),
                                                         registry.metricsFactory());
        this.resource = new Resource(attributes(new TreeMap<>(Objects.requireNonNull(resourceAttributes))));
    }

    OtlpRequest collect() {
        if (closed) {
            throw new IllegalStateException("OTLP encoder is closed");
        }
        long now = Math.max(lastCollectionTime + 1, TimeUnit.MILLISECONDS.toNanos(registry.clock().wallTime()));
        lastCollectionTime = now;
        List<Meter> meters = registry.meters();
        Set<Meter> currentMeters = Collections.newSetFromMap(new IdentityHashMap<>());
        currentMeters.addAll(meters);
        states.keySet().retainAll(currentMeters);
        Map<MetricKey, MetricAccumulator> metrics = new LinkedHashMap<>();
        for (Meter meter : meters) {
            if (Thread.currentThread().isInterrupted()) {
                return OtlpRequest.EMPTY;
            }
            if (registry.isDeleted(meter)) {
                states.remove(meter);
                continue;
            }
            try {
                switch (meter) {
                case Counter counter -> counter(metrics, counter, counter.count(), now);
                case FunctionalCounter counter -> counter(metrics, counter, counter.count(), now);
                case Gauge<?> gauge -> gauge(metrics, gauge, now);
                case Timer timer -> histogram(metrics, timer, timer.snapshot(), true, now);
                case DistributionSummary summary -> histogram(metrics, summary, summary.snapshot(), false, now);
                default -> {
                    // There is no OTLP representation for an unknown meter implementation.
                }
                }
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.WARNING, "Cannot collect meter " + meter.id().name(), e);
            }
        }
        warnConflictingIdentities(metrics.keySet());
        if (metrics.isEmpty()) {
            return OtlpRequest.EMPTY;
        }
        var scope = new ScopeMetrics(SCOPE, metrics.values().stream().map(MetricAccumulator::toMetric).toList());
        return new OtlpRequest(List.of(new ResourceMetrics(resource, List.of(scope))));
    }

    @Override
    public void close() {
        closed = true;
        states.clear();
        conflictingIdentities.clear();
    }

    private static List<KeyValue> attributes(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> new KeyValue(entry.getKey(), new AnyValue(entry.getValue())))
                .toList();
    }

    private static MetricAccumulator metric(Map<MetricKey, MetricAccumulator> metrics, Meter meter, Kind kind, String unit) {
        var key = new MetricKey(meter.id().name(), unit, kind);
        MetricAccumulator result = metrics.computeIfAbsent(key, MetricAccumulator::new);
        if (result.description.isEmpty()) {
            meter.description().ifPresent(description -> result.description = description);
        }
        return result;
    }

    private void warnConflictingIdentities(Set<MetricKey> metricKeys) {
        Map<String, MetricKey> firstByName = new HashMap<>();
        Map<String, Set<MetricKey>> currentConflicts = new LinkedHashMap<>();
        for (MetricKey key : metricKeys) {
            MetricKey first = firstByName.putIfAbsent(key.name(), key);
            if (first != null) {
                Set<MetricKey> identities = currentConflicts.computeIfAbsent(key.name(), _ -> new LinkedHashSet<>());
                identities.add(first);
                identities.add(key);
            }
        }
        currentConflicts.forEach((name, identities) -> {
            if (!identities.equals(conflictingIdentities.get(name))) {
                LOGGER.log(System.Logger.Level.WARNING,
                           "Conflicting OTLP metric identities for " + name + ": " + identities
                                   + ". Exporting all conflicting data.");
            }
        });
        conflictingIdentities.clear();
        conflictingIdentities.putAll(currentConflicts);
    }

    private void counter(Map<MetricKey, MetricAccumulator> metrics, Meter meter, long count, long now) {
        if (count < 0) {
            return;
        }
        var point = new NumberDataPoint(attributes(meter),
                                        Long.toString(state(meter, count, now).startTime),
                                        Long.toString(now),
                                        Long.toString(count),
                                        null);
        metric(metrics, meter, Kind.SUM, meter.baseUnit().orElse(""))
                .numberDataPoints.add(point);
    }

    private void gauge(Map<MetricKey, MetricAccumulator> metrics, Gauge<?> gauge, long now) {
        Number value = gauge.value();
        String asInt = null;
        Double asDouble = null;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof AtomicInteger || value instanceof AtomicLong) {
            asInt = Long.toString(value.longValue());
        } else {
            // OTLP doubles preserve NaN and infinities, including unavailable gauge values.
            asDouble = value.doubleValue();
        }
        var point = new NumberDataPoint(attributes(gauge), null, Long.toString(now), asInt, asDouble);
        metric(metrics, gauge, Kind.GAUGE, gauge.baseUnit().orElse(""))
                .numberDataPoints.add(point);
    }

    private void histogram(Map<MetricKey, MetricAccumulator> metrics,
                           Meter meter,
                           HistogramSnapshot snapshot,
                           boolean timer,
                           long now) {
        long count = snapshot.count();
        if (count < 0) {
            return;
        }
        double scale = timer ? 1D / TimeUnit.SECONDS.toNanos(1) : 1D;
        String startTime = Long.toString(state(meter, count, now).startTime);
        List<KeyValue> attributes = attributes(meter);
        double sum = count == 0 ? 0 : snapshot.total() * scale;
        double max = snapshot.max() * scale;
        List<Double> explicitBounds = new ArrayList<>();
        List<String> bucketCounts = new ArrayList<>();
        long previousCount = 0;
        double previousBoundary = Double.NEGATIVE_INFINITY;
        for (Bucket bucket : snapshot.histogramCounts()) {
            double boundary = bucket.boundary() * scale;
            if (!Double.isFinite(boundary) || boundary <= previousBoundary) {
                continue;
            }
            // Helidon's adders are sampled independently. Keep the wire representation consistent even if recording
            // advances bucket counts after the snapshot's total count was read.
            long cumulativeCount = Math.clamp(bucket.count(), previousCount, count);
            explicitBounds.add(boundary);
            bucketCounts.add(Long.toString(cumulativeCount - previousCount));
            previousCount = cumulativeCount;
            previousBoundary = boundary;
        }
        if (!explicitBounds.isEmpty()) {
            bucketCounts.add(Long.toString(count - previousCount));
        }
        var point = new HistogramDataPoint(attributes,
                                           startTime,
                                           Long.toString(now),
                                           Long.toString(count),
                                           Double.isFinite(sum) ? sum : null,
                                           count != 0 && Double.isFinite(max) ? max : null,
                                           explicitBounds,
                                           bucketCounts);
        // OTLP permits count and sum without buckets. Reservoir percentiles are not histogram populations.
        metric(metrics, meter, Kind.HISTOGRAM, timer ? "s" : meter.baseUnit().orElse(""))
                .histogramDataPoints.add(point);
    }

    private List<KeyValue> attributes(Meter meter) {
        Map<String, String> values = new TreeMap<>();
        systemTagsManager.withoutSystemTags(meter.id().tags()).forEach(tag -> values.put(tag.key(), tag.value()));
        systemTagsManager.displayTags().forEach(tag -> values.put(tag.key(), tag.value()));
        return attributes(values);
    }

    private SeriesState state(Meter meter, long count, long now) {
        SeriesState state = states.computeIfAbsent(meter, _ -> new SeriesState(now));
        if (count < state.lastCount) {
            state.startTime = now;
        }
        state.lastCount = count;
        return state;
    }

    private enum Kind {
        SUM,
        GAUGE,
        HISTOGRAM
    }

    private record MetricKey(String name, String unit, Kind kind) {
    }

    private static final class MetricAccumulator {
        private final MetricKey key;
        private final List<NumberDataPoint> numberDataPoints = new ArrayList<>();
        private final List<HistogramDataPoint> histogramDataPoints = new ArrayList<>();
        private String description = "";

        private MetricAccumulator(MetricKey key) {
            this.key = key;
        }

        private Metric toMetric() {
            return switch (key.kind()) {
            case SUM -> new Metric(key.name(), description, key.unit(),
                                   new OtlpRequest.Sum(numberDataPoints, AGGREGATION_TEMPORALITY_CUMULATIVE, true),
                                   null, null);
            case GAUGE -> new Metric(key.name(), description, key.unit(), null, new OtlpRequest.Gauge(numberDataPoints), null);
            case HISTOGRAM -> new Metric(key.name(), description, key.unit(), null, null,
                                         new OtlpRequest.Histogram(histogramDataPoints, AGGREGATION_TEMPORALITY_CUMULATIVE));
            };
        }
    }

    private static final class SeriesState {
        private long startTime;
        private long lastCount;

        private SeriesState(long startTime) {
            // Meter creation timestamps are unavailable. A zero-duration initial point marks an unknown start.
            this.startTime = startTime;
        }
    }
}
