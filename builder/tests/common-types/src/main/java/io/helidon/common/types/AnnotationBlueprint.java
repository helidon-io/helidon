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

package io.helidon.common.types;

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * An annotation with defined values.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(AnnotationSupport.class)
@Prototype.Implement("java.lang.Comparable<Annotation>")
interface AnnotationBlueprint {
    /**
     * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
     *
     * @return the annotation type name
     */
    @ConfiguredOption(required = true)
    TypeName typeName();

    /**
     * Get a key-value of all the annotation properties.
     *
     * @return key-value pairs of all the properties present
     */
    @Prototype.Singular
    Map<String, String> values();

    /**
     * The value property.
     *
     * @return The string value of value property
     */
    default Optional<String> value() {
        return getValue("value");
    }

    /**
     * Get a value of an annotation property.
     *
     * @param name name of the annotation property
     * @return string value of the property
     */
    default Optional<String> getValue(String name) {
        return Optional.ofNullable(values().get(name));
    }
}
