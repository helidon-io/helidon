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

import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.data.sql.datasource.ProviderConfig;
import io.helidon.data.sql.datasource.common.DataSourceProviderConfig;
import io.helidon.data.sql.datasource.spi.DataSourceConfigProvider;

@Prototype.Blueprint
@Prototype.Configured(root = false, value = HikariDataSourceProviderService.PROVIDER_TYPE)
@Prototype.Provides(DataSourceConfigProvider.class)
interface HikariDataSourceConfigBlueprint extends DataSourceProviderConfig, ProviderConfig {

    /**
     * Type of this provider.
     *
     * @return provider type - {@value HikariDataSourceProviderService#PROVIDER_TYPE}
     */
    @Override
    default String type() {
        return HikariDataSourceProviderService.PROVIDER_TYPE;
    }

    /**
     * Set whether pool suspension is allowed.
     * See {@link com.zaxxer.hikari.HikariConfig#setAllowPoolSuspension(boolean)} for details.
     *
     * @return the desired pool suspension allowance
     */
    @Option.Configured
    Optional<Boolean> allowPoolSuspension();

    /**
     * Set the default auto-commit behavior of connections in the pool.
     * See {@link com.zaxxer.hikari.HikariConfig#setAutoCommit(boolean)} for details.
     *
     * @return the desired auto-commit default for connections
     */
    @Option.Configured
    Optional<Boolean> autoCommit();

    /**
     * Set the default catalog name to be set on connections.
     * See {@link com.zaxxer.hikari.HikariConfig#setCatalog(String)} for details.
     *
     * @return the default catalog name
     */
    @Option.Configured
    Optional<String> catalog();

    /**
     * Set the SQL string that will be executed on all new connections when they are created,
     * before they are added to the pool.
     * See {@link com.zaxxer.hikari.HikariConfig#setConnectionInitSql(String)} for details.
     *
     * @return the SQL to execute on new connections
     */
    @Option.Configured
    Optional<String> connectionInitSql();

    /**
     * Set the SQL query to be executed to test the validity of connections.
     * See {@link com.zaxxer.hikari.HikariConfig#setConnectionTestQuery(String)} for details.
     *
     * @return a SQL query string
     */
    @Option.Configured
    Optional<String> connectionTestQuery();

    /**
     * Set the maximum number of milliseconds that a client will wait for a connection from the pool.
     * See {@link com.zaxxer.hikari.HikariConfig#setConnectionTimeout(long)} for details.
     *
     * @return the connection timeout in milliseconds
     */
    @Option.Configured
    Optional<Long> connectionTimeout();

    /**
     * Set JDBC driver class name.
     * See {@link com.zaxxer.hikari.HikariConfig#setDriverClassName(String)} for details.
     *
     * @return the JDBC driver class name
     */
    @Option.Configured
    Optional<String> driverClassName();

    /**
     * Add properties (name/value pair) that will be used to configure the connection pool health check.
     * See {@link com.zaxxer.hikari.HikariConfig#addHealthCheckProperty(String, String)} for details.
     *
     * @return the health check properties
     */
    @Option.Configured
    @Option.Singular("healthCheckProperty")
    Map<String, String> healthCheckProperties();

    /**
     * This property controls the maximum amount of time that a connection is allowed to sit idle in the pool.
     * See {@link com.zaxxer.hikari.HikariConfig#setIdleTimeout(long)} for details.
     *
     * @return the idle timeout in milliseconds
     */
    @Option.Configured
    Optional<Long> idleTimeout();

    /**
     * Set the pool initialization failure timeout.
     * See {@link com.zaxxer.hikari.HikariConfig#setInitializationFailTimeout(long)} for details.
     *
     * @return the number of milliseconds before the pool initialization fails, or {@code 0}
     *         to validate connection setup but continue with pool start, or less than zero to skip
     *         all initialization checks and start the pool without delay
     */
    @Option.Configured
    Optional<Long> initializationFailTimeout();

    /**
     * Configure whether internal pool queries, principally aliveness checks, will be isolated
     * in their own transaction via {@link java.sql.Connection#rollback()}.
     * See {@link com.zaxxer.hikari.HikariConfig#setIsolateInternalQueries(boolean)} for details.
     *
     * @return {@code true} if internal pool queries should be isolated, {@code false} if not
     */
    @Option.Configured
    Optional<Boolean> isolateInternalQueries();

    /**
     * This property controls the keepalive interval for a connection in the pool.
     * See {@link com.zaxxer.hikari.HikariConfig#setKeepaliveTime(long)} for details.
     *
     * @return the interval in which connections will be tested for aliveness, thus
     *         keeping them alive by the act of checking. Value is in milliseconds.
     */
    @Option.Configured
    Optional<Long> keepaliveTime();

    /**
     * This property controls the amount of time that a connection can be out of the pool
     * before a message is logged indicating a possible connection leak.
     * See {@link com.zaxxer.hikari.HikariConfig#setLeakDetectionThreshold(long)} for details.
     *
     * @return the connection leak detection threshold in milliseconds
     */
    @Option.Configured
    Optional<Long> leakDetectionThreshold();

    /**
     * The property controls the maximum size that the pool is allowed to reach, including
     * both idle and in-use connections.
     * See {@link com.zaxxer.hikari.HikariConfig#setMaximumPoolSize(int)} for details.
     *
     * @return the maximum number of connections in the pool
     */
    @Option.Configured
    Optional<Integer> maximumPoolSize();

    /**
     * This property controls the maximum lifetime of a connection in the pool.
     * See {@link com.zaxxer.hikari.HikariConfig#setMaxLifetime(long)} for details.
     *
     * @return the maximum connection lifetime in milliseconds
     */
    @Option.Configured
    Optional<Long> maxLifetime();

    /**
     * The property controls the minimum number of idle connections that HikariCP tries to maintain
     * in the pool, including both idle and in-use connections.
     * See {@link com.zaxxer.hikari.HikariConfig#setMinimumIdle(int)} for details.
     *
     * @return the minimum number of idle connections in the pool to maintain
     */
    @Option.Configured
    Optional<Integer> minimumIdle();

    /**
     * Set the name of the connection pool.
     * See {@link com.zaxxer.hikari.HikariConfig#setPoolName(String)} for details.
     *
     * @return the name of the connection pool
     */
    @Option.Configured
    Optional<String> poolName();

    /**
     * Configures the Connections to be added to the pool as read-only Connections.
     * See {@link com.zaxxer.hikari.HikariConfig#setReadOnly(boolean)} for details.
     *
     * @return {@code true} if the Connections in the pool are read-only, {@code false} if not
     */
    @Option.Configured
    Optional<Boolean> readOnly();

    /**
     * Configures whether HikariCP self-registers the {@link com.zaxxer.hikari.HikariConfigMXBean}
     * and {@link com.zaxxer.hikari.HikariPoolMXBean} in JMX.
     * See {@link com.zaxxer.hikari.HikariConfig#setRegisterMbeans(boolean)} for details.
     *
     * @return {@code true} if HikariCP should register MXBeans, {@code false} if it should not
     */
    @Option.Configured
    Optional<Boolean> registerMbeans();

    /**
     * Set the default schema name to be set on connections.
     * See {@link com.zaxxer.hikari.HikariConfig#setSchema(String)} for details.
     *
     * @return the name of the default schema
     */
    @Option.Configured
    Optional<String> schema();

    /**
     * Set the default transaction isolation level. The specified value is the constant name
     * from the {@link java.sql.Connection} class, e.g. {@code TRANSACTION_REPEATABLE_READ}.
     * See {@link com.zaxxer.hikari.HikariConfig#setTransactionIsolation(String)} for details.
     *
     * @return the name of the isolation level
     */
    @Option.Configured
    Optional<String> transactionIsolation();

    /**
     * Sets the maximum number of milliseconds that the pool will wait for a connection to be validated as alive.
     * See {@link com.zaxxer.hikari.HikariConfig#setValidationTimeout(long)} for details.
     *
     * @return the validation timeout in milliseconds
     */
    @Option.Configured
    Optional<Long> validationTimeout();

    /**
     * Add properties (name/value pair) that will be used to configure the DataSource/Driver.
     * Property values are limited to {@link String} values.
     * See {@link com.zaxxer.hikari.HikariConfig#addDataSourceProperty(String, Object)} for details.
     *
     * @return the properties
     */
    @Option.Configured
    @Option.Singular("property")
    Map<String, String> properties();

/*
    Following HikariConfig methods were not added to the config.
     - DataSource: Nested datasource may require additional support to be implemented
     - Object instances can't be simply put into the config which is being read by Service.ServicesFactory

    void setDataSource(javax.sql.DataSource dataSource)
    void setDataSourceClassName(java.lang.String className)
    void setDataSourceJNDI(java.lang.String jndiDataSource)
    void setDataSourceProperties(java.util.Properties dsProperties)
    void setHealthCheckRegistry(java.lang.Object healthCheckRegistry)
    void setMetricRegistry(java.lang.Object metricRegistry)
    void setMetricsTrackerFactory(MetricsTrackerFactory metricsTrackerFactory)
    void setScheduledExecutor(java.util.concurrent.ScheduledExecutorService executor)
    void setScheduledExecutorService(java.util.concurrent.ScheduledThreadPoolExecutor executor)
    void setThreadFactory(java.util.concurrent.ThreadFactory threadFactory)
*/

}
