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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
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
public class Registry extends MetricRegistry implements io.helidon.common.metrics.InternalBridge.MetricRegistry {

    private static final Tag[] NO_TAGS = new Tag[0];
    private static final Map<Class<? extends HelidonMetric>, MetricType> METRIC_TO_TYPE_MAP = prepareMetricToTypeMap();

    private final MetricRegistry.Type type;
    private final Map<MetricID, HelidonMetric> allMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<MetricID>> allMetricIDsByName = new ConcurrentHashMap<>();
    private final Map<String, Metadata> allMetadata = new ConcurrentHashMap<>(); // metric name -> metadata

    /**
     * Create a registry of a certain type.
     *
     * @param type Registry type.
     */
    protected Registry(Type type) {
        this.type = type;
    }

    /**
     * Create a registry of a certain type.
     *
     * @param type Registry type.
     * @return The newly created registry.
     */
    public static Registry create(Type type) {
        return new Registry(type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        Metadata metadata = getOrCreateMetadata(name, metric);
        return (T) reuseOrRegisterMetric(new MetricID(name), toImpl(metadata, metric));
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException {
        return register(metadata, metric, NO_TAGS);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Metric> T register(Metadata metadata, T metric, Tag... tags) throws IllegalArgumentException {
        metadata = checkAgainstExistingMetadataOrRegister(metadata, tags);
        return (T) reuseOrRegisterMetric(new MetricID(metadata.getName(), tags), toImpl(metadata, metric));
    }

    @Override
    public Counter counter(String name) {
        return counter(name, NO_TAGS);
    }

    @Override
    public Counter counter(Metadata metadata) {
        return counter(metadata, NO_TAGS);
    }

    @Override
    public Counter counter(io.helidon.common.metrics.InternalBridge.Metadata metadata) {
        return counter(toMetadata(metadata));
    }

    @Override
    public Counter counter(io.helidon.common.metrics.InternalBridge.Metadata metadata, Map<String, String> tags) {
        return counter(toMetadata(metadata), toTags(tags));
    }

    @Override
    public Counter counter(String name, Tag... tags) {
        return getOrRegisterMetric(name, HelidonCounter::create, HelidonCounter.class, tags);
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, HelidonCounter::create, HelidonCounter.class, tags);
    }

    @Override
    public Histogram histogram(String name) {
        return histogram(name, NO_TAGS);
    }

    @Override
    public Histogram histogram(Metadata metadata) {
        return histogram(metadata, NO_TAGS);
    }

    @Override
    public Histogram histogram(io.helidon.common.metrics.InternalBridge.Metadata metadata) {
        return histogram(toMetadata(metadata));
    }

    @Override
    public Histogram histogram(io.helidon.common.metrics.InternalBridge.Metadata metadata, Map<String, String> tags) {
        return histogram(toMetadata(metadata), toTags(tags));
    }

    @Override
    public Histogram histogram(String name, Tag... tags) {
        return getOrRegisterMetric(name, HelidonHistogram::create, HelidonHistogram.class, tags);
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, HelidonHistogram::create, HelidonHistogram.class, tags);
    }

    @Override
    public Meter meter(String name) {
        return meter(name, NO_TAGS);
    }

    @Override
    public Meter meter(Metadata metadata) {
        return meter(metadata, NO_TAGS);
    }

    @Override
    public Meter meter(io.helidon.common.metrics.InternalBridge.Metadata metadata) {
        return meter(toMetadata(metadata));
    }

    @Override
    public Meter meter(io.helidon.common.metrics.InternalBridge.Metadata metadata, Map<String, String> tags) {
        return meter(toMetadata(metadata), toTags(tags));
    }

    @Override
    public Meter meter(String name, Tag... tags) {
        return getOrRegisterMetric(name, HelidonMeter::create, HelidonMeter.class, tags);
    }

    @Override
    public Meter meter(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, HelidonMeter::create, HelidonMeter.class, tags);
    }

    @Override
    public Timer timer(String name) {
        return timer(name, NO_TAGS);
    }

    @Override
    public Timer timer(Metadata metadata) {
        return timer(metadata, NO_TAGS);
    }

    @Override
    public Timer timer(io.helidon.common.metrics.InternalBridge.Metadata metadata) {
        return timer(toMetadata(metadata));
    }

    @Override
    public Timer timer(io.helidon.common.metrics.InternalBridge.Metadata metadata, Map<String, String> tags) {
        return timer(toMetadata(metadata), toTags(tags));
    }

    @Override
    public Timer timer(String name, Tag... tags) {
        return getOrRegisterMetric(name, HelidonTimer::create, HelidonTimer.class, tags);
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, HelidonTimer::create, HelidonTimer.class, tags);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name) {
        return concurrentGauge(name, NO_TAGS);
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata) {
        return concurrentGauge(metadata, NO_TAGS);
    }

    @Override
    public ConcurrentGauge concurrentGauge(String name, Tag... tags) {
        return getOrRegisterMetric(name, HelidonConcurrentGauge::create, HelidonConcurrentGauge.class, tags);
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, HelidonConcurrentGauge::create, HelidonConcurrentGauge.class, tags);
    }

    @Override
    public boolean remove(String name) {
        final boolean result = allMetricIDsByName.get(name).stream()
                .map(metricID -> allMetrics.remove(metricID) != null)
                .reduce((a, b) -> a || b)
                .orElse(false);
        allMetricIDsByName.remove(name);
        allMetadata.remove(name);
        return result;
    }

    @Override
    public boolean remove(MetricID metricID) {
        final List<MetricID> likeNamedMetrics = allMetricIDsByName.get(metricID.getName());
        likeNamedMetrics.remove(metricID);
        if (likeNamedMetrics.isEmpty()) {
            allMetricIDsByName.remove(metricID.getName());
            allMetadata.remove(metricID.getName());
        }

        return allMetrics.remove(metricID) != null;
    }

    @Override
    public void removeMatching(MetricFilter filter) {
        allMetrics.entrySet().stream()
                .filter(entry -> filter.matches(entry.getKey(), entry.getValue()))
                .map(entry -> remove(entry.getKey()))
                .reduce((a, b) -> a || b)
                .orElse(false);
    }

    @Override
    public SortedSet<String> getNames() {
        return allMetrics.keySet().stream()
                .map(MetricID::getName)
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
        return Collections.unmodifiableMap(allMetadata);
    }

    @Override
    public Map<MetricID, Metric> getMetrics() {
        return Collections.unmodifiableMap(allMetrics);
    }

    @Override
    public Map<io.helidon.common.metrics.InternalBridge.MetricID, Metric> getBridgeMetrics(
            Predicate<? super Map.Entry<? extends io.helidon.common.metrics.InternalBridge.MetricID,
                                        ? extends Metric>> predicate) {

        final Map<io.helidon.common.metrics.InternalBridge.MetricID, Metric> result = new HashMap<>();

        allMetrics.entrySet().stream()
                .map(Registry::toBridgeEntry)
                .filter(predicate)
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    @Override
    public Map<io.helidon.common.metrics.InternalBridge.MetricID, Metric> getBridgeMetrics() {
        return getBridgeMetrics(entry -> true);
    }

    @Override
    public SortedMap<io.helidon.common.metrics.InternalBridge.MetricID, Gauge> getBridgeGauges() {
        return getBridgeMetrics(getGauges(), Gauge.class);
    }

    @Override
    public SortedMap<io.helidon.common.metrics.InternalBridge.MetricID, Counter> getBridgeCounters() {
        return getBridgeMetrics(getCounters(), Counter.class);
    }

    @Override
    public SortedMap<io.helidon.common.metrics.InternalBridge.MetricID, Histogram> getBridgeHistograms() {
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

    private static <T extends Metric> SortedMap<io.helidon.common.metrics.InternalBridge.MetricID, T> getBridgeMetrics(
            SortedMap<MetricID, T> metrics, Class<T> clazz) {
        return metrics.entrySet().stream()
                .map(Registry::toBridgeEntry)
                .filter(entry -> clazz.isAssignableFrom(entry.getValue().getClass()))
                .collect(TreeMap::new,
                        (map, entry) -> map.put(entry.getKey(), clazz.cast(entry.getValue())),
                        Map::putAll);
    }


    @Override
    public <T extends Metric> T register(
            io.helidon.common.metrics.InternalBridge.Metadata metadata, T metric) throws IllegalArgumentException {
        return register(toMetadata(metadata), metric);
    }

    @Override
    public <T extends Metric> T register(
            io.helidon.common.metrics.InternalBridge.MetricID metricID, T metric) throws IllegalArgumentException {
        return register(toMetadata(metricID.getName(), metric), metric, toTags(metricID.getTags()));
    }

    private static Map.Entry<? extends io.helidon.common.metrics.InternalBridge.MetricID,
                        ? extends Metric> toBridgeEntry(
            Map.Entry<? extends MetricID, ? extends Metric> entry) {
        return new AbstractMap.SimpleEntry<>(new InternalMetricIDImpl(
                    entry.getKey().getName(), entry.getKey().getTags()),
                entry.getValue());
    }

    /**
     * Access a metric by name. Used by FT library.
     *
     * @param metricName Metric name.
     * @return Optional metric.
     */
    public Optional<Metric> getMetric(String metricName) {
        return getOptionalMetricEntry(metricName).map(Map.Entry::getValue);
    }

    @Override
    public Optional<Map.Entry<? extends io.helidon.common.metrics.InternalBridge.MetricID,
         ? extends Metric>> getBridgeMetric(String metricName) {
        return getOptionalMetricEntry(metricName)
                .map(Registry::toBridgeEntry);
    }

    // -- Public not overridden -----------------------------------------------

    /**
     * Returns a stream of {@link Map.Entry} for this registry.
     *
     * @return Stream of {@link Map.Entry}
     */
    Stream<Map.Entry<MetricID, HelidonMetric>> stream() {
        return allMetrics.entrySet().stream();
    }

    /**
     * Returns type of this registry.
     *
     * @return The type.
     */
    public String type() {
        return type.getName();
    }

    /**
     * Determines if registry is empty.
     *
     * @return Outcome of test.
     */
    public boolean empty() {
        return allMetrics.isEmpty();
    }

    @Override
    public String toString() {
        return type() + ": " + allMetrics.size() + " metrics";
    }

    // -- Package private -----------------------------------------------------

    static Metadata toMetadata(io.helidon.common.metrics.InternalBridge.Metadata metadata) {
        final MetadataBuilder builder = new MetadataBuilder();
        builder.withName(metadata.getName())
                .withDisplayName(metadata.getDisplayName())
                .withType(metadata.getTypeRaw());
        metadata.getDescription().ifPresent(builder::withDescription);
        metadata.getUnit().ifPresent(builder::withUnit);
        return (metadata.isReusable() ? builder.reusable() : builder.notReusable()).build();
    }

    Optional<Map.Entry<MetricID, HelidonMetric>> getOptionalMetricEntry(String metricName) {
        return getOptionalMetricWithIDsEntry(metricName).map(entry -> {
                final MetricID metricID = entry.getValue().get(0);
                return new AbstractMap.SimpleImmutableEntry<>(metricID,
                        allMetrics.get(metricID));
        });
    }

    <T extends HelidonMetric> Optional<T> getOptionalMetric(String metricName, Class<T> clazz, Tag... tags) {
        return getOptionalMetric(new MetricID(metricName, tags), clazz);
    }

    <T extends HelidonMetric> Optional<T> getOptionalMetric(Metadata metadata, Class<T> clazz, Tag... tags) {
        return getOptionalMetric(new MetricID(metadata.getName(), tags), clazz);
    }

    Optional<Map.Entry<HelidonMetric, List<MetricID>>> getOptionalMetricWithIDsEntry(String metricName) {
        final List<MetricID> metricIDs = allMetricIDsByName.get(metricName);
        if (metricIDs == null || metricIDs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new AbstractMap.SimpleEntry<>(allMetrics.get(metricIDs.get(0)), metricIDs));
    }

    <T extends HelidonMetric> Optional<T> getOptionalMetric(MetricID metricID, Class<T> clazz) {
        return Optional.ofNullable(allMetrics.get(metricID))
                .map(metric -> {
                    return toType(metric, clazz);
                });
    }

    Type registryType() {
        return type;
    }

    List<MetricID> metricIDsForName(String metricName) {
        return allMetricIDsByName.get(metricName);
    }

    static <T extends Metadata, U extends Metadata> boolean  metadataMatches(T a, U b) {
        if ((a == null && b == null) || (a == b)) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getName().equals(b.getName())
                && a.getTypeRaw().equals(b.getTypeRaw())
                && (((isFlexible(a) || isFlexible(b))
                    || (a.getDisplayName().equals(b.getDisplayName())
                        && a.getDescription().equals(b.getDescription())
                        && a.getUnit().equals(b.getUnit())
                        && (a.isReusable() == b.isReusable())
                    ))
                   );
    }

    // -- Private methods -----------------------------------------------------

    private void enforceConsistentMetadata(MetricID metricID, Metadata m1, Metadata m2) {

        // Check that metadata is compatible.
        if (!metadataMatches(m1, m2)) {
            throw new IllegalArgumentException("New metric " + metricID
                    + " with metadata " + m2
                    + " conflicts with a metric already registered with metadata "
                    + m1);
        }
    }

    private void enforceReusabilityAllowed(MetricID metricID, Metadata m1, Metadata m2) {
        if (!m1.isReusable()) {
            throw new IllegalArgumentException("A metric " + metricID
                    + " already registered as non-reusable");
        } else if (!m2.isReusable()) {
            throw new IllegalArgumentException("A metric " + metricID
                    + " already registered as reusable but a new registration requires non-reusable");
        }
    }

    private <T extends HelidonMetric> T getOrRegisterMetric(Metadata metadata,
            BiFunction<String, Metadata, T> metricFactory,
            Class<T> clazz,
            Tag... tags) {
        final Metadata metadataToUse = checkAgainstExistingMetadataOrRegister(metadata, tags);
        return getOptionalMetric(metadata.getName(), clazz, tags)
                .map(metric -> enforceReusability(metric, metadataToUse, metadata))
                .orElseGet(() -> {
                    return reuseOrRegisterMetric(metadataToUse.getName(),
                            metricFactory.apply(type.getName(), metadata),
                            tags);
                });
    }

    private <T extends HelidonMetric> T getOrRegisterMetric(String metricName,
            BiFunction<String, Metadata, T> metricFactory,
            Class<T> clazz,
            Tag... tags) {
        final T metric = getOptionalMetric(metricName, clazz, tags)
                .orElseGet(() -> {
                    Metadata metadata = getOrCreateMetadata(metricName, METRIC_TO_TYPE_MAP.get(clazz));
                    return reuseOrRegisterMetric(metricName,
                            metricFactory.apply(type.getName(), metadata),
                            tags);
                });
        return metric;
    }

    private Metadata getOrRegisterMetadata(String metricName, Supplier<HelidonMetadata> metadataSupplier) {
        Metadata result = allMetadata.get(metricName);
        if (result == null) {
            result = metadataSupplier.get();
            result = registerMetadata(result);
        }
        return result;
    }

    private Metadata getOrCreateMetadata(String metricName, MetricType metricType) {
        Metadata result = getOrRegisterMetadata(metricName, () -> {
                return new HelidonMetadata(metricName, metricType);
            });
        // result might have been previously registered, so make sure the metric types match
        if (metricType != result.getTypeRaw()) {
            throw new IllegalArgumentException("Attempting to register a new metric "
                    + metricName + " of type " + metricType.toString()
                    + " found pre-existing metadata with conflicting type "
                    + result.getTypeRaw().toString());
        }
        return result;
    }

    private Metadata getOrCreateMetadata(String metricName, Metric metric) {

        Metadata result = getOrRegisterMetadata(metricName, () -> {
                return toMetadata(metricName, metric);
            });
        return result;
    }

    private <T extends HelidonMetric> T enforceReusability(T metric, Metadata m1, Metadata m2) {
        // We are here because a metric already exists during an attempt to register.
        if (!metric.metadata().isReusable() && !isFlexible(m1) && !isFlexible(m2)) {
            throw new IllegalArgumentException("Attempting to re-register metric "
                    + metric.getName() + " that is already registered as non-reusable");
        }
        return metric;
    }

    private Metadata checkAgainstExistingMetadataOrRegister(Metadata metadata, Tag... tags) {
        final String metricName = metadata.getName();
        final Metadata existingMetadata = allMetadata.get(metricName);
        if (existingMetadata != null) {
            enforceConsistentMetadata(new MetricID(metadata.getName(), tags),
                    existingMetadata, metadata);
            return existingMetadata;
        }
        return registerMetadata(metadata);
    }

    private <T extends HelidonMetric, U extends HelidonMetric> U toType(T m1, Class<U> clazz) {
        MetricType type1 = toType(m1);
        MetricType type2 = toType(clazz);
        if (type1 == type2) {
            return clazz.cast(m1);
        }
        throw new IllegalArgumentException("Metric types " + type1.toString()
                + " and " + type2.toString() + " do not match");
    }

    private Metadata registerMetadata(Metadata metadata) {
        allMetadata.put(metadata.getName(), metadata);
        return metadata;
    }

    private <T extends HelidonMetric> T reuseOrRegisterMetric(String metricName, T metric, Tag... tags) {
        return reuseOrRegisterMetric(new MetricID(metricName, tags), metric);
    }

    @SuppressWarnings("unchecked")
    private <T extends HelidonMetric> T reuseOrRegisterMetric(MetricID metricID, T metric) {
        HelidonMetric existingMetric = allMetrics.get(metricID);

        if (existingMetric != null) {
            enforceReusabilityAllowed(metricID, existingMetric.metadata(), metric.metadata());
            if (metric.getClass().isAssignableFrom(existingMetric.getClass())) {
                return (T) toType(existingMetric, metric.getClass());
            }
        }
        final String metricName = metricID.getName();
        allMetrics.put(metricID, metric);
        List<MetricID> metricIDsWithSameName = allMetricIDsByName.get(metricName);
        if (metricIDsWithSameName == null) {
            metricIDsWithSameName = new ArrayList<>();
            allMetricIDsByName.put(metricName, metricIDsWithSameName);
        }
        metricIDsWithSameName.add(metricID);
        return metric;
    }


    private <T extends Metric> HelidonMetric toImpl(Metadata metadata, T metric) {
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

    private static <T extends Metric> HelidonMetadata toMetadata(String name, T metric) {
        return new HelidonMetadata(name, toType(metric));
    }

    private static MetricType toType(Metric metric) {
        // Find subtype of Metric, needed for user-defined metrics
        Class<?> clazz = metric.getClass();
        do {
            Optional<Class<?>> optionalClass = Arrays.stream(clazz.getInterfaces())
                    .filter(Metric.class::isAssignableFrom)
                    .findFirst();
            if (optionalClass.isPresent()) {
                clazz = optionalClass.get();
                break;
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);
        return MetricType.from(clazz == null ? metric.getClass() : clazz);
    }

    private static <T extends HelidonMetric> MetricType toType(Class<T> clazz) {
        return METRIC_TO_TYPE_MAP.getOrDefault(clazz, MetricType.INVALID);
    }

    /**
     * Returns a sorted map based on a filter a metric class.
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

    private static Tag[] toTags(Map<String, String> tags) {

        return tags.entrySet().stream()
                .map(entry -> new Tag(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList())
                .toArray(new Tag[0]);
    }

    private static Map<Class<? extends HelidonMetric>, MetricType> prepareMetricToTypeMap() {
        final Map<Class<? extends HelidonMetric>, MetricType> result = new HashMap<>();
        result.put(HelidonConcurrentGauge.class, MetricType.CONCURRENT_GAUGE);
        result.put(HelidonCounter.class, MetricType.COUNTER);
        result.put(HelidonGauge.class, MetricType.GAUGE);
        result.put(HelidonHistogram.class, MetricType.HISTOGRAM);
        result.put(HelidonMeter.class, MetricType.METERED);
        result.put(HelidonTimer.class, MetricType.TIMER);
        return result;
    }

    private static boolean isFlexible(Metadata metadata) {
        return ((metadata instanceof HelidonMetadata)
                && HelidonMetadata.class.cast(metadata).isFlexible());
    }
}
