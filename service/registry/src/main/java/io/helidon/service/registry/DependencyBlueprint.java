/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.GenericType;
import io.helidon.common.types.TypeName;

/**
 * Dependency metadata.
 * The basic dependency supports other services to be passed to a constructor parameter.
 * The dependency may be a contract, {@link java.util.List} of contracts, or an {@link java.util.Optional}
 * of contract, or {@link java.util.function.Supplier} of any of these.
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
}
