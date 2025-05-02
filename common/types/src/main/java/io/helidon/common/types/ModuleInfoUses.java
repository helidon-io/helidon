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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * A uses directive of a module info.
 *
 * @see #builder()
 * @see #create()
 */
public interface ModuleInfoUses extends ModuleInfoUsesBlueprint, Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static ModuleInfoUses.Builder builder() {
        return new ModuleInfoUses.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static ModuleInfoUses.Builder builder(ModuleInfoUses instance) {
        return ModuleInfoUses.builder().from(instance);
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static ModuleInfoUses create() {
        return ModuleInfoUses.builder().buildPrototype();
    }

    /**
     * Fluent API builder base for {@link ModuleInfoUses}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends ModuleInfoUses.BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends ModuleInfoUses>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

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
        public BUILDER from(ModuleInfoUses prototype) {
            service(prototype.service());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(ModuleInfoUses.BuilderBase<?, ?> builder) {
            builder.service().ifPresent(this::service);
            return self();
        }

        /**
         * Type of the service used by this module.
         *
         * @param service service
         * @return updated builder instance
         * @see #service()
         */
        public BUILDER service(TypeName service) {
            Objects.requireNonNull(service);
            this.service = service;
            return self();
        }

        /**
         * Type of the service used by this module.
         *
         * @param consumer consumer of builder for
         *                 service
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
         * Type of the service used by this module.
         *
         * @param supplier supplier of
         *                 service
         * @return updated builder instance
         * @see #service()
         */
        public BUILDER service(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.service(supplier.get());
            return self();
        }

        /**
         * Type of the service used by this module.
         *
         * @return the service
         */
        public Optional<TypeName> service() {
            return Optional.ofNullable(service);
        }

        @Override
        public String toString() {
            return "ModuleInfoUsesBuilder{"
                    + "service=" + service
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
        protected static class ModuleInfoUsesImpl implements ModuleInfoUses {

            private final TypeName service;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected ModuleInfoUsesImpl(ModuleInfoUses.BuilderBase<?, ?> builder) {
                this.service = builder.service().get();
            }

            @Override
            public TypeName service() {
                return service;
            }

            @Override
            public String toString() {
                return "ModuleInfoUses{"
                        + "service=" + service
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof ModuleInfoUses other)) {
                    return false;
                }
                return Objects.equals(service, other.service());
            }

            @Override
            public int hashCode() {
                return Objects.hash(service);
            }

        }

    }

    /**
     * Fluent API builder for {@link ModuleInfoUses}.
     */
    class Builder extends ModuleInfoUses.BuilderBase<ModuleInfoUses.Builder, ModuleInfoUses>
            implements io.helidon.common.Builder<ModuleInfoUses.Builder, ModuleInfoUses> {

        private Builder() {
        }

        @Override
        public ModuleInfoUses buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new ModuleInfoUsesImpl(this);
        }

        @Override
        public ModuleInfoUses build() {
            return buildPrototype();
        }

    }

}
