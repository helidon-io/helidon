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
import io.helidon.common.types.TypedElementInfo;

/**
 * Factory methods for the deprecated annotation.
 * <p>
 * The following method types can be hidden behind this method:
 * <ul>
 *     <li>Factory method that creates an option type from Config</li>
 *     <li>Factory method that creates an option type from a prototype (to handle third party types)</li>
 *     <li>Factory method to be copied to the generated prototype interface</li>
 * </ul>
 *
 * @see #builder()
 * @deprecated this is only present for backward compatibility and will be removed in a future version
 */
public interface DeprecatedFactoryMethod extends Prototype.Api {

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
    static Builder builder(DeprecatedFactoryMethod instance) {
        return DeprecatedFactoryMethod.builder().from(instance);
    }

    /**
     * Referenced method.
     *
     * @return referenced method definition
     */
    TypedElementInfo method();

    /**
     * Type declaring the (static) factory method.
     *
     * @return declaring type
     */
    TypeName declaringType();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.DeprecatedFactoryMethod}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends DeprecatedFactoryMethod>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private TypedElementInfo method;
        private TypeName declaringType;

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
        public BUILDER from(DeprecatedFactoryMethod prototype) {
            method(prototype.method());
            declaringType(prototype.declaringType());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.method().ifPresent(this::method);
            builder.declaringType().ifPresent(this::declaringType);
            return self();
        }

        /**
         * Referenced method.
         *
         * @param method referenced method definition
         * @return updated builder instance
         * @see #method()
         */
        public BUILDER method(TypedElementInfo method) {
            Objects.requireNonNull(method);
            this.method = method;
            return self();
        }

        /**
         * Referenced method.
         *
         * @param consumer consumer of builder of referenced method definition
         * @return updated builder instance
         * @see #method()
         */
        public BUILDER method(Consumer<TypedElementInfo.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypedElementInfo.builder();
            consumer.accept(builder);
            this.method(builder.build());
            return self();
        }

        /**
         * Referenced method.
         *
         * @param supplier supplier of referenced method definition
         * @return updated builder instance
         * @see #method()
         */
        public BUILDER method(Supplier<? extends TypedElementInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.method(supplier.get());
            return self();
        }

        /**
         * Type declaring the (static) factory method.
         *
         * @param declaringType declaring type
         * @return updated builder instance
         * @see #declaringType()
         */
        public BUILDER declaringType(TypeName declaringType) {
            Objects.requireNonNull(declaringType);
            this.declaringType = declaringType;
            return self();
        }

        /**
         * Type declaring the (static) factory method.
         *
         * @param consumer consumer of builder of declaring type
         * @return updated builder instance
         * @see #declaringType()
         */
        public BUILDER declaringType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.declaringType(builder.build());
            return self();
        }

        /**
         * Type declaring the (static) factory method.
         *
         * @param supplier supplier of declaring type
         * @return updated builder instance
         * @see #declaringType()
         */
        public BUILDER declaringType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.declaringType(supplier.get());
            return self();
        }

        /**
         * Referenced method.
         *
         * @return referenced method definition
         */
        public Optional<TypedElementInfo> method() {
            return Optional.ofNullable(method);
        }

        /**
         * Type declaring the (static) factory method.
         *
         * @return declaring type
         */
        public Optional<TypeName> declaringType() {
            return Optional.ofNullable(declaringType);
        }

        @Override
        public String toString() {
            return "DeprecatedFactoryMethodBuilder{"
                    + "method=" + method + ","
                    + "declaringType=" + declaringType
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
            if (method == null) {
                collector.fatal(getClass(), "Property \"method\" must not be null, but not set");
            }
            if (declaringType == null) {
                collector.fatal(getClass(), "Property \"declaringType\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class DeprecatedFactoryMethodImpl implements DeprecatedFactoryMethod {

            private final TypedElementInfo method;
            private final TypeName declaringType;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected DeprecatedFactoryMethodImpl(BuilderBase<?, ?> builder) {
                this.method = builder.method().get();
                this.declaringType = builder.declaringType().get();
            }

            @Override
            public TypedElementInfo method() {
                return method;
            }

            @Override
            public TypeName declaringType() {
                return declaringType;
            }

            @Override
            public String toString() {
                return "DeprecatedFactoryMethod{"
                        + "method=" + method + ","
                        + "declaringType=" + declaringType
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof DeprecatedFactoryMethod other)) {
                    return false;
                }
                return Objects.equals(method, other.method())
                        && Objects.equals(declaringType, other.declaringType());
            }

            @Override
            public int hashCode() {
                return Objects.hash(method, declaringType);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.DeprecatedFactoryMethod}.
     */
    class Builder extends BuilderBase<Builder, DeprecatedFactoryMethod>
            implements io.helidon.common.Builder<Builder, DeprecatedFactoryMethod> {

        private Builder() {
        }

        @Override
        public DeprecatedFactoryMethod buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new DeprecatedFactoryMethodImpl(this);
        }

        @Override
        public DeprecatedFactoryMethod build() {
            return buildPrototype();
        }

    }

}
