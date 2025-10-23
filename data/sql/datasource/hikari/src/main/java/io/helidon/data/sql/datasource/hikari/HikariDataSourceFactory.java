/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.data.sql.datasource.hikari;

import javax.sql.DataSource;

import io.helidon.common.config.Config;
import io.helidon.data.sql.datasource.TransactionIsolation;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Hikari {@link javax.sql.DataSource} factory.
 * Builds Hikari {@link DataSource} instance from config.
 */
public class HikariDataSourceFactory {

    private final HikariDataSourceConfig dataSourceConfig;
    private final HikariConfig hikariConfig;

    // Factory class instance keeps config and DataSource context to let us use method references.
    private HikariDataSourceFactory(HikariDataSourceConfig dataSourceConfig) {
        this.dataSourceConfig = dataSourceConfig;
        this.hikariConfig = new HikariConfig();
    }

    /**
     * Create new instance of UCP {@link javax.sql.DataSource}.
     *
     * @param config UCP {@link javax.sql.DataSource} specific configuration node.
     * @return new instance of UCP {@link javax.sql.DataSource}
     */
    public static DataSource create(Config config) {
        return create(HikariDataSourceConfig.create(config));
    }

    /**
     * Create new instance of UCP {@link DataSource}.
     *
     * @param dataSourceConfig UCP {@link DataSource} specific configuration.
     * @return new instance of UCP {@link DataSource}
     */
    public static DataSource create(HikariDataSourceConfig dataSourceConfig) {
        return new HikariDataSourceFactory(dataSourceConfig).create();
    }

    private DataSource create() {
        dataSourceConfig.allowPoolSuspension().ifPresent(hikariConfig::setAllowPoolSuspension);
        dataSourceConfig.autoCommit().ifPresent(hikariConfig::setAutoCommit);
        dataSourceConfig.catalog().ifPresent(hikariConfig::setCatalog);
        dataSourceConfig.connectionInitSql().ifPresent(hikariConfig::setConnectionInitSql);
        dataSourceConfig.connectionTestQuery().ifPresent(hikariConfig::setConnectionTestQuery);
        dataSourceConfig.connectionTimeout().ifPresent(hikariConfig::setConnectionTimeout);
        hikariConfig.setJdbcUrl(dataSourceConfig.url());
        dataSourceConfig.jdbcDriverClassName().ifPresent(hikariConfig::setDriverClassName);
        dataSourceConfig.healthCheckProperties().forEach(hikariConfig::addHealthCheckProperty);
        dataSourceConfig.idleTimeout().ifPresent(hikariConfig::setIdleTimeout);
        dataSourceConfig.initializationFailTimeout().ifPresent(hikariConfig::setInitializationFailTimeout);
        dataSourceConfig.isolateInternalQueries().ifPresent(hikariConfig::setIsolateInternalQueries);
        dataSourceConfig.keepaliveTime().ifPresent(hikariConfig::setKeepaliveTime);
        dataSourceConfig.leakDetectionThreshold().ifPresent(hikariConfig::setLeakDetectionThreshold);
        dataSourceConfig.maximumPoolSize().ifPresent(hikariConfig::setMaximumPoolSize);
        dataSourceConfig.maxLifetime().ifPresent(hikariConfig::setMaxLifetime);
        dataSourceConfig.minimumIdle().ifPresent(hikariConfig::setMinimumIdle);
        dataSourceConfig.password().ifPresent(this::setPassword);
        dataSourceConfig.poolName().ifPresent(hikariConfig::setPoolName);
        dataSourceConfig.properties().forEach(hikariConfig::addDataSourceProperty);
        dataSourceConfig.readOnly().ifPresent(hikariConfig::setReadOnly);
        dataSourceConfig.registerMbeans().ifPresent(hikariConfig::setRegisterMbeans);
        dataSourceConfig.schema().ifPresent(hikariConfig::setSchema);
        dataSourceConfig.transactionIsolation()
                .map(TransactionIsolation::name)
                .ifPresent(hikariConfig::setTransactionIsolation);
        dataSourceConfig.validationTimeout().ifPresent(hikariConfig::setValidationTimeout);
        dataSourceConfig.username().ifPresent(hikariConfig::setUsername);
        return new HikariDataSource(hikariConfig);
    }

    private void setPassword(char[] password) {
        hikariConfig.setPassword(new String(password));
    }

}
