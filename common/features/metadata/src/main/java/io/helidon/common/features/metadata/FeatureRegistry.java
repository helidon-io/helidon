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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import io.helidon.metadata.hson.Hson;

import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_AOT;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_AOT_SUPPORTED;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_DEPRECATION;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_DEPRECATION_DEPRECATED;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_DESCRIPTION;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_FLAVOR;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_INVALID_FLAVOR;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_MODULE;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_NAME;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_PATH;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_SINCE;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_STATUS;
import static io.helidon.common.features.metadata.FeatureMetadataSupport.HSON_VERSION;

/**
 * Feature descriptor utilities.
 */
public class FeatureRegistry {
    /**
     * Location of version 1 feature metadata.
     */
    public static final String FEATURE_REGISTRY_LOCATION_V1 = "META-INF/helidon/feature-metadata.properties";
    /**
     * Location of version 2 feature registry.
     */
    public static final String FEATURE_REGISTRY_LOCATION_V2 = "META-INF/helidon/feature-registry.json";
    private static final int CURRENT_REGISTRY_VERSION = 2;
    private static final int DEFAULT_REGISTRY_VERSION = 2;

    private FeatureRegistry() {
    }

    /**
     * Get all feature metadata from the provided root.
     *
     * @param location where was this metadata obtained
     * @param properties feature properties (version 1)
     * @return feature metadata
     * @throws java.lang.IllegalStateException in case the format is not as expected
     */
    public static FeatureMetadata metadata(String location, Properties properties) {
        String moduleName = properties.getProperty("m");
        String featureName = properties.getProperty("n");

        if (moduleName == null || featureName == null) {
            throw new IllegalStateException("Invalid feature metadata on " + location + ", missing feature name"
                                                    + " or module name.");
        }

        FeatureMetadata.Builder builder = FeatureMetadata.builder()
                .module(moduleName)
                .name(featureName);

        Optional.ofNullable(properties.getProperty("d"))
                .ifPresent(builder::description);
        Optional.ofNullable(properties.getProperty("s"))
                .ifPresent(builder::since);
        Optional.ofNullable(properties.getProperty("p"))
                .map(it -> it.split(","))
                .map(List::of)
                .ifPresent(builder::path);
        Optional.ofNullable(properties.getProperty("in"))
                .map(it -> it.split(","))
                .map(List::of)
                .stream()
                .flatMap(List::stream)
                .map(Flavor::valueOf)
                .forEach(builder::addFlavor);
        Optional.ofNullable(properties.getProperty("not"))
                .map(it -> it.split(","))
                .map(List::of)
                .stream()
                .flatMap(List::stream)
                .map(Flavor::valueOf)
                .forEach(builder::addInvalidFlavor);
        String aot = properties.getProperty("aot");
        String aotd = properties.getProperty("aotd");
        if (aot != null || aotd != null) {
            Aot.Builder aotBuilder = Aot.builder();
            if (aot != null) {
                aotBuilder.supported(Boolean.parseBoolean(aot));
            }
            if (aotd != null) {
                aotBuilder.description(aotd);
            }
            builder.aot(aotBuilder.build());
        }
        String dep =  properties.getProperty("dep");
        if (dep != null) {
            Deprecation.Builder depBuilder = Deprecation.builder();

            depBuilder.isDeprecated(Boolean.parseBoolean(dep));
            Optional.ofNullable(properties.getProperty("deps"))
                    .ifPresent(depBuilder::since);

            builder.deprecation(depBuilder.build());
        }

        return builder.build();
    }

    /**
     * Get all feature metadata from the provided root.
     *
     * @param location where was this array obtained (such as "Classpath URL: ...")
     * @param root     array of feature metadata
     * @return a list of feature metadata
     * @throws java.lang.IllegalStateException in case the format is not as expected
     */
    public static List<FeatureMetadata> metadata(String location, Hson.Array root) {
        List<FeatureMetadata> result = new ArrayList<>();

        for (Hson.Struct featureMetadata : root.getStructs()) {
            String moduleName = featureMetadata.stringValue(HSON_MODULE)
                    .orElseThrow(() -> new IllegalStateException("Missing required property 'module' for " + location));
            String featureName = featureMetadata.stringValue(HSON_NAME)
                    .orElseThrow(() -> new IllegalStateException("Missing required property 'name' for " + location));
            int version = featureMetadata.intValue(HSON_VERSION, DEFAULT_REGISTRY_VERSION);
            if (version != CURRENT_REGISTRY_VERSION) {
                throw new IllegalStateException("Invalid registry version: " + version
                                                        + " for module \"" + moduleName + "\""
                                                        + " for feature \"" + featureName + "\""
                                                        + " loaded from \"" + location + "\", "
                                                        + "expected version: \"" + CURRENT_REGISTRY_VERSION + "\"");
            }
            result.add(createV2(moduleName, featureName, featureMetadata));
        }

        return result;
    }

    private static FeatureMetadata createV2(String moduleName, String featureName, Hson.Struct featureMetadata) {
        FeatureMetadata.Builder builder = FeatureMetadata.builder()
                .module(moduleName)
                .name(featureName);

        featureMetadata.stringArray(HSON_PATH)
                .ifPresent(builder::path);
        featureMetadata.stringValue(HSON_DESCRIPTION)
                .ifPresent(builder::description);
        featureMetadata.stringValue(HSON_SINCE)
                .ifPresent(builder::since);
        featureMetadata.stringArray(HSON_FLAVOR)
                .stream()
                .flatMap(List::stream)
                .map(Flavor::valueOf)
                .forEach(builder::addFlavor);
        featureMetadata.stringArray(HSON_INVALID_FLAVOR)
                .stream()
                .flatMap(List::stream)
                .map(Flavor::valueOf)
                .forEach(builder::addInvalidFlavor);
        featureMetadata.stringValue(HSON_STATUS)
                .map(FeatureStatus::valueOf)
                .ifPresent(builder::status);
        featureMetadata.structValue(HSON_AOT)
                .ifPresent(it -> addAot(builder, it));
        featureMetadata.structValue(HSON_DEPRECATION)
                .ifPresent(it -> addDeprecation(builder, it));

        return builder.build();
    }

    private static void addDeprecation(FeatureMetadata.Builder builder, Hson.Struct deprecation) {
        Deprecation.Builder deprecationBuilder = Deprecation.builder();

        deprecation.booleanValue(HSON_DEPRECATION_DEPRECATED)
                .ifPresent(deprecationBuilder::isDeprecated);
        deprecation.stringValue(HSON_DESCRIPTION)
                .ifPresent(deprecationBuilder::description);
        deprecation.stringValue(HSON_SINCE)
                .ifPresent(deprecationBuilder::since);

        builder.deprecation(deprecationBuilder.build());
    }

    private static void addAot(FeatureMetadata.Builder builder, Hson.Struct aot) {
        Aot.Builder aotBuilder = Aot.builder()
                .supported(aot.booleanValue(HSON_AOT_SUPPORTED, true));
        aot.stringValue(HSON_DESCRIPTION)
                .ifPresent(aotBuilder::description);
        builder.aot(aotBuilder.build());
    }
}
