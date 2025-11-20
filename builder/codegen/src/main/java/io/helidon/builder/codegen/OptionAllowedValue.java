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
 * Option allowed value.
 *
 * @see #builder()
 */
public interface OptionAllowedValue extends Prototype.Api {

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
    static Builder builder(OptionAllowedValue instance) {
        return OptionAllowedValue.builder().from(instance);
    }

    /**
     * Value, such as a string constant or enum value.
     *
     * @return value that is allowed
     */
    String value();

    /**
     * Description of this value.
     *
     * @return value description
     */
    String description();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.OptionAllowedValue}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends OptionAllowedValue>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private String description;
        private String value;

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
        public BUILDER from(OptionAllowedValue prototype) {
            value(prototype.value());
            description(prototype.description());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.value().ifPresent(this::value);
            builder.description().ifPresent(this::description);
            return self();
        }

        /**
         * Value, such as a string constant or enum value.
         *
         * @param value value that is allowed
         * @return updated builder instance
         * @see #value()
         */
        public BUILDER value(String value) {
            Objects.requireNonNull(value);
            this.value = value;
            return self();
        }

        /**
         * Description of this value.
         *
         * @param description value description
         * @return updated builder instance
         * @see #description()
         */
        public BUILDER description(String description) {
            Objects.requireNonNull(description);
            this.description = description;
            return self();
        }

        /**
         * Value, such as a string constant or enum value.
         *
         * @return value that is allowed
         */
        public Optional<String> value() {
            return Optional.ofNullable(value);
        }

        /**
         * Description of this value.
         *
         * @return value description
         */
        public Optional<String> description() {
            return Optional.ofNullable(description);
        }

        @Override
        public String toString() {
            return "OptionAllowedValueBuilder{"
                    + "value=" + value + ","
                    + "description=" + description
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
            if (value == null) {
                collector.fatal(getClass(), "Property \"value\" must not be null, but not set");
            }
            if (description == null) {
                collector.fatal(getClass(), "Property \"description\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OptionAllowedValueImpl implements OptionAllowedValue {

            private final String description;
            private final String value;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected OptionAllowedValueImpl(BuilderBase<?, ?> builder) {
                this.value = builder.value().get();
                this.description = builder.description().get();
            }

            @Override
            public String value() {
                return value;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String toString() {
                return "OptionAllowedValue{"
                        + "value=" + value + ","
                        + "description=" + description
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof OptionAllowedValue other)) {
                    return false;
                }
                return Objects.equals(value, other.value())
                        && Objects.equals(description, other.description());
            }

            @Override
            public int hashCode() {
                return Objects.hash(value, description);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.OptionAllowedValue}.
     */
    class Builder extends BuilderBase<Builder, OptionAllowedValue>
            implements io.helidon.common.Builder<Builder, OptionAllowedValue> {

        private Builder() {
        }

        @Override
        public OptionAllowedValue buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new OptionAllowedValueImpl(this);
        }

        @Override
        public OptionAllowedValue build() {
            return buildPrototype();
        }

    }

}
