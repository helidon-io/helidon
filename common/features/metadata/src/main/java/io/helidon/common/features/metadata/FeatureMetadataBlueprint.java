/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.features.metadata;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.metadata.hson.Hson;

/**
 * Metadata of a feature, as stored in one of the Helidon specific files
 * {@link io.helidon.common.features.metadata.FeatureRegistry#FEATURE_REGISTRY_LOCATION_V1} or
 * {@link io.helidon.common.features.metadata.FeatureRegistry#FEATURE_REGISTRY_LOCATION_V2}.
 */
@Prototype.Blueprint(decorator = FeatureMetadataSupport.class)
interface FeatureMetadataBlueprint {
    /**
     * Module name.
     *
     * @return module name
     */
    String module();

    /**
     * Feature name.
     *
     * @return feature name
     */
    String name();

    /**
     * Feature description.
     *
     * @return description
     */
    Optional<String> description();

    /**
     * Feature path.
     *
     * @return path
     */
    List<String> path();

    /**
     * First version of Helidon this feature was in.
     *
     * @return since version
     */
    Optional<String> since();

    /**
     * Which flavor(s) should print this feature.
     *
     * @return flavors
     */
    @Option.Singular
    List<Flavor> flavors();

    /**
     * In which flavor we should warn that this feature is present on classpath.
     *
     * @return flavors
     */
    @Option.Singular
    List<Flavor> invalidFlavors();

    /**
     * Ahead of time compilation information (native-image).
     *
     * @return AOT information
     */
    Optional<Aot> aot();

    /**
     * Deprecation information.
     *
     * @return deprecation info
     */
    Optional<Deprecation> deprecation();

    /**
     * Feature status.
     *
     * @return status
     */
    FeatureStatus status();

    /**
     * Create the metadata in Helidon metadata format. This is used by components that store
     * the metadata.
     *
     * @return HSON object (simplified JSON)
     */
    default Hson.Struct toHson() {
        return FeatureMetadataSupport.toHson(this);
    }

    /**
     * String representation of the {@link io.helidon.common.features.metadata.FeatureMetadata#path()}, using forward
     * slash as a separator.
     *
     * @return string path representation
     */
    default String stringPath() {
        return String.join("/", path());
    }
}
