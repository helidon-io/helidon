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

package io.helidon.validation;

import java.util.List;
import java.util.Optional;

import io.helidon.common.types.Annotation;

/**
 * Violation of a constraint.
 */
public interface ConstraintViolation {

    /**
     * Descriptive message of the failure.
     *
     * @return message
     */
    String message();

    /**
     * Location of the failure.
     *
     * @return location
     */
    List<PathElement> location();

    /**
     * Root validated object.
     * When validating an instance, this returns the same instance,
     * when validating a method, field, parameter etc., this returns the instance containing the validated
     * element.
     * When validating a constructor, this returns empty.
     *
     * @return the root of validation, or empty if not available
     */
    Optional<Object> rootObject();

    /**
     * The type of the root validated object, or the class containing the element that is validated.
     *
     * @return root type
     */
    Class<?> rootType();

    /**
     * The value that failed validation.
     * <p>
     * Note: this method may return {@code null}!
     *
     * @return the value that failed validation
     */
    Object invalidValue();

    /**
     * Annotation that triggered the constraint validation.
     *
     * @return annotation
     */
    Annotation annotation();

    /**
     * Location of the violation.
     */
    enum Location {
        /**
         * A type (interface, record, class).
         */
        TYPE,
        /**
         * A record component.
         */
        RECORD_COMPONENT,
        /**
         * An annotated return value of a method.
         */
        RETURN_VALUE,
        /**
         * An annotation parameter of a method or constructor.
         */
        PARAMETER,
        /**
         * An annotated property - getter.
         */
        PROPERTY,
        /**
         * An annotated field.
         */
        FIELD,
        /**
         * Method that may have annotated return type or parameters.
         */
        METHOD,
        /**
         * Constructor that may have annotated return type or parameters.
         */
        CONSTRUCTOR,
        /**
         * A map key.
         */
        KEY,
        /**
         * A collection element, optional or map value.
         */
        ELEMENT
    }

    /**
     * A path element of a constraint violation.
     */
    interface PathElement {
        /**
         * Create a new path element.
         *
         * @param location location of the element (such as {@link io.helidon.validation.ConstraintViolation.Location#METHOD}
         * @param name name of the element (such as {@code process(ValidatedType)})
         * @return a new path element
         */
        static PathElement create(Location location, String name) {
            return new ValidationContextImpl.PathImpl(location, name);
        }

        /**
         * Location of the element.
         *
         * @return location
         */
        Location location();

        /**
         * Name of the element.
         *
         * @return element name
         */
        String name();
    }
}
