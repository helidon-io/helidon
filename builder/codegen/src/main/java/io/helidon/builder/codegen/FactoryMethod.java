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
import io.helidon.common.types.TypeName;

/**
 * Some static methods on custom methods (and deprecated option on the blueprint itself)
 * may be annotated with {@code Prototype.FactoryMethod}.
 * <p>
 * Such methods can be used to map from configuration to a type, or from a prototype to a
 * third party runtime-type.
 *
 * @see #builder()
 */
public interface FactoryMethod extends Prototype.Api {

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
    static Builder builder(FactoryMethod instance) {
        return FactoryMethod.builder().from(instance);
    }

    /**
     * Type declaring the factory method.
     *
     * @return type declaring the factory method
     */
    TypeName declaringType();

    /**
     * Return type of the factory method.
     *
     * @return return type of the factory method
     */
    TypeName returnType();

    /**
     * Name of the factory method.
     *
     * @return factory method name
     */
    String methodName();

    /**
     * A parameter of the factory method, if any.
     *
     * @return parameter type, if any
     */
    Optional<TypeName> parameterType();

    /**
     * A factory method may be bound to a specific option.
     *
     * @return name of the option this factory method is bound to, if any
     */
    Optional<String> optionName();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.FactoryMethod}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends FactoryMethod>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private String methodName;
        private String optionName;
        private TypeName declaringType;
        private TypeName parameterType;
        private TypeName returnType;

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
        public BUILDER from(FactoryMethod prototype) {
            declaringType(prototype.declaringType());
            returnType(prototype.returnType());
            methodName(prototype.methodName());
            parameterType(prototype.parameterType());
            optionName(prototype.optionName());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.declaringType().ifPresent(this::declaringType);
            builder.returnType().ifPresent(this::returnType);
            builder.methodName().ifPresent(this::methodName);
            builder.parameterType().ifPresent(this::parameterType);
            builder.optionName().ifPresent(this::optionName);
            return self();
        }

        /**
         * Type declaring the factory method.
         *
         * @param declaringType type declaring the factory method
         * @return updated builder instance
         * @see #declaringType()
         */
        public BUILDER declaringType(TypeName declaringType) {
            Objects.requireNonNull(declaringType);
            this.declaringType = declaringType;
            return self();
        }

        /**
         * Type declaring the factory method.
         *
         * @param consumer consumer of builder of type declaring the factory method
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
         * Type declaring the factory method.
         *
         * @param supplier supplier of type declaring the factory method
         * @return updated builder instance
         * @see #declaringType()
         */
        public BUILDER declaringType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.declaringType(supplier.get());
            return self();
        }

        /**
         * Return type of the factory method.
         *
         * @param returnType return type of the factory method
         * @return updated builder instance
         * @see #returnType()
         */
        public BUILDER returnType(TypeName returnType) {
            Objects.requireNonNull(returnType);
            this.returnType = returnType;
            return self();
        }

        /**
         * Return type of the factory method.
         *
         * @param consumer consumer of builder of return type of the factory method
         * @return updated builder instance
         * @see #returnType()
         */
        public BUILDER returnType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.returnType(builder.build());
            return self();
        }

        /**
         * Return type of the factory method.
         *
         * @param supplier supplier of return type of the factory method
         * @return updated builder instance
         * @see #returnType()
         */
        public BUILDER returnType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.returnType(supplier.get());
            return self();
        }

        /**
         * Name of the factory method.
         *
         * @param methodName factory method name
         * @return updated builder instance
         * @see #methodName()
         */
        public BUILDER methodName(String methodName) {
            Objects.requireNonNull(methodName);
            this.methodName = methodName;
            return self();
        }

        /**
         * Clear existing value of parameterType.
         *
         * @return updated builder instance
         * @see #parameterType()
         */
        public BUILDER clearParameterType() {
            this.parameterType = null;
            return self();
        }

        /**
         * A parameter of the factory method, if any.
         *
         * @param parameterType parameter type, if any
         * @return updated builder instance
         * @see #parameterType()
         */
        public BUILDER parameterType(TypeName parameterType) {
            Objects.requireNonNull(parameterType);
            this.parameterType = parameterType;
            return self();
        }

        /**
         * A parameter of the factory method, if any.
         *
         * @param consumer consumer of builder of parameter type, if any
         * @return updated builder instance
         * @see #parameterType()
         */
        public BUILDER parameterType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.parameterType(builder.build());
            return self();
        }

        /**
         * A parameter of the factory method, if any.
         *
         * @param supplier supplier of parameter type, if any
         * @return updated builder instance
         * @see #parameterType()
         */
        public BUILDER parameterType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.parameterType(supplier.get());
            return self();
        }

        /**
         * Clear existing value of optionName.
         *
         * @return updated builder instance
         * @see #optionName()
         */
        public BUILDER clearOptionName() {
            this.optionName = null;
            return self();
        }

        /**
         * A factory method may be bound to a specific option.
         *
         * @param optionName name of the option this factory method is bound to, if any
         * @return updated builder instance
         * @see #optionName()
         */
        public BUILDER optionName(String optionName) {
            Objects.requireNonNull(optionName);
            this.optionName = optionName;
            return self();
        }

        /**
         * Type declaring the factory method.
         *
         * @return type declaring the factory method
         */
        public Optional<TypeName> declaringType() {
            return Optional.ofNullable(declaringType);
        }

        /**
         * Return type of the factory method.
         *
         * @return return type of the factory method
         */
        public Optional<TypeName> returnType() {
            return Optional.ofNullable(returnType);
        }

        /**
         * Name of the factory method.
         *
         * @return factory method name
         */
        public Optional<String> methodName() {
            return Optional.ofNullable(methodName);
        }

        /**
         * A parameter of the factory method, if any.
         *
         * @return parameter type, if any
         */
        public Optional<TypeName> parameterType() {
            return Optional.ofNullable(parameterType);
        }

        /**
         * A factory method may be bound to a specific option.
         *
         * @return name of the option this factory method is bound to, if any
         */
        public Optional<String> optionName() {
            return Optional.ofNullable(optionName);
        }

        @Override
        public String toString() {
            return "FactoryMethodBuilder{"
                    + "declaringType=" + declaringType + ","
                    + "returnType=" + returnType + ","
                    + "methodName=" + methodName + ","
                    + "parameterType=" + parameterType + ","
                    + "optionName=" + optionName
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
            if (declaringType == null) {
                collector.fatal(getClass(), "Property \"declaringType\" must not be null, but not set");
            }
            if (returnType == null) {
                collector.fatal(getClass(), "Property \"returnType\" must not be null, but not set");
            }
            if (methodName == null) {
                collector.fatal(getClass(), "Property \"methodName\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * A parameter of the factory method, if any.
         *
         * @param parameterType parameter type, if any
         * @return updated builder instance
         * @see #parameterType()
         */
        @SuppressWarnings("unchecked")
        BUILDER parameterType(Optional<? extends TypeName> parameterType) {
            Objects.requireNonNull(parameterType);
            this.parameterType = parameterType.map(TypeName.class::cast).orElse(this.parameterType);
            return self();
        }

        /**
         * A factory method may be bound to a specific option.
         *
         * @param optionName name of the option this factory method is bound to, if any
         * @return updated builder instance
         * @see #optionName()
         */
        BUILDER optionName(Optional<String> optionName) {
            Objects.requireNonNull(optionName);
            this.optionName = optionName.orElse(this.optionName);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class FactoryMethodImpl implements FactoryMethod {

            private final Optional<TypeName> parameterType;
            private final Optional<String> optionName;
            private final String methodName;
            private final TypeName declaringType;
            private final TypeName returnType;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected FactoryMethodImpl(BuilderBase<?, ?> builder) {
                this.declaringType = builder.declaringType().get();
                this.returnType = builder.returnType().get();
                this.methodName = builder.methodName().get();
                this.parameterType = builder.parameterType().map(Function.identity());
                this.optionName = builder.optionName().map(Function.identity());
            }

            @Override
            public TypeName declaringType() {
                return declaringType;
            }

            @Override
            public TypeName returnType() {
                return returnType;
            }

            @Override
            public String methodName() {
                return methodName;
            }

            @Override
            public Optional<TypeName> parameterType() {
                return parameterType;
            }

            @Override
            public Optional<String> optionName() {
                return optionName;
            }

            @Override
            public String toString() {
                return "FactoryMethod{"
                        + "declaringType=" + declaringType + ","
                        + "returnType=" + returnType + ","
                        + "methodName=" + methodName + ","
                        + "parameterType=" + parameterType + ","
                        + "optionName=" + optionName
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof FactoryMethod other)) {
                    return false;
                }
                return Objects.equals(declaringType, other.declaringType())
                        && Objects.equals(returnType, other.returnType())
                        && Objects.equals(methodName, other.methodName())
                        && Objects.equals(parameterType, other.parameterType())
                        && Objects.equals(optionName, other.optionName());
            }

            @Override
            public int hashCode() {
                return Objects.hash(declaringType, returnType, methodName, parameterType, optionName);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.FactoryMethod}.
     */
    class Builder extends BuilderBase<Builder, FactoryMethod> implements io.helidon.common.Builder<Builder, FactoryMethod> {

        private Builder() {
        }

        @Override
        public FactoryMethod buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new FactoryMethodImpl(this);
        }

        @Override
        public FactoryMethod build() {
            return buildPrototype();
        }

    }

}
