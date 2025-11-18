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
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.Errors;
import io.helidon.common.types.TypedElementInfo;

/**
 * A method to be generated.
 * <p>
 * Rules for referenced static custom methods:
 * <ul>
 *     <li>The first parameter must be the Prototype type for custom prototype methods</li>
 *     <li>The first parameter must be the BuilderBase type for custom builder methods</li>
 *     <li>Custom factory methods are simply referenced</li>
 * </ul>
 *
 * @see #builder()
 */
public interface GeneratedMethod extends Prototype.Api {

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
    static Builder builder(GeneratedMethod instance) {
        return GeneratedMethod.builder().from(instance);
    }

    /**
     * Definition of this method, including annotations (such as {@link java.lang.Override}).
     *
     * @return method definition
     */
    TypedElementInfo method();

    /**
     * Generator for the method content.
     *
     * @return content builder consumer
     */
    Consumer<ContentBuilder<?>> contentBuilder();

    /**
     * Javadoc for this method. We intentionally ignore documentation on {@link #method()}, as it may be
     * complicated to update it.
     * <p>
     * If not configured, no javadoc will be generated (useful for methods that override documented interface methods).
     *
     * @return javadoc for this method if defined
     */
    Optional<Javadoc> javadoc();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.GeneratedMethod}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends GeneratedMethod>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private Consumer<ContentBuilder<?>> contentBuilder;
        private Javadoc javadoc;
        private TypedElementInfo method;

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
        public BUILDER from(GeneratedMethod prototype) {
            method(prototype.method());
            contentBuilder(prototype.contentBuilder());
            javadoc(prototype.javadoc());
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
            builder.contentBuilder().ifPresent(this::contentBuilder);
            builder.javadoc().ifPresent(this::javadoc);
            return self();
        }

        /**
         * Definition of this method, including annotations (such as {@link Override}).
         *
         * @param method method definition
         * @return updated builder instance
         * @see #method()
         */
        public BUILDER method(TypedElementInfo method) {
            Objects.requireNonNull(method);
            this.method = method;
            return self();
        }

        /**
         * Definition of this method, including annotations (such as {@link Override}).
         *
         * @param consumer consumer of builder of method definition
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
         * Definition of this method, including annotations (such as {@link Override}).
         *
         * @param supplier supplier of method definition
         * @return updated builder instance
         * @see #method()
         */
        public BUILDER method(Supplier<? extends TypedElementInfo> supplier) {
            Objects.requireNonNull(supplier);
            this.method(supplier.get());
            return self();
        }

        /**
         * Generator for the method content.
         *
         * @param contentBuilder content builder consumer
         * @return updated builder instance
         * @see #contentBuilder()
         */
        public BUILDER contentBuilder(Consumer<ContentBuilder<?>> contentBuilder) {
            Objects.requireNonNull(contentBuilder);
            this.contentBuilder = contentBuilder;
            return self();
        }

        /**
         * Clear existing value of javadoc.
         *
         * @return updated builder instance
         * @see #javadoc()
         */
        public BUILDER clearJavadoc() {
            this.javadoc = null;
            return self();
        }

        /**
         * Javadoc for this method. We intentionally ignore documentation on {@link #method()}, as it may be
         * complicated to update it.
         * <p>
         * If not configured, no javadoc will be generated (useful for methods that override documented interface methods).
         *
         * @param javadoc javadoc for this method if defined
         * @return updated builder instance
         * @see #javadoc()
         */
        public BUILDER javadoc(Javadoc javadoc) {
            Objects.requireNonNull(javadoc);
            this.javadoc = javadoc;
            return self();
        }

        /**
         * Javadoc for this method. We intentionally ignore documentation on {@link #method()}, as it may be
         * complicated to update it.
         * <p>
         * If not configured, no javadoc will be generated (useful for methods that override documented interface methods).
         *
         * @param consumer consumer of builder of javadoc for this method if defined
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
         * Javadoc for this method. We intentionally ignore documentation on {@link #method()}, as it may be
         * complicated to update it.
         * <p>
         * If not configured, no javadoc will be generated (useful for methods that override documented interface methods).
         *
         * @param supplier supplier of javadoc for this method if defined
         * @return updated builder instance
         * @see #javadoc()
         */
        public BUILDER javadoc(Supplier<? extends Javadoc> supplier) {
            Objects.requireNonNull(supplier);
            this.javadoc(supplier.get());
            return self();
        }

        /**
         * Definition of this method, including annotations (such as {@link Override}).
         *
         * @return method definition
         */
        public Optional<TypedElementInfo> method() {
            return Optional.ofNullable(method);
        }

        /**
         * Generator for the method content.
         *
         * @return content builder consumer
         */
        public Optional<Consumer<ContentBuilder<?>>> contentBuilder() {
            return Optional.ofNullable(contentBuilder);
        }

        /**
         * Javadoc for this method. We intentionally ignore documentation on {@link #method()}, as it may be
         * complicated to update it.
         * <p>
         * If not configured, no javadoc will be generated (useful for methods that override documented interface methods).
         *
         * @return javadoc for this method if defined
         */
        public Optional<Javadoc> javadoc() {
            return Optional.ofNullable(javadoc);
        }

        @Override
        public String toString() {
            return "GeneratedMethodBuilder{"
                    + "method=" + method + ","
                    + "contentBuilder=" + contentBuilder + ","
                    + "javadoc=" + javadoc
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
            if (contentBuilder == null) {
                collector.fatal(getClass(), "Property \"contentBuilder\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Javadoc for this method. We intentionally ignore documentation on {@link #method()}, as it may be
         * complicated to update it.
         * <p>
         * If not configured, no javadoc will be generated (useful for methods that override documented interface methods).
         *
         * @param javadoc javadoc for this method if defined
         * @return updated builder instance
         * @see #javadoc()
         */
        @SuppressWarnings("unchecked")
        BUILDER javadoc(Optional<? extends Javadoc> javadoc) {
            Objects.requireNonNull(javadoc);
            this.javadoc = javadoc.map(Javadoc.class::cast).orElse(this.javadoc);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class GeneratedMethodImpl implements GeneratedMethod {

            private final Consumer<ContentBuilder<?>> contentBuilder;
            private final Optional<Javadoc> javadoc;
            private final TypedElementInfo method;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected GeneratedMethodImpl(BuilderBase<?, ?> builder) {
                this.method = builder.method().get();
                this.contentBuilder = builder.contentBuilder().get();
                this.javadoc = builder.javadoc().map(Function.identity());
            }

            @Override
            public TypedElementInfo method() {
                return method;
            }

            @Override
            public Consumer<ContentBuilder<?>> contentBuilder() {
                return contentBuilder;
            }

            @Override
            public Optional<Javadoc> javadoc() {
                return javadoc;
            }

            @Override
            public String toString() {
                return "GeneratedMethod{"
                        + "method=" + method + ","
                        + "contentBuilder=" + contentBuilder + ","
                        + "javadoc=" + javadoc
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof GeneratedMethod other)) {
                    return false;
                }
                return Objects.equals(method, other.method())
                        && Objects.equals(contentBuilder, other.contentBuilder())
                        && Objects.equals(javadoc, other.javadoc());
            }

            @Override
            public int hashCode() {
                return Objects.hash(method, contentBuilder, javadoc);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.GeneratedMethod}.
     */
    class Builder extends BuilderBase<Builder, GeneratedMethod> implements io.helidon.common.Builder<Builder, GeneratedMethod> {

        private Builder() {
        }

        @Override
        public GeneratedMethod buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new GeneratedMethodImpl(this);
        }

        @Override
        public GeneratedMethod build() {
            return buildPrototype();
        }

    }

}
