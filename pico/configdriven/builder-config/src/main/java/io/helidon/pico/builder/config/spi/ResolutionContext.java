/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.config.spi;

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.config.Config;

/**
 * Config resolution process context.
 */
public class ResolutionContext {
    private final Class<?> configBeanType;
    private final Config cfg;
    private final ConfigResolver resolver;
    private final ConfigBeanBuilderValidator<?> validator;

    /**
     * Constructor for this type that takes a builder.
     *
     * @param b the builder
     */
    protected ResolutionContext(Builder b) {
        this.configBeanType = Objects.requireNonNull(b.configBeanType);
        this.cfg = Objects.requireNonNull(b.cfg);
        this.resolver = Objects.requireNonNull(b.resolver);
        this.validator = b.validator;
    }

    /**
     * Return the config bean type.
     *
     * @return the config bean type
     */
    public Class<?> configBeanType() {
        return configBeanType;
    }

    /**
     * Return the config.
     *
     * @return the config
     */
    public Config config() {
        return cfg;
    }

    /**
     * Return the config resolver.
     *
     * @return the resolver
     */
    public ConfigResolver resolver() {
        return resolver;
    }

    /**
     * Return the config bean builder validator.
     *
     * @return the validator
     */
    public Optional<ConfigBeanBuilderValidator<?>> validator() {
        return Optional.ofNullable(validator);
    }

    @Override
    public String toString() {
        return config().toString();
    }

    /**
     * Creates a new fluent builder for this type.
     *
     * @return a fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * Creates a resolution context from the provided arguments.
     *
     * @param configBeanType    the config bean type
     * @param cfg               the config
     * @param resolver          the resolver
     * @param validator         the bean builder validator
     * @return the resolution context
     */
    public static ResolutionContext create(Class<?> configBeanType,
                                           Config cfg,
                                           ConfigResolver resolver,
                                           ConfigBeanBuilderValidator<?> validator) {
        return ResolutionContext.builder()
                .configBeanType(configBeanType)
                .config(cfg)
                .resolver(resolver)
                .validator(validator)
                .build();
    }

    /**
     * Fluent builder for {@link ResolutionContext}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, ResolutionContext> {
        private Class<?> configBeanType;
        private Config cfg;
        private ConfigResolver resolver;
        private ConfigBeanBuilderValidator<?> validator;

        /**
         * Constructor for the fluent builder.
         */
        protected Builder() {
        }

        /**
         * Build the instance.
         *
         * @return the built instance
         */
        @Override
        public ResolutionContext build() {
            return new ResolutionContext(this);
        }

        /**
         * Set the config bean type.
         *
         * @param configBeanType    the config bean type
         * @return this fluent builder
         */
        public Builder configBeanType(Class<?> configBeanType) {
            this.configBeanType = Objects.requireNonNull(configBeanType);
            return this;
        }

        /**
         * Set the config.
         *
         * @param val   the config
         * @return this fluent builder
         */
        public Builder config(Config val) {
            this.cfg = Objects.requireNonNull(val);
            return identity();
        }

        /**
         * Set the config resolver.
         *
         * @param val   the config resolver
         * @return this fluent builder
         */
        public Builder resolver(ConfigResolver val) {
            this.resolver = Objects.requireNonNull(val);
            return identity();
        }

        /**
         * Set the config bean builder validator.
         *
         * @param val   the config validator
         * @return this fluent builder
         */
        public Builder validator(ConfigBeanBuilderValidator<?> val) {
            this.validator = val;
            return identity();
        }
    }

}
