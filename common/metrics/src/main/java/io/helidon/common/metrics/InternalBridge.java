/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.metrics;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.config.Config;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Internal abstraction layer for MicroProfile 1.1 and 2.0.
 * <p>
 * Only Helidon internal clients of metrics should use this interface. Other
 * clients should use Helidon BOMs or bundles which will use the appropriate
 * versions of Helidon metrics and MicroProfile Metrics.
 * <p>
 * Implementations provide for getting instances of these features:
 * <ul>
 * <li>registry factory
 * <li>metric registry
 * <li>metric ID
 * <li>metadata builder
 * <li>metadata
 * </ul>
 * This interface is organized accordingly. Each of these exposes a factory and
 * an instance of that factory. (The metadata builder from MP Metrics 2.0 is the
 * factory for metadata, and therefore is nested under metadata in this
 * interface. ) The top level of the bridge also exposes a few convenience
 * methods for getting new instances of a version-specific implementations of the types.
 */
public interface InternalBridge {

    /**
     * Returns the singleton instance of the bridge.
     */
    InternalBridge INSTANCE = Loader.internalBridge();

    /**
     *
     * @return the singleton metric ID factory
     */
    MetricID.Factory getMetricIDFactory();

    /**
     *
     * @return the singleton metadata builder factory
     */
    Metadata.MetadataBuilder.Factory getMetadataBuilderFactory();

    /**
     *
     * @return the singleton registry factory
     */
    MetricRegistry.RegistryFactory getRegistryFactory();

    /**
     *
     * @return a metadata builder
     */
    static Metadata.MetadataBuilder newMetadataBuilder() {
        return INSTANCE.getMetadataBuilderFactory().newMetadataBuilder();
    }

    /**
     * Creates a new {@code RegistryFactory} with the default configuration, as
     * exposed through the {@code InternalBridge.RegistryFactory} interface.
     *
     * @return the new {@code RegistryFactory}
     */
    MetricRegistry.RegistryFactory createRegistryFactory();

    /**
     * Creates a new {@code RegistryFactory} with the specified configuration,
     * as exposed through the {@code InternalBridge.RegistryFactory} interface.
     *
     * @param config the Helidon {@link Config} to use in initializing the
     * factory.
     * @return the new {@code RegistryFactory}
     */
    MetricRegistry.RegistryFactory createRegistryFactory(Config config);

    /**
     * Abstraction of the {@code MetricRegistry} behavior used by internal
     * Helidon clients.
     * <p>
     * The exposed methods use version-neutral abstractions for
     * {@code Metadata}, {@code MetricID}, and {@code Tag} which are used by
     * MicroProfile Metrics. Some methods have {@code bridge} in their names because
     * the corresponding MicroProfile Metrics methods changed return type but
     * kept the same signature from 1.1 to 2.0, so here they need distinct names
     * to distinguish them from the spec-prescribed methods (that do not use the
     * version-neutral constructs).
     */
    public interface MetricRegistry {

        /**
         * Finds or creates a new {@code Counter} using the specified
         * version-neutral {@code Metadata}.
         *
         * @param metadata used in locating and, if needed, building the counter
         * @return the {@code Counter}
         */
        Counter counter(Metadata metadata);

        /**
         * Finds or creates a new {@code} Counter using the specified
         * version-neutral {@code Metadata} and version-neutral {@code Tag}s.
         *
         * @param metadata used in locating and, if needed, building the counter
         * @param tags used in locating and, if needed, building the counter
         * @return the {@code Counter}
         */
        Counter counter(Metadata metadata, Map<String, String> tags);

        /**
         * Finds or creates a new {@code Counter} using the specified name.
         *
         * @param name name for the new {@code Counter}
         * @return the {@code Counter}
         */
        Counter counter(String name);

        /**
         * Finds or creates a new {@code Meter} using the specified
         * version-neutral {@code Metadata}.
         *
         * @param metadata used in locating and, if needed, building the meter
         * @return the {@code Meter}
         */
        Meter meter(Metadata metadata);

        /**
         * Finds or creates a new {@code Meter} using the specified
         * version-neutral {@code Metadata} and version-neutral {@code Tag}s.
         *
         * @param metadata used in locating and, if needed, building the meter
         * @param tags used in locating and, if needed, building the meter
         * @return the {@code Meter}
         */
        Meter meter(Metadata metadata, Map<String, String> tags);

        /**
         * Finds or creates a new {@code Meter} using the specified name.
         *
         * @param name used in locating and, if needed, building the meter
         * @return the {@code Meter}
         */
        Meter meter(String name);

        /**
         * Finds or creates a new {@code Histogram} using the specified
         * version-neutral {@code Metadata}.
         *
         * @param metadata used in locating and, if needed, building the
         * histogram
         * @return the {@code Histogram}
         */
        Histogram histogram(Metadata metadata);

        /**
         * Finds or creates a new {@code Histogram} using the specified
         * version-neutral {@code Metadata} and version-neutral {@code Tag}s.
         *
         * @param metadata used in locating and, if needed, building the
         * histogram
         * @param tags used in locating and, if needed, building the histogram
         * @return the {@code Histogram}
         */
        Histogram histogram(Metadata metadata, Map<String, String> tags);

        /**
         * Finds or creates a new {@code Histogram} using the specified
         * {@code Metadata}.
         *
         * @param name used in locating and, if needed, building the histogram
         * @return the {@code Histogram}
         */
        Histogram histogram(String name);

        /**
         * Finds or creates a new {@code Timer} using the specified
         * version-neutral {@code Metadata}.
         *
         * @param metadata used in locating and, if needed, building the timer
         * @return the {@code Timer}
         */
        Timer timer(Metadata metadata);

        /**
         * Finds or creates a new {@code Timer} using the specified
         * version-neutral {@code Metadata} and version-neutral {@code Tag}s.
         *
         * @param metadata used in locating and, if needed, building the timer
         * @param tags used in locationg and, if needed, building the timer
         * @return the {@code Timer}
         */
        Timer timer(Metadata metadata, Map<String, String> tags);

        /**
         * Finds or creates a new {@code Timer} using the specified name.
         *
         * @param name used in locating and, if needed, building the timer
         * @return the {@code Timer}
         */
        Timer timer(String name);

        /**
         * Returns all metrics from the registry as a map of version-neutral
         * {@link MetricID}s to {@code Metric}s.
         *
         * @return the metrics
         */
        Map<MetricID, Metric> getBridgeMetrics();

        /**
         * Returns all metrics from the registry as a map of version-neutral
         * {@link MetricID}s to {@code Metric}s, filtered by the provided
         * {@link Predicate}.
         *
         * @param predicate for selecting which metrics to include in the result
         * @return the metrics matching the criteria expressed in the predicate
         */
        Map<MetricID, Metric> getBridgeMetrics(
                Predicate<? super Map.Entry<? extends MetricID, ? extends Metric>> predicate);

        /**
         * Returns an {@link Optional} of the {@code MetricID} and
         * {@link Metric} of the metric matching the given name. If multiple
         * metrics match on the name (this can happen in MP Metrics 2.0 if the
         * metrics were created with different tags) then the method returns the
         * first metric with that name, if any.
         *
         * @param metricName name of the metric to find
         * @return {@code Optional} of a {@code Map.Entry} for the matching ID
         * and metric
         */
        Optional<Map.Entry<? extends MetricID, ? extends Metric>> getBridgeMetric(String metricName);

        /**
         * Returns the names of all metrics in the registry.
         *
         * @return a {@code Set} containing the names
         */
        SortedSet<String> getNames();

        /**
         * Returns all {@code Counter} metrics in the registry as a map of
         * version-neutral {@link MetricID} to {@link Metric} entries.
         *
         * @return a map of all counters
         */
        SortedMap<MetricID, Counter> getBridgeCounters();

        /**
         * Returns all {@code Gauge} metrics in the registry as a map of
         * version-neutral {@link MetricID} to {@link Metric} entries.
         *
         * @return a map of all gauges
         */
        SortedMap<MetricID, Gauge> getBridgeGauges();

        /**
         * Returns all {@code Histogram} metrics in the registry as a map of
         * version-neutral {@link MetricID} to {@link Metric} entries.
         *
         * @return a map of all histograms
         */
        SortedMap<MetricID, Histogram> getBridgeHistograms();

        /**
         * Returns all {@code Meter} metrics in the registry as a map of
         * version-neutral {@link MetricID} to {@link Metric} entries.
         *
         * @return a map of all meters
         */
        SortedMap<MetricID, Meter> getBridgeMeters();

        /**
         * Returns all {@code Timer} metrics in the registry as a map of
         * version-neutral {@link MetricID} to {@link Metric} entries.
         *
         * @return a map of all timers
         */
        SortedMap<MetricID, Timer> getBridgeTimers();

        /**
         * Registers a new metric using the specified version-neutral
         * {@link Metadata} and the typed metric itself.
         *
         * @param <T> the metric type
         * @param metadata the metadata used in registering the metric
         * @param metric the metric to register
         * @return the registered metric
         * @throws IllegalArgumentException if a metric with the same name but
         * inconsistent metadata is already registered
         */
        <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException;

        /**
         * Registers a new metric using the specified version-neutral
         * {@link MetricID} and the typed metric itself.
         *
         * @param <T> the metric type
         * @param metricID the metric ID to be used in registering the metric
         * @param metric the metric to register
         * @return the registered metric
         * @throws IllegalArgumentException if a metric with the same identify
         * but inconsistent metadata is already registered
         */
        <T extends Metric> T register(MetricID metricID, T metric) throws IllegalArgumentException;

        /**
         * Removes the metrics with matching name from the registry.
         *
         * @param name name of the metric
         * @return true if a matching metric was removed; false otherwise
         */
        boolean remove(String name);

        /**
         * Abstraction of the {@code RegistryFactory} behavior used by internal
         * Helidon clients.
         * <p>
         * Using the name {@code RegistryFactory} here instead of just {@code Factory}
         * should simplify changing the client code to use only the MP Metrics 2.0
         * implementation later.
         */
        public interface RegistryFactory {

            /**
             * The factory's singleton instance.
             */
            RegistryFactory INSTANCE = Loader.registryFactory();

            /**
             * Returns the MicroProfile metric {@code MetricRegistry} of the
             * indicated registry type typed as the internal abstraction.
             *
             * @param type registry type selected
             * @return {@code MetricRegistry} of the selected type
             */
            MetricRegistry getBridgeRegistry(org.eclipse.microprofile.metrics.MetricRegistry.Type type);
        }
    }

    /**
     * Version-neutral abstraction of a metric identifier.
     * <p>
     * Note that for a metric with tags, the tags are ALWAYS present in the
     * neutral {@code MetricID}. We want to encourage the internal clients to
     * use the newer programming style, retrieving tags from the ID rather than
     * the metadata (where it was stored in MP Metrics 1.1).
     */
    public interface MetricID extends Comparable<MetricID> {

        /**
         *
         * @return the name from the identifier
         */
        String getName();

        /**
         *
         * @return the tags from the identifier, as a {@code Map}
         */
        Map<String, String> getTags();

        /**
         * Provides the tags as a {@code List}. The returned {@code Tag} objects
         * are separate from those associated with the ID so changes to the tags
         * made by the caller do not perturb the original ID.
         *
         * @return the {@code Tag}s
         */
        default List<Tag> getTagsAsList() {
            return getTags().entrySet().stream()
                    .map(entry -> Tag.newTag(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
        }

        /**
         * Describes the tags as a single string: name1=value1,name2=value2,...
         *
         * @return {@code String} containing the tags
         */
        default String getTagsAsString() {
            return getTags().entrySet().stream()
                    .map((entry) -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                    .collect(Collectors.joining(","));
        }

        /**
         * Compares this instance to another object (per {@code Comparable}.
         *
         * @param o the other object to compare to
         * @return -1, 0, +1 depending on whether this instance is less than,
         * equal to, or greater than the other object.
         */
        @Override
        default int compareTo(MetricID o) {
            int result = getName().compareTo(Objects.requireNonNull(o).getName());
            if (result != 0) {
                return result;
            }
            result = getTags().size() - o.getTags().size();
            if (result == 0) {
                Iterator<Map.Entry<String, String>> thisIterator = getTags().entrySet().iterator();
                Iterator<Map.Entry<String, String>> otherIterator = o.getTags().entrySet().iterator();
                while (thisIterator.hasNext() && otherIterator.hasNext()) {
                    Map.Entry<String, String> thisEntry = thisIterator.next();
                    Map.Entry<String, String> otherEntry = otherIterator.next();
                    result = thisEntry.getKey().compareTo(otherEntry.getKey());
                    if (result != 0) {
                        return result;
                    } else {
                        result = thisEntry.getValue().compareTo(otherEntry.getValue());
                        if (result != 0) {
                            return result;
                        }
                    }
                }
            }
            return result;
        }

        /**
         * Creates new {@code MetricID} instances.
         */
        public interface Factory {

            /**
             * Singleton instance of the factory.
             */
            Factory INSTANCE = Loader.metricIDFactory();

            /**
             * Creates a version-neutral {@code MetricID} initialized with the
             * specified name and any global tags.
             *
             * @param name name of the metric to create
             * @return the new {@code MetricID}
             */
            InternalBridge.MetricID newMetricID(String name);

            /**
             * Creates a version-neutral {@code MetricID} initializes with the
             * specified name and tags.
             *
             * @param name name of the metric to create
             * @param tags tags to associated with the new ID
             * @return the new {@code MetricID}
             */
            InternalBridge.MetricID newMetricID(String name, Map<String, String> tags);
        }
    }

    /**
     * Version-neutral abstraction of metric metadata.
     * <p>
     * Although this interface supports tags, if you are using MicroProfile
     * Metrics 2.0 or later the system ignores tags associated with metadata.
     */
    public interface Metadata {

        /**
         *
         * @return the metric name stored in the metadata
         */
        String getName();

        /**
         *
         * @return the display name
         */
        String getDisplayName();

        /**
         *
         * @return an {@code Optional} of the metadata description
         */
        Optional<String> getDescription();

        /**
         *
         * @return the metric type as a {@code String}
         */
        String getType();

        /**
         *
         * @return the metric type as a MicroProfile Metrics {link MetricType}
         */
        MetricType getTypeRaw();

        /**
         *
         * @return an {@code Optional} of the unit associated with this metadata
         */
        Optional<String> getUnit();

        /**
         *
         * @return whether metrics described by this metadata are reusable or
         * not
         */
        boolean isReusable();

        /**
         * Returns the tags associated with the metadata.
         * <p>
         * Note that if you are using MicroProfile Metrics 2.0 and later the
         * tags associated with this version-neutral metadata are ignored.
         *
         * @return tags
         */
        Map<String, String> getTags();

        /**
         * Prepares a version-neutral {@link Metadata} instance using the
         * specified values, avoiding the need to create and act on a builder.
         * <p>
         * Note that although this method accepts tags, if you are using
         * MicroProfile Metrics 2.0 or later the returned metadata will not
         * include the tags.
         *
         * @param name name for the metrics associated with the metadata
         * @param displayName display name
         * @param description description of the metric
         * @param type {@code MetricType} of the metric
         * @param unit unit that applies to the metric
         * @param isReusable whether or not metrics based on this metadata
         * should be reusable
         * @param tags name/value pairs representing tags
         * @return the prepared version-neutral {@code Metadata}
         */
        static InternalBridge.Metadata newMetadata(String name, String displayName, String description,
                MetricType type, String unit, boolean isReusable, Map<String, String> tags) {
            final MetadataBuilder builder = MetadataBuilder.Factory.INSTANCE.newMetadataBuilder()
                    .withName(name)
                    .withDescription(description)
                    .withDisplayName(displayName)
                    .withType(type)
                    .withTags(tags)
                    .withUnit(unit);
            return (isReusable ? builder.reusable() : builder.notReusable()).build();
        }

        /**
         * Prepares a version-neutral {@link Metadata} instance using the
         * specified values, avoiding the need to create and act on a builder.
         * <p>
         * Note that although this method accepts tags, if you are using
         * MicroProfile Metrics 2.0 or later the returned metadata will not
         * include the tags.
         * <p>
         * Also note that whether the metadata is reusable relies on the
         * underlying MicroProfile Metrics version you are using.
         *
         * @param name name for the metrics associated with the metadata
         * @param displayName display name
         * @param description description of the metric
         * @param type {@code MetricType} of the metric
         * @param unit unit that applies to the metric
         * @param tags name/value pairs representing tags
         * @return the prepared version-neutral {@code Metadata}
         */
        static InternalBridge.Metadata newMetadata(String name, String displayName, String description,
                MetricType type, String unit, Map<String, String> tags) {
            return MetadataBuilder.Factory.INSTANCE.newMetadataBuilder()
                    .withName(name)
                    .withDescription(description)
                    .withDisplayName(displayName)
                    .withType(type)
                    .withTags(tags)
                    .withUnit(unit)
                    .build();
        }

        /**
         * Prepares a version-neutral {@link Metadata} instance using the
         * specified values, avoiding the need to create and act on a builder.
         * <p>
         * Note that whether the metadata is reusable relies on the underlying
         * MicroProfile Metrics version you are using.
         *
         * @param name name for the metrics associated with the metadata
         * @param displayName display name
         * @param description description of the metric
         * @param type {@code MetricType} of the metric
         * @param unit unit that applies to the metric
         * @return the prepared version-neutral {@code Metadata}
         */
        static InternalBridge.Metadata newMetadata(String name, String displayName, String description,
                MetricType type, String unit) {
            return MetadataBuilder.Factory.INSTANCE.newMetadataBuilder()
                    .withName(name)
                    .withDescription(description)
                    .withDisplayName(displayName)
                    .withType(type)
                    .withUnit(unit)
                    .build();
        }

        /**
         * Fluent-style builder for version-neutral {@link Metadata}.
         * <p>
         * The name is from MP Metrics but is actually the factory for {@code Metadata}
         * in the design of the bridge.
         *
         */
        public interface MetadataBuilder {

            /**
             * Sets the name.
             *
             * @param name name to be used in the metadata; cannot be null
             * @return the same builder
             */
            MetadataBuilder withName(String name);

            /**
             * Sets the display name.
             *
             * @param displayName display name to be used in the metadata;
             * cannot be null
             * @return the same builder
             */
            MetadataBuilder withDisplayName(String displayName);

            /**
             * Sets the description.
             *
             * @param description description to be used in the metadata; cannot
             * be null
             * @return the same builder
             */
            MetadataBuilder withDescription(String description);

            /**
             * Sets the metric type.
             *
             * @param type {@link MetricType} to be used in the metadata; cannot
             * be null
             * @return the same builder
             */
            MetadataBuilder withType(MetricType type);

            /**
             * Sets the unit.
             *
             * @param unit unit to be used in the metadata; cannot be null
             * @return the same builder
             */
            MetadataBuilder withUnit(String unit);

            /**
             * Sets that the resulting metadata will be reusable.
             *
             * @return the same builder
             */
            MetadataBuilder reusable();

            /**
             * Sets that the resulting metadata will not be reusable.
             *
             * @return the same builder
             */
            MetadataBuilder notReusable();

            /**
             * Sets the tags.
             * <p>
             * Note that when you use MicroProfile Metrics 2.0 or later, tags
             * associated with metadata are ignored except within the metadata
             * itself.
             *
             * @param tags map conveying the tags to be used in the metadata;
             * @return the same builder
             */
            MetadataBuilder withTags(Map<String, String> tags);

            /**
             * Creates a {@link Metadata} instance using the values set by
             * invocations of the various {@code withXXX} methods.
             *
             * @return the version-neutral {@code Metadata}
             * @throws IllegalStateException if the name was never set
             */
            InternalBridge.Metadata build();

            /**
             * Factory for {@code MetadataBuilder} instances.
             */
            public interface Factory {

                /**
                 * The factory's singleton instance.
                 */
                Factory INSTANCE = Loader.metadataBuilderFactory();

                /**
                 * Returns a new version-specific {@code MetadataBuilder} that
                 * implements the neutral interface.
                 *
                 * @return the builder
                 */
                MetadataBuilder newMetadataBuilder();
            }
        }

    }

    /**
     * Version-neutral representation of a tag.
     */
    public interface Tag {

        /**
         * Creates a new version-neutral tag with the specified name and value.
         *
         * @param name name for the new tag
         * @param value value for the new tag
         * @return the new version-neutral tag
         */
        static Tag newTag(String name, String value) {
            return new InternalTagImpl(name, value);
        }

        /**
         *
         * @return the tag's name
         */
        String getTagName();

        /**
         *
         * @return the tag's value
         */
        String getTagValue();
    }

}
