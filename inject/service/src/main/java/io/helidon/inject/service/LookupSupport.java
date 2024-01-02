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

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

final class LookupSupport {
    private LookupSupport() {
    }

    static final class CustomMethods {
        /**
         * Empty criteria would match anything and everything except for abstract types.
         */
        @Prototype.Constant
        static final Lookup EMPTY = createEmpty();

        private CustomMethods() {
        }

        /**
         * Create service info criteria for lookup from this injection point information.
         *
         * @param ipId injection point id to create criteria for
         * @return criteria to lookup matching services
         */
        @Prototype.FactoryMethod
        static Lookup create(Ip ipId) {
            return Lookup.builder()
                    .qualifiers(ipId.qualifiers())
                    .addContract(ipId.contract())
                    .build();
        }

        /**
         * Create service info criteria for lookup from a contract type.
         *
         * @param contract a single contract to base the criteria on
         * @return criteria to lookup matching services
         */
        @Prototype.FactoryMethod
        static Lookup create(Class<?> contract) {
            return Lookup.builder()
                    .addContract(contract)
                    .build();
        }

        /**
         * The managed services advertised types (i.e., typically its interfaces).
         *
         * @param builder  builder instance
         * @param contract the service contracts implemented
         * @see #contracts()
         */
        @Prototype.BuilderMethod
        static void addContract(Lookup.BuilderBase<?, ?> builder, Class<?> contract) {
            builder.addContract(TypeName.create(contract));
        }

        /**
         * The managed service implementation type.
         *
         * @param builder  builder instance
         * @param contract the service type
         */
        @Prototype.BuilderMethod
        static void serviceType(Lookup.BuilderBase<?, ?> builder, Class<?> contract) {
            builder.serviceType(TypeName.create(contract));
        }

        private static Lookup createEmpty() {
            return Lookup.builder().build();
        }
    }
}
