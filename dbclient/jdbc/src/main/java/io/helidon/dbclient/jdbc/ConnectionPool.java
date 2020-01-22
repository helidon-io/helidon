/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.config.Config;

/**
 * JDBC Configuration parameters.
 */
@FunctionalInterface
public interface ConnectionPool {

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
    static ConnectionPool create(Config config) {
        return ConnectionPool.builder()
                .config(config)
                .build();
    }

    /**
     * Create a fluent API builder for a JDBC Connection pool based on URL, username and password.
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
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
     * The type of this database - if better details than {@value JdbcDbClientProvider#JDBC_DB_TYPE} is
     * available, return it. This could be "jdbc:mysql" etc.
     *
     * @return type of this database
     */
    default String dbType() {
        return JdbcDbClientProvider.JDBC_DB_TYPE;
    }

    /**
     * Fluent API builder for {@link io.helidon.dbclient.jdbc.ConnectionPool}.
     */
    final class Builder implements io.helidon.common.Builder<ConnectionPool> {
        //jdbc:mysql://127.0.0.1:3306/pokemon?useSSL=false
        private static final Pattern URL_PATTERN = Pattern.compile("(\\w+:\\w+):.*");
        private String url;
        private String username;
        private String password;
        private Properties properties;

        private Builder() {
        }

        @Override
        public ConnectionPool build() {
            final Matcher matcher = URL_PATTERN.matcher(url);
            String dbType = matcher.matches()
                    ? matcher.group(1)
                    : JdbcDbClientProvider.JDBC_DB_TYPE;
            return new HikariConnectionPool(url, username, password, properties, dbType);
        }

        public Builder config(Config config) {
            Map<String, String> poolConfig = config.detach().asMap().get();
            properties = new Properties();
            poolConfig.forEach((key, value) -> {
                switch (key) {
                    case "url":
                        url(value);
                        break;
                    case "username":
                        username(value);
                        break;
                    case "password":
                        password(value);
                        break;
                    default:
                        properties.put(key, value);
                }
            });
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder properties(Properties properties) {
            this.properties = properties;
            return this;
        }

    }

}
