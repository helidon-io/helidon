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

package io.helidon.builder.config.spi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.config.Config;

/**
 * Config resolution process context.
 */
public class ResolutionContext {
    private final Class<?> configBeanType;
    private final Config cfg;
    private final ConfigResolver resolver;
    private final ConfigBeanBuilderValidator<?> validator;
    private final Map<Class<?>, Function<Config, ?>> mappers;

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
        this.mappers = Map.copyOf(b.mappers);
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

    /**
     * Return the known config bean mappers associated with this config bean context.
     *
     * @return the config bean mappers
     */
    public Map<Class<?>, Function<Config, ?>> mappers() {
        return mappers;
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
     * @param configBeanType the config bean type
     * @param cfg            the config
     * @param resolver       the resolver
     * @param validator      the bean builder validator
     * @param mappers        the known config bean mappers related to this config bean context
     * @return the resolution context
     */
    public static ResolutionContext create(Class<?> configBeanType,
                                           Config cfg,
                                           ConfigResolver resolver,
                                           ConfigBeanBuilderValidator<?> validator, Map<Class<?>,
            Function<Config, ?>> mappers) {
        return ResolutionContext.builder()
                .configBeanType(configBeanType)
                .config(cfg)
                .resolver(resolver)
                .validator(validator)
                .mappers(mappers)
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
        private final Map<Class<?>, Function<Config, ?>> mappers = new LinkedHashMap<>();

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
            return identity();
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

        /**
         * Sets the mappers to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder mappers(Map<Class<?>, Function<Config, ?>> val) {
            Objects.requireNonNull(val);
            this.mappers.clear();
            this.mappers.putAll(val);
            return identity();
        }

        /**
         * Adds a single mapper val.
         *
         * @param key the key
         * @param val the value
         * @return this fluent builder
         */
        public Builder addMapper(Class<?> key, Function<Config, ?> val) {
            Objects.requireNonNull(val);
            this.mappers.put(key, val);
            return identity();
        }
    }

}
