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
import java.util.function.Function;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

/**
 * Deprecated option information.
 *
 * @see #builder()
 */
public interface OptionDeprecation extends Prototype.Api {

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
    static Builder builder(OptionDeprecation instance) {
        return OptionDeprecation.builder().from(instance);
    }

    /**
     * Deprecation message.
     *
     * @return deprecation message
     */
    String message();

    /**
     * If this is scheduled for removal, defaults to {@code true}.
     *
     * @return whether scheduled for removal
     */
    boolean forRemoval();

    /**
     * Name of the option to use instead of this one.
     *
     * @return alternative option name
     */
    Optional<String> alternative();

    /**
     * Version that deprecated this option.
     *
     * @return since version
     */
    Optional<String> since();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.OptionDeprecation}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends OptionDeprecation>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private boolean forRemoval = true;
        private String alternative;
        private String message;
        private String since;

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
        public BUILDER from(OptionDeprecation prototype) {
            message(prototype.message());
            forRemoval(prototype.forRemoval());
            alternative(prototype.alternative());
            since(prototype.since());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.message().ifPresent(this::message);
            forRemoval(builder.forRemoval());
            builder.alternative().ifPresent(this::alternative);
            builder.since().ifPresent(this::since);
            return self();
        }

        /**
         * Deprecation message.
         *
         * @param message deprecation message
         * @return updated builder instance
         * @see #message()
         */
        public BUILDER message(String message) {
            Objects.requireNonNull(message);
            this.message = message;
            return self();
        }

        /**
         * If this is scheduled for removal, defaults to {@code true}.
         *
         * @param forRemoval whether scheduled for removal
         * @return updated builder instance
         * @see #forRemoval()
         */
        public BUILDER forRemoval(boolean forRemoval) {
            this.forRemoval = forRemoval;
            return self();
        }

        /**
         * Clear existing value of alternative.
         *
         * @return updated builder instance
         * @see #alternative()
         */
        public BUILDER clearAlternative() {
            this.alternative = null;
            return self();
        }

        /**
         * Name of the option to use instead of this one.
         *
         * @param alternative alternative option name
         * @return updated builder instance
         * @see #alternative()
         */
        public BUILDER alternative(String alternative) {
            Objects.requireNonNull(alternative);
            this.alternative = alternative;
            return self();
        }

        /**
         * Clear existing value of since.
         *
         * @return updated builder instance
         * @see #since()
         */
        public BUILDER clearSince() {
            this.since = null;
            return self();
        }

        /**
         * Version that deprecated this option.
         *
         * @param since since version
         * @return updated builder instance
         * @see #since()
         */
        public BUILDER since(String since) {
            Objects.requireNonNull(since);
            this.since = since;
            return self();
        }

        /**
         * Deprecation message.
         *
         * @return deprecation message
         */
        public Optional<String> message() {
            return Optional.ofNullable(message);
        }

        /**
         * If this is scheduled for removal, defaults to {@code true}.
         *
         * @return whether scheduled for removal
         */
        public boolean forRemoval() {
            return forRemoval;
        }

        /**
         * Name of the option to use instead of this one.
         *
         * @return alternative option name
         */
        public Optional<String> alternative() {
            return Optional.ofNullable(alternative);
        }

        /**
         * Version that deprecated this option.
         *
         * @return since version
         */
        public Optional<String> since() {
            return Optional.ofNullable(since);
        }

        @Override
        public String toString() {
            return "OptionDeprecationBuilder{"
                    + "message=" + message + ","
                    + "forRemoval=" + forRemoval + ","
                    + "alternative=" + alternative + ","
                    + "since=" + since
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
            if (message == null) {
                collector.fatal(getClass(), "Property \"message\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Name of the option to use instead of this one.
         *
         * @param alternative alternative option name
         * @return updated builder instance
         * @see #alternative()
         */
        BUILDER alternative(Optional<String> alternative) {
            Objects.requireNonNull(alternative);
            this.alternative = alternative.orElse(this.alternative);
            return self();
        }

        /**
         * Version that deprecated this option.
         *
         * @param since since version
         * @return updated builder instance
         * @see #since()
         */
        BUILDER since(Optional<String> since) {
            Objects.requireNonNull(since);
            this.since = since.orElse(this.since);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OptionDeprecationImpl implements OptionDeprecation {

            private final boolean forRemoval;
            private final Optional<String> alternative;
            private final Optional<String> since;
            private final String message;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected OptionDeprecationImpl(BuilderBase<?, ?> builder) {
                this.message = builder.message().get();
                this.forRemoval = builder.forRemoval();
                this.alternative = builder.alternative().map(Function.identity());
                this.since = builder.since().map(Function.identity());
            }

            @Override
            public String message() {
                return message;
            }

            @Override
            public boolean forRemoval() {
                return forRemoval;
            }

            @Override
            public Optional<String> alternative() {
                return alternative;
            }

            @Override
            public Optional<String> since() {
                return since;
            }

            @Override
            public String toString() {
                return "OptionDeprecation{"
                        + "message=" + message + ","
                        + "forRemoval=" + forRemoval + ","
                        + "alternative=" + alternative + ","
                        + "since=" + since
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof OptionDeprecation other)) {
                    return false;
                }
                return Objects.equals(message, other.message())
                        && forRemoval == other.forRemoval()
                        && Objects.equals(alternative, other.alternative())
                        && Objects.equals(since, other.since());
            }

            @Override
            public int hashCode() {
                return Objects.hash(message, forRemoval, alternative, since);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.OptionDeprecation}.
     */
    class Builder extends BuilderBase<Builder, OptionDeprecation>
            implements io.helidon.common.Builder<Builder, OptionDeprecation> {

        private Builder() {
        }

        @Override
        public OptionDeprecation buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new OptionDeprecationImpl(this);
        }

        @Override
        public OptionDeprecation build() {
            return buildPrototype();
        }

    }

}
