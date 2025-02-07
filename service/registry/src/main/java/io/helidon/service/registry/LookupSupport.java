/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.common.GenericType;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;

final class LookupSupport {
    private LookupSupport() {
    }

    static final class CustomMethods {
        /**
         * Empty lookup matches anything and everything except for abstract types.
         */
        @Prototype.Constant
        static final Lookup EMPTY = createEmpty();

        private CustomMethods() {
        }

        /**
         * Create service lookup from this injection point information.
         *
         * @param dependency injection point to create lookup for
         * @return lookup to match injection point
         */
        @Prototype.FactoryMethod
        static Lookup create(Dependency dependency) {
            return Lookup.builder()
                    .dependency(dependency)
                    .build();
        }

        /**
         * Create service lookup from a contract type.
         *
         * @param contract a single contract to base the lookup on
         * @return lookup for matching services
         */
        @Prototype.FactoryMethod
        static Lookup create(Class<?> contract) {
            return Lookup.builder()
                    .addContract(contract)
                    .build();
        }

        /**
         * Create service lookup from a contract type.
         *
         * @param contract a single contract to base the lookup on
         * @return lookup for matching services
         */
        @Prototype.FactoryMethod
        static Lookup create(TypeName contract) {
            return Lookup.builder()
                    .addContract(ResolvedType.create(contract))
                    .build();
        }

        /**
         * The managed services advertised types (i.e., typically its interfaces).
         *
         * @param builder  builder instance
         * @param contract the service contracts implemented
         * @see Lookup#contracts()
         */
        @Prototype.BuilderMethod
        static void addContract(Lookup.BuilderBase<?, ?> builder, Class<?> contract) {
            builder.addContract(ResolvedType.create(contract));
        }

        /**
         * The managed services advertised types (i.e., typically its interfaces).
         *
         * @param builder  builder instance
         * @param contract contract the service implements
         * @see Lookup#contracts()
         */
        @Prototype.BuilderMethod
        static void addContract(Lookup.BuilderBase<?, ?> builder, TypeName contract) {
            builder.addContract(ResolvedType.create(contract));
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

    static final class DependencyDecorator implements Prototype.OptionDecorator<Lookup.BuilderBase<?, ?>, Optional<Dependency>> {
        @Override
        public void decorate(Lookup.BuilderBase<?, ?> builder, Optional<Dependency> dependency) {
            if (dependency.isPresent()) {
                Dependency value = dependency.get();
                builder.qualifiers(value.qualifiers());

                if (!GenericType.OBJECT.equals(value.contractType())) {
                    builder.contractType(value.contractType())
                            .addContract(ResolvedType.create(value.contract()));
                }
            } else {
                builder.dependency().ifPresent(existing -> {
                    // clear if contained only IP stuff
                    boolean shouldClear = builder.qualifiers().equals(existing.qualifiers());

                    if (!(builder.contracts().contains(ResolvedType.create(existing.contract()))
                                    && builder.contracts().size() == 1)) {
                        shouldClear = false;
                    }

                    if (shouldClear) {
                        builder.qualifiers(Set.of());
                        builder.contracts(Set.of());
                        builder.clearContractType();
                    }
                });
            }

        }
    }

    static class GenericTypeDecorator implements Prototype.OptionDecorator<Lookup.BuilderBase<?, ?>, Optional<GenericType<?>>> {
        @Override
        public void decorate(Lookup.BuilderBase<?, ?> builder, Optional<GenericType<?>> optionValue) {
            if (optionValue.isEmpty()) {
                return;
            }
            builder.addContract(ResolvedType.create(optionValue.get()));
        }
    }
}
