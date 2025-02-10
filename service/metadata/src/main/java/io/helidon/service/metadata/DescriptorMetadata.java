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

package io.helidon.service.metadata;

import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.metadata.hson.Hson;

/**
 * Metadata of a service descriptor, as stored in Helidon specific file {@value Descriptors#SERVICE_REGISTRY_LOCATION}.
 */
public interface DescriptorMetadata {
    /**
     * Create a new instance from descriptor information, i.e. when code generating the descriptor metadata.
     *
     * @param descriptor       type of the service descriptor (the generated file from {@code helidon-service-codegen})
     * @param weight           weight of the service descriptor
     * @param contracts        contracts the service implements
     * @param factoryContracts factory contracts the service instance implements
     * @return a new descriptor metadata instance
     */
    static DescriptorMetadata create(TypeName descriptor,
                                     double weight,
                                     Set<ResolvedType> contracts,
                                     Set<ResolvedType> factoryContracts) {
        return new DescriptorMetadataImpl(weight, descriptor, contracts, factoryContracts);
    }

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
    Set<ResolvedType> contracts();

    /**
     * Contracts of the factory service, if this describes a factory, empty otherwise.
     *
     * @return factory contracts
     */
    Set<ResolvedType> factoryContracts();

    /**
     * Weight of the service.
     *
     * @return service weight
     * @see io.helidon.common.Weight
     */
    double weight();

    /**
     * Create the metadata in Helidon metadata format. This is used by components that store
     * the metadata.
     *
     * @return HSON object (similar to JSON)
     */
    Hson.Struct toHson();
}
