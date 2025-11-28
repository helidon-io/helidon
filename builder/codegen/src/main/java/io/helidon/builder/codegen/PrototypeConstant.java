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
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.Errors;
import io.helidon.common.types.TypeName;

/**
 * Custom constant definition. This constant will be code generated on the prototype interface.
 *
 * @see #builder()
 */
public interface PrototypeConstant extends Prototype.Api {

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
    static Builder builder(PrototypeConstant instance) {
        return PrototypeConstant.builder().from(instance);
    }

    /**
     * Name of the constant.
     *
     * @return field name
     */
    String name();

    /**
     * Type of the constant.
     *
     * @return field type
     */
    TypeName type();

    /**
     * Javadoc for the constant.
     *
     * @return javadoc
     */
    Javadoc javadoc();

    /**
     * Consumer of the content to generate the constant.
     *
     * @return content builder consumer to generate the constant value
     */
    Consumer<ContentBuilder<?>> content();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.PrototypeConstant}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends PrototypeConstant>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private Consumer<ContentBuilder<?>> content;
        private Javadoc javadoc;
        private String name;
        private TypeName type;

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
        public BUILDER from(PrototypeConstant prototype) {
            name(prototype.name());
            type(prototype.type());
            javadoc(prototype.javadoc());
            content(prototype.content());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.name().ifPresent(this::name);
            builder.type().ifPresent(this::type);
            builder.javadoc().ifPresent(this::javadoc);
            builder.content().ifPresent(this::content);
            return self();
        }

        /**
         * Name of the constant.
         *
         * @param name field name
         * @return updated builder instance
         * @see #name()
         */
        public BUILDER name(String name) {
            Objects.requireNonNull(name);
            this.name = name;
            return self();
        }

        /**
         * Type of the constant.
         *
         * @param type field type
         * @return updated builder instance
         * @see #type()
         */
        public BUILDER type(TypeName type) {
            Objects.requireNonNull(type);
            this.type = type;
            return self();
        }

        /**
         * Type of the constant.
         *
         * @param consumer consumer of builder of field type
         * @return updated builder instance
         * @see #type()
         */
        public BUILDER type(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.type(builder.build());
            return self();
        }

        /**
         * Type of the constant.
         *
         * @param supplier supplier of field type
         * @return updated builder instance
         * @see #type()
         */
        public BUILDER type(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.type(supplier.get());
            return self();
        }

        /**
         * Javadoc for the constant.
         *
         * @param javadoc javadoc
         * @return updated builder instance
         * @see #javadoc()
         */
        public BUILDER javadoc(Javadoc javadoc) {
            Objects.requireNonNull(javadoc);
            this.javadoc = javadoc;
            return self();
        }

        /**
         * Javadoc for the constant.
         *
         * @param consumer consumer of builder of javadoc
         * @return updated builder instance
         * @see #javadoc()
         */
        public BUILDER javadoc(Consumer<Javadoc.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Javadoc.builder();
            consumer.accept(builder);
            this.javadoc(builder.build());
            return self();
        }

        /**
         * Javadoc for the constant.
         *
         * @param supplier supplier of javadoc
         * @return updated builder instance
         * @see #javadoc()
         */
        public BUILDER javadoc(Supplier<? extends Javadoc> supplier) {
            Objects.requireNonNull(supplier);
            this.javadoc(supplier.get());
            return self();
        }

        /**
         * Consumer of the content to generate the constant.
         *
         * @param content content builder consumer to generate the constant value
         * @return updated builder instance
         * @see #content()
         */
        public BUILDER content(Consumer<ContentBuilder<?>> content) {
            Objects.requireNonNull(content);
            this.content = content;
            return self();
        }

        /**
         * Name of the constant.
         *
         * @return field name
         */
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        /**
         * Type of the constant.
         *
         * @return field type
         */
        public Optional<TypeName> type() {
            return Optional.ofNullable(type);
        }

        /**
         * Javadoc for the constant.
         *
         * @return javadoc
         */
        public Optional<Javadoc> javadoc() {
            return Optional.ofNullable(javadoc);
        }

        /**
         * Consumer of the content to generate the constant.
         *
         * @return content builder consumer to generate the constant value
         */
        public Optional<Consumer<ContentBuilder<?>>> content() {
            return Optional.ofNullable(content);
        }

        @Override
        public String toString() {
            return "PrototypeConstantBuilder{"
                    + "name=" + name + ","
                    + "type=" + type + ","
                    + "javadoc=" + javadoc + ","
                    + "content=" + content
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
            if (name == null) {
                collector.fatal(getClass(), "Property \"name\" must not be null, but not set");
            }
            if (type == null) {
                collector.fatal(getClass(), "Property \"type\" must not be null, but not set");
            }
            if (javadoc == null) {
                collector.fatal(getClass(), "Property \"javadoc\" must not be null, but not set");
            }
            if (content == null) {
                collector.fatal(getClass(), "Property \"content\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class PrototypeConstantImpl implements PrototypeConstant {

            private final Consumer<ContentBuilder<?>> content;
            private final Javadoc javadoc;
            private final String name;
            private final TypeName type;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected PrototypeConstantImpl(BuilderBase<?, ?> builder) {
                this.name = builder.name().get();
                this.type = builder.type().get();
                this.javadoc = builder.javadoc().get();
                this.content = builder.content().get();
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public TypeName type() {
                return type;
            }

            @Override
            public Javadoc javadoc() {
                return javadoc;
            }

            @Override
            public Consumer<ContentBuilder<?>> content() {
                return content;
            }

            @Override
            public String toString() {
                return "PrototypeConstant{"
                        + "name=" + name + ","
                        + "type=" + type + ","
                        + "javadoc=" + javadoc + ","
                        + "content=" + content
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof PrototypeConstant other)) {
                    return false;
                }
                return Objects.equals(name, other.name())
                        && Objects.equals(type, other.type())
                        && Objects.equals(javadoc, other.javadoc())
                        && Objects.equals(content, other.content());
            }

            @Override
            public int hashCode() {
                return Objects.hash(name, type, javadoc, content);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.PrototypeConstant}.
     */
    class Builder extends BuilderBase<Builder, PrototypeConstant>
            implements io.helidon.common.Builder<Builder, PrototypeConstant> {

        private Builder() {
        }

        @Override
        public PrototypeConstant buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new PrototypeConstantImpl(this);
        }

        @Override
        public PrototypeConstant build() {
            return buildPrototype();
        }

    }

}
