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

package io.helidon.inject.service;

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;

/**
 * Unique identification, and metadata of an injection point.
 */
@Prototype.Blueprint
interface IpBlueprint {
    /**
     * Type name of the service that contains this injection point.
     *
     * @return the service declaring this injection point
     */
    TypeName service();

    /**
     * Kind of element we inject into (constructor, field, method).
     *
     * @return element kind (for parameters, the containing element)
     */
    @Option.Default("CONSTRUCTOR")
    ElementKind elementKind();

    /**
     * Unique name within a kind within a single type.
     *
     * @return unique name of the field or parameter
     */
    String name();

    /**
     * Descriptor declaring this dependency.
     *
     * @return descriptor
     */
    @Option.Redundant
    // kind + service type + name is a unique identification already
    TypeName descriptor();

    /**
     * Each injection point expects a specific contract to be injected.
     * For example for {@code List<MyService>}, the contract is {@code MyService}.
     *
     * @return contract of the injected service(s)
     */
    @Option.Redundant
    // kind + service type + name is a unique identification already
    TypeName contract();

    /**
     * The qualifier type annotations on this element.
     *
     * @return the qualifier type annotations on this element
     */
    @Option.Singular
    @Option.Redundant(stringValue = false)
    // kind + service type + name is a unique identification already
    Set<Qualifier> qualifiers();

    /**
     * Field name that declares this ID in the {@link #descriptor()}. Can be used for code generation.
     * This field is always a public constant.
     *
     * @return field that has the id on the descriptor
     */
    @Option.Redundant
    // kind + service type + name is a unique identification already
    String field();

    /**
     * The access modifier on the injection point/receiver.
     * Defaults to {@link io.helidon.common.types.AccessModifier#PACKAGE_PRIVATE}.
     *
     * @return the access
     */
    @Option.Default("PACKAGE_PRIVATE")
    @Option.Redundant
    // kind + service type + name is a unique identification already
    AccessModifier access();

    /**
     * The annotations on this element.
     *
     * @return the annotations on this element
     */
    @Option.Singular
    @Option.Redundant
    // kind + service type + name is a unique identification already
    Set<Annotation> annotations();

    /**
     * Type of the injection point (exact parameter type with all generics).
     *
     * @return type of the injection point as {@link io.helidon.common.types.TypeName}
     */
    @Option.Redundant(stringValue = false)
    // kind + service type + name is a unique identification already
    TypeName typeName();

    /**
     * Top level method that declares this method.
     * This is to provide information about overridden methods, as we should only inject such methods once.
     *
     * @return unique identification of a declaring method
     */
    @Option.Redundant(stringValue = false)
    Optional<String> method();
}
