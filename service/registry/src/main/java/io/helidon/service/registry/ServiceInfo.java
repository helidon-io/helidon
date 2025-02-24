/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Weighted;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;

/**
 * Service metadata.
 */
public interface ServiceInfo extends Weighted {
    /**
     * Type of the service this descriptor describes.
     *
     * @return service type
     */
    TypeName serviceType();

    /**
     * The type that implements {@link #contracts()}.
     * If the described service is not a factory, this will be the {@link #serviceType()}.
     * If the described service is a factory, this will be the type in its factory declaration, i.e. the type
     * it supplies, or the type of the services factory.
     *
     * @return type this service provides; this may be an interface, or an implementation, the type will implement all
     *         {@link #contracts()} of this service
     */
    default TypeName providedType() {
        return serviceType();
    }

    /**
     * Type of the service descriptor (usually generated).
     *
     * @return descriptor type
     */
    TypeName descriptorType();

    /**
     * Set of contracts the described service implements or provides through a factory method.
     *
     * @return set of contracts
     */
    default Set<ResolvedType> contracts() {
        return Set.of();
    }

    /**
     * Set of contracts the described service implements directly. If the service is not a factory,
     * this set is empty.
     *
     * @return set of factory contracts
     */
    default Set<ResolvedType> factoryContracts() {
        return Set.of();
    }

    /**
     * List of dependencies required by this service.
     * These may be injection points as constructor parameters, fields, or setter methods.
     *
     * @return required dependencies
     */
    default List<Dependency> dependencies() {
        return List.of();
    }

    /**
     * Returns {@code true} for abstract classes and interfaces,
     * returns {@code false} by default.
     *
     * @return whether this descriptor describes an abstract class or interface
     */
    default boolean isAbstract() {
        return false;
    }

    /**
     * Service qualifiers.
     *
     * @return qualifiers
     */
    default Set<Qualifier> qualifiers() {
        return Set.of();
    }

    /**
     * Run level of this service.
     *
     * @return run level
     */
    default Optional<Double> runLevel() {
        return Optional.empty();
    }

    /**
     * Scope of this service.
     *
     * @return scope of the service
     */
    TypeName scope();

    /**
     * What factory type is the described service.
     * Inject services can be any of the types in the {@link FactoryType enum}.
     *
     * @return factory type
     */
    default FactoryType factoryType() {
        return FactoryType.SERVICE;
    }
}
