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
import java.util.function.Function;
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

    Optional<HelidonMetric> getMetric(String metricName) {
        return Optional.ofNullable(allMetrics.get(metricName));
    }

    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        return register(toImpl(name, metric));
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException {
        return register(toImpl(metadata, metric));
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric, Tag... tags) throws IllegalArgumentException {
        return register(toImpl(metadata, metric), tags);
    }

    @SuppressWarnings("unchecked")
    private <T extends Metric> T register(MetricImpl impl, Tag... tags) throws IllegalArgumentException {
        MetricImpl existing = allMetrics.putIfAbsent(new MetricID(impl.getName(), tags), impl);
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
        return counter(new HelidonMetadata(name, MetricType.COUNTER));
    }

    @Override
    public Counter counter(Metadata metadata) {
        return getMetric(metadata,
                Counter.class,
                name -> HelidonCounter.create(type.getName(), metadata));
    }

    @Override
    public Counter counter(String name, Tag... tags) {
        return counter(new HelidonMetadata(name, MetricType.COUNTER), tags);
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return getMetric(metadata,
                Counter.class,
                name -> HelidonCounter.create(type.getName(), metadata),
                tags);
    }

    @Override
    public Histogram histogram(String name) {
        return histogram(new HelidonMetadata(name, MetricType.HISTOGRAM));
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return getMetric(metadata,
                Histogram.class,
                name -> HelidonHistogram.create(type.getName(), metadata));
    }
    
    @Override
    public Histogram histogram(String name, Tag... tags) {
        return histogram(new HelidonMetadata(name, MetricType.HISTOGRAM), tags);
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return getMetric(metadata,
                Histogram.class,
                name -> HelidonCounter.create(type.getName(), metadata),
                tags);
    }

    @Override
    public Meter meter(String name) {
        return meter(new HelidonMetadata(name, MetricType.METERED));
    }

    @Override
    public Meter meter(Metadata metadata) {
        return getMetric(metadata, 
                Meter.class, 
                name -> HelidonMeter.create(type.getName(), metadata));
    }
    
    @Override
    public Meter meter(String name, Tag... tags) {
        return meter(new HelidonMetadata(name, MetricType.METERED), tags);
    }

    @Override
    public Meter meter(Metadata metadata, Tag... tags) {
        return getMetric(metadata,
                Meter.class,
                name -> HelidonCounter.create(type.getName(), metadata),
                tags);    
    }
    
    @Override
    public Timer timer(String name) {
        return timer(new HelidonMetadata(name, MetricType.TIMER));
    }

    @Override
    public Timer timer(Metadata metadata) {
        return getMetric(metadata,
                Timer.class,
                name -> HelidonTimer.create(type.getName(), metadata));
    }

    @Override
    public Timer timer(String name, Tag... tags) {
        return timer(new HelidonMetadata(name, MetricType.TIMER), tags);
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return getMetric(metadata,
                Timer.class,
                name -> HelidonCounter.create(type.getName(), metadata),
                tags);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name) {
        return null;        // TODO
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name, Tag... tags) {
        return null;        // TODO
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata) {
        return null;        // TODO
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata, Tag... tags) {
        return null;        // TODO
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
        return null;        // TODO
    }

    @Override
    public SortedMap<MetricID, ConcurrentGauge> getConcurrentGauges(MetricFilter filter) {
        return null;        // TODO
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
                throw new IllegalArgumentException("Unexpected metric type " + metadata.getType()
                        + ": " + metric.getClass().getName());
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

        return toImpl(new HelidonMetadata(name, MetricType.from(clazz == null ? metric.getClass() : clazz)), metric);
    }

    @Override
    public String toString() {
        return type() + ": " + allMetrics.size() + " metrics";
    }

    private <V> SortedMap<MetricID, V> getSortedMetrics(MetricFilter filter, Class<V> metricClass) {
        Map<MetricID, V> collected = allMetrics.entrySet()
                .stream()
                .filter(it -> metricClass.isAssignableFrom(it.getValue().getClass()))
                .filter(it -> filter.matches(it.getKey(), it.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, it -> metricClass.cast(it.getValue())));

        return new TreeMap<>(collected);
    }

    private <T extends Metric, I extends MetricImpl> T getMetric(Metadata metadata,
                                                                 Class<T> type,
                                                                 Function<String, I> newInstanceCreator,
                                                                 Tag... tags) {
        MetricImpl metric = allMetrics.get(new MetricID(metadata.getName(), tags));
        if (metric != null) {
            if (metric.isReusable() != metadata.isReusable()) {
                throw new IllegalArgumentException("Metadata not re-usable for metric " + metadata.getName());
            }
        } else {
            metric = newInstanceCreator.apply(metadata.getName());
            allMetrics.put(new MetricID(metadata.getName()), metric);
        }
        if (!(type.isAssignableFrom(metric.getClass()))) {
            throw new IllegalArgumentException("Attempting to get " + metadata.getType()
                                               + ", but metric registered under this name is "
                                               + metric);
        }

        return type.cast(metric);
    }
}
