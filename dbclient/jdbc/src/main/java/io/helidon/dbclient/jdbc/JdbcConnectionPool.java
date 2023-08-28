/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.common.config.NamedService;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.jdbc.spi.JdbcConnectionPoolProvider;

/**
 * JDBC connection pool.
 */
@FunctionalInterface
public interface JdbcConnectionPool extends NamedService {

    /** Default JDBC connection pool {@link #dbType()} value. */
    String DEFAULT_DB_TYPE = "jdbc";

    /**
     * Create a JDBC connection pool from provided configuration.
     * <table>
     * <caption>Optional configuration parameters</caption>
     * <tr>
     *     <th>key</th>
     *     <th>default value</th>
     *     <th>description</th>
     * </tr>
     * <tr>
     *     <td>url</td>
     *     <td>&nbsp;</td>
     *     <td>JDBC URL of the database - this property is required when only configuration is used.
     *              Example: {@code jdbc:mysql://127.0.0.1:3306/pokemon?useSSL=false}</td>
     * </tr>
     * <tr>
     *     <td>username</td>
     *     <td>&nbsp;</td>
     *     <td>Username used to connect to the database</td>
     * </tr>
     * <tr>
     *     <td>password</td>
     *     <td>&nbsp;</td>
     *     <td>Password used to connect to the database</td>
     * </tr>
     * </table>
     *
     * @param config configuration of connection pool
     * @return a new instance configured from the provided config
     */
    static JdbcConnectionPool create(Config config) {
        List<JdbcConnectionPoolProvider> poolProviders = HelidonServiceLoader
                .create(ServiceLoader.load(JdbcConnectionPoolProvider.class))
                .asList();
        if (poolProviders.isEmpty()) {
            throw new DbClientException("No JDBC connection pool provider is available");
        }
        return poolProviders.getFirst().create(config, config.name());
    }

    /**
     * Return a connection from the pool.
     * The call to {@link java.sql.Connection#close()} should return that connection to the pool.
     * The connection pool should handle capacity issues and timeouts using unchecked exceptions thrown by this method.
     *
     * @return a connection read to execute statements
     */
    Connection connection();

    /**
     * The type of this database. This could be {@code "jdbc:mysql"}, etc.
     * Default value is just {@code "jdbc"} but implementing class should set something more specific.
     *
     * @return type of this database
     */
    default String dbType() {
        return DEFAULT_DB_TYPE;
    }

    @Override
    default String name() {
        return "inlined";
    }

    @Override
    default String type() {
        return "inlined";
    }

    /**
     * Base fluent API builder for {@link JdbcConnectionPool}.
     * The builder will produce a connection pool based on connection pool provider available on the classpath.
     *
     * @param <B> Type of the builder
     * @param <T> Type of the built instance
     */
    abstract class BuilderBase<B extends BuilderBase<B, T>, T extends JdbcConnectionPool>
            implements io.helidon.common.Builder<B, T> {
        /**
         * Database connection URL configuration key.
         */
        protected static final String URL = "url";
        /**
         * Database connection username configuration key.
         */
        protected static final String USERNAME = "username";
        /**
         * Database connection user password configuration key.
         */
        protected static final String PASSWORD = "password";

        /**
         * Database connection configuration key for Helidon specific
         * properties.
         */
        protected static final String HELIDON_RESERVED_CONFIG_KEY = "helidon";

        private Properties properties = new Properties();
        private String serviceName;
        private String url;
        private String username;
        private String password;

        protected BuilderBase() {
        }

        /**
         * Update builder from configuration.
         *
         * @param config configuration
         * @return updated builder
         */
        public B config(Config config) {
            Map<String, String> poolConfig = config.detach().asMap().get();
            poolConfig.forEach((key, value) -> {
                switch (key) {
                    case URL -> url(value);
                    case USERNAME -> username(value);
                    case PASSWORD -> password(value);
                    default -> {
                        if (!key.startsWith(HELIDON_RESERVED_CONFIG_KEY + ".")) {
                            // all other properties are sent to the pool
                            properties().setProperty(key, value);
                        }
                    }
                }
            });
            return identity();
        }

        /**
         * Connection pool URL string.
         *
         * @param url connection pool string to use
         * @return updated builder
         */
        public B url(String url) {
            this.url = url;
            return identity();
        }

        /**
         * Connection pool username.
         *
         * @param username username to use
         * @return updated builder
         */
        public B username(String username) {
            this.username = username;
            return identity();
        }

        /**
         * Connection pool password.
         *
         * @param password password to use
         * @return updated builder
         */
        public B password(String password) {
            this.password = password;
            return identity();
        }

        /**
         * Configure connection pool properties.
         *
         * @param properties properties to use
         * @return updated builder
         */
        public B properties(Properties properties) {
            this.properties = properties;
            return identity();
        }

        /**
         * Name of the connection pool.
         * This value is returned as {@link io.helidon.common.config.NamedService#name()} value.
         *
         * @param serviceName service name
         * @return updated builder
         */
        public B serviceName(String serviceName) {
            this.serviceName = serviceName;
            return identity();
        }

        /**
         * Configured connection pool service name.
         *
         * @return service name
         */
        public String serviceName() {
            return serviceName;
        }

        /**
         * Configured connection pool URL string.
         *
         * @return URL {@link String}
         */
        public String url() {
            return url;
        }

        /**
         * Configured connection pool username.
         *
         * @return username {@link String}
         */
        public String username() {
            return username;
        }

        /**
         * Configured connection pool password.
         *
         * @return password {@link String}
         */
        public String password() {
            return password;
        }

        /**
         * Configured connection pool properties.
         *
         * @return connection pool properties
         */
        public Properties properties() {
            return properties;
        }

    }

}
