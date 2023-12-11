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

package io.helidon.config.metadata.processor;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;

import static io.helidon.config.metadata.processor.UsedTypes.CONFIGURED;
import static io.helidon.config.metadata.processor.UsedTypes.DESCRIPTION;
import static io.helidon.config.metadata.processor.UsedTypes.PROTOTYPE_PROVIDES;

record ConfiguredAnnotation(Optional<String> description,
                            Optional<String> prefix,
                            List<String> provides,
                            boolean root,
                            boolean ignoreBuildMethod) {

    static ConfiguredAnnotation createMeta(Annotation annotation) {
        return new ConfiguredAnnotation(
                annotation.stringValue("description").filter(Predicate.not(String::isBlank)),
                annotation.stringValue("prefix").filter(Predicate.not(String::isBlank)),
                toProvidesMeta(annotation),
                annotation.booleanValue("root").orElse(false),
                annotation.booleanValue("ignoreBuildMethod").orElse(false)
        );
    }

    static ConfiguredAnnotation createBuilder(TypeInfo blueprint) {
        Optional<String> config = blueprint.findAnnotation(CONFIGURED)
                .flatMap(Annotation::stringValue)
                .filter(Predicate.not(String::isBlank));
        boolean isRoot = config.isPresent() && blueprint.findAnnotation(CONFIGURED)
                .flatMap(it -> it.booleanValue("root"))
                .orElse(true);

        return new ConfiguredAnnotation(
                blueprint.findAnnotation(DESCRIPTION).flatMap(Annotation::stringValue),
                config,
                toProvidesBuilder(blueprint),
                isRoot,
                false
        );
    }

    private static List<String> toProvidesBuilder(TypeInfo blueprint) {
        return blueprint.findAnnotation(PROTOTYPE_PROVIDES)
                .flatMap(Annotation::stringValues)
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    private static List<String> toProvidesMeta(Annotation annotation) {
        return annotation.stringValues("provides")
                .orElseGet(List::of);
    }
}
