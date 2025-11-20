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

/**
 * Setup of configured option.
 *
 * @see #builder()
 */
public interface OptionConfigured extends Prototype.Api {

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
    static Builder builder(OptionConfigured instance) {
        return OptionConfigured.builder().from(instance);
    }

    /**
     * Config key to use.
     *
     * @return config key
     */
    String configKey();

    /**
     * Whether to merge the key with the current object.
     *
     * @return whether to merge, defaults to {@code false}, i.e. this option will have its own key, named {@link #configKey()}
     */
    boolean merge();

    /**
     * Whether to traverse the config node when creating a map.
     *
     * @return whether to traverse config, defaults to {@code true}
     */
    boolean traverse();

    /**
     * Factory method for this option. Factory method will be discovered from
     * {@link PrototypeInfo#configFactories()}.
     *
     * @return config factory method if defined
     */
    Optional<FactoryMethod> factoryMethod();

    /**
     * Fluent API builder base for {@link io.helidon.builder.codegen.OptionConfigured}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends OptionConfigured>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private boolean merge = false;
        private boolean traverse = true;
        private FactoryMethod factoryMethod;
        private String configKey;

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
        public BUILDER from(OptionConfigured prototype) {
            configKey(prototype.configKey());
            merge(prototype.merge());
            traverse(prototype.traverse());
            factoryMethod(prototype.factoryMethod());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            builder.configKey().ifPresent(this::configKey);
            merge(builder.merge());
            traverse(builder.traverse());
            builder.factoryMethod().ifPresent(this::factoryMethod);
            return self();
        }

        /**
         * Config key to use.
         *
         * @param configKey config key
         * @return updated builder instance
         * @see #configKey()
         */
        public BUILDER configKey(String configKey) {
            Objects.requireNonNull(configKey);
            this.configKey = configKey;
            return self();
        }

        /**
         * Whether to merge the key with the current object.
         *
         * @param merge whether to merge, defaults to {@code false}, i.e. this option will have its own key, named
         *              {@link #configKey()}
         * @return updated builder instance
         * @see #merge()
         */
        public BUILDER merge(boolean merge) {
            this.merge = merge;
            return self();
        }

        /**
         * Whether to traverse the config node when creating a map.
         *
         * @param traverse whether to traverse config, defaults to {@code true}
         * @return updated builder instance
         * @see #traverse()
         */
        public BUILDER traverse(boolean traverse) {
            this.traverse = traverse;
            return self();
        }

        /**
         * Clear existing value of factoryMethod.
         *
         * @return updated builder instance
         * @see #factoryMethod()
         */
        public BUILDER clearFactoryMethod() {
            this.factoryMethod = null;
            return self();
        }

        /**
         * Factory method for this option. Factory method will be discovered from
         * {@link PrototypeInfo#configFactories()}.
         *
         * @param factoryMethod config factory method if defined
         * @return updated builder instance
         * @see #factoryMethod()
         */
        public BUILDER factoryMethod(FactoryMethod factoryMethod) {
            Objects.requireNonNull(factoryMethod);
            this.factoryMethod = factoryMethod;
            return self();
        }

        /**
         * Factory method for this option. Factory method will be discovered from
         * {@link PrototypeInfo#configFactories()}.
         *
         * @param consumer consumer of builder of config factory method if defined
         * @return updated builder instance
         * @see #factoryMethod()
         */
        public BUILDER factoryMethod(Consumer<FactoryMethod.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = FactoryMethod.builder();
            consumer.accept(builder);
            this.factoryMethod(builder.build());
            return self();
        }

        /**
         * Factory method for this option. Factory method will be discovered from
         * {@link PrototypeInfo#configFactories()}.
         *
         * @param supplier supplier of config factory method if defined
         * @return updated builder instance
         * @see #factoryMethod()
         */
        public BUILDER factoryMethod(Supplier<? extends FactoryMethod> supplier) {
            Objects.requireNonNull(supplier);
            this.factoryMethod(supplier.get());
            return self();
        }

        /**
         * Config key to use.
         *
         * @return config key
         */
        public Optional<String> configKey() {
            return Optional.ofNullable(configKey);
        }

        /**
         * Whether to merge the key with the current object.
         *
         * @return whether to merge, defaults to {@code false}, i.e. this option will have its own key, named {@link #configKey()}
         */
        public boolean merge() {
            return merge;
        }

        /**
         * Whether to traverse the config node when creating a map.
         *
         * @return whether to traverse config, defaults to {@code true}
         */
        public boolean traverse() {
            return traverse;
        }

        /**
         * Factory method for this option. Factory method will be discovered from
         * {@link PrototypeInfo#configFactories()}.
         *
         * @return config factory method if defined
         */
        public Optional<FactoryMethod> factoryMethod() {
            return Optional.ofNullable(factoryMethod);
        }

        @Override
        public String toString() {
            return "OptionConfiguredBuilder{"
                    + "configKey=" + configKey + ","
                    + "merge=" + merge + ","
                    + "traverse=" + traverse + ","
                    + "factoryMethod=" + factoryMethod
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
            if (configKey == null) {
                collector.fatal(getClass(), "Property \"configKey\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Factory method for this option. Factory method will be discovered from
         * {@link PrototypeInfo#configFactories()}.
         *
         * @param factoryMethod config factory method if defined
         * @return updated builder instance
         * @see #factoryMethod()
         */
        @SuppressWarnings("unchecked")
        BUILDER factoryMethod(Optional<? extends FactoryMethod> factoryMethod) {
            Objects.requireNonNull(factoryMethod);
            this.factoryMethod = factoryMethod.map(FactoryMethod.class::cast).orElse(this.factoryMethod);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class OptionConfiguredImpl implements OptionConfigured {

            private final boolean merge;
            private final boolean traverse;
            private final Optional<FactoryMethod> factoryMethod;
            private final String configKey;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected OptionConfiguredImpl(BuilderBase<?, ?> builder) {
                this.configKey = builder.configKey().get();
                this.merge = builder.merge();
                this.traverse = builder.traverse();
                this.factoryMethod = builder.factoryMethod().map(Function.identity());
            }

            @Override
            public String configKey() {
                return configKey;
            }

            @Override
            public boolean merge() {
                return merge;
            }

            @Override
            public boolean traverse() {
                return traverse;
            }

            @Override
            public Optional<FactoryMethod> factoryMethod() {
                return factoryMethod;
            }

            @Override
            public String toString() {
                return "OptionConfigured{"
                        + "configKey=" + configKey + ","
                        + "merge=" + merge + ","
                        + "traverse=" + traverse + ","
                        + "factoryMethod=" + factoryMethod
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof OptionConfigured other)) {
                    return false;
                }
                return Objects.equals(configKey, other.configKey())
                        && merge == other.merge()
                        && traverse == other.traverse()
                        && Objects.equals(factoryMethod, other.factoryMethod());
            }

            @Override
            public int hashCode() {
                return Objects.hash(configKey, merge, traverse, factoryMethod);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.builder.codegen.OptionConfigured}.
     */
    class Builder extends BuilderBase<Builder, OptionConfigured> implements io.helidon.common.Builder<Builder, OptionConfigured> {

        private Builder() {
        }

        @Override
        public OptionConfigured buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new OptionConfiguredImpl(this);
        }

        @Override
        public OptionConfigured build() {
            return buildPrototype();
        }

    }

}
