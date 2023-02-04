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

package io.helidon.builder.types;

import java.util.Map;
import java.util.Optional;

/**
 * Represents an annotation along with its value(s).
 */
public interface AnnotationAndValue {

    /**
     * The type name, e.g., {@link java.util.Objects} -> "java.util.Objects".
     *
     * @return the annotation type name
     */
    TypeName typeName();

    /**
     * The value property.
     *
     * @return The string value of value property
     */
    Optional<String> value();

    /**
     * Get a value of an annotation property.
     *
     * @param name name of the annotation property
     * @return string value of the property
     */
    Optional<String> value(String name);

    /**
     * Get a key-value of all the annotation properties.
     *
     * @return key-value pairs of all the properties present
     */
    Map<String, String> values();

    /**
     * Determines whether the {@link #value()} is present and a non-blank String (see {@link String#isBlank()}.
     *
     * @return true if our value is present and non-blank
     */
    default boolean hasNonBlankValue() {
        Optional<String> val = value();
        return val.isPresent() && !val.get().isBlank();
    }

}
