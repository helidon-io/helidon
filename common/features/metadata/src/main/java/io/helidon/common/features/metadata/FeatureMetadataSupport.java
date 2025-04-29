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
import java.util.stream.Collectors;

import io.helidon.builder.api.Prototype;
import io.helidon.metadata.hson.Hson;

class FeatureMetadataSupport implements Prototype.BuilderDecorator<FeatureMetadata.BuilderBase<?, ?>> {
    static final String HSON_VERSION = "version";
    static final String HSON_MODULE = "module";
    static final String HSON_NAME = "name";
    static final String HSON_PATH = "path";
    static final String HSON_DESCRIPTION = "description";
    static final String HSON_SINCE = "since";
    static final String HSON_FLAVOR = "flavor";
    static final String HSON_INVALID_FLAVOR = "invalid-flavor";
    static final String HSON_STATUS = "status";
    static final String HSON_AOT = "aot";
    static final String HSON_AOT_SUPPORTED = "supported";
    static final String HSON_DEPRECATION = "deprecation";
    static final String HSON_DEPRECATION_DEPRECATED = "deprecated";

    static Hson.Struct toHson(FeatureMetadataBlueprint featureMetadata) {
        var builder = Hson.structBuilder();

        builder.set(HSON_MODULE, featureMetadata.module());
        builder.set(HSON_NAME, featureMetadata.name());

        var path = featureMetadata.path();
        if (path.size() != 1 || !path.getFirst().equals(featureMetadata.name())) {
            builder.setStrings(HSON_PATH, path);
        }
        featureMetadata.description()
                .ifPresent(it -> builder.set(HSON_DESCRIPTION, it));
        featureMetadata.since()
                .ifPresent(it -> builder.set(HSON_SINCE, it));
        var flavors = featureMetadata.flavors()
                .stream()
                .map(Flavor::name)
                .collect(Collectors.toUnmodifiableList());
        if (!flavors.isEmpty()) {
            builder.setStrings(HSON_FLAVOR, flavors);
        }
        var invalidFlavors = featureMetadata.invalidFlavors()
                .stream()
                .map(Flavor::name)
                .collect(Collectors.toUnmodifiableList());
        if (!invalidFlavors.isEmpty()) {
            builder.setStrings(HSON_INVALID_FLAVOR, invalidFlavors);
        }
        if (featureMetadata.status() != FeatureStatus.PRODUCTION) {
            // only set if status is not production and deprecation is not defined
            if (!featureMetadata.deprecation().map(Deprecation::isDeprecated).orElse(false)) {
                builder.set(HSON_STATUS, featureMetadata.status().name());
            }
        }
        if (featureMetadata.aot().isPresent()) {
            Aot aot = featureMetadata.aot().get();
            if (!aot.supported() || aot.description().isPresent()) {
                var aotBuilder = Hson.structBuilder();

                if (!aot.supported()) {
                    aotBuilder.set(HSON_AOT_SUPPORTED, aot.supported());
                }
                aot.description().ifPresent(it -> aotBuilder.set(HSON_DESCRIPTION, it));

                builder.set(HSON_AOT, aotBuilder.build());
            }
        }
        if (featureMetadata.deprecation().isPresent()) {
            Deprecation deprecation = featureMetadata.deprecation().get();
            if (deprecation.isDeprecated()) {
                // ignore otherwise
                var deprecationBuilder = Hson.structBuilder();

                deprecationBuilder.set(HSON_DEPRECATION_DEPRECATED, true);
                deprecation.description().ifPresent(it -> deprecationBuilder.set(HSON_DESCRIPTION, it));
                deprecation.since().ifPresent(it -> deprecationBuilder.set(HSON_SINCE, it));

                builder.set(HSON_DEPRECATION, deprecationBuilder.build());
            }
        }

        return builder.build();
    }

    @Override
    public void decorate(FeatureMetadata.BuilderBase<?, ?> target) {
        if (target.path().isEmpty()) {
            target.name().map(List::of).ifPresent(target::path);
        }
        if (target.deprecation().map(Deprecation::isDeprecated).orElse(false)) {
            target.status(FeatureStatus.DEPRECATED);
        }
        if (target.status().isEmpty()) {
            target.status(FeatureStatus.PRODUCTION);
        }
    }
}
