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

package io.helidon.service.codegen;

import java.util.Set;

import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

/**
 * Context of a single round of code generation.
 * For example the first round may generate types, that require additional code generation.
 */
public interface RegistryRoundContext extends RoundContext {
    /**
     * Add a new service descriptor.
     *
     * @param serviceType         type of the service (the implementation class we generate descriptor for)
     * @param descriptorType      type of the service descriptor
     * @param descriptor          descriptor class model
     * @param weight              weight of this service descriptor
     * @param contracts           contracts of this service descriptor
     * @param factoryContracts    contracts of the service class if it is a factory
     * @param originatingElements possible originating elements (such as Element in APT, or ClassInfo in classpath scanning)
     * @throws java.lang.IllegalStateException if an attempt is done to register a new descriptor for the same type
     */
    void addDescriptor(TypeName serviceType,
                       TypeName descriptorType,
                       ClassModel.Builder descriptor,
                       double weight,
                       Set<ResolvedType> contracts,
                       Set<ResolvedType> factoryContracts,
                       Object... originatingElements);

    /**
     * Create service contracts for the provided service that honor configuration of contract discovery.
     *
     * @param serviceInfo type info of the analyzed service
     * @return service contracts
     */
    ServiceContracts serviceContracts(TypeInfo serviceInfo);
}
