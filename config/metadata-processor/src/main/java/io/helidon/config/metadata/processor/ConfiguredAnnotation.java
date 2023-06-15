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

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;

import static io.helidon.config.metadata.processor.ConfigMetadataHandler.findValue;

record ConfiguredAnnotation(Optional<String> description,
                            Optional<String> prefix,
                            List<String> provides,
                            boolean root,
                            boolean ignoreBuildMethod) {
    static ConfiguredAnnotation create(AnnotationMirror mirror) {
        return new ConfiguredAnnotation(
                findValue(mirror, "description", String.class).filter(Predicate.not(String::isBlank)),
                findValue(mirror, "prefix", String.class).filter(Predicate.not(String::isBlank)),
                toProvides(mirror),
                findValue(mirror, "root", Boolean.class, false),
                findValue(mirror, "ignoreBuildMethod", Boolean.class, false));
    }

    @SuppressWarnings("unchecked")
    private static List<String> toProvides(AnnotationMirror mirror) {
        return ((List<AnnotationValue>) findValue(mirror, "provides", List.class, List.of()))
                .stream()
                .map(AnnotationValue::getValue)
                .map(Object::toString)
                .toList();
    }
}
