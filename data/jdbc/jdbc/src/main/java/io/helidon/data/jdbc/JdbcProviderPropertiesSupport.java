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
import io.helidon.common.Size;
import io.helidon.data.DataException;

/**
 * Converts public JDBC provider configuration into validated runtime policy.
 */
final class JdbcProviderPropertiesSupport {

    private JdbcProviderPropertiesSupport() {
    }

    /**
     * Creates the complete immutable policy for one persistence unit.
     *
     * @param properties public provider configuration
     * @return validated runtime policy
     */
    static Policy create(JdbcPropertiesConfig properties) {
        Objects.requireNonNull(properties, "The JDBC provider properties must not be null.");
        JdbcProviderPropertiesConfig jdbc = Objects.requireNonNull(
                properties.jdbc(),
                "The JDBC provider configuration must not be null.");
        return new Policy(cachePolicy(jdbc.parameterCountCache()), bootstrapPolicy(jdbc.scripts()));
    }

    private static JdbcClientImpl.CachePolicy cachePolicy(JdbcParameterCountCacheConfig config) {
        Objects.requireNonNull(config, "The JDBC parameter count cache configuration must not be null.");
        return new JdbcClientImpl.CachePolicy(config.capacity(), config.maxSqlLength());
    }

    private static JdbcScriptRunner.BootstrapPolicy bootstrapPolicy(JdbcScriptConfig config) {
        Objects.requireNonNull(config, "The JDBC script configuration must not be null.");
        return bootstrapPolicy(config.maxResourceSize(), config.maxTotalSize(), config.maxStatements());
    }

    private static JdbcScriptRunner.BootstrapPolicy bootstrapPolicy(Size maxResourceSize,
                                                                    Size maxTotalSize,
                                                                    int maxStatements) {
        int maxResourceBytes = byteCount(maxResourceSize, "max-resource-size");
        int maxTotalBytes = byteCount(maxTotalSize, "max-total-size");
        return new JdbcScriptRunner.BootstrapPolicy(maxResourceBytes,
                                                    maxTotalBytes,
                                                    maxStatements);
    }

    private static int byteCount(Size size, String propertyName) {
        if (size == null) {
            throw new DataException("The JDBC script property '" + propertyName + "' must not be null.");
        }
        long bytes;
        try {
            bytes = size.toBytes();
        } catch (ArithmeticException failure) {
            throw new DataException("The JDBC script property '" + propertyName
                                            + "' must represent a whole number of bytes within the supported range.",
                                    failure);
        }
        if (bytes < 1) {
            throw new DataException("The JDBC script property '" + propertyName + "' must be greater than zero bytes.");
        }
        if (bytes > Integer.MAX_VALUE - 1L) {
            throw new DataException("The JDBC script property '" + propertyName + "' must not exceed "
                                            + (Integer.MAX_VALUE - 1L) + " bytes.");
        }
        return Math.toIntExact(bytes);
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

    /**
     * Validates the script builder before it creates a public configuration
     * instance.
     */
    static final class ScriptDecorator implements Prototype.BuilderDecorator<JdbcScriptConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(JdbcScriptConfig.BuilderBase<?, ?> target) {
            bootstrapPolicy(target.maxResourceSize(), target.maxTotalSize(), target.maxStatements());
        }
    }

    /**
     * Immutable provider policy retained for one persistence unit.
     *
     * @param cache parameter count cache policy
     * @param bootstrap bootstrap script policy
     */
    record Policy(JdbcClientImpl.CachePolicy cache, JdbcScriptRunner.BootstrapPolicy bootstrap) {

        Policy {
            Objects.requireNonNull(cache, "The JDBC cache policy must not be null.");
            Objects.requireNonNull(bootstrap, "The JDBC bootstrap policy must not be null.");
        }
    }
}
