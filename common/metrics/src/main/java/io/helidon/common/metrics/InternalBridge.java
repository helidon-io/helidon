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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.Predicate;

import io.helidon.config.Config;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Timer;


/**
 *
 */
public interface InternalBridge {

    static InternalBridge INSTANCE = Loader.loadInternalBridge();

    RegistryFactory registryFactoryInstance();

    RegistryFactory createRegistryFactory();

    RegistryFactory createRegistryFactory(Config config);

    String toOpenMetricsData(String name, Metric metric);

    public interface RegistryFactory {

        MetricRegistry getBridgeRegistry(org.eclipse.microprofile.metrics.MetricRegistry.Type type);
    }

    public interface MetricRegistry {
        Counter counter(Metadata metadata);
        Counter counter(Metadata metadata, Map<String, String> tags);
        Counter counter(String name);

        Meter meter(Metadata metadata);
        Meter meter(Metadata metadata, Map<String, String> tags);
        Meter meter(String name);

        Histogram histogram(Metadata metadata);
        Histogram histogram(Metadata metadata, Map<String, String> tags);
        Histogram histogram(String name);

        Timer timer(Metadata metadata);
        Timer timer(Metadata metadata, Map<String, String> tags);
        Timer timer(String name);

        Map<MetricID, Metric> getBridgeMetrics();

        Map<MetricID, Metric> getBridgeMetrics(Predicate<? super Map.Entry<? extends MetricID, ? extends Metric>> predicate);

        Optional<Metric> getBridgeMetric(String metricName);

        SortedSet<String> getNames();

        SortedMap<MetricID, Counter> getBridgeCounters();

        SortedMap<MetricID, Gauge> getBridgeGauges();

        SortedMap<MetricID, Histogram> getBridgeHistograms();

        SortedMap<MetricID, Meter> getBridgeMeters();

        SortedMap<MetricID, Timer> getBridgeTimers();

        <T extends Metric> T register(Metadata metadata, T metric) throws IllegalArgumentException;

        <T extends Metric> T register(MetricID metricID, T metric) throws IllegalArgumentException;

        boolean remove(String name);
    }

    public interface Metadata {

        String getName();
        String getDisplayName();
        Optional<String> getDescription();
        String getType();
        MetricType getTypeRaw();
        Optional<String> getUnit();
        boolean isReusable();
        Map<String, String> getTags();

        static MetadataBuilder builder() {
            return new MetadataBuilder();
        }

        static MetadataBuilder builder(Metadata metadata) {
            return new MetadataBuilder(Objects.requireNonNull(metadata, "metadata cannot be null"));
        }

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

    public static class Tag {

        private final String name;
        private final String value;

        public Tag(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getTagName() {
            return name;
        }

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

    public static class MetricID implements Comparable<MetricID> {
        private final String name;
        private final Map<String, String> tags;

        public MetricID(String name) {
            this.name = name;
            tags = Collections.emptyMap();
        }

        public MetricID(String name, Map<String, String> tags) {
            this.name = name;
            this.tags = Collections.unmodifiableMap(tags);
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getTags() {
            return Collections.unmodifiableMap(tags);
        }

        public List<Tag> getTagsAsList() {
            return tags.entrySet().stream()
                    .collect(ArrayList::new,
                            (list, entry) ->list.add(new Tag(entry.getKey(), entry.getValue())),
                            List::addAll);
        }

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
                    }
                    else {
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
     *
     */
    public static class MetadataBuilder {

        private String name;
        private String displayName;
        private String description;
        private MetricType type;
        private String unit;
        private boolean reusable;
        private Map<String, String> tags;

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

        public MetadataBuilder withName(String name) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            return this;
        }

        public MetadataBuilder withDisplayName(String displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName cannot be null");
            return this;
        }

        public MetadataBuilder withDescription(String description) {
            this.description = Objects.requireNonNull(description, "description cannot be null");
            return this;
        }

        public MetadataBuilder withType(MetricType type) {
            this.type = Objects.requireNonNull(type, "type cannot be null");
            return this;
        }

        public MetadataBuilder withUnit(String unit) {
            this.unit = Objects.requireNonNull(unit, "unit cannot be null");
            return this;
        }

        public MetadataBuilder reusable() {
            this.reusable = true;
            return this;
        }

        public MetadataBuilder notReusable() {
            this.reusable = false;
            return this;
        }

        public MetadataBuilder withTags(Map<String, String> tags) {
            this.tags = new HashMap<>(tags);
            return this;
        }

        public Metadata build() {
            if (name == null) {
                throw new IllegalStateException("name must be assigned");
            }
            return new MetadataImpl(name, displayName, description, type, unit, reusable, tags);
        }
    }
}
