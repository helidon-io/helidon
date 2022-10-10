/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.types;

import java.util.Map;
import java.util.Objects;

/**
 * Represents an annotation along with its value(s).
 */
public interface AnnotationAndValue {

    /**
     * The type name, e.g., {@link jakarta.inject.Named} -> "jakarta.inject.Named".
     *
     * @return the annotation type name
     */
    TypeName getTypeName();

    /**
     * The value property.
     *
     * @return The string value of value property, or null if no value is present
     */
    String getValue();

    /**
     * Get a value of an annotation property.
     *
     * @param name name of the annotation property
     * @return string value of the property
     */
    String getValue(String name);

    /**
     * Get a key-value of all the annotation properties.
     *
     * @return key-value pairs of all the properties present
     */
    Map<String, String> getValues();

    /**
     * Determines whether the {@link #getValue()} is a non-null and non-blank value.
     *
     * @return true if the value provided is non-null and non-blank (i.e., {@link String#isBlank()})
     */
    default boolean hasValue() {
        return hasValue(getValue());
    }

    /**
     * Helper method to determine if the value is present (i.e., non-null and non-blank).
     *
     * @param val the value to check
     * @return true if the value provided is non-null and non-blank.
     */
    static boolean hasValue(String val) {
        return Objects.nonNull(val) && !val.isBlank();
    }

}
