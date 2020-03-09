/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.config.spi.ConfigFilter;

/**
 * Class provides access to built-in {@link io.helidon.config.spi.ConfigFilter} implementations.
 *
 * @see io.helidon.config.spi.ConfigFilter
 */
public final class ConfigFilters {

    private ConfigFilters() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Creates a value reference resolving config filter.
     *
     * @return a new config filter builder
     * @see ConfigFilter
     */
    public static ValueResolvingBuilder valueResolving() {
        return new ValueResolvingBuilder();
    }

    /**
     * A builder for value reference resolving filter.
     * <p>
     * The {@link ValueResolvingFilter} can either allow (the default) or reject
     * references to missing tokens. To reject such references, invoke the
     * {@link #failOnMissingReference} method on the builder before invoking
     * {@link #build()}.
     * <p>
     * Alternatively, if you create the builder using the
     * {@link #create(io.helidon.config.Config)} method, in the {@code Config}
     * instance you pass set the config key
     * {@value FAIL_ON_MISSING_REFERENCE_KEY_NAME} to {@code true}.
     */
    public static final class ValueResolvingBuilder implements Supplier<Function<Config, ConfigFilter>> {

        /**
         * Config key for setting missing reference behavior on
         * {@code ValueResolvingFilter}s.
         */
        public static final String FAIL_ON_MISSING_REFERENCE_KEY_NAME =
                "config.value-resolving-filter.fail-on-missing-reference";

        private boolean failOnMissingReference;

        private ValueResolvingBuilder() {
            failOnMissingReference = false;
        }

        /**
         * Initializes config filter instance from configuration properties.
         * <p>
         * Optional {@code properties}:
         * <ul>
         * <li>{@code failOnMissingReference} - type {@link Boolean}, see {@link #failOnMissingReference}</li>
         * </ul>
         *
         * @param metaConfig meta-configuration used to initialize returned config filter instance from
         * @return new instance of config filter builder described by {@code metaConfig}
         * @throws MissingValueException  in case the configuration tree does not contain all expected sub-nodes
         *                                required by the mapper implementation to provide instance of Java type.
         * @throws ConfigMappingException in case the mapper fails to map the (existing) configuration tree represented by the
         *                                supplied configuration node to an instance of a given Java type.
         * @see ConfigFilters#valueResolving()
         */
        public static ValueResolvingBuilder create(Config metaConfig) throws ConfigMappingException, MissingValueException {
            ValueResolvingBuilder builder = new ValueResolvingBuilder();
            builder.failOnMissingReference(metaConfig.get(FAIL_ON_MISSING_REFERENCE_KEY_NAME).asBoolean().orElse(false));
            return builder;
        }

        /**
         * Sets how the {@code ValueResolvingFilter} resulting from this builder
         * will behave when a value contains a reference to a non-existent
         * key.
         *
         * @param failOnMissing whether the filter should fail on missing references
         * or not
         * @return this builder
         */
        public ValueResolvingBuilder failOnMissingReference(boolean failOnMissing) {
            failOnMissingReference = failOnMissing;
            return this;
        }

        /**
         * Creates a function of values reference resolving.
         *
         * @return a provider of config filter
         */
        public Function<Config, ConfigFilter> build() {
            return (c) -> new ValueResolvingFilter(failOnMissingReference);
        }

        @Override
        public Function<Config, ConfigFilter> get() {
            return build();
        }

    }
}
