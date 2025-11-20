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
import io.helidon.common.types.AccessModifier;

/**
 * Configuration information for a prototype.
 *
 * @see #builder()
 * @see #create()
 */
public interface PrototypeConfigured extends Prototype.Api {

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
    static Builder builder(PrototypeConfigured instance) {
        return PrototypeConfigured.builder().from(instance);
    }

    /**
     * Create a new instance with default values.
     *
     * @return a new instance
     */
    static PrototypeConfigured create() {
        return PrototypeConfigured.builder().buildPrototype();
    }

    /**
     * Access modifier.
     *
     * @return access modifier of the method {@code create(Config)}, defaults to {@code public}
     */
    AccessModifier createAccessModifier();

    /**
     * Whether the configuration is expected from the root of config tree.
     * Defaults to {@code true} in case a {@link #key()} is defined.
     *
     * @return whether this prototype uses root configuration key
     */
    boolean root();

    /**
     * Key of this prototype's configuration.
     *
     * @return key if configured
     */
    Optional<String> key();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.PrototypeConfigured}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends PrototypeConfigured>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private AccessModifier createAccessModifier = AccessModifier.PUBLIC;
        private boolean root = true;
        private String key;

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
        public BUILDER from(PrototypeConfigured prototype) {
            createAccessModifier(prototype.createAccessModifier());
            root(prototype.root());
            key(prototype.key());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            createAccessModifier(builder.createAccessModifier());
            root(builder.root());
            builder.key().ifPresent(this::key);
            return self();
        }

        /**
         * Access modifier.
         *
         * @param createAccessModifier access modifier of the method {@code create(Config)}, defaults to {@code public}
         * @return updated builder instance
         * @see #createAccessModifier()
         */
        public BUILDER createAccessModifier(AccessModifier createAccessModifier) {
            Objects.requireNonNull(createAccessModifier);
            this.createAccessModifier = createAccessModifier;
            return self();
        }

        /**
         * Whether the configuration is expected from the root of config tree.
         * Defaults to {@code true} in case a {@link #key()} is defined.
         *
         * @param root whether this prototype uses root configuration key
         * @return updated builder instance
         * @see #root()
         */
        public BUILDER root(boolean root) {
            this.root = root;
            return self();
        }

        /**
         * Clear existing value of key.
         *
         * @return updated builder instance
         * @see #key()
         */
        public BUILDER clearKey() {
            this.key = null;
            return self();
        }

        /**
         * Key of this prototype's configuration.
         *
         * @param key key if configured
         * @return updated builder instance
         * @see #key()
         */
        public BUILDER key(String key) {
            Objects.requireNonNull(key);
            this.key = key;
            return self();
        }

        /**
         * Access modifier.
         *
         * @return access modifier of the method {@code create(Config)}, defaults to {@code public}
         */
        public AccessModifier createAccessModifier() {
            return createAccessModifier;
        }

        /**
         * Whether the configuration is expected from the root of config tree.
         * Defaults to {@code true} in case a {@link #key()} is defined.
         *
         * @return whether this prototype uses root configuration key
         */
        public boolean root() {
            return root;
        }

        /**
         * Key of this prototype's configuration.
         *
         * @return key if configured
         */
        public Optional<String> key() {
            return Optional.ofNullable(key);
        }

        @Override
        public String toString() {
            return "PrototypeConfiguredBuilder{"
                    + "createAccessModifier=" + createAccessModifier + ","
                    + "root=" + root + ","
                    + "key=" + key
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
        }

        /**
         * Key of this prototype's configuration.
         *
         * @param key key if configured
         * @return updated builder instance
         * @see #key()
         */
        BUILDER key(Optional<String> key) {
            Objects.requireNonNull(key);
            this.key = key.orElse(this.key);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class PrototypeConfiguredImpl implements PrototypeConfigured {

            private final AccessModifier createAccessModifier;
            private final boolean root;
            private final Optional<String> key;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected PrototypeConfiguredImpl(BuilderBase<?, ?> builder) {
                this.createAccessModifier = builder.createAccessModifier();
                this.root = builder.root();
                this.key = builder.key().map(Function.identity());
            }

            @Override
            public AccessModifier createAccessModifier() {
                return createAccessModifier;
            }

            @Override
            public boolean root() {
                return root;
            }

            @Override
            public Optional<String> key() {
                return key;
            }

            @Override
            public String toString() {
                return "PrototypeConfigured{"
                        + "createAccessModifier=" + createAccessModifier + ","
                        + "root=" + root + ","
                        + "key=" + key
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof PrototypeConfigured other)) {
                    return false;
                }
                return Objects.equals(createAccessModifier, other.createAccessModifier())
                        && root == other.root()
                        && Objects.equals(key, other.key());
            }

            @Override
            public int hashCode() {
                return Objects.hash(createAccessModifier, root, key);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.PrototypeConfigured}.
     */
    class Builder extends BuilderBase<Builder, PrototypeConfigured>
            implements io.helidon.common.Builder<Builder, PrototypeConfigured> {

        private Builder() {
        }

        @Override
        public PrototypeConfigured buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new PrototypeConfiguredImpl(this);
        }

        @Override
        public PrototypeConfigured build() {
            return buildPrototype();
        }

    }

}
