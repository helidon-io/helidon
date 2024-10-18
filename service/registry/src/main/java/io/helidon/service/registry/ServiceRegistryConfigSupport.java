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

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

class ServiceRegistryConfigSupport {
    private ServiceRegistryConfigSupport() {
    }

    static class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Put an instance of a contract. In case there is a descriptor that matches the contract
         * (i.e. the service type is the provided contract), the instance will be assigned that descriptor.
         * The instance would be outside of service described services otherwise, creating a
         * "virtual" service descriptor that will not be valid for metadata operations.
         * <p>
         * If there is no descriptor for the contract, you will not be able to use our Maven plugin to code generate bindings and
         * main classes.
         *
         * @param builder  ignored
         * @param contract contract to add a specific instance for
         * @param instance instance of the contract
         */
        @Prototype.BuilderMethod
        static void putContractInstance(ServiceRegistryConfig.BuilderBase<?, ?> builder,
                                        TypeName contract,
                                        Object instance) {
            builder.putServiceInstance(new VirtualDescriptor(contract), instance);
        }

        /**
         * Put an instance of a contract. In case there is a descriptor that matches the contract
         * (i.e. the service type is the provided contract), the instance will be assigned that descriptor.
         * The instance would be outside of service described services otherwise, creating a
         * "virtual" service descriptor that will not be valid for metadata operations.
         * <p>
         * If there is no descriptor for the contract, you will not be able to use our Maven plugin to code generate bindings and
         * main classes.
         *
         * @param builder  ignored
         * @param contract contract to add a specific instance for
         * @param instance instance of the contract
         */
        @Prototype.BuilderMethod
        static void putContractInstance(ServiceRegistryConfig.BuilderBase<?, ?> builder,
                                        Class<?> contract,
                                        Object instance) {
            putContractInstance(builder, TypeName.create(contract), instance);
        }
    }
}
