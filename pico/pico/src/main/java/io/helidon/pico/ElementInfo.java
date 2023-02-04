/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico;

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.builder.types.AnnotationAndValue;

/**
 * Abstractly describes method or field elements of a managed service type (i.e., fields, constructors, injectable methods, etc.).
 */
@Builder
public interface ElementInfo {

    /**
     * The name assigned to constructors.
     */
    String CONSTRUCTOR = "<init>";

    /**
     * The kind of injection target.
     */
    enum ElementKind {
        /**
         * The injectable constructor.  Note that there can be at most 1 injectable constructor.
         */
        CONSTRUCTOR,

        /**
         * A field.
         */
        FIELD,

        /**
         * A method.
         */
        METHOD
    }

    /**
     * The access describing the target injection point.
     */
    enum Access {
        /**
         * public.
         */
        PUBLIC,

        /**
         * protected.
         */
        PROTECTED,

        /**
         * package private.
         */
        PACKAGE_PRIVATE,

        /**
         * private.
         */
        PRIVATE
    }

    /**
     * The injection point/receiver kind.
     *
     * @return the kind
     */
    ElementKind elementKind();

    /**
     * The access modifier on the injection point/receiver.
     *
     * @return the access
     */
    Access access();

    /**
     * The element type name (e.g., method type or field type).
     *
     * @return the target receiver type name
     */
    String elementTypeName();

    /**
     * The element name (e.g., method name or field name).
     *
     * @return the target receiver name
     */
    String elementName();

    /**
     * If the element is a method or constructor then this is the ordinal argument position of that argument.
     *
     * @return the offset argument, 0 based, or empty if field type
     */
    Optional<Integer> elementOffset();

    /**
     * If the element is a method or constructor then this is the total argument count for that method.
     *
     * @return total argument count
     */
    Optional<Integer> elementArgs();

    /**
     * True if the injection point is static.
     *
     * @return true if static receiver
     */
    boolean staticDeclaration();

    /**
     * The enclosing class name for the element.
     *
     * @return service type name
     */
    String serviceTypeName();

    /**
     * The annotations on this element.
     *
     * @return the annotations on this element
     */
    @Singular
    Set<AnnotationAndValue> annotations();

    /**
     * The qualifier type annotations on this element.
     *
     * @return the qualifier type annotations on this element
     */
    @Singular
    Set<QualifierAndValue> qualifiers();

}
