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

package io.helidon.service.codegen;

import java.util.Set;

import io.helidon.codegen.ClassCode;
import io.helidon.common.types.ResolvedType;

/**
 * New service descriptor metadata with its class code.
 */
public interface DescriptorClassCode {
    /**
     * Create a new instance.
     *
     * @param classCode        class code that contains necessary information for the generated class.
     * @param registryType     type of registry that generates the descriptor (core, inject)
     * @param weight           weight of the service this descriptor describes
     * @param contracts        contracts of the service (i.e. {@code MyContract})
     * @param factoryContracts factory contracts of this service (i.e. {@code Supplier<MyContract>})
     * @return a new class code of service descriptor
     */
    static DescriptorClassCode create(ClassCode classCode,
                                      String registryType,
                                      double weight,
                                      Set<ResolvedType> contracts,
                                      Set<ResolvedType> factoryContracts) {
        return new DescriptorClassCodeImpl(classCode,
                                           registryType,
                                           weight,
                                           contracts,
                                           factoryContracts);
    }

    /**
     * New source code information.
     *
     * @return class code
     */
    ClassCode classCode();

    /**
     * Type of registry of this descriptor.
     *
     * @return registry type
     */
    String registryType();

    /**
     * Weight of the new descriptor.
     *
     * @return weight
     */
    double weight();

    /**
     * Contracts the described service implements/provides.
     *
     * @return contracts of the service
     */
    Set<ResolvedType> contracts();

    /**
     * Contracts of the class if it is a factory.
     *
     * @return factory contracts
     */
    Set<ResolvedType> factoryContracts();
}
