/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.GenericType;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;

/**
 * Dependency metadata.
 * <p>
 * Dependencies can be injected into a service through a constructor. We also support field injection, though it is not
 * recommended due to complicated unit testing.
 */
@Prototype.Blueprint
interface DependencyBlueprint {
    /**
     * Type name of the service that uses this dependency.
     *
     * @return the service declaring this dependency
     */
    TypeName service();

    /**
     * Name of the constructor parameter.
     *
     * @return unique name of the parameter
     */
    String name();

    /**
     * Each dependency ia a specific contract. Each service provides one or more contracts for dependencies.
     * For example for {@code List<MyService>}, the contract is {@code MyService}.
     *
     * @return contract of the service we depend on
     */
    @Option.Redundant
    TypeName contract();

    /**
     * Generic type equivalent to {@link Dependency#contract()}. We need both, to prevent reflection at runtime.
     *
     * @return generic type of the dependency
     */
    @Option.Redundant
    @Option.Default("OBJECT")
    GenericType<?> contractType();

    /**
     * Descriptor declaring this dependency.
     * Descriptor is always public.
     *
     * @return descriptor
     */
    @Option.Redundant
    TypeName descriptor();

    /**
     * Field name that declares this dependency in the {@link Dependency#descriptor()}. Can be used for code generation.
     * This field is always a public constant.
     *
     * @return field that has the dependency on the descriptor type
     */
    @Option.Redundant
    String descriptorConstant();

    /**
     * Type of the dependency (exact parameter type with all generics).
     *
     * @return type of the dependency as {@link io.helidon.common.types.TypeName}
     */
    @Option.Redundant(stringValue = false)
    TypeName typeName();

    /**
     * Kind of element we inject into (constructor, field, method).
     *
     * @return element kind (for parameters, the containing element)
     */
    @Option.Default("CONSTRUCTOR")
    ElementKind elementKind();

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
     * The annotations on this element, excluding {@link io.helidon.service.registry.Dependency#qualifiers()}.
     *
     * @return the annotations on this element
     */
    @Option.Singular
    @Option.Redundant
    // kind + service type + name is a unique identification already
    Set<Annotation> annotations();

    /**
     * Top level method that declares this method.
     * This is to provide information about overridden methods, as we should only inject such methods once.
     *
     * @return unique identification of a declaring method
     */
    @Option.Redundant(stringValue = false)
    Optional<String> method();

    /**
     * Cardinality of this dependency. Defaults to {@link io.helidon.service.registry.DependencyCardinality#REQUIRED}.
     *
     * @return cardinality of this dependency
     */
    @Option.Default("REQUIRED")
    DependencyCardinality cardinality();

    /**
     * Whether this dependency uses {@link io.helidon.service.registry.ServiceInstance}.
     * Defaults to {@code false}, which means the service is injected via its contract.
     *
     * @return whether the dependency is declared as a {@link io.helidon.service.registry.ServiceInstance}
     */
    boolean isServiceInstance();

    /**
     * Whether this dependency uses a {@link java.util.function.Supplier} instead of a direct instance.
     * This defaults to {@code false}.
     *
     * @return whether the dependency injection point uses a supplier
     */
    boolean isSupplier();
}
