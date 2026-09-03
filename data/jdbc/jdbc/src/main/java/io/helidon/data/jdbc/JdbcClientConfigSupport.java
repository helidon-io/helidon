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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.data.DataException;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.service.registry.Service;

/**
 * Validation support for JDBC client configuration.
 */
final class JdbcClientConfigSupport {

    private JdbcClientConfigSupport() {
    }

    /**
     * Validates a JDBC client configuration at the runtime boundary.
     *
     * @param config configuration to validate
     */
    static void validate(JdbcClientConfig config) {
        validate(config.name(), config.connection(), config.dataSource());
        cachePolicy(config);
    }

    /**
     * Validates an effective list of JDBC client configurations.
     *
     * @param configurations configurations to validate
     */
    static void validateAll(Iterable<JdbcClientConfig> configurations) {
        Set<String> names = new HashSet<>();
        for (JdbcClientConfig config : configurations) {
            validate(config);
            if (!names.add(config.name())) {
                if (Service.Named.DEFAULT_NAME.equals(config.name())) {
                    throw new DataException("The Default JDBC Client is configured more than once.");
                }
                throw new DataException("More than one JDBC client configuration uses the name '"
                                                + config.name() + "'.");
            }
        }
    }

    /**
     * Returns a safe description for a client name.
     *
     * @param name client name
     * @return safe client description
     */
    static String clientDescription(String name) {
        return Service.Named.DEFAULT_NAME.equals(name)
                ? "Default JDBC Client"
                : "JDBC client '" + name + "'";
    }

    /**
     * Creates the immutable cache policy for one JDBC client.
     *
     * @param config client configuration
     * @return validated cache policy
     */
    static JdbcClientImpl.CachePolicy cachePolicy(JdbcClientConfig config) {
        return cachePolicy(config.parameterCountCacheCapacity(), config.parameterCountCacheMaxSqlLength());
    }

    private static void validate(String name,
                                 Optional<ConnectionConfig> connection,
                                 Optional<String> dataSource) {
        if (name.isBlank()) {
            throw new DataException("A JDBC client name must not be blank.");
        }
        int sourceCount = connection.isPresent() ? 1 : 0;
        sourceCount += dataSource.isPresent() ? 1 : 0;
        if (sourceCount != 1) {
            throw new DataException("A JDBC client requires exactly one connection source.");
        }
        if (dataSource.filter(String::isBlank).isPresent()) {
            throw new DataException("A JDBC data source name must not be blank.");
        }
    }

    private static JdbcClientImpl.CachePolicy cachePolicy(int capacity, int maxSqlLength) {
        return new JdbcClientImpl.CachePolicy(capacity, maxSqlLength);
    }

    /**
     * Validates client configuration supplied by the application.
     */
    static final class Decorator implements Prototype.BuilderDecorator<JdbcClientConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(JdbcClientConfig.BuilderBase<?, ?> builder) {
            validate(builder.name(), builder.connection(), builder.dataSource());
            cachePolicy(builder.parameterCountCacheCapacity(), builder.parameterCountCacheMaxSqlLength());
        }
    }
}
