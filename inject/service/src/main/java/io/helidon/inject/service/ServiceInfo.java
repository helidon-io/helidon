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

import java.util.List;
import java.util.Set;

import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;

/**
 * Service metadata.
 * Also serves as a unique identification of a service in the service registry.
 */
public interface ServiceInfo extends Weighted {
    /**
     * Id used by the basic Helidon injection.
     */
    String INJECTION_RUNTIME_ID = "INJECTION";

    /**
     * Id of runtime responsible for this service, such as
     * injection, or config driven. The provider is discovered at runtime through service loader.
     *
     * @return type of the runtime
     */
    default String runtimeId() {
        return INJECTION_RUNTIME_ID;
    }

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
     * Set of scopes of this service.
     *
     * @return scopes
     */
    Set<TypeName> scopes();

    /**
     * Returns {@code true} for abstract classes and interfaces,
     * returns {@code false} by default.
     *
     * @return whether this descriptor describes an abstract class or interface
     */
    default boolean isAbstract() {
        return false;
    }
}
