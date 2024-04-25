/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.hikari.spi.HikariMetricsProvider;
import io.helidon.dbclient.jdbc.CloseableJdbcConnectionPool;
import io.helidon.dbclient.jdbc.JdbcConnectionPool;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class HikariConnectionPool implements CloseableJdbcConnectionPool {

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
                .forEach(registry -> registry.register(config::setMetricRegistry));
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
    public void close() {
        dataSource.close();
    }

    @Override
    public String dbType() {
        return dbType;
    }

    static Builder builder() {
        return new Builder();
    }


    /**
     * Fluent API builder for {@link JdbcConnectionPool}.
     * The builder will produce a connection pool based on Hikari connection pool and will support
     * {@link io.helidon.dbclient.hikari.spi.HikariMetricsProvider} to enhance the Hikari pool.
     */
    static final class Builder extends JdbcConnectionPool.BuilderBase<Builder, HikariConnectionPool> {

        /**
         * Database connection configuration key for Hikari specific properties.
         */
        private static final String HIKARI_RESERVED_CONFIG_KEY = "hikari";

        //jdbc:mysql://127.0.0.1:3306/pokemon?useSSL=false
        private static final Pattern URL_PATTERN = Pattern.compile("(\\w+:\\w+):.*");

        private final List<HikariMetricsProvider> extensions;
        private Config extensionsConfig;


        private Builder() {
            super();
            this.extensions = HelidonServiceLoader
                    .create(ServiceLoader.load(HikariMetricsProvider.class))
                    .asList();
        }

        @Override
        public Builder config(Config config) {
            super.config(config);
            extensionsConfig = config.get(HIKARI_RESERVED_CONFIG_KEY);
            return this;
        }

        @Override
        public HikariConnectionPool build() {
            final Matcher matcher = URL_PATTERN.matcher(url());
            String dbType = matcher.matches()
                    ? matcher.group(1)
                    : DEFAULT_DB_TYPE;

            return new HikariConnectionPool(this, dbType);
        }

        /**
         * Loaded connection pool extensions providers.
         */
        public List<HikariMetricsRegistry> extensions() {
            if (null == extensionsConfig) {
                extensionsConfig = Config.empty();
            }
            return extensions.stream()
                    .map(provider -> provider.extension(extensionsConfig.get(provider.configKey())))
                    .collect(Collectors.toList());
        }

    }

}
