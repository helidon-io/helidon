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
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * Configuration specific to a factory method to create a runtime type from a prototype with a builder.
 *
 * @see #builder()
 */
public interface RuntimeTypeInfo extends Prototype.Api {

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
    static Builder builder(RuntimeTypeInfo instance) {
        return RuntimeTypeInfo.builder().from(instance);
    }

    /**
     * Factory method.
     * If not defined, we expect the builder to build the correct type.
     *
     * @return the factory method if present
     */
    Optional<FactoryMethod> factoryMethod();

    /**
     * Builder information associated with this factory method.
     *
     * @return builder information
     */
    OptionBuilder optionBuilder();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.RuntimeTypeInfo}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends RuntimeTypeInfo>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private FactoryMethod factoryMethod;
        private OptionBuilder optionBuilder;

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
        public BUILDER from(RuntimeTypeInfo prototype) {
            factoryMethod(prototype.factoryMethod());
            optionBuilder(prototype.optionBuilder());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.factoryMethod().ifPresent(this::factoryMethod);
            builder.optionBuilder().ifPresent(this::optionBuilder);
            return self();
        }

        /**
         * Clear existing value of factoryMethod.
         *
         * @return updated builder instance
         * @see #factoryMethod()
         */
        public BUILDER clearFactoryMethod() {
            this.factoryMethod = null;
            return self();
        }

        /**
         * Factory method.
         * If not defined, we expect the builder to build the correct type.
         *
         * @param factoryMethod the factory method if present
         * @return updated builder instance
         * @see #factoryMethod()
         */
        public BUILDER factoryMethod(FactoryMethod factoryMethod) {
            Objects.requireNonNull(factoryMethod);
            this.factoryMethod = factoryMethod;
            return self();
        }

        /**
         * Factory method.
         * If not defined, we expect the builder to build the correct type.
         *
         * @param consumer consumer of builder of the factory method if present
         * @return updated builder instance
         * @see #factoryMethod()
         */
        public BUILDER factoryMethod(Consumer<FactoryMethod.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = FactoryMethod.builder();
            consumer.accept(builder);
            this.factoryMethod(builder.build());
            return self();
        }

        /**
         * Factory method.
         * If not defined, we expect the builder to build the correct type.
         *
         * @param supplier supplier of the factory method if present
         * @return updated builder instance
         * @see #factoryMethod()
         */
        public BUILDER factoryMethod(Supplier<? extends FactoryMethod> supplier) {
            Objects.requireNonNull(supplier);
            this.factoryMethod(supplier.get());
            return self();
        }

        /**
         * Builder information associated with this factory method.
         *
         * @param optionBuilder builder information
         * @return updated builder instance
         * @see #optionBuilder()
         */
        public BUILDER optionBuilder(OptionBuilder optionBuilder) {
            Objects.requireNonNull(optionBuilder);
            this.optionBuilder = optionBuilder;
            return self();
        }

        /**
         * Builder information associated with this factory method.
         *
         * @param consumer consumer of builder of builder information
         * @return updated builder instance
         * @see #optionBuilder()
         */
        public BUILDER optionBuilder(Consumer<OptionBuilder.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = OptionBuilder.builder();
            consumer.accept(builder);
            this.optionBuilder(builder.build());
            return self();
        }

        /**
         * Builder information associated with this factory method.
         *
         * @param supplier supplier of builder information
         * @return updated builder instance
         * @see #optionBuilder()
         */
        public BUILDER optionBuilder(Supplier<? extends OptionBuilder> supplier) {
            Objects.requireNonNull(supplier);
            this.optionBuilder(supplier.get());
            return self();
        }

        /**
         * Factory method.
         * If not defined, we expect the builder to build the correct type.
         *
         * @return the factory method if present
         */
        public Optional<FactoryMethod> factoryMethod() {
            return Optional.ofNullable(factoryMethod);
        }

        /**
         * Builder information associated with this factory method.
         *
         * @return builder information
         */
        public Optional<OptionBuilder> optionBuilder() {
            return Optional.ofNullable(optionBuilder);
        }

        @Override
        public String toString() {
            return "RuntimeTypeInfoBuilder{"
                    + "factoryMethod=" + factoryMethod + ","
                    + "optionBuilder=" + optionBuilder
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
            if (optionBuilder == null) {
                collector.fatal(getClass(), "Property \"optionBuilder\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Factory method.
         * If not defined, we expect the builder to build the correct type.
         *
         * @param factoryMethod the factory method if present
         * @return updated builder instance
         * @see #factoryMethod()
         */
        @SuppressWarnings("unchecked")
        BUILDER factoryMethod(Optional<? extends FactoryMethod> factoryMethod) {
            Objects.requireNonNull(factoryMethod);
            this.factoryMethod = factoryMethod.map(FactoryMethod.class::cast).orElse(this.factoryMethod);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class RuntimeTypeInfoImpl implements RuntimeTypeInfo {

            private final Optional<FactoryMethod> factoryMethod;
            private final OptionBuilder optionBuilder;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected RuntimeTypeInfoImpl(BuilderBase<?, ?> builder) {
                this.factoryMethod = builder.factoryMethod().map(Function.identity());
                this.optionBuilder = builder.optionBuilder().get();
            }

            @Override
            public Optional<FactoryMethod> factoryMethod() {
                return factoryMethod;
            }

            @Override
            public OptionBuilder optionBuilder() {
                return optionBuilder;
            }

            @Override
            public String toString() {
                return "RuntimeTypeInfo{"
                        + "factoryMethod=" + factoryMethod + ","
                        + "optionBuilder=" + optionBuilder
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof RuntimeTypeInfo other)) {
                    return false;
                }
                return Objects.equals(factoryMethod, other.factoryMethod())
                        && Objects.equals(optionBuilder, other.optionBuilder());
            }

            @Override
            public int hashCode() {
                return Objects.hash(factoryMethod, optionBuilder);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.RuntimeTypeInfo}.
     */
    class Builder extends BuilderBase<Builder, RuntimeTypeInfo> implements io.helidon.common.Builder<Builder, RuntimeTypeInfo> {

        private Builder() {
        }

        @Override
        public RuntimeTypeInfo buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new RuntimeTypeInfoImpl(this);
        }

        @Override
        public RuntimeTypeInfo build() {
            return buildPrototype();
        }

    }

}
