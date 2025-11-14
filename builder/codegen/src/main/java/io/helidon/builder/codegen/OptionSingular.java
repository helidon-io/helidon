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

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * Definition of a singular option.
 *
 * @see #builder()
 */
public interface OptionSingular extends Prototype.Api {

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
    static Builder builder(OptionSingular instance) {
        return OptionSingular.builder().from(instance);
    }

    /**
     * Singular form of the option name.
     * For {@code lines}, this would be {@code line}.
     * For {@code properties}, this should be {@code property}, so we allow customization by the user.
     *
     * @return singular name
     */
    String name();

    /**
     * Name of the singular setter method.
     *
     * @return method name
     */
    String methodName();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.OptionSingular}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends OptionSingular>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private String methodName;
        private String name;

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
        public BUILDER from(OptionSingular prototype) {
            name(prototype.name());
            methodName(prototype.methodName());
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
            builder.methodName().ifPresent(this::methodName);
            return self();
        }

        /**
         * Singular form of the option name.
         * For {@code lines}, this would be {@code line}.
         * For {@code properties}, this should be {@code property}, so we allow customization by the user.
         *
         * @param name singular name
         * @return updated builder instance
         * @see #name()
         */
        public BUILDER name(String name) {
            Objects.requireNonNull(name);
            this.name = name;
            return self();
        }

        /**
         * Name of the singular setter method.
         *
         * @param methodName method name
         * @return updated builder instance
         * @see #methodName()
         */
        public BUILDER methodName(String methodName) {
            Objects.requireNonNull(methodName);
            this.methodName = methodName;
            return self();
        }

        /**
         * Singular form of the option name.
         * For {@code lines}, this would be {@code line}.
         * For {@code properties}, this should be {@code property}, so we allow customization by the user.
         *
         * @return singular name
         */
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        /**
         * Name of the singular setter method.
         *
         * @return method name
         */
        public Optional<String> methodName() {
            return Optional.ofNullable(methodName);
        }

        @Override
        public String toString() {
            return "OptionSingularBuilder{"
                    + "name=" + name + ","
                    + "methodName=" + methodName
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
            if (methodName == null) {
                collector.fatal(getClass(), "Property \"methodName\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OptionSingularImpl implements OptionSingular {

            private final String methodName;
            private final String name;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected OptionSingularImpl(BuilderBase<?, ?> builder) {
                this.name = builder.name().get();
                this.methodName = builder.methodName().get();
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String methodName() {
                return methodName;
            }

            @Override
            public String toString() {
                return "OptionSingular{"
                        + "name=" + name + ","
                        + "methodName=" + methodName
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof OptionSingular other)) {
                    return false;
                }
                return Objects.equals(name, other.name())
                        && Objects.equals(methodName, other.methodName());
            }

            @Override
            public int hashCode() {
                return Objects.hash(name, methodName);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.OptionSingular}.
     */
    class Builder extends BuilderBase<Builder, OptionSingular> implements io.helidon.common.Builder<Builder, OptionSingular> {

        private Builder() {
        }

        @Override
        public OptionSingular buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new OptionSingularImpl(this);
        }

        @Override
        public OptionSingular build() {
            return buildPrototype();
        }

    }

}
