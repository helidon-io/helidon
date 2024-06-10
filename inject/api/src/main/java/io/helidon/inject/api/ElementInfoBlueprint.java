/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.api;

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Abstractly describes method or field elements of a managed service type (i.e., fields, constructors, injectable methods, etc.).
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
@Prototype.Blueprint
interface ElementInfoBlueprint {

    /**
     * The name assigned to constructors.
     */
    String CONSTRUCTOR = "<init>";

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
    AccessModifier access();

    /**
     * The element type name (e.g., method type or field type).
     *
     * @return the target receiver type name
     */
    TypeName elementTypeName();

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
    @ConfiguredOption("false")
    boolean staticDeclaration();

    /**
     * The enclosing class name for the element.
     *
     * @return service type name
     */
    TypeName serviceTypeName();

    /**
     * The annotations on this element.
     *
     * @return the annotations on this element
     */
    @Option.Singular
    Set<Annotation> annotations();

    /**
     * The qualifier type annotations on this element.
     *
     * @return the qualifier type annotations on this element
     */
    @Option.Singular
    Set<Qualifier> qualifiers();

}
