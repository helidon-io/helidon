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

package io.helidon.inject.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;

/**
 * Service metadata.
 * Also serves as a unique identification of a service in the service registry.
 */
public interface ServiceInfo extends Weighted {
    /**
     * Type of the service this descriptor describes.
     *
     * @return service type
     */
    TypeName serviceType();

    /**
     * Type of the service info (usually generated).
     *
     * @return descriptor type
     */
    default TypeName infoType() {
        return TypeName.create(getClass());
    }

    /**
     * Set of contracts the described service implements.
     *
     * @return set of contracts
     */
    default Set<TypeName> contracts() {
        return Set.of();
    }

    /**
     * List of dependencies required by this service (and possibly by its supertypes).
     * Each dependency is a point of injection of one instance into
     * constructor, method parameter, or a field.
     *
     * @return required dependencies
     */
    default List<Ip> dependencies() {
        return List.of();
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
    default int runLevel() {
        return Injection.RunLevel.NORMAL;
    }

    /**
     * Services are activated lazily by default. This is to prevent huge load at startup even for services that may never
     * be needed.
     * <p>
     * In addition, we provide the {@link #runLevel()} construct to control service initialization.
     * <p>
     * For the rare case, where we know a service should be initialized eagerly when its scope is activated, you can
     * add {@link io.helidon.inject.service.Injection.Eager} annotation to the service.
     * Please use with extreme care, as this will most likely have performance implications!
     *
     * @return whether this service should be eagerly initialized with its scope
     */
    default boolean isEager() {
        return false;
    }

    /**
     * A service may be driven by instances of another service.
     * If a type is driven by another type, it inherits ALL qualifiers of the type that is driving it.
     *
     * @return driven by type
     */
    default Optional<TypeName> drivenBy() {
        return Optional.empty();
    }

    /**
     * Scope of this service.
     *
     * @return scope of the service
     */
    TypeName scope();

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
     * Type of qualifier a {@link io.helidon.inject.service.QualifiedProvider} provides. This method is only generated
     * for a type that implements qualified provider.
     *
     * @return type name of the qualifier this qualified provider can provide instances for
     */
    default Optional<TypeName> qualifierType() {
        return Optional.empty();
    }
}
