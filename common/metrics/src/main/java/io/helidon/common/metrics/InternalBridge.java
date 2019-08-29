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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 *
 */
public interface InternalBridge {

    /**
     * Returns the singleton instance of the bridge.
     */
    InternalBridge INSTANCE = Loader.loadInternalBridge();

    /**
     * Returns the singleton instance of the {@code RegistryFactory} as exposed
     * through the {@code InternalBridge.RegistryFactory} interface.
     *
     * @return the {@code RegistryFactory}
     */
    RegistryFactory registryFactoryInstance();

    /**
     * Creates a new {@code RegistryFactory} with the default configuration, as
     * exposed through the {@code InternalBridge.RegistryFactory} interface.
     *
     * @return the new {@code RegistryFactory}
     */
    RegistryFactory createRegistryFactory();

    /**
     * Creates a new {@code RegistryFactory} with the specified configuration,
     * as exposed through the {@code InternalBridge.RegistryFactory} interface.
     *
     * @param config the Helidon {@link Config} to use in initializing the
     * factory.
     * @return the new {@code RegistryFactory}
     */
    RegistryFactory createRegistryFactory(Config config);

    /**
     * Abstraction of the {@code RegistryFactory} behavior used by internal
     * Helidon clients.
     */
    public interface RegistryFactory {

        /**
         * Returns the MicroProfile metric {@code MetricRegistry} of the
         * indicated registry type typed as the internal abstraction.
         *
         * @param type registry type selected
         * @return {@code MetricRegistry} of the selected type
         */
        MetricRegistry getBridgeRegistry(org.eclipse.microprofile.metrics.MetricRegistry.Type type);
    }

    /**
     * Abstraction of the {@code MetricRegistry} behavior used by internal
     * Helidon clients.
     * <p>
     * The exposed methods use version-neutral abstractions for
     * {@code Metadata}, {@code MetricID}, and {@code Tag} which are used by
     * MicroProfile Metrics.
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
        Map<MetricID, Metric> getBridgeMetrics(Predicate<? super Map.Entry<? extends MetricID, ? extends Metric>> predicate);

        /**
         * Returns an {@link Optional} of the {@link Metric} of the given name.
         * If multiple metrics match on the name (this can happen in MP Metrics
         * 2.0 if the metrics were created with different tags) then the method
         * returns the first metric with that name, if any.
         *
         * @param metricName name of the metric to find
         * @return {@code Optional} of the matching {@code Metric}
         */
        Optional<Metric> getBridgeMetric(String metricName);

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
         *
         * @return a builder for version-neutral {@link Metadata}
         */
        static MetadataBuilder builder() {
            return new MetadataBuilder();
        }

        /**
         *
         * @param metadata to be used to initialize the builder
         * @return a pre-initialized builder for version-neutral
         * {@link Metadata}
         */
        static MetadataBuilder builder(Metadata metadata) {
            return new MetadataBuilder(Objects.requireNonNull(metadata, "metadata cannot be null"));
        }

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
        static Metadata newMetadata(String name,
                String displayName,
                String description,
                MetricType type,
                String unit,
                boolean isReusable,
                Map<String, String> tags) {

            final MetadataBuilder builder = new MetadataBuilder()
                    .withName(name)
                    .withDisplayName(displayName)
                    .withDescription(description)
                    .withType(type)
                    .withUnit(unit)
                    .withTags(tags);
            return (isReusable ? builder.reusable() : builder.notReusable())
                    .build();
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
        static Metadata newMetadata(String name,
                String displayName,
                String description,
                MetricType type,
                String unit,
                Map<String, String> tags) {

            return new MetadataBuilder()
                    .withName(name)
                    .withDisplayName(displayName)
                    .withDescription(description)
                    .withType(type)
                    .withUnit(unit)
                    .withTags(tags)
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
        static Metadata newMetadata(String name,
                String displayName,
                String description,
                MetricType type,
                String unit) {
            return new MetadataBuilder()
                    .withName(name)
                    .withDisplayName(displayName)
                    .withType(type)
                    .withUnit(unit)
                    .build();
        }

    }

    /**
     * Version-neutral implementation of {@code Tag} expressing a name/value
     * pair.
     */
    class Tag {

        private final String name;
        private final String value;

        /**
         * Creates a new tag.
         *
         * @param name used for the tag
         * @param value used for the tag
         */
        public Tag(String name, String value) {
            this.name = name;
            this.value = value;
        }

        /**
         *
         * @return the name of the tag
         */
        public String getTagName() {
            return name;
        }

        /**
         *
         * @return the value of the tag
         */
        public String getTagValue() {
            return value;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + Objects.hashCode(this.name);
            hash = 59 * hash + Objects.hashCode(this.value);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Tag other = (Tag) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.value, other.value)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return String.format("Tag{%s=%s}", name, value);
        }
    }

    // TODO - do we need to delegate this to the impls for auto-tag creation?
    /**
     * Version-neutral implementation of a metric identifier, consisting of a
     * name and possibly tags.
     */
    class MetricID implements Comparable<MetricID> {

        private final String name;
        private final Map<String, String> tags;

        /**
         * Creates a new instance using a name (no tags).
         *
         * @param name the name for the identifier
         */
        public MetricID(String name) {
            this.name = name;
            tags = Collections.emptyMap();
        }

        /**
         * Creates a new instance using a name and tags.
         *
         * @param name the name for the identifier
         * @param tags tags to be associated with the identifier
         */
        public MetricID(String name, Map<String, String> tags) {
            this.name = name;
            this.tags = Collections.unmodifiableMap(tags);
        }

        /**
         *
         * @return the name from the identifier
         */
        public String getName() {
            return name;
        }

        /**
         *
         * @return the tags from the identifier, as a {@code Map}
         */
        public Map<String, String> getTags() {
            return Collections.unmodifiableMap(tags);
        }

        /**
         * Provides the tags as a {@code List}. The returned {@code Tag} objects
         * are separate from those associated with the ID so changes to the tags
         * made by the caller do not perturb the original ID.
         *
         * @return the {@code Tag}s
         */
        public List<Tag> getTagsAsList() {
            return tags.entrySet().stream()
                    .collect(ArrayList::new,
                            (list, entry) -> list.add(new Tag(entry.getKey(), entry.getValue())),
                            List::addAll);
        }

        /**
         * Describes the tags as a single string: name1=value1,name2=value2,...
         *
         * @return {@code String} containing the tags
         */
        public String getTagsAsString() {
            return tags.entrySet().stream()
                    .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                    .collect(Collectors.joining(","));
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.name);
            hash = 29 * hash + Objects.hashCode(this.tags);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final MetricID other = (MetricID) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.tags, other.tags)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return String.format("MetricID{name='%s', tags=[%s]}", name, getTags());
        }

        @Override
        public int compareTo(MetricID o) {
            int result = name.compareTo(Objects.requireNonNull(o).getName());
            if (result != 0) {
                return result;
            }
            result = tags.size() - o.getTags().size();
            if (result == 0) {
                Iterator<Map.Entry<String, String>> thisIterator = tags.entrySet().iterator();
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
    }

    /**
     * Fluent-style builder for version-neutral {@link Metadata}.
     *
     */
    class MetadataBuilder {

        private String name;
        private String displayName;
        private String description;
        private MetricType type;
        private String unit;
        private boolean reusable;
        private Map<String, String> tags;

        /**
         * Creates a new builder.
         */
        public MetadataBuilder() {
            super();
        }

        MetadataBuilder(Metadata metadata) {
            super();
            this.name = metadata.getName();
            this.type = metadata.getTypeRaw();
            this.reusable = metadata.isReusable();
            this.displayName = metadata.getDisplayName();
            metadata.getDescription().ifPresent(this::withDescription);
            metadata.getUnit().ifPresent(this::withUnit);
            this.tags = new HashMap<>(Optional.ofNullable(metadata.getTags()).orElse(Collections.emptyMap()));
        }

        /**
         * Sets the name.
         *
         * @param name name to be used in the metadata; cannot be null
         * @return the same builder
         */
        public MetadataBuilder withName(String name) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            return this;
        }

        /**
         * Sets the display name.
         *
         * @param displayName display name to be used in the metadata; cannot be
         * null
         * @return the same builder
         */
        public MetadataBuilder withDisplayName(String displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
            return this;
        }

        /**
         * Sets the description.
         *
         * @param description description to be used in the metadata; cannot be
         * null
         * @return the same builder
         */
        public MetadataBuilder withDescription(String description) {
            this.description = Objects.requireNonNull(description, "description cannot be null");
            return this;
        }

        /**
         * Sets the metric type.
         *
         * @param type {@link MetricType} to be used in the metadata; cannot be
         * null
         * @return the same builder
         */
        public MetadataBuilder withType(MetricType type) {
            this.type = Objects.requireNonNull(type, "type cannot be null");
            return this;
        }

        /**
         * Sets the unit.
         *
         * @param unit unit to be used in the metadata; cannot be null
         * @return the same builder
         */
        public MetadataBuilder withUnit(String unit) {
            this.unit = Objects.requireNonNull(unit, "unit cannot be null");
            return this;
        }

        /**
         * Sets that the resulting metadata will be reusable.
         *
         * @return the same builder
         */
        public MetadataBuilder reusable() {
            this.reusable = true;
            return this;
        }

        /**
         * Sets that the resulting metadata will not be reusable.
         *
         * @return the same builder
         */
        public MetadataBuilder notReusable() {
            this.reusable = false;
            return this;
        }

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
        public MetadataBuilder withTags(Map<String, String> tags) {
            this.tags = new HashMap<>(tags);
            return this;
        }

        /**
         * Creates a {@link Metadata} instance using the values set by
         * invocations of the various {@code withXXX} methods.
         *
         * @return the version-neutral {@code Metadata}
         * @throws IllegalStateException if the name was never set
         */
        public Metadata build() {
            if (name == null) {
                throw new IllegalStateException("name must be assigned");
            }
            return new MetadataImpl(name, displayName, description, type, unit, reusable, tags);
        }
    }
}
