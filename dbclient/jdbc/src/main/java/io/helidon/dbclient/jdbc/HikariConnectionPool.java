/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
import java.util.List;

import io.helidon.dbclient.DbClientException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Hikari Connection Pool integration.
 */
public class HikariConnectionPool implements ConnectionPool {
    /** Hikari Connection Pool instance. */
    private final HikariDataSource dataSource;

    /** The type of this database. */
    private final String dbType;

    HikariConnectionPool(Builder builder, String dbType, List<HikariCpExtension> extensions) {
        this.dbType = dbType;
        HikariConfig config = new HikariConfig(builder.properties());
        config.setJdbcUrl(builder.url());
        config.setUsername(builder.username());
        config.setPassword(builder.password());
        // Apply configuration update from extensions
        extensions.forEach(interceptor -> {
            interceptor.configure(config);
        });
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
