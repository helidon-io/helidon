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
 * If an option itself has a builder, we add a method with {@code Consumer<Builder>}.
 * <p>
 * The type must have a {@code builder} method that returns a builder type.
 * The builder then must have a {@code build} method that returns the option type, or a {@code buildPrototype} method.
 *
 * @see #builder()
 */
public interface OptionBuilder extends Prototype.Api {

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
    static Builder builder(OptionBuilder instance) {
        return OptionBuilder.builder().from(instance);
    }

    /**
     * Name of the static builder method, or {@code <init>} to identify a constructor should be used.
     * If a method name is defined, it is expected to be on the type of the option. If constructor is defined,
     * it is expected to be an accessible constructor on the {@link #builderType()}.
     *
     * @return name of the method
     */
    String builderMethodName();

    /**
     * Type of the builder.
     *
     * @return type of the builder
     */
    TypeName builderType();

    /**
     * Name of the build method ({@code build} or {@code buildPrototype}).
     *
     * @return builder build method name
     */
    String buildMethodName();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.OptionBuilder}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends OptionBuilder>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private String buildMethodName;
        private String builderMethodName = "builder";
        private TypeName builderType;

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
        public BUILDER from(OptionBuilder prototype) {
            builderMethodName(prototype.builderMethodName());
            builderType(prototype.builderType());
            buildMethodName(prototype.buildMethodName());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builderMethodName(builder.builderMethodName());
            builder.builderType().ifPresent(this::builderType);
            builder.buildMethodName().ifPresent(this::buildMethodName);
            return self();
        }

        /**
         * Name of the static builder method, or {@code <init>} to identify a constructor should be used.
         * If a method name is defined, it is expected to be on the type of the option. If constructor is defined,
         * it is expected to be an accessible constructor on the {@link #builderType()}.
         *
         * @param builderMethodName name of the method
         * @return updated builder instance
         * @see #builderMethodName()
         */
        public BUILDER builderMethodName(String builderMethodName) {
            Objects.requireNonNull(builderMethodName);
            this.builderMethodName = builderMethodName;
            return self();
        }

        /**
         * Type of the builder.
         *
         * @param builderType type of the builder
         * @return updated builder instance
         * @see #builderType()
         */
        public BUILDER builderType(TypeName builderType) {
            Objects.requireNonNull(builderType);
            this.builderType = builderType;
            return self();
        }

        /**
         * Type of the builder.
         *
         * @param consumer consumer of builder of type of the builder
         * @return updated builder instance
         * @see #builderType()
         */
        public BUILDER builderType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.builderType(builder.build());
            return self();
        }

        /**
         * Type of the builder.
         *
         * @param supplier supplier of type of the builder
         * @return updated builder instance
         * @see #builderType()
         */
        public BUILDER builderType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.builderType(supplier.get());
            return self();
        }

        /**
         * Name of the build method ({@code build} or {@code buildPrototype}).
         *
         * @param buildMethodName builder build method name
         * @return updated builder instance
         * @see #buildMethodName()
         */
        public BUILDER buildMethodName(String buildMethodName) {
            Objects.requireNonNull(buildMethodName);
            this.buildMethodName = buildMethodName;
            return self();
        }

        /**
         * Name of the static builder method, or {@code <init>} to identify a constructor should be used.
         * If a method name is defined, it is expected to be on the type of the option. If constructor is defined,
         * it is expected to be an accessible constructor on the {@link #builderType()}.
         *
         * @return name of the method
         */
        public String builderMethodName() {
            return builderMethodName;
        }

        /**
         * Type of the builder.
         *
         * @return type of the builder
         */
        public Optional<TypeName> builderType() {
            return Optional.ofNullable(builderType);
        }

        /**
         * Name of the build method ({@code build} or {@code buildPrototype}).
         *
         * @return builder build method name
         */
        public Optional<String> buildMethodName() {
            return Optional.ofNullable(buildMethodName);
        }

        @Override
        public String toString() {
            return "OptionBuilderBuilder{"
                    + "builderMethodName=" + builderMethodName + ","
                    + "builderType=" + builderType + ","
                    + "buildMethodName=" + buildMethodName
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
            if (builderType == null) {
                collector.fatal(getClass(), "Property \"builderType\" must not be null, but not set");
            }
            if (buildMethodName == null) {
                collector.fatal(getClass(), "Property \"buildMethodName\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OptionBuilderImpl implements OptionBuilder {

            private final String buildMethodName;
            private final String builderMethodName;
            private final TypeName builderType;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected OptionBuilderImpl(BuilderBase<?, ?> builder) {
                this.builderMethodName = builder.builderMethodName();
                this.builderType = builder.builderType().get();
                this.buildMethodName = builder.buildMethodName().get();
            }

            @Override
            public String builderMethodName() {
                return builderMethodName;
            }

            @Override
            public TypeName builderType() {
                return builderType;
            }

            @Override
            public String buildMethodName() {
                return buildMethodName;
            }

            @Override
            public String toString() {
                return "OptionBuilder{"
                        + "builderMethodName=" + builderMethodName + ","
                        + "builderType=" + builderType + ","
                        + "buildMethodName=" + buildMethodName
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof OptionBuilder other)) {
                    return false;
                }
                return Objects.equals(builderMethodName, other.builderMethodName())
                        && Objects.equals(builderType, other.builderType())
                        && Objects.equals(buildMethodName, other.buildMethodName());
            }

            @Override
            public int hashCode() {
                return Objects.hash(builderMethodName, builderType, buildMethodName);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.OptionBuilder}.
     */
    class Builder extends BuilderBase<Builder, OptionBuilder> implements io.helidon.common.Builder<Builder, OptionBuilder> {

        private Builder() {
        }

        @Override
        public OptionBuilder buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new OptionBuilderImpl(this);
        }

        @Override
        public OptionBuilder build() {
            return buildPrototype();
        }

    }

}
