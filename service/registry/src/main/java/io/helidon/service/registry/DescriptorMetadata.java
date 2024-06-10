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

import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Metadata of a single service descriptor.
 * This information is stored within the Helidon specific {code META-INF} services file.
 */
public interface DescriptorMetadata {
    /**
     * {@link #registryType()} for core services.
     */
    String REGISTRY_TYPE_CORE = "core";

    /**
     * Type of registry, such as {@code core}.
     *
     * @return registry type this descriptor is created for
     */
    String registryType();

    /**
     * Descriptor type name.
     *
     * @return descriptor type
     */
    TypeName descriptorType();

    /**
     * Contracts of the service.
     *
     * @return contracts the service implements/provides.
     */
    Set<TypeName> contracts();

    /**
     * Weight of the service.
     *
     * @return service weight
     * @see io.helidon.common.Weight
     */
    double weight();

    /**
     * Descriptor instance.
     *
     * @return the descriptor
     */
    GeneratedService.Descriptor<?> descriptor();
}
