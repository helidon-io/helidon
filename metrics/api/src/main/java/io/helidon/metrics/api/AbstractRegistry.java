/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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
 */
public abstract class AbstractRegistry implements Registry {

    private static final Tag[] NO_TAGS = new Tag[0];

    private final MetricRegistry.Type type;

    private final Map<MetricType, BiFunction<String, Metadata, HelidonMetric>> metricFactories = prepareMetricFactories();

    private final MetricStore metricStore;

    /**
     * Create a registry of a certain type.
     *
     * @param type Registry type.
     * @param registrySettings registry settings which influence this registry
     */
    protected AbstractRegistry(Type type,
                               RegistrySettings registrySettings) {
        this.type = type;
        this.metricStore = MetricStore.create(registrySettings,
                                              prepareMetricFactories(),
                                              this::createGauge,
                                              this::createGauge,
                                              type,
                                              this::toImpl);
    }

    /**
     * Indicates whether the specific metrics has been marked as deleted.
     *
     * @param metric the metric to check
     * @return true if it's a Helidon metric and has been marked as deleted; false otherwise
     */
    public static boolean isMarkedAsDeleted(Metric metric) {
        return (metric instanceof HelidonMetric)
                && ((HelidonMetric) metric).isDeleted();
    }

    @Override
    public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
        return metricStore.register(name, metric);
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException {
        return register(metadata, metric, NO_TAGS);
    }

    @Override
    public <T extends Metric> T register(Metadata metadata, T metric, Tag... tags) throws IllegalArgumentException {
        return metricStore.register(metadata, metric, tags);
    }

    @Override
    public Optional<MetricInstance> find(String name) {
        return Optional.ofNullable(metricStore.untaggedOrFirstMetricInstance(name));
    }

    @Override
    public List<MetricInstance> list(String metricName) {
        return metricStore.metricsWithIDs(metricName);
    }

    @Override
    public List<MetricID> metricIdsByName(String name) {
        return metricStore.metricIDs(name);
    }

    @Override
    public Optional<MetricsForMetadata> metricsByName(String name) {
        return Optional.ofNullable(metricStore.metadataWithIDs(name));
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
        return metricStore.getOrRegisterMetric(name, Counter.class, tags);
    }

    @Override
    public Counter counter(Metadata metadata, Tag... tags) {
        return metricStore.getOrRegisterMetric(metadata, Counter.class, tags);
    }

    @Override
    public Counter counter(MetricID metricID) {
        return metricStore.getOrRegisterMetric(metricID, Counter.class);
    }

    @Override
    public Counter getCounter(MetricID metricID) {
        return getMetric(metricID, Counter.class);
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
        return metricStore.getOrRegisterMetric(name, Histogram.class, tags);
    }

    @Override
    public Histogram histogram(Metadata metadata, Tag... tags) {
        return metricStore.getOrRegisterMetric(metadata, Histogram.class, tags);
    }

    @Override
    public Histogram histogram(MetricID metricID) {
        return metricStore.getOrRegisterMetric(metricID, Histogram.class);
    }

    @Override
    public Histogram getHistogram(MetricID metricID) {
        return getMetric(metricID, Histogram.class);
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
        return metricStore.getOrRegisterMetric(name, Meter.class, tags);
    }

    @Override
    public Meter meter(Metadata metadata, Tag... tags) {
        return metricStore.getOrRegisterMetric(metadata, Meter.class, tags);
    }

    @Override
    public Meter meter(MetricID metricID) {
        return metricStore.getOrRegisterMetric(metricID, Meter.class);
    }

    @Override
    public Meter getMeter(MetricID metricID) {
        return getMetric(metricID, Meter.class);
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
        return metricStore.getOrRegisterMetric(name, Timer.class, tags);
    }

    @Override
    public Timer timer(Metadata metadata, Tag... tags) {
        return metricStore.getOrRegisterMetric(metadata, Timer.class, tags);
    }

    @Override
    public Timer timer(MetricID metricID) {
        return metricStore.getOrRegisterMetric(metricID, Timer.class);
    }

    @Override
    public Timer getTimer(MetricID metricID) {
        return getMetric(metricID, Timer.class);
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
        return metricStore.getOrRegisterMetric(name, ConcurrentGauge.class, tags);
    }

    @Override
    public ConcurrentGauge concurrentGauge(Metadata metadata, Tag... tags) {
        return metricStore.getOrRegisterMetric(metadata, ConcurrentGauge.class, tags);
    }

    @Override
    public ConcurrentGauge concurrentGauge(MetricID metricID) {
        return metricStore.getOrRegisterMetric(metricID, ConcurrentGauge.class);
    }

    @Override
    public ConcurrentGauge getConcurrentGauge(MetricID metricID) {
        return getMetric(metricID, ConcurrentGauge.class);
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
        return metricStore.getOrRegisterMetric(name, SimpleTimer.class, tags);
    }

    @Override
    public SimpleTimer simpleTimer(Metadata metadata, Tag... tags) {
        return metricStore.getOrRegisterMetric(metadata, SimpleTimer.class, tags);
    }

    @Override
    public SimpleTimer getSimpleTimer(MetricID metricID) {
        return getMetric(metricID, SimpleTimer.class);
    }

    @Override
    public SimpleTimer simpleTimer(MetricID metricID) {
        return metricStore.getOrRegisterMetric(metricID, SimpleTimer.class);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(String name, T object, Function<T, R> func, Tag... tags) {
        return metricStore.getOrRegisterGauge(name, object, func, tags);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(MetricID metricID, T object, Function<T, R> func) {
        return metricStore.getOrRegisterGauge(metricID, object, func);
    }

    @Override
    public <T, R extends Number> Gauge<R> gauge(Metadata metadata, T object, Function<T, R> func, Tag... tags) {
        return metricStore.getOrRegisterGauge(metadata, object, func, tags);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(String name, Supplier<T> supplier, Tag... tags) {
        return metricStore.getOrRegisterGauge(name, supplier, tags);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(MetricID metricID, Supplier<T> supplier) {
        return metricStore.getOrRegisterGauge(metricID, supplier);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(Metadata metadata, Supplier<T> supplier, Tag... tags) {
        return metricStore.getOrRegisterGauge(metadata, supplier, tags);
    }

    @Override
    public Gauge<?> getGauge(MetricID metricID) {
        return getMetric(metricID, Gauge.class);
    }

    /**
     * Removes a metric by name.
     *
     * @param name Name of the metric.
     * @return Outcome of removal.
     */
    @Override
    public boolean remove(String name) {
        return metricStore.remove(name);
    }

    /**
     * Removes a metric by ID.
     *
     * @param metricID ID of metric.
     * @return Outcome of removal.
     */
    @Override
    public boolean remove(MetricID metricID) {
        return metricStore.remove(metricID);
    }

    @Override
    public void removeMatching(MetricFilter filter) {
        metricStore.removeMatching(filter);
    }

    @Override
    public SortedSet<String> getNames() {
        return metricStore.getNames();
    }

    @Override
    public SortedSet<MetricID> getMetricIDs() {
        return metricStore.getMetricIDs();
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges() {
        return getGauges(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Gauge> getGauges(MetricFilter filter) {
        return metricStore.getSortedMetrics(filter, Gauge.class);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters() {
        return getCounters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Counter> getCounters(MetricFilter filter) {
        return metricStore.getSortedMetrics(filter, Counter.class);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms() {
        return getHistograms(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Histogram> getHistograms(MetricFilter filter) {
        return metricStore.getSortedMetrics(filter, Histogram.class);
    }

    @Override
    public SortedMap<MetricID, Meter> getMeters() {
        return getMeters(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Meter> getMeters(MetricFilter filter) {
        return metricStore.getSortedMetrics(filter, Meter.class);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers() {
        return getTimers(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, Timer> getTimers(MetricFilter filter) {
        return metricStore.getSortedMetrics(filter, Timer.class);
    }

    @Override
    public SortedMap<MetricID, ConcurrentGauge> getConcurrentGauges() {
        return getConcurrentGauges(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, ConcurrentGauge> getConcurrentGauges(MetricFilter filter) {
        return metricStore.getSortedMetrics(filter, ConcurrentGauge.class);
    }

    @Override
    public SortedMap<MetricID, SimpleTimer> getSimpleTimers() {
        return getSimpleTimers(MetricFilter.ALL);
    }

    @Override
    public SortedMap<MetricID, SimpleTimer> getSimpleTimers(MetricFilter filter) {
        return metricStore.getSortedMetrics(filter, SimpleTimer.class);
    }

    @Override
    public Map<String, Metadata> getMetadata() {
        return Collections.unmodifiableMap(metricStore.metadata());
    }

    @Override
    public Metadata getMetadata(String name) {
        return metricStore.metadata(name);
    }

    @Override
    public Map<MetricID, Metric> getMetrics() {
        return Collections.unmodifiableMap(metricStore.metrics());
    }

    @Override
    public SortedMap<MetricID, Metric> getMetrics(MetricFilter filter) {
        return metricStore.getSortedMetrics(filter, Metric.class);
    }

    @Override
    public <T extends Metric> SortedMap<MetricID, T> getMetrics(Class<T> ofType, MetricFilter filter) {
        return metricStore.getSortedMetrics(filter, ofType);
    }

    @Override
    public HelidonMetric getMetric(MetricID metricID) {
        return metricStore.metric(metricID);
    }

    @Override
    public <T extends Metric> T getMetric(MetricID metricID, Class<T> asType) {
        return asType.cast(metricStore.metric(metricID));
    }

    /**
     * Update the registry settings for this registry.
     *
     * @param registrySettings new settings to use going forward
     */
    public void update(RegistrySettings registrySettings) {
        metricStore.update(registrySettings);
    }


    /**
     * Returns type of this registry.
     *
     * @return The type.
     */
    public String type() {
        return type.getName();
    }

    @Override
    public Type getType() {
        return type;
    }

    /**
     * Determines if registry is empty.
     *
     * @return Outcome of test.
     */
    public boolean empty() {
        return metricStore.metrics().isEmpty();
    }

    @Override
    public String toString() {
        return type() + ": " + metricStore.metrics().size() + " metrics";
    }

    /**
     * Returns a stream of {@link Map.Entry} for this registry for enabled metrics.
     *
     * @return Stream of {@link Map.Entry}
     */
    public Stream<MetricInstance> stream() {
        return metricStore.stream();
    }

    /**
     * Creates a new instance of an implementation wrapper around the indicated metric.
     *
     * @param metadata {@code Metadata} for the metric
     * @param metric the existing metric to be wrapped by the impl
     * @return new wrapper implementation around the specified metric instance
     */
    protected abstract HelidonMetric toImpl(Metadata metadata, Metric metric);

    /**
     * Provides a map from MicroProfile metric type to a factory which creates a concrete metric instance of the MP metric type
     * which also extends the implementation metric base class for the concrete implementation (e.g., no-op or full-featured).
     *
     * @return map from each MicroProfile metric type to the correspondingfactory method
     */
    protected abstract Map<MetricType, BiFunction<String, Metadata, HelidonMetric>> prepareMetricFactories();

    // -- Package private -----------------------------------------------------
    /**
     * Returns a list of metric IDs given a metric name.
     *
     * @param metricName name of the metric of interest
     * @return list of metric IDs for metrics with the specified name; empty if no matches
     */
    protected List<MetricID> metricIDsForName(String metricName) {
        return metricStore.metricIDs(metricName);
    }

    // Concrete implementations might choose to override the default implementation to avoid the extra lambda binding
    // if performance of gauge creation might be an issue.

    /**
     * Creates a gauge instance according to the provided metadata such that retrievals of the gauge value trigger an
     * invocation of the provided function, passing the indicated object.
     * <p>
     *     This default implementation uses a capturing lambda for retrieving the value. Concrete subclasses can override this
     *     implementation if capturing lambda behavior might become a performance issue.
     * </p>
     *
     * @param metadata metadata to use in creating the gauge
     * @param object object to pass to the value-returning function
     * @param func gauge-value-returning function
     * @param <T> Java type of the function parameter (and the object to pass to it)
     * @param <R> specific {@code Number} subtype the gauge reports
     * @return new gauge
     */
    protected <T, R extends Number> Gauge<R> createGauge(Metadata metadata, T object, Function<T, R> func) {
        return createGauge(metadata, () -> func.apply(object));
    }

    /**
     * Creates a gauge instance according to the specified supplier which returns the gauge value.
     *
     * @param metadata metadata to use in creating the gauge
     * @param supplier gauge-value-returning supplier
     * @param <R> specific {@code Number} subtype the supplier returns
     * @return new gauge
     */
    protected abstract <R extends Number> Gauge<R> createGauge(Metadata metadata, Supplier<R> supplier);

    // -- Private methods -----------------------------------------------------

    private static boolean enforceConsistentMetadataType(Metadata existingMetadata, MetricType newType, Tag... tags) {
        if (!existingMetadata.getTypeRaw().equals(newType)) {
            throw new IllegalArgumentException("Attempting to register a new metric "
                                                       + new MetricID(existingMetadata.getName(), tags)
                                                       + " of type "
                                                       + newType.toString()
                                                       + " found pre-existing metadata with conflicting type "
                                                       + existingMetadata.getTypeRaw().toString());
        }
        return true;
    }

    /**
     * Infers the {@link MetricType} from a provided candidate type and a metric instance.
     *
     * @param candidateType type of metric to use if not invalid; typically computed from an existing metric
     * @param metric the metric for which to derive the metric type
     * @return the {@code MetricType} of the metric
     */
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

    /**
     * For testing.
     *
     * @return map from MicroProfile metric type to factory functions.
     */
    protected Map<MetricType, BiFunction<String, Metadata, HelidonMetric>> metricFactories() {
        return metricFactories;
    }

    /**
     * Prepares the map from Java types of implementation metrics to the corresponding {@link MetricType}.
     *
     * @return prepared map for a given metrics implementation
     */
    protected abstract Map<Class<? extends HelidonMetric>, MetricType> prepareMetricToTypeMap();

    /**
     * Gauge factories based on either functions or suppliers.
     * <p>
     *     Metrics implementations (such as the no-op implementation of the full-featured one) implement
     *     these interfaces so as to allow the {@link AbstractRegistry} to simply implement the MicroProfile
     *     methods for registering gauges.
     * </p>
     */
    public interface GaugeFactory {

        /**
         * Gauge factory based on a supplier which provides the gauge value.
         */
        @FunctionalInterface
        interface SupplierBased {

            /**
             * Creates a gauge implemention with the specified metadata which invokes the provided supplier to fetch the gauge
             * value.
             *
             * @param metadata metadata to use in defining the gauge
             * @param valueSupplier {@code Supplier} of the gauge value
             * @param <R> specific {@code Number} subtype the gauge reports
             * @return new gauge implementation
             */
            <R extends Number> Gauge<R> createGauge(Metadata metadata, Supplier<R> valueSupplier);
        }

        /**
         * Gauge factory based on a function which provides the gauge value when passed an object of a declared type.
         */
        @FunctionalInterface
        interface FunctionBased {
            /**
             * Creates a gauge implementation with the specified metadata which invokes the provided function passing the given
             * object.
             *
             * @param metadata metadata to use in defining the gauge
             * @param object object to be passed to the function which provides the gauge value
             * @param valueFunction function which provides the gauge value
             * @param <T> type of object passed to the function
             * @param <R> specific {@code Number} subtype the gauge reports
             * @return new gauge implementation
             */
            <T, R extends Number> Gauge<R> createGauge(Metadata metadata, T object, Function<T, R> valueFunction);
        }
    }
}
