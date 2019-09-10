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
package io.helidon.metrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.helidon.common.metrics.InternalBridge;
import io.helidon.common.metrics.InternalBridge.MetricID;
import io.helidon.common.metrics.InternalBridge.Tag;

import org.eclipse.microprofile.metrics.MetricType;

/**
 * Implementation of a version-neutral metric identifier.
 */
class InternalMetricIDImpl implements InternalBridge.MetricID, Comparable<MetricID> {

    /*
     * Harvest the automatically-created tags from a dummy instance of {@code Metadata}.
     * We'll use these to augment any caller-supplied tags when instantiating an ID.
     */
    private static final Map<String, String> GLOBAL_TAGS = Collections.unmodifiableMap(
            new org.eclipse.microprofile.metrics.Metadata("thisIsPrivate", MetricType.INVALID)
                    .getTags());

    private final String name;
    private final Map<String, String> tags;

    /**
     * Creates a new instance using a name (no tags).
     *
     * @param name the name for the identifier
     */
    InternalMetricIDImpl(String name) {
        this.name = name;
        tags = GLOBAL_TAGS; // No need to copy it; it's already immutable.
    }

    /**
     * Creates a new instance using a name and tags.
     *
     * @param name the name for the identifier
     * @param tags tags to be associated with the identifier
     */
    InternalMetricIDImpl(String name, Map<String, String> tags) {
        this.name = name;
        this.tags = Collections.unmodifiableMap(augmentedTags(tags));
    }

    /**
     *
     * @return the name from the identifier
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     *
     * @return the tags from the identifier, as a {@code Map}
     */
    @Override
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
    @Override
    public List<Tag> getTagsAsList() {
        return tags.entrySet().stream()
                .map(entry -> Tag.newTag(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Describes the tags as a single string: name1=value1,name2=value2,...
     *
     * @return {@code String} containing the tags
     */
    @Override
    public String getTagsAsString() {
        return tags.entrySet().stream()
                .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(","));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.name);
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
        final InternalMetricIDImpl other = (InternalMetricIDImpl) obj;
        return Objects.equals(this.name, other.name);
    }

    @Override
    public String toString() {
        return String.format("MetricID{name='%s', tags=[%s]}", name, getTags());
    }

    @Override
    public int compareTo(MetricID o) {
        return name.compareTo(Objects.requireNonNull(o).getName());
    }

    static class FactoryImpl implements Factory {

        @Override
        public MetricID newMetricID(String name) {
            return new InternalMetricIDImpl(name);
        }

        @Override
        public MetricID newMetricID(String name, Map<String, String> tags) {
            return new InternalMetricIDImpl(name, tags);
        }

    }

    private static Map<String, String> augmentedTags(Map<String, String> explicitTags) {
        final Map<String, String> result = new HashMap<>(GLOBAL_TAGS);
        result.putAll(explicitTags);
        return result;
    }

}
