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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import io.helidon.builder.api.Prototype;
import io.helidon.data.DataException;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.service.registry.Service;

/**
 * Custom methods for the JDBC client configuration builder.
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
        Objects.requireNonNull(config, "The JDBC client configuration must not be null.");
        validate(config.name(), config.connection(), config.dataSource(), config.dataSourceInstance());
    }

    /**
     * Validates an effective list of JDBC client configurations.
     *
     * @param configurations configurations to validate
     */
    static void validateAll(Iterable<JdbcClientConfig> configurations) {
        Objects.requireNonNull(configurations, "The JDBC client configurations must not be null.");
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

    private static void validate(String name,
                                 Optional<ConnectionConfig> connection,
                                 Optional<String> dataSource,
                                 Optional<DataSource> dataSourceInstance) {
        Objects.requireNonNull(name, "The JDBC client name must not be null.");
        Objects.requireNonNull(connection, "The direct JDBC connection configuration must not be null.");
        Objects.requireNonNull(dataSource, "The data source name configuration must not be null.");
        Objects.requireNonNull(dataSourceInstance, "The existing data source configuration must not be null.");
        if (name.isBlank()) {
            throw new DataException("A JDBC client name must not be blank.");
        }
        int sourceCount = connection.isPresent() ? 1 : 0;
        sourceCount += dataSource.isPresent() ? 1 : 0;
        sourceCount += dataSourceInstance.isPresent() ? 1 : 0;
        if (sourceCount != 1) {
            throw new DataException("A JDBC client requires exactly one connection source.");
        }
        if (dataSource.filter(String::isBlank).isPresent()) {
            throw new DataException("A JDBC data source name must not be blank.");
        }
        if (connection.isPresent()) {
            ConnectionConfig connectionConfig = connection.get();
            String url = Objects.requireNonNull(connectionConfig.url(),
                                                "The direct JDBC connection URL must not be null.");
            if (url.isBlank()) {
                throw new DataException("The direct JDBC connection URL must not be blank.");
            }
            Objects.requireNonNull(connectionConfig.username(),
                                   "The JDBC username configuration must not be null.");
            Objects.requireNonNull(connectionConfig.password(),
                                   "The JDBC password configuration must not be null.");
            Optional<String> driverClassName = Objects.requireNonNull(
                    connectionConfig.jdbcDriverClassName(),
                    "The JDBC driver class name configuration must not be null.");
            if (driverClassName.filter(String::isBlank).isPresent()) {
                throw new DataException("The JDBC driver class name must not be blank.");
            }
        }
    }

    /**
     * Methods copied to the generated builder.
     */
    static final class CustomMethods {

        private CustomMethods() {
        }

        /**
         * Supplies an existing data source for direct client construction.
         *
         * @param builder builder to update
         * @param dataSource existing data source
         */
        @Prototype.BuilderMethod
        static void dataSource(JdbcClientConfig.BuilderBase<?, ?> builder, DataSource dataSource) {
            builder.dataSourceInstance(Objects.requireNonNull(dataSource, "The data source must not be null."));
        }
    }

    /**
     * Validates the connection source selected by the application.
     */
    static final class Decorator implements Prototype.BuilderDecorator<JdbcClientConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(JdbcClientConfig.BuilderBase<?, ?> builder) {
            validate(builder.name(), builder.connection(), builder.dataSource(), builder.dataSourceInstance());
        }
    }
}
