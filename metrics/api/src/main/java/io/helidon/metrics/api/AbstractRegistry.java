/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.metrics.api;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
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
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Common behavior for any category (e.g., full-featured or no-op) metrics registry.
 * <p>
 *     This class provides the bookkeeping for tracking the metrics which are created and registered along with their
 *     IDs and metadata. Concrete subclasses create new instances of the various types of metrics (counter, timer, etc.).
 * </p>
 *
 * @param <M> general type of metric implementation supported by an implementation of this class (e.g., {@code
 * HelidonMetric}
 */
public abstract class AbstractRegistry<M extends BaseMetric> extends MetricRegistry {

    private static final Tag[] NO_TAGS = new Tag[0];
    private final Map<Class<? extends M>, MetricType> metricToTypeMap = prepareMetricToTypeMap();

    private final MetricRegistry.Type type;
    private final Map<MetricID, M> allMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<MetricID>> allMetricIDsByName = new ConcurrentHashMap<>();
    private final Map<String, Metadata> allMetadata = new ConcurrentHashMap<>(); // metric name -> metadata

    /**
     * Create a registry of a certain type.
     *
     * @param type Registry type.
     */
    protected AbstractRegistry(Type type) {
        this.type = type;
    }

    /**
     * Indicates whether the specific metrics has been marked as deleted.
     *
     * @param metric the metric to check
     * @return true if it's a Helidon metric and has been marked as deleted; false otherwise
     */
    public static boolean isMarkedAsDeleted(Metric metric) {
        return (metric instanceof BaseMetric)
                && ((BaseMetric) metric).isDeleted();
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
    public Counter counter(String name, Tag... tags) {
        return getOrRegisterMetric(name, Counter.class, tags);
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, Counter.class, tags);
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
    public Histogram histogram(String name, Tag... tags) {
        return getOrRegisterMetric(name, Histogram.class, tags);
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, Histogram.class, tags);
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
    public Meter meter(String name, Tag... tags) {
        return getOrRegisterMetric(name, Meter.class, tags);
    }

    @Override
    public Meter meter(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, Meter.class, tags);
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
    public Timer timer(String name, Tag... tags) {
        return getOrRegisterMetric(name, Timer.class, tags);
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, Timer.class, tags);
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
        return getOrRegisterMetric(name, ConcurrentGauge.class, tags);
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, ConcurrentGauge.class, tags);
    }

    @Override
    public SimpleTimer simpleTimer(String name) {
        return simpleTimer(name, NO_TAGS);
    }

    @Override
    public SimpleTimer simpleTimer(Metadata metadata) {
        return simpleTimer(metadata, NO_TAGS);
    }

    @Override
    public SimpleTimer simpleTimer(String name, Tag... tags) {
        return getOrRegisterMetric(name, SimpleTimer.class, tags);
    }

    @Override
    public SimpleTimer simpleTimer(Metadata metadata, Tag... tags) {
        return getOrRegisterMetric(metadata, SimpleTimer.class, tags);
    }

    /**
     * Removes a metric by name. Synchronized for atomic update of more than one internal map.
     *
     * @param name Name of the metric.
     * @return Outcome of removal.
     */
    @Override
    public synchronized boolean remove(String name) {
        final List<Map.Entry<MetricID, M>> doomedMetrics = getMetricsByName(name);
        if (doomedMetrics.isEmpty()) {
            return false;
        }

        final boolean result = doomedMetrics.stream()
                .peek(entry -> entry.getValue().markAsDeleted())
                .map(entry -> allMetrics.remove(entry.getKey()) != null)
                .reduce((a, b) -> a || b)
                .orElse(false);
        allMetricIDsByName.remove(name);
        allMetadata.remove(name);
        return result;
    }

    /**
     * Removes a metric by ID. Synchronized for atomic update of more than one internal map.
     *
     * @param metricID ID of metric.
     * @return Outcome of removal.
     */
    @Override
    public synchronized boolean remove(MetricID metricID) {
        final List<MetricID> metricIDS = allMetricIDsByName.get(metricID.getName());
        if (metricIDS == null) {
            return false;
        }
        metricIDS.remove(metricID);
        if (metricIDS.isEmpty()) {
            allMetricIDsByName.remove(metricID.getName());
            allMetadata.remove(metricID.getName());
        }

        M doomedMetric = allMetrics.remove(metricID);
        if (doomedMetric != null) {
            doomedMetric.markAsDeleted();
        }
        return doomedMetric != null;
    }

    @Override
    public void removeMatching(MetricFilter filter) {
        allMetrics.entrySet().stream()
                .filter(entry -> filter.matches(entry.getKey(), entry.getValue()))
                .map(entry -> remove(entry.getKey()))
                .reduce((a, b) -> a || b);
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
    public SortedMap<MetricID, SimpleTimer> getSimpleTimers() {
        return getSimpleTimers(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, SimpleTimer> getSimpleTimers(MetricFilter filter) {
        return getSortedMetrics(filter, SimpleTimer.class);
    }

    @Override
    public Map<String, Metadata> getMetadata() {
        return Collections.unmodifiableMap(allMetadata);
    }

    @Override
    public Map<MetricID, Metric> getMetrics() {
        return Collections.unmodifiableMap(allMetrics);
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

    /**
     * Get internal map entry given a metric name. Synchronized for atomic access of more than
     * one internal map.
     *
     * @param metricName The metric name.
     * @return Optional map entry..
     */
    public synchronized Optional<Map.Entry<? extends Metric, List<MetricID>>> getOptionalMetricWithIDsEntry(String metricName) {
        final List<MetricID> metricIDs = allMetricIDsByName.get(metricName);
        if (metricIDs == null || metricIDs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new AbstractMap.SimpleEntry<>(allMetrics.get(metricIDs.get(0)), metricIDs));
    }

    /**
     * Returns a stream of {@link Map.Entry} for this registry.
     *
     * @return Stream of {@link Map.Entry}
     */
    protected Stream<Map.Entry<MetricID, M>> stream() {
        return allMetrics.entrySet().stream();
    }

    /**
     * Creates a new instance of an implementation wrapper around the indicated metric.
     *
     * @param metadata {@code Metadata} for the metric
     * @param metric the existing metric to be wrapped by the impl
     * @param <T> specific type of {@code Metric} provided and wrapped
     * @return new wrapper implementation around the specified metric instance
     */
    protected abstract <T extends Metric> M toImpl(Metadata metadata, T metric);

    /**
     * Creates a new instance of the kind of metric specified by the metadata.
     *
     * @param metadata {@code Metadata} for the metric
     * @return new instance of the indicated metric type
     */
    protected abstract M newImpl(Metadata metadata);

    // -- Package private -----------------------------------------------------

    protected Optional<Map.Entry<MetricID, M>> getOptionalMetricEntry(String metricName) {
        return getOptionalMetricWithIDsEntry(metricName).map(entry -> {
                final MetricID metricID = entry.getValue().get(0);
                return new AbstractMap.SimpleImmutableEntry<>(metricID,
                        allMetrics.get(metricID));
        });
    }

    private Optional<M> getOptionalMetric(String metricName, Tag... tags) {
        // Looks up metric by name and tags without creating a throw-away MetricID (which can be expensive).
        List<MetricID> metricIDs = allMetricIDsByName.get(metricName);
        return metricIDs == null ? Optional.empty() : metricIDs.stream()
                .filter(metricID -> metricID.getTags().equals(tagsMap(tags)))
                .findFirst()
                .map(allMetrics::get);
    }

    private static Map<String, String> tagsMap(Tag... tags) {
        return Arrays.stream(tags).collect(Collectors.toMap(Tag::getTagName, Tag::getTagValue));
    }

    protected List<Map.Entry<MetricID, M>> getMetricsByName(String metricName) {
        List<MetricID> metricIDs = allMetricIDsByName.get(metricName);
        if (metricIDs == null) {
            return Collections.emptyList();
        }
        List<Map.Entry<MetricID, M>> result = new ArrayList<>();
        for (MetricID metricID : metricIDs) {
            result.add(new AbstractMap.SimpleEntry<>(metricID, allMetrics.get(metricID)));
        }
        return result;
    }

    protected Type registryType() {
        return type;
    }

    protected List<MetricID> metricIDsForName(String metricName) {
        return allMetricIDsByName.get(metricName);
    }

    static <T extends Metadata, U extends Metadata> boolean  metadataMatches(T a, U b) {
        if (a == b) {
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

    private <T extends M> boolean enforceConsistentMetadata(T existingMetric,
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
     * Synchronized for atomic access of more than one internal map.
     *
     * @param <U> type of the metric
     * @param newMetadata metadata describing the metric type
     * @param clazz class of the metric to find or create
     * @param tags tags for refining the identity of the metric
     * @return the existing or newly-created metric
     * @throws IllegalArgumentException if the metadata is inconsistent with
     * previously-registered metadata or if the metric is being reused and the
     * metadata prohibits reuse
     */
    private synchronized <U extends Metric> U getOrRegisterMetric(Metadata newMetadata,
            Class<U> clazz,
            Tag... tags) throws IllegalArgumentException {
        final String metricName = newMetadata.getName();
        /*
         * If there is an existing compatible metric then there's really nothing
         * new to register; the existing registration is enough so return that
         * previously-registered metric.
         */
        return clazz.cast(getOptionalMetric(metricName, tags)
                .filter(existingMetric -> enforceConsistentMetadata(existingMetric, newMetadata, tags))
                .orElseGet(() -> {
                    final Metadata metadata = getOrRegisterMetadata(metricName, newMetadata, tags);
                    return registerMetric(metricName,
                                    newImpl(metadata),
                                    tags);
                }));
    }

    /**
     * Returns an existing metric with the requested name and tags, or if none
     * is already registered registers a new metric using the name and type. If
     * metadata with the same name already exists it is used and checked for
     * consistency with the metric type {@code T}.
     * Synchronized for atomic access of more than one internal map.
     *
     * @param <U> specific type of {@code Metric} (e.g., {@code Counter}
     * @param metricName name of the metric
     * @param clazz class of the {@code Metric} type {@code <U>}to find or create
     * @param tags tags for refining the identity of the metric
     * @return the existing or newly-created metric
     */
    private synchronized <U extends Metric> U getOrRegisterMetric(String metricName,
            Class<U> clazz,
            Tag... tags) {
        final MetricType newType = MetricType.from(clazz);
        M result = getOptionalMetric(metricName, tags)
                .orElseGet(() -> {
                    final Metadata metadata = getOrRegisterMetadata(metricName, newType,
                            () ->  Metadata.builder()
                                    .withName(metricName)
                                    .withType(newType)
                                    .build(), tags);
                    return registerMetric(metricName, newImpl(metadata), tags);
                });
        if (!clazz.isInstance(result)) {
            throw new IllegalArgumentException("Metric types " + result.metadata().getType()
                    + " and " + MetricType.from(clazz).toString() + " do not match");
        }
        return clazz.cast(result);
    }

    /**
     * Registers a new metric, using the specified name, using existing metadata
     * or, if none, creating new metadata based on the metric's name and type,
     * returning the metric itself. Throws an exception if the metric is already
     * registered or if the metric and existing metadata are incompatible.
     * Synchronized for atomic access of more than one internal map.
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
                () -> Metadata.builder()
                        .withName(metricName)
                        .withType(metricType)
                        .build(), NO_TAGS);
        registerMetric(metricName, toImpl(metadata, metric), NO_TAGS);
        return metric;
    }


    /**
     * Registers a new metric, using the metadata's name and the tags, described
     * by the given metadata, returning the metric itself. Throws an exception
     * if the metric is already registered or if incompatible metadata is
     * already registered.
     * Synchronized for atomic access of more than one internal map.
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

    /**
     * Returns an existing metadata instance with the requested name or, if there
     * is none, registers the provided new metadata. Throws an exception if the
     * provided new metadata is incompatible with any existing metadata
     * Synchronized for multiple access of an internal map.
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
     * metadata. Synchronized for multiple access of an internal map.
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

    /**
     * Register a metric using name and tags. Synchronized for atomic access of more than
     * one internal map.
     *
     * @param metricName Name of metric.
     * @param metric The metric instance.
     * @param tags The metric tags.
     * @param <T> Metric subtype.
     * @return The metric instance.
     */
    private synchronized <T extends M> T registerMetric(String metricName, T metric, Tag... tags) {
        final MetricID metricID = new MetricID(metricName, tags);
        allMetrics.put(metricID, metric);
        List<MetricID> metricIDsWithSameName = allMetricIDsByName.computeIfAbsent(metricName, k -> new ArrayList<>());
        metricIDsWithSameName.add(metricID);
        return metric;
    }

    protected static MetricType deriveType(MetricType candidateType, Metric metric) {
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

    protected Map<Class<? extends M>, MetricType> metricToTypeMap() {
        return metricToTypeMap;
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

    protected abstract Map<Class<? extends M>, MetricType> prepareMetricToTypeMap();
}
