/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
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

    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        return registerUniqueMetric(name, metric);
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException {
        return register(metadata, metric, NO_TAGS);
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric, Tag... tags) throws IllegalArgumentException {
        return registerUniqueMetric(metadata, metric, tags);
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
    public synchronized boolean remove(String name) {
        final boolean result = allMetricIDsByName.get(name).stream()
                .map(metricID -> allMetrics.remove(metricID) != null)
                .reduce((a, b) -> a || b)
                .orElse(false);
        allMetricIDsByName.remove(name);
        allMetadata.remove(name);
        return result;
    }

    @Override
    public synchronized boolean remove(MetricID metricID) {
        final List<MetricID> likeNamedMetrics = allMetricIDsByName.get(metricID.getName());
        likeNamedMetrics.remove(metricID);
        if (likeNamedMetrics.isEmpty()) {
            allMetricIDsByName.remove(metricID.getName());
            allMetadata.remove(metricID.getName());
        }

        return allMetrics.remove(metricID) != null;
    }

    @Override
    public synchronized void removeMatching(MetricFilter filter) {
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
    public synchronized Map<io.helidon.common.metrics.InternalBridge.MetricID, Metric> getBridgeMetrics(
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

    private static synchronized
        <T extends Metric> SortedMap<io.helidon.common.metrics.InternalBridge.MetricID, T> getBridgeMetrics(
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
                .withType(metadata.getTypeRaw())
                .reusable(metadata.isReusable());

        metadata.getDescription().ifPresent(builder::withDescription);
        metadata.getUnit().ifPresent(builder::withUnit);
        return builder.build();
    }

    synchronized Optional<Map.Entry<MetricID, HelidonMetric>> getOptionalMetricEntry(String metricName) {
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

    synchronized Optional<Map.Entry<HelidonMetric, List<MetricID>>> getOptionalMetricWithIDsEntry(String metricName) {
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
                && (a.getDisplayName().equals(b.getDisplayName())
                && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getUnit(), b.getUnit())
                && (a.isReusable() == b.isReusable())
            );
    }

    // -- Private methods -----------------------------------------------------

    private static boolean enforceConsistentMetadata(Metadata existingMetadata, Metadata newMetadata,
            Tag... tags) {
        if (!metadataMatches(existingMetadata, newMetadata)) {
            throw new IllegalArgumentException("New metric " + new MetricID(newMetadata.getName(), tags)
                    + " with metadata " + newMetadata
                    + " conflicts with a metric already registered with metadata "
                    + existingMetadata);
        }
        return true;
    }

    private static <T extends HelidonMetric> boolean enforceConsistentMetadata(T existingMetric,
            Metadata newMetadata, Tag... tags) {

        return enforceConsistentMetadata(existingMetric.metadata(), newMetadata, tags);
    }

    private static boolean enforceConsistentMetadataType(Metadata existingMetadata, MetricType newType, Tag... tags) {
        if (!existingMetadata.getTypeRaw().equals(newType)) {
            throw new IllegalArgumentException("Attempting to register a new metric "
                    + new MetricID(existingMetadata.getName(), tags) + " of type " + newType.toString()
                    + " found pre-existing metadata with conflicting type "
                    + existingMetadata.getTypeRaw().toString());
        }
        return true;
    }

    /**
     * Returns an existing metric (if one is already registered with the name
     * from the metadata plus the tags, and if the existing metadata is
     * consistent with the new metadata) or a new metric, registered using the metadata and tags.
     *
     * @param <T> type of the metric
     * @param newMetadata metadata describing the metric
     * @param metricFactory factory for creating a new instance of the metric
     * type
     * @param clazz class of the metric to find or create
     * @param tags tags for refining the identity of the metric
     * @return the existing or newly-created metric
     * @throws IllegalArgumentException if the metadata is inconsistent with
     * previously-registered metadata or if the metric is being reused and the
     * metadata prohibits reuse
     */
    private synchronized <T extends HelidonMetric> T getOrRegisterMetric(Metadata newMetadata,
            BiFunction<String, Metadata, T> metricFactory,
            Class<T> clazz,
            Tag... tags) throws IllegalArgumentException {
        final String metricName = newMetadata.getName();
        /*
         * If there is an existing compatible metric then there's really nothing
         * new to register; the existing registration is enough so return that
         * previously-registered metric.
         */
        return getOptionalMetric(metricName, clazz, tags)
                .filter(existingMetric -> enforceConsistentMetadata(existingMetric, newMetadata, tags))
                .orElseGet(() -> {
                    final Metadata metadata = getOrRegisterMetadata(metricName, newMetadata, tags);
                    return registerMetric(metricName,
                                    metricFactory.apply(type.getName(), metadata),
                                    tags);
                });
    }

    /**
     * Returns an existing metric with the requested name and tags, or if none
     * is already registered registers a new metric using the name and type. If
     * metadata with the same name already exists it is used and checked for
     * consistency with the metric type {@code T}.
     *
     * @param <T> type of the metric
     * @param metricName name of the metric
     * @param metricFactory factory for creating a new instance of the metric type
     * @param clazz class of the metric to find or create
     * @param tags tags for refining the identity of the metric
     * @return the existing or newly-created metric
     */
    private synchronized <T extends HelidonMetric> T getOrRegisterMetric(String metricName,
            BiFunction<String, Metadata, T> metricFactory,
            Class<T> clazz,
            Tag... tags) {
        final MetricType newType = METRIC_TO_TYPE_MAP.get(clazz);
        return getOptionalMetric(metricName, clazz, tags)
                .orElseGet(() -> {
                    final Metadata metadata = getOrRegisterMetadata(metricName, newType,
                            () ->  new HelidonMetadata(metricName, newType), tags);
                    return registerMetric(metricName, metricFactory.apply(type.getName(), metadata),
                            tags);
                });
    }

    /**
     * Registers a new metric, using the specified name, using existing metadata
     * or, if none, creating new metadata based on the metric's name and type,
     * returning the metric itself. Throws an exception if the metric is already
     * registered or if the metric and existing metadata are incompatible.
     *
     *
     * @param <T> type of the metric
     * @param metricName name of the metric
     * @param metric the metric to register
     * @return the newly-registered metric
     * @throws IllegalArgumentException if the metric is already registered and
     * its metadata prohibits reuse
     */
    private synchronized <T extends Metric> T registerUniqueMetric(String metricName, T metric) throws IllegalArgumentException {

        enforceMetricUniqueness(metricName);
        final MetricType metricType = MetricType.from(metric.getClass());

        final Metadata metadata = getOrRegisterMetadata(metricName, metricType,
                () -> new HelidonMetadata(metricName, metricType), NO_TAGS);
        registerMetric(metricName, toImpl(metadata, metric), NO_TAGS);
        return metric;
    }


    /**
     * Registers a new metric, using the metadata's name and the tags, described
     * by the given metadata, returning the metric itself. Throws an exception
     * if the metric is already registered or if incompatible metadata is
     * already registered.
     *
     * @param <T> type of the metric
     * @param metadata metadata describing the metric
     * @param metric the metric to register
     * @param tags tags associated with the metric
     * @return the newly-registered metric
     * @throws IllegalArgumentException if the specified metadata is incompatible with previously-registered metadata
     */
    private synchronized <T extends Metric> T registerUniqueMetric(Metadata metadata, T metric, Tag... tags)
            throws IllegalArgumentException {

        final String metricName = metadata.getName();
        enforceMetricUniqueness(metricName, tags);

        metadata = getOrRegisterMetadata(metricName, metadata, tags);
        registerMetric(metricName, toImpl(metadata, metric), tags);
        return metric;
    }

    private boolean enforceMetricUniqueness(String metricName) {
        return enforceMetricUniqueness(new MetricID(metricName));
    }

    private boolean enforceMetricUniqueness(String metricName, Tag... tags) {
        return enforceMetricUniqueness(new MetricID(metricName, tags));
    }

    private boolean enforceMetricUniqueness(MetricID metricID) {
        if (allMetrics.containsKey(metricID)) {
            throw new IllegalArgumentException("Attempt to reregister the existing metric " + metricID);
        }
        return true;
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

    /**
     * Returns an existing metadata instance with the requested name or, if there
     * is none, registers the provided new metadata. Throws an exception if the
     * provided new metadata is incompatible with any existing metadata
     *
     * @param metricName name of the metric
     * @param newMetadata new metadata to register if none exists for this name
     * @param tags tags associated with the metric being sought or created (for error messaging)
     * @return existing metadata if any; otherwise the provided new metadata
     */
    private synchronized Metadata getOrRegisterMetadata(String metricName, Metadata newMetadata, Tag... tags) {

        return getOptionalMetadata(metricName)
                .filter(existingMetadata -> enforceConsistentMetadata(existingMetadata, newMetadata, tags))
                .orElseGet(() -> registerMetadata(newMetadata));
    }

    /**
     * Returns an existing metadata instance with the requested name or, if there is none,
     * registers the metadata supplied by the provided metadata factory. Throws an exception
     * if the provided new metric type is incompatible with any previously-registered
     * metadata.
     *
     * @param metricName name of the metric
     * @param newMetricType metric type of the new metric being created
     * @param metadataFactory supplier for new metadata if none is found under the specified name
     * @param tags tags associated with the metric being sought or created (for error messaging)
     * @return existing metadata if any; otherwise the metadata from the provided supplier
     */
    private synchronized Metadata getOrRegisterMetadata(String metricName, MetricType newMetricType,
            Supplier<Metadata> metadataFactory, Tag... tags) {

        return getOptionalMetadata(metricName)
                .filter(existingMetadata -> enforceConsistentMetadataType(existingMetadata, newMetricType, tags))
                .orElseGet(() -> registerMetadata(metadataFactory.get()));
    }

    private Optional<Metadata> getOptionalMetadata(String name) {
        return Optional.ofNullable(allMetadata.get(name));
    }

    private Metadata registerMetadata(Metadata metadata) {
        allMetadata.put(metadata.getName(), metadata);
        return metadata;
    }

    private synchronized <T extends HelidonMetric> T registerMetric(String metricName, T metric, Tag... tags) {
        final MetricID metricID = new MetricID(metricName, tags);
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

        MetricType metricType = deriveType(metadata.getTypeRaw(), metric);
        switch (metricType) {
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
                throw new IllegalArgumentException("Unexpected metric type " + metricType
                        + ": " + metric.getClass().getName());
        }
    }

    private static MetricType deriveType(MetricType candidateType, Metric metric) {
        if (candidateType != MetricType.INVALID) {
            return candidateType;
        }
         /*
         * A metric could be passed as a lambda, in which case neither
         * MetricType.from() nor, for example, Counter.class.isAssignableFrom,
         * works. Check each specific metric class using instanceof.
         */
        return Stream.of(Counter.class, Gauge.class, Histogram.class, Meter.class, Timer.class, ConcurrentGauge.class)
                .filter(clazz -> clazz.isInstance(metric))
                .map(MetricType::from)
                .findFirst()
                .orElse(MetricType.INVALID);
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
    private synchronized <V> SortedMap<MetricID, V> getSortedMetrics(MetricFilter filter, Class<V> metricClass) {
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
}
