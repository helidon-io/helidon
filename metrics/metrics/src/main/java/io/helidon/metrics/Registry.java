/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.metrics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Metrics registry.
 */
class Registry extends MetricRegistry {

    private final Type type;
    private final Map<MetricID, MetricImpl> allMetrics = new ConcurrentHashMap<>();

    protected Registry(Type type) {
        this.type = type;
    }

    public static Registry create(Type type) {
        return new Registry(type);
    }

    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        return register(toMetadata(name, metric), metric, (Tag[]) null);
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException {
        return register(metadata, metric, (Tag[]) null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Metric> T register(Metadata metadata, T metric, Tag... tags) throws IllegalArgumentException {
        return (T) getOptionalMetric(metadata, metric, tags);
    }

    @Override
    public Counter counter(String name) {
        return counter(new HelidonMetadata(name, MetricType.COUNTER));
    }

    @Override
    public Counter counter(Metadata metadata) {
        return counter(metadata, (Tag[]) null);
    }

    @Override
    public Counter counter(String name, Tag... tags) {
        return counter(new HelidonMetadata(name, MetricType.COUNTER), tags);
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return (Counter) getOptionalMetric(metadata,
                HelidonCounter.create(type.getName(), metadata),
                tags);
    }

    @Override
    public Histogram histogram(String name) {
        return histogram(new HelidonMetadata(name, MetricType.HISTOGRAM));
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return histogram(metadata, (Tag[]) null);
    }

    @Override
    public Histogram histogram(String name, Tag... tags) {
        return histogram(new HelidonMetadata(name, MetricType.HISTOGRAM), tags);
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return (Histogram) getOptionalMetric(metadata,
                HelidonHistogram.create(type.getName(), metadata),
                tags);
    }

    @Override
    public Meter meter(String name) {
        return meter(new HelidonMetadata(name, MetricType.METERED));
    }

    @Override
    public Meter meter(Metadata metadata) {
        return meter(metadata, (Tag[]) null);
    }

    @Override
    public Meter meter(String name, Tag... tags) {
        return meter(new HelidonMetadata(name, MetricType.METERED), tags);
    }

    @Override
    public Meter meter(Metadata metadata, Tag... tags) {
        return (Meter) getOptionalMetric(metadata,
                HelidonMeter.create(type.getName(), metadata),
                tags);
    }

    @Override
    public Timer timer(String name) {
        return timer(new HelidonMetadata(name, MetricType.TIMER));
    }

    @Override
    public Timer timer(Metadata metadata) {
        return timer(metadata, (Tag[]) null);
    }

    @Override
    public Timer timer(String name, Tag... tags) {
        return timer(new HelidonMetadata(name, MetricType.TIMER), tags);
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return (Timer) getOptionalMetric(metadata,
                HelidonTimer.create(type.getName(), metadata),
                tags);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name) {
        return concurrentGauge(new HelidonMetadata(name, MetricType.CONCURRENT_GAUGE));
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata) {
        return concurrentGauge(metadata, (Tag[]) null);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name, Tag... tags) {
        return concurrentGauge(new HelidonMetadata(name, MetricType.CONCURRENT_GAUGE), tags);

    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata, Tag... tags) {
        return (ConcurrentGauge) getOptionalMetric(metadata,
                HelidonConcurrentGauge.create(type.getName(), metadata),
                tags);
    }

    @Override
    public boolean remove(String name) {
        return remove(new MetricID(name));
    }

    @Override
    public boolean remove(MetricID metricID) {
        return allMetrics.remove(metricID) != null;
    }

    @Override
    public void removeMatching(MetricFilter filter) {
        allMetrics.entrySet().removeIf(entry -> filter.matches(entry.getKey(), entry.getValue()));
    }

    @Override
    public SortedSet<String> getNames() {
        return allMetrics.keySet().stream()
                .map(id -> id.getName())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public SortedSet<MetricID> getMetricIDs() {
        return new TreeSet<>(allMetrics.keySet());
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges() {
        return getGauges(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges(MetricFilter filter) {
        return getSortedMetrics(filter, Gauge.class);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters() {
        return getCounters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters(MetricFilter filter) {
        return getSortedMetrics(filter, Counter.class);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms() {
        return getHistograms(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms(MetricFilter filter) {
        return getSortedMetrics(filter, Histogram.class);
    }

    @Override
    public SortedMap<MetricID, Meter> getMeters() {
        return getMeters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Meter> getMeters(MetricFilter filter) {
        return getSortedMetrics(filter, Meter.class);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers() {
        return getTimers(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers(MetricFilter filter) {
        return getSortedMetrics(filter, Timer.class);
    }

    @Override
    public SortedMap<MetricID, ConcurrentGauge> getConcurrentGauges() {
        return getConcurrentGauges(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, ConcurrentGauge> getConcurrentGauges(MetricFilter filter) {
        return getSortedMetrics(filter, ConcurrentGauge.class);
    }

    @Override
    public Map<String, Metadata> getMetadata() {
        HashMap<String, Metadata> result = new HashMap<>();
        allMetrics.forEach((id, metric) -> result.put(id.getName(), metric));
        return result;
    }

    @Override
    public Map<MetricID, Metric> getMetrics() {
        HashMap<MetricID, Metric> result = new HashMap<>();
        allMetrics.forEach(result::put);
        return result;
    }

    // -- Public not overridden -----------------------------------------------

    public Stream<Map.Entry<MetricID, MetricImpl>> stream() {
        return allMetrics.entrySet().stream();
    }

    public String type() {
        return type.getName();
    }

    public boolean empty() {
        return allMetrics.isEmpty();
    }

    @Override
    public String toString() {
        return type() + ": " + allMetrics.size() + " metrics";
    }

    // -- Package private -----------------------------------------------------

    Optional<HelidonMetric> getOptionalMetric(String metricName) {
        return Optional.ofNullable(allMetrics.get(new MetricID(metricName)));
    }

    Optional<HelidonMetric> getOptionalMetric(MetricID metricID) {
        return Optional.ofNullable(allMetrics.get(metricID));
    }

    Type registryType() {
        return type;
    }

    // -- Private methods -----------------------------------------------------

    private <T extends Metric> MetricImpl getOptionalMetric(Metadata metadata, T newMetric, Tag... tags) {
        // If same name regardless of tags, must have same metadata
        Optional<Metadata> oldMetadata = findMetadataForName(metadata.getName());
        oldMetadata.ifPresent(m -> {
            if (!m.isReusable()) {
                throw new IllegalArgumentException("A metric of name '" + metadata.getName()
                        + "' already registered with non-reusable metadata");
            }
            // Check that metadata is compatible
            if (!m.getTypeRaw().equals(metadata.getTypeRaw())) {
                throw new IllegalArgumentException("A metric of name '" + metadata.getName()
                        + "' already registered with different metadata");
            }
        });

        // Now search for metric by ID including tags
        MetricID metricID = new MetricID(metadata.getName(), tags);
        MetricImpl metric = allMetrics.get(metricID);
        if (metric == null) {
            metric = toImpl(metadata, newMetric);
            allMetrics.put(metricID, metric);
        }
        return metric;
    }

    private <T extends Metric> MetricImpl toImpl(Metadata metadata, T metric) {
        switch (metadata.getTypeRaw()) {
            case COUNTER:
                return HelidonCounter.create(type.getName(), metadata, (Counter) metric);
            case GAUGE:
                return HelidonGauge.create(type.getName(), metadata, (Gauge<?>) metric);
            case HISTOGRAM:
                return HelidonHistogram.create(type.getName(), metadata, (Histogram) metric);
            case METERED:
                return HelidonMeter.create(type.getName(), metadata, (Meter) metric);
            case TIMER:
                return HelidonTimer.create(type.getName(), metadata, (Timer) metric);
            case CONCURRENT_GAUGE:
                return HelidonConcurrentGauge.create(type.getName(), metadata, (ConcurrentGauge) metric);
            case INVALID:
            default:
                throw new IllegalArgumentException("Unexpected metric type " + metadata.getType()
                        + ": " + metric.getClass().getName());
        }
    }

    private <T extends Metric> Metadata toMetadata(String name, T metric) {
        // Find subtype of Metric, needed for user-defined metrics
        Class<?> clazz = metric.getClass();
        do {
            Optional<Class<?>> optionalClass = Arrays.stream(clazz.getInterfaces())
                    .filter(c -> Metric.class.isAssignableFrom(c))
                    .findFirst();
            if (optionalClass.isPresent()) {
                clazz = optionalClass.get();
                break;
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        return new HelidonMetadata(name, MetricType.from(clazz == null ? metric.getClass() : clazz));
    }

    /**
     * Finds the metric type for a registered metric of the same name. All
     * metrics of same name, regardless of tags, must have the same type.
     *
     * @param name Metric name.
     * @return Metadata for name.
     */
    private Optional<Metadata> findMetadataForName(String name) {
        for (MetricID metricID : allMetrics.keySet()) {
            if (metricID.getName().equals(name)) {
                return Optional.of(allMetrics.get(metricID));
            }
        }
        return Optional.empty();
    }

    /**
     * Returns a sorted map based ona filter a metric class.
     *
     * @param filter The filter.
     * @param metricClass The class.
     * @param <V> Type of class.
     * @return The sorted map.
     */
    private <V> SortedMap<MetricID, V> getSortedMetrics(MetricFilter filter, Class<V> metricClass) {
        Map<MetricID, V> collected = allMetrics.entrySet()
                .stream()
                .filter(it -> metricClass.isAssignableFrom(it.getValue().getClass()))
                .filter(it -> filter.matches(it.getKey(), it.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, it -> metricClass.cast(it.getValue())));

        return new TreeMap<>(collected);
    }
}
