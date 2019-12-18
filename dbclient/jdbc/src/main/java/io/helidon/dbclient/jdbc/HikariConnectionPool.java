/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.sql.SQLException;
import java.util.Properties;

import io.helidon.dbclient.DbClientException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Hikari Connection Pool integration.
 */
public class HikariConnectionPool implements ConnectionPool {

    /** Default connection pool name. */
    private static final String DEFAULT_NAME = "Helidon JDBC Connection Pool";

    /** Properties prefix. */
    private static final String PROPERTIES_PREFIX = "dataSource.";

    /** Hikari Connection Pool instance. */
    private final HikariDataSource dataSource;

    /** The type of this database. */
    private final String dbType;

    /**
     * Creates an instance of Hikari Connection Pool from common connection pool builder.
     *
     * @param url database connection URL
     * @param username database connection user name
     * @param password database connection password
     * @param properties additional connection pool properties (names without {@code dataSource.} prefix)
     * @param dbType database type
     */
    HikariConnectionPool(
            final String url,
            final String username,
            final String password,
            final Properties properties,
            final String dbType
    ) {
        this.dbType = dbType;
        final HikariConfig config = new HikariConfig(properties);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection connection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException ex) {
            throw new DbClientException(
                    String.format("Failed to create a connection to %s", dataSource.getJdbcUrl()), ex);
        }
    }

    @Override
    public String dbType() {
        return dbType;
    }

}
