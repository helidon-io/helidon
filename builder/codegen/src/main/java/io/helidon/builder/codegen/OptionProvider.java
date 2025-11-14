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

package io.helidon.builder.codegen;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;
import io.helidon.common.types.TypeName;

/**
 * Definition of an option that is a provider (i.e. loaded through registry or service loader).
 *
 * @see #builder()
 */
public interface OptionProvider extends Prototype.Api {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static Builder builder(OptionProvider instance) {
        return OptionProvider.builder().from(instance);
    }

    /**
     * Type of the provider to lookup.
     *
     * @return provider type
     */
    TypeName providerType();

    /**
     * Whether to discover services by default.
     *
     * @return whether to discover services
     */
    boolean discoverServices();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.OptionProvider}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends OptionProvider>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private boolean discoverServices;
        private TypeName providerType;

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
        public BUILDER from(OptionProvider prototype) {
            providerType(prototype.providerType());
            discoverServices(prototype.discoverServices());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.providerType().ifPresent(this::providerType);
            discoverServices(builder.discoverServices());
            return self();
        }

        /**
         * Type of the provider to lookup.
         *
         * @param providerType provider type
         * @return updated builder instance
         * @see #providerType()
         */
        public BUILDER providerType(TypeName providerType) {
            Objects.requireNonNull(providerType);
            this.providerType = providerType;
            return self();
        }

        /**
         * Type of the provider to lookup.
         *
         * @param consumer consumer of builder of provider type
         * @return updated builder instance
         * @see #providerType()
         */
        public BUILDER providerType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.providerType(builder.build());
            return self();
        }

        /**
         * Type of the provider to lookup.
         *
         * @param supplier supplier of provider type
         * @return updated builder instance
         * @see #providerType()
         */
        public BUILDER providerType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.providerType(supplier.get());
            return self();
        }

        /**
         * Whether to discover services by default.
         *
         * @param discoverServices whether to discover services
         * @return updated builder instance
         * @see #discoverServices()
         */
        public BUILDER discoverServices(boolean discoverServices) {
            this.discoverServices = discoverServices;
            return self();
        }

        /**
         * Type of the provider to lookup.
         *
         * @return provider type
         */
        public Optional<TypeName> providerType() {
            return Optional.ofNullable(providerType);
        }

        /**
         * Whether to discover services by default.
         *
         * @return whether to discover services
         */
        public boolean discoverServices() {
            return discoverServices;
        }

        @Override
        public String toString() {
            return "OptionProviderBuilder{"
                    + "providerType=" + providerType + ","
                    + "discoverServices=" + discoverServices
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
            if (providerType == null) {
                collector.fatal(getClass(), "Property \"providerType\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OptionProviderImpl implements OptionProvider {

            private final boolean discoverServices;
            private final TypeName providerType;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected OptionProviderImpl(BuilderBase<?, ?> builder) {
                this.providerType = builder.providerType().get();
                this.discoverServices = builder.discoverServices();
            }

            @Override
            public TypeName providerType() {
                return providerType;
            }

            @Override
            public boolean discoverServices() {
                return discoverServices;
            }

            @Override
            public String toString() {
                return "OptionProvider{"
                        + "providerType=" + providerType + ","
                        + "discoverServices=" + discoverServices
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof OptionProvider other)) {
                    return false;
                }
                return Objects.equals(providerType, other.providerType())
                        && discoverServices == other.discoverServices();
            }

            @Override
            public int hashCode() {
                return Objects.hash(providerType, discoverServices);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.OptionProvider}.
     */
    class Builder extends BuilderBase<Builder, OptionProvider> implements io.helidon.common.Builder<Builder, OptionProvider> {

        private Builder() {
        }

        @Override
        public OptionProvider buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new OptionProviderImpl(this);
        }

        @Override
        public OptionProvider build() {
            return buildPrototype();
        }

    }

}
