/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.hikari;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.jdbc.JdbcConnectionPool;
import io.helidon.dbclient.jdbc.spi.JdbcCpExtensionProvider;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class HikariConnectionPool implements JdbcConnectionPool {

    // JDBC database URL prefix.
    private static final String JDBC_URL_PREFIX = "jdbc";

    private final HikariDataSource dataSource;
    private final String dbType;
    private final String serviceName;

    private HikariConnectionPool(Builder builder, String dbType) {
        this.dbType = dbType;
        HikariConfig config = new HikariConfig(builder.properties());
        config.setJdbcUrl(builder.url());
        config.setUsername(builder.username());
        config.setPassword(builder.password());
        // Apply configuration update from extensions
        builder.extensions()
                .forEach(interceptor -> interceptor.metricRegistry(config::setMetricRegistry));
        this.dataSource = new HikariDataSource(config);
        this.serviceName = builder.serviceName();
    }

    @Override
    public String name() {
        return serviceName;
    }

    @Override
    public String type() {
        return "hikari";
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

    static Builder builder(List<JdbcCpExtensionProvider> extensions) {
        return new Builder(extensions);
    }

    /**
     * Fluent API builder for {@link JdbcConnectionPool}.
     * The builder will produce a connection pool based on Hikari connection pool and will support
     * {@link io.helidon.dbclient.jdbc.spi.JdbcCpExtensionProvider} to enhance the Hikari pool.
     */
    static final class Builder extends JdbcConnectionPool.BuilderBase<Builder, HikariConnectionPool> {

        //jdbc:mysql://127.0.0.1:3306/pokemon?useSSL=false
        private static final Pattern URL_PATTERN = Pattern.compile("(\\w+:\\w+):.*");

        private Builder(List<JdbcCpExtensionProvider> extensions) {
            super(extensions);
        }

        @Override
        public HikariConnectionPool build() {
            final Matcher matcher = URL_PATTERN.matcher(url());
            String dbType = matcher.matches()
                    ? matcher.group(1)
                    : JDBC_URL_PREFIX;

            return new HikariConnectionPool(this, dbType);
        }

    }

}
