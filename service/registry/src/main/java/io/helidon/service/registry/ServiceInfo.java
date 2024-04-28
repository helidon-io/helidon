/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.util.Set;

import io.helidon.common.Weighted;
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
     * Type of the service descriptor (usually generated).
     *
     * @return descriptor type
     */
    TypeName descriptorType();

    /**
     * Set of contracts the described service implements.
     *
     * @return set of contracts
     */
    default Set<TypeName> contracts() {
        return Set.of();
    }

    /**
     * List of dependencies required by this service.
     * Each dependency is a point of injection of a dependency into
     * a constructor.
     *
     * @return required dependencies
     */
    default List<? extends Dependency> dependencies() {
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
}
