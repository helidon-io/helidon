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
 * Interface generated from definition. Please add javadoc to the definition interface.
 *
 * @see #builder()
 * @see #create()
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
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static OptionSingular create() {
        return OptionSingular.builder().buildPrototype();
    }

    /**
     * Singular setter method.
     * <p>
     * Examples:
     * <ul>
     *     <li>{@code addAllowedValue} - for option named {@code allowedValues}</li>
     *     <li>{@code putOption} - for a map option named {@code options}</li>
     * </ul>
     *
     * @return singular setter method
     */
    String setter();

    /**
     * Singular form of the option name.
     * For {@code lines}, this would be {@code line}.
     * For {@code properties}, this should be {@code property}, so we allow customization by the user.
     *
     * @return singular name
     */
    String name();

    /**
     * Fluent API builder base for {@link OptionSingular}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends OptionSingular>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private String name;
        private String setter;

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
            setter(prototype.setter());
            name(prototype.name());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(OptionSingular.BuilderBase<?, ?> builder) {
            builder.setter().ifPresent(this::setter);
            builder.name().ifPresent(this::name);
            return self();
        }

        /**
         * Singular setter method.
         *
         * @param setter singular setter
         * @return updated builder instance
         * @see #setter()
         */
        public BUILDER setter(String setter) {
            Objects.requireNonNull(setter);
            this.setter = setter;
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
         * Singular setter method.
         *
         * @return the setter
         */
        public Optional<String> setter() {
            return Optional.ofNullable(setter);
        }

        /**
         * Singular form of the option name.
         * For {@code lines}, this would be {@code line}.
         * For {@code properties}, this should be {@code property}, so we allow customization by the user.
         *
         * @return the name
         */
        public Optional<String> name() {
            return Optional.ofNullable(name);
        }

        @Override
        public String toString() {
            return "OptionSingularBuilder{"
                    + "setter=" + setter + ","
                    + "name=" + name
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
            if (setter == null) {
                collector.fatal(getClass(), "Property \"setter\" must not be null, but not set");
            }
            if (name == null) {
                collector.fatal(getClass(), "Property \"name\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OptionSingularImpl implements OptionSingular {

            private final String name;
            private final String setter;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected OptionSingularImpl(OptionSingular.BuilderBase<?, ?> builder) {
                this.setter = builder.setter().get();
                this.name = builder.name().get();
            }

            @Override
            public String setter() {
                return setter;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String toString() {
                return "OptionSingular{"
                        + "setter=" + setter + ","
                        + "name=" + name
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
                return Objects.equals(setter, other.setter())
                        && Objects.equals(name, other.name());
            }

            @Override
            public int hashCode() {
                return Objects.hash(setter, name);
            }

        }

    }

    /**
     * Fluent API builder for {@link OptionSingular}.
     */
    class Builder extends OptionSingular.BuilderBase<OptionSingular.Builder, OptionSingular>
            implements io.helidon.common.Builder<OptionSingular.Builder, OptionSingular> {

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
