/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.LazyValue;

import org.eclipse.microprofile.metrics.Metadata;

/**
 * Tracks metadata that needs to be consistent across scopes (multiple {@link io.helidon.metrics.api.MetricStore} instances).
 *
 * <p>
 *     All these methods assume that the caller has obtained a lock to arbitrate access to shared data.
 * </p>
 */
class CommonMetadataManager {

    private static final LazyValue<CommonMetadataManager> INSTANCE = LazyValue.create(CommonMetadataManager::new);

    /**
     * Singleton instance of the manager.
     *
     * @return the singleton instance.
     */
    static CommonMetadataManager instance() {
        return INSTANCE.get();
    }

    private final Map<String, Set<String>> tagNameSets = new HashMap<>();
    private final Map<String, Metadata> metadata = new HashMap<>();

    /**
     * If metadata is already associated with the metadata name, throws an exception if the existing and proposed metadata are
     * inconsistent; if there is no existing metadata stored for this name, stores it.
     *
     * @param candidateMetadata proposed metadata
     * @return the metadata
     * @throws java.lang.IllegalArgumentException if metadata has been registered for this name which is inconsistent with
     * the proposed metadata
     */
    Metadata checkOrStoreMetadata(Metadata candidateMetadata) {
        Metadata currentMetadata = metadata.get(candidateMetadata.getName());
        if (currentMetadata == null) {
            return metadata.put(candidateMetadata.getName(), candidateMetadata);
        }
        enforceConsistentMetadata(currentMetadata, candidateMetadata);
        return candidateMetadata;
    }

    /**
     * If tag names are already associated with the metric name, throws an exception if the existing and proposed tag name sets
     * are inconsistent; if there are no tag names stored for this name, store the proposed ones.
     *
     * @param metricName metrics name
     * @param tagNames tag names to validate
     * @return the {@link Set} of tag names
     * @throws java.lang.IllegalArgumentException if tag names have been registered for this name which are inconsistent with
     * the proposed tag names
     */
    Set<String> checkOrStoreTagNames(String metricName, Set<String> tagNames) {
        Set<String> currentTagNames = tagNameSets.get(metricName);
        if (currentTagNames == null) {
            return tagNameSets.put(metricName, tagNames);
        }
        enforceConsistentTagNames(currentTagNames, tagNames);
        return tagNames;
    }

    /**
     * Clears the internal data structures (for testing only).
     *
     */
    void clear() {
        tagNameSets.clear();
        metadata.clear();
    }

    private static void enforceConsistentMetadata(Metadata existingMetadata, Metadata newMetadata) {
        if (!metadataMatches(existingMetadata, newMetadata)) {
            throw new IllegalArgumentException("New metadata conflicts with existing metadata with the same name; existing: "
                                                       + existingMetadata + ", new: "
                                                       + newMetadata);
        }
    }

    private static <T extends Metadata, U extends Metadata> boolean metadataMatches(T a, U b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.getName().equals(b.getName())
                && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getUnit(), b.getUnit());
    }

    private static void enforceConsistentTagNames(Set<String> existingTagNames, Set<String> newTagNames) {
        if (!existingTagNames.equals(newTagNames)) {
            throw new IllegalArgumentException(String.format("New tag names %s conflict with existing tag names %s",
                                                             newTagNames,
                                                             existingTagNames));
        }
    }
}
