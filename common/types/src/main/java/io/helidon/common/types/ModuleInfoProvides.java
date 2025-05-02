/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * A provides directive of a module info.
 *
 * @see #builder()
 * @see #create()
 */
public interface ModuleInfoProvides extends ModuleInfoProvidesBlueprint, Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static ModuleInfoProvides.Builder builder() {
        return new ModuleInfoProvides.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static ModuleInfoProvides.Builder builder(ModuleInfoProvides instance) {
        return ModuleInfoProvides.builder().from(instance);
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static ModuleInfoProvides create() {
        return ModuleInfoProvides.builder().buildPrototype();
    }

    /**
     * Fluent API builder base for {@link ModuleInfoProvides}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends ModuleInfoProvides.BuilderBase<BUILDER, PROTOTYPE>,
            PROTOTYPE extends ModuleInfoProvides>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<TypeName> implementations = new ArrayList<>();
        private boolean isImplementationsMutated;
        private TypeName service;

        /**
         * Protected to support extensibility.
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance. This method disables automatic service discovery.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(ModuleInfoProvides prototype) {
            service(prototype.service());
            if (!isImplementationsMutated) {
                implementations.clear();
            }
            addImplementations(prototype.implementations());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(ModuleInfoProvides.BuilderBase<?, ?> builder) {
            builder.service().ifPresent(this::service);
            if (isImplementationsMutated) {
                if (builder.isImplementationsMutated) {
                    addImplementations(builder.implementations);
                }
            } else {
                implementations.clear();
                addImplementations(builder.implementations);
            }
            return self();
        }

        /**
         * Type of the service provided.
         *
         * @param service service type
         * @return updated builder instance
         * @see #service()
         */
        public BUILDER service(TypeName service) {
            Objects.requireNonNull(service);
            this.service = service;
            return self();
        }

        /**
         * Type of the service provided.
         *
         * @param consumer consumer of builder for
         *                 service type
         * @return updated builder instance
         * @see #service()
         */
        public BUILDER service(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.service(builder.build());
            return self();
        }

        /**
         * Type of the service provided.
         *
         * @param supplier supplier of
         *                 service type
         * @return updated builder instance
         * @see #service()
         */
        public BUILDER service(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.service(supplier.get());
            return self();
        }

        /**
         * List of implementations of the service.
         *
         * @param implementations implementation types
         * @return updated builder instance
         * @see #implementations()
         */
        public BUILDER implementations(List<? extends TypeName> implementations) {
            Objects.requireNonNull(implementations);
            isImplementationsMutated = true;
            this.implementations.clear();
            this.implementations.addAll(implementations);
            return self();
        }

        /**
         * List of implementations of the service.
         *
         * @param implementations implementation types
         * @return updated builder instance
         * @see #implementations()
         */
        public BUILDER addImplementations(List<? extends TypeName> implementations) {
            Objects.requireNonNull(implementations);
            isImplementationsMutated = true;
            this.implementations.addAll(implementations);
            return self();
        }

        /**
         * List of implementations of the service.
         *
         * @param implementation implementation types
         * @return updated builder instance
         * @see #implementations()
         */
        public BUILDER addImplementation(TypeName implementation) {
            Objects.requireNonNull(implementation);
            this.implementations.add(implementation);
            isImplementationsMutated = true;
            return self();
        }

        /**
         * List of implementations of the service.
         *
         * @param consumer implementation types
         * @return updated builder instance
         * @see #implementations()
         */
        public BUILDER addImplementation(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.implementations.add(builder.build());
            return self();
        }

        /**
         * Type of the service provided.
         *
         * @return the service
         */
        public Optional<TypeName> service() {
            return Optional.ofNullable(service);
        }

        /**
         * List of implementations of the service.
         *
         * @return the implementations
         */
        public List<TypeName> implementations() {
            return implementations;
        }

        @Override
        public String toString() {
            return "ModuleInfoProvidesBuilder{"
                    + "service=" + service + ","
                    + "implementations=" + implementations
                    + "}";
        }

        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
            Errors.Collector collector = Errors.collector();
            if (service == null) {
                collector.fatal(getClass(), "Property \"service\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class ModuleInfoProvidesImpl implements ModuleInfoProvides {

            private final List<TypeName> implementations;
            private final TypeName service;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected ModuleInfoProvidesImpl(ModuleInfoProvides.BuilderBase<?, ?> builder) {
                this.service = builder.service().get();
                this.implementations = List.copyOf(builder.implementations());
            }

            @Override
            public TypeName service() {
                return service;
            }

            @Override
            public List<TypeName> implementations() {
                return implementations;
            }

            @Override
            public String toString() {
                return "ModuleInfoProvides{"
                        + "service=" + service + ","
                        + "implementations=" + implementations
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof ModuleInfoProvides other)) {
                    return false;
                }
                return Objects.equals(service, other.service())
                        && Objects.equals(implementations, other.implementations());
            }

            @Override
            public int hashCode() {
                return Objects.hash(service, implementations);
            }

        }

    }

    /**
     * Fluent API builder for {@link ModuleInfoProvides}.
     */
    class Builder extends ModuleInfoProvides.BuilderBase<ModuleInfoProvides.Builder, ModuleInfoProvides>
            implements io.helidon.common.Builder<ModuleInfoProvides.Builder, ModuleInfoProvides> {

        private Builder() {
        }

        @Override
        public ModuleInfoProvides buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new ModuleInfoProvidesImpl(this);
        }

        @Override
        public ModuleInfoProvides build() {
            return buildPrototype();
        }

    }

}
