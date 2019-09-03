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

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.metrics.InternalBridge;
import io.helidon.common.metrics.InternalBridge.MetricID;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Metrics registry.
 */
class Registry extends MetricRegistry implements io.helidon.common.metrics.InternalBridge.MetricRegistry {
    private final Type type;
    private final Map<String, MetricImpl> allMetrics = new ConcurrentHashMap<>();

    protected Registry(Type type) {
        this.type = type;
    }

    public static Registry create(Type type) {
        return new Registry(type);
    }

    static Metadata toMetadata(io.helidon.common.metrics.InternalBridge.Metadata metadata) {
        return toMetadata(metadata, metadata.getTags());
    }

    static Metadata toMetadata(io.helidon.common.metrics.InternalBridge.Metadata metadata, Map<String, String> tags) {
        return new Metadata(metadata.getName(), metadata.getDisplayName(),
        metadata.getDescription().orElse(null), metadata.getTypeRaw(),
                metadata.getUnit().orElse(null), tagsAsString(tags));
    }

    Optional<HelidonMetric> getMetric(String metricName) {
        return Optional.ofNullable(allMetrics.get(metricName));
    }

    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        return register(toImpl(name, metric));
    }

    @Override
    public <T extends Metric> T register(String name, T metric, Metadata metadata) throws IllegalArgumentException {
        return register(metadata, metric);
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException {
        return register(toImpl(metadata, metric));
    }

    @Override
    public <T extends Metric> T register(InternalBridge.Metadata metadata, T metric) throws IllegalArgumentException {
        return register(toMetadata(metadata), metric);
    }

    @Override
    public <T extends Metric> T register(InternalBridge.MetricID metricID, T metric) throws IllegalArgumentException {
        return register(metricID.getName(), metric);
    }

    @SuppressWarnings("unchecked")
    private <T extends Metric> T register(MetricImpl impl) throws IllegalArgumentException {
        MetricImpl existing = allMetrics.putIfAbsent(impl.getName(), impl);
        if (null != existing) {
            throw new IllegalArgumentException("Attempting to register duplicate metric. New: "
                                                       + impl
                                                       + ", existing: "
                                                       + existing);
        }

        return (T) impl;
    }

    @Override
    public Counter counter(String name) {
        return counter(new Metadata(name, MetricType.COUNTER));
    }

    @Override
    public Counter counter(Metadata metadata) {
        return getMetric(metadata, Counter.class, (name) -> HelidonCounter.create(type.getName(), metadata));
    }

    @Override
    public Counter counter(io.helidon.common.metrics.InternalBridge.Metadata metadata) {
        return counter(toMetadata(metadata));
    }

    @Override
    public Counter counter(io.helidon.common.metrics.InternalBridge.Metadata metadata, Map<String, String> tags) {
        return counter(toMetadata(metadata, tags));
    }

    @Override
    public Histogram histogram(String name) {
        return histogram(new Metadata(name, MetricType.HISTOGRAM));
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return getMetric(metadata, Histogram.class, (name) -> HelidonHistogram.create(type.getName(), metadata));
    }

    @Override
    public Histogram histogram(io.helidon.common.metrics.InternalBridge.Metadata metadata) {
        return histogram(toMetadata(metadata));
    }

    @Override
    public Histogram histogram(io.helidon.common.metrics.InternalBridge.Metadata metadata, Map<String, String> tags) {
        return histogram(toMetadata(metadata, tags));
    }

    @Override
    public Meter meter(String name) {
        return meter(new Metadata(name, MetricType.METERED));
    }

    @Override
    public Meter meter(Metadata metadata) {
        return getMetric(metadata, Meter.class, (name) -> HelidonMeter.create(type.getName(), metadata));
    }

    @Override
    public Meter meter(io.helidon.common.metrics.InternalBridge.Metadata metadata) {
        return meter(toMetadata(metadata));
    }

    @Override
    public Meter meter(io.helidon.common.metrics.InternalBridge.Metadata metadata, Map<String, String> tags) {
        return meter(toMetadata(metadata, tags));
    }

    @Override
    public Timer timer(String name) {
        return timer(new Metadata(name, MetricType.TIMER));
    }

    @Override
    public Timer timer(Metadata metadata) {
        return getMetric(metadata, Timer.class, (name) -> HelidonTimer.create(type.getName(), metadata));
    }

    @Override
    public Timer timer(io.helidon.common.metrics.InternalBridge.Metadata metadata) {
        return timer(toMetadata(metadata));
    }

    @Override
    public Timer timer(io.helidon.common.metrics.InternalBridge.Metadata metadata, Map<String, String> tags) {
        return timer(toMetadata(metadata, tags));
    }


    @Override
    public boolean remove(String name) {
        return allMetrics.remove(name) != null;
    }

    @Override
    public void removeMatching(MetricFilter filter) {
        allMetrics.entrySet().removeIf(entry -> filter.matches(entry.getKey(), entry.getValue()));
    }

    @Override
    public SortedSet<String> getNames() {
        return new TreeSet<>(allMetrics.keySet());
    }

    @Override
    public SortedMap<String, Gauge> getGauges() {
        return getGauges(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
        return getSortedMetrics(filter, Gauge.class);
    }

    @Override
    public SortedMap<String, Counter> getCounters() {
        return getCounters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Counter> getCounters(MetricFilter filter) {
        return getSortedMetrics(filter, Counter.class);
    }

    @Override
    public SortedMap<String, Histogram> getHistograms() {
        return getHistograms(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
        return getSortedMetrics(filter, Histogram.class);
    }

    @Override
    public SortedMap<String, Meter> getMeters() {
        return getMeters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Meter> getMeters(MetricFilter filter) {
        return getSortedMetrics(filter, Meter.class);
    }

    @Override
    public SortedMap<String, Timer> getTimers() {
        return getTimers(MetricFilter.ALL);
    }

    @Override
    public SortedMap<String, Timer> getTimers(MetricFilter filter) {
        return getSortedMetrics(filter, Timer.class);
    }

    @Override
    public Map<String, Metric> getMetrics() {
        return new HashMap<>(allMetrics);
    }

    @Override
    public Map<String, Metadata> getMetadata() {
        return new HashMap<>(allMetrics);
    }

    @Override
    public Map<InternalBridge.MetricID, Metric> getBridgeMetrics(
            Predicate<? super Map.Entry<? extends InternalBridge.MetricID, ? extends Metric>> predicate) {
        return allMetrics.entrySet().stream()
                .map(Registry::toBridgeEntry)
                .filter(predicate)
                .collect(HashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        Map::putAll);
    }

    @Override
    public Map<InternalBridge.MetricID, Metric> getBridgeMetrics() {
        return getBridgeMetrics(entry -> true);
    }

    @Override
    public SortedMap<InternalBridge.MetricID, Counter> getBridgeCounters() {
        return getBridgeMetrics(getCounters(), Counter.class);
    }

    @Override
    public SortedMap<InternalBridge.MetricID, Gauge> getBridgeGauges() {
        return getBridgeMetrics(getGauges(), Gauge.class);
    }

    @Override
    public SortedMap<InternalBridge.MetricID, Histogram> getBridgeHistograms() {
        return getBridgeMetrics(getHistograms(), Histogram.class);
    }

    @Override
    public SortedMap<io.helidon.common.metrics.InternalBridge.MetricID, Meter> getBridgeMeters() {
        return getBridgeMetrics(getMeters(), Meter.class);
    }

    @Override
    public SortedMap<io.helidon.common.metrics.InternalBridge.MetricID, Timer> getBridgeTimers() {
        return getBridgeMetrics(getTimers(), Timer.class);
    }

    @Override
    public Optional<Map.Entry<? extends MetricID, ? extends Metric>> getBridgeMetric(String metricName) {
        return Optional.ofNullable(allMetrics.get(metricName))
                .map(Registry::toBridgeEntry);
    }

    private static <T extends Metric> SortedMap<MetricID, T>
            getBridgeMetrics(SortedMap<String, T> metrics, Class<T> clazz) {
        return metrics.entrySet().stream()
                .map(Registry::toBridgeEntry)
                .filter(entry -> clazz.isAssignableFrom(entry.getValue().getClass()))
                .collect(TreeMap::new,
                        (map, entry) -> map.put(entry.getKey(), clazz.cast(entry.getValue())),
                        Map::putAll);
    }

    private static Map.Entry<? extends MetricID,
                        ? extends Metric> toBridgeEntry(
            Map.Entry<String, ? extends Metric> entry) {
        /*
         * Copy the tags from the metric into the neutral key's tags.
         */
        MetricImpl metricImpl = MetricImpl.class.cast(entry.getValue());
        return new AbstractMap.SimpleEntry<>(new InternalMetricIDImpl(
                    entry.getKey(), metricImpl.getTags()), metricImpl);
    }

    private static <T extends MetricImpl> Map.Entry<? extends MetricID, T> toBridgeEntry(T metric) {
        return new AbstractMap.SimpleEntry<>(new InternalMetricIDImpl(metric.getName(), metric.getTags()), metric);
    }

    public Stream<? extends HelidonMetric> stream() {
        return allMetrics.values().stream();
    }

    public String type() {
        return type.getName();
    }

    public boolean empty() {
        return allMetrics.isEmpty();
    }

    Type registryType() {
        return type;
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
        case INVALID:
        default:
            throw new IllegalArgumentException("Unexpected metric type " + metadata.getType() + ": " + metric.getClass()
                    .getName());
        }

    }

    private <T extends Metric> MetricImpl toImpl(String name, T metric) {
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

        return toImpl(new Metadata(name, MetricType.from(clazz == null ? metric.getClass() : clazz)), metric);
    }

    @Override
    public String toString() {
        return type() + ": " + allMetrics.size() + " metrics";
    }

    private <V> SortedMap<String, V> getSortedMetrics(MetricFilter filter, Class<V> metricClass) {
        Map<String, V> collected = allMetrics.entrySet()
                .stream()
                .filter(it -> metricClass.isAssignableFrom(it.getValue().getClass()))
                .filter(it -> filter.matches(it.getKey(), it.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, it -> metricClass.cast(it.getValue())));

        return new TreeMap<>(collected);
    }

    private <T extends Metric, I extends MetricImpl> T getMetric(Metadata metadata,
                                                                 Class<T> type,
                                                                 Function<String, I> newInstanceCreator) {
        MetricImpl metric = allMetrics.get(metadata.getName());
        if (metric != null) {
            if (metric.isReusable() != metadata.isReusable()) {
                throw new IllegalArgumentException("Metadata not re-usable for metric " + metadata.getName());
            }
        } else {
            metric = newInstanceCreator.apply(metadata.getName());
            metric.setReusable(metadata.isReusable());
            allMetrics.put(metadata.getName(), metric);
        }
        if (!(type.isAssignableFrom(metric.getClass()))) {
            throw new IllegalArgumentException("Attempting to get " + metadata.getType()
                                               + ", but metric registered under this name is "
                                               + metric);
        }

        return type.cast(metric);
    }

    static String tagsAsString(Map<String, String> tags) {
        return tags.entrySet().stream()
                .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(","));
    }

}
