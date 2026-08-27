/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc;

import java.util.Objects;

import io.helidon.builder.api.Prototype;

/**
 * Converts public JDBC provider configuration into validated runtime policy.
 */
final class JdbcProviderPropertiesSupport {

    private JdbcProviderPropertiesSupport() {
    }

    /**
     * Creates the immutable cache policy for one JDBC client.
     *
     * @param properties public provider configuration
     * @return validated cache policy
     */
    static JdbcClientImpl.CachePolicy create(JdbcPropertiesConfig properties) {
        Objects.requireNonNull(properties, "The JDBC provider properties must not be null.");
        JdbcProviderPropertiesConfig jdbc = Objects.requireNonNull(
                properties.jdbc(),
                "The JDBC provider configuration must not be null.");
        return cachePolicy(jdbc.parameterCountCache());
    }

    private static JdbcClientImpl.CachePolicy cachePolicy(JdbcParameterCountCacheConfig config) {
        Objects.requireNonNull(config, "The JDBC parameter count cache configuration must not be null.");
        return new JdbcClientImpl.CachePolicy(config.capacity(), config.maxSqlLength());
    }

    /**
     * Validates the parameter count cache builder before it creates a public
     * configuration instance.
     */
    static final class ParameterCountCacheDecorator
            implements Prototype.BuilderDecorator<JdbcParameterCountCacheConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(JdbcParameterCountCacheConfig.BuilderBase<?, ?> target) {
            new JdbcClientImpl.CachePolicy(target.capacity(), target.maxSqlLength());
        }
    }

}
