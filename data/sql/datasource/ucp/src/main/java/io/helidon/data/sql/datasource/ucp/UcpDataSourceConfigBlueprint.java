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
package io.helidon.data.sql.datasource.ucp;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.datasource.ProviderConfig;

/**
 * UCP specific configuration for {@link javax.sql.DataSource}.
 * <p>Properties are limited to {@link Map}{@code <}{@link String}{@code ,}{@link String}{@code >}.
 * {@link oracle.ucp.jdbc.PoolDataSource#setHostnameResolver(oracle.ucp.jdbc.PoolDataSource.HostnameResolver)},
 * {@link oracle.ucp.jdbc.PoolDataSource#setSSLContext(javax.net.ssl.SSLContext)}
 * and {@link oracle.ucp.jdbc.PoolDataSource#setTokenSupplier(java.util.function.Supplier)}
 * methods are not supported.
 * <p>Oracle Database 23ai compliant config.
 */
@Prototype.Blueprint
@Prototype.Configured(root = false)
interface UcpDataSourceConfigBlueprint extends ConnectionConfig, ProviderConfig {

    /**
     * Type of this provider.
     *
     * @return provider type - {@value UcpDataSourceProviderService#PROVIDER_TYPE}
     */
    @Override
    default String type() {
        return UcpDataSourceProviderService.PROVIDER_TYPE;
    }

    /**
     * Name of this provider.
     *
     * @return the provider name
     */
    @Override
    String name();

    /**
     * Support for distributed transactions.
     * {@link oracle.ucp.jdbc.PoolXADataSource} instance is returned when {@code true},
     * {@link oracle.ucp.jdbc.PoolDataSource} instance is returned when {@code false}.
     * Default value is {@code false}.
     *
     * @return whether distributed transactions are supported.
     */
    @Option.Configured
    Optional<Boolean> xaDataSource();

    /**
     * Sets the abandoned connection timeout.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setAbandonedConnectionTimeout(int)} for details.
     *
     * @return the abandoned connection timeout.
     */
    @Option.Configured
    Optional<Integer> abandonedConnectionTimeout();

    /**
     * Sets the {@code boolean} value for the property that controls the behavior of UCP
     * when a connection is released back to the pool with pending uncommitted changes
     * in an active transaction.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setCommitOnConnectionReturn(boolean)} for details.
     *
     * @return {@code true} to commit, {@code false} to rollback, default value is {@code true}.
     */
    @Option.Configured
    Optional<Boolean> commitOnConnectionReturn();

    /**
     * Sets the connection factory class name.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setConnectionFactoryClassName(String)} for details.
     *
     * @return the connection factory class name
     */
    @Option.Configured
    Optional<String> connectionFactoryClassName();

    /**
     * Sets the connection factory properties on the connection factory.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setConnectionFactoryProperties(java.util.Properties)} for details.
     *
     * @return the connection factory properties
     */
    @Option.Configured
    Optional<Map<String, String>> connectionFactoryProperties();

    /**
     * Sets the maximum number of connections that may be harvested when the connection harvesting
     * occurs.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setConnectionHarvestMaxCount(int)} for details.
     *
     * @return the maximum number of connections that may be harvested
     */
    @Option.Configured
    Optional<Integer> connectionHarvestMaxCount();

    /**
     * Sets the number of available connections below which the connection pool's connection harvesting
     * will occur.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setConnectionHarvestTriggerCount(int)} for details.
     *
     * @return the number of available connections below which the connection harvesting will occur
     */
    @Option.Configured
    Optional<Integer> connectionHarvestTriggerCount();

    /**
     * Sets the cost value which identifies a connection as "high-cost" for connection labeling.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setConnectionLabelingHighCost(int)} for details.
     *
     * @return the cost value
     */
    @Option.Configured
    Optional<Integer> connectionLabelingHighCost();

    /**
     * Sets the connection pool name.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setConnectionPoolName(String)} for details.
     *
     * @return the connection pool name
     */
    @Option.Configured
    Optional<String> connectionPoolName();

    /**
     * Sets the connection properties on the connection factory.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setConnectionProperties(java.util.Properties)} for details.
     *
     * @return the connection properties
     */
    @Option.Configured
    Optional<Map<String, String>> connectionProperties();

    /**
     * Sets the connection repurpose threshold for the pool.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setConnectionRepurposeThreshold(int)} for details.
     *
     * @return the connection repurpose threshold
     */
    @Option.Configured
    Optional<Integer> connectionRepurposeThreshold();

    /**
     * Sets the connection validation timeout in seconds.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setConnectionValidationTimeout(int)} for details.
     *
     * @return the connection validation timeout
     */
    @Option.Configured
    Optional<Integer> connectionValidationTimeout();

    /**
     * Configures how much time a connection request call may wait before it either successfully
     * returns a connection or throws an exception.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setConnectionWaitDuration(java.time.Duration)} for details.
     *
     * @return the connection wait duration
     */
    @Option.Configured
    Optional<Duration> connectionWaitDuration();

    /**
     * Set this flag to {@code true} to make UCP use the borrowing thread to create new connections.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setCreateConnectionInBorrowThread(boolean)} for details.
     *
     * @return whether to use the borrowing thread to create new connections
     */
    @Option.Configured
    Optional<Boolean> createConnectionInBorrowThread();

    /**
     * Sets the database name.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setDatabaseName(String)} for details.
     *
     * @return the database name
     */
    @Option.Configured
    Optional<String> databaseName();

    /**
     * Sets the data source name.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setDataSourceName(String)} for details.
     *
     * @return the data source name
     */
    @Option.Configured
    Optional<String> dataSourceName();

    /**
     * Sets the data source description.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setDescription(String)} for details.
     *
     * @return the data source description
     */
    @Option.Configured
    Optional<String> description();

    /**
     * Enables Fast Connection Failover (FCF) for the connection pool accessed using this pool-enabled data source.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setFastConnectionFailoverEnabled(boolean)} for details.
     *
     * @return whether the Fast Connection Failover (FCF) is enabled
     */
    @Option.Configured
    Optional<Boolean> fastConnectionFailoverEnabled();

    /**
     * Sets the high-cost connection reuse threshold for connection labeling.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setHighCostConnectionReuseThreshold(int)} for details.
     *
     * @return the high-cost connection reuse threshold
     */
    @Option.Configured
    Optional<Integer> highCostConnectionReuseThreshold();

    /**
     * Sets the inactive connection timeout.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setInactiveConnectionTimeout(int)} for details.
     *
     * @return the inactive connection timeout
     */
    @Option.Configured
    Optional<Integer> inactiveConnectionTimeout();

    /**
     * Sets the initial pool size.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setInitialPoolSize(int)} for details.
     *
     * @return the initial pool size
     */
    @Option.Configured
    Optional<Integer> initialPoolSize();

    /**
     * Sets the maximum connection reuse count.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setMaxConnectionReuseCount(int)} for details.
     *
     * @return the maximum connection reuse count
     */
    @Option.Configured
    Optional<Integer> maxConnectionReuseCount();

    /**
     * Sets the maximum connection reuse time in seconds.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setMaxConnectionReuseTime(long)} for details.
     *
     * @return the maximum connection reuse time
     */
    @Option.Configured
    Optional<Long> maxConnectionReuseTime();

    /**
     * Sets the max number of connections that can be created per shard from this connection pool.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setMaxConnectionsPerShard(int)} for details.
     *
     * @return the max number of connections to be created
     */
    @Option.Configured
    Optional<Integer> maxConnectionsPerShard();

    /**
     * Sets the maximum idle time for available connections in the pool in seconds.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setMaxIdleTime(int)} for details.
     *
     * @return the maximum idle time
     */
    @Option.Configured
    Optional<Integer> maxIdleTime();

    /**
     * Sets the maximum number of connections.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setMaxPoolSize(int)} for details.
     *
     * @return the maximum number of connections
     */
    @Option.Configured
    Optional<Integer> maxPoolSize();

    /**
     * Sets the minimum number of connections.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setMinPoolSize(int)} for details.
     *
     * @return the minimum number of connections
     */
    @Option.Configured
    Optional<Integer> minPoolSize();

    /**
     * Sets the maximum number of statements that may be pooled or cached on a connection.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setMaxStatements(int)} for details.
     *
     * @return the maximum number of statements
     */
    @Option.Configured
    Optional<Integer> maxStatements();

    /**
     * Sets the minimum number of idle connections.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setMinIdle(int)} for details.
     *
     * @return the minimum number of idle connections
     */
    @Option.Configured
    Optional<Integer> minIdle();

    /**
     * Sets the data source network protocol.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setNetworkProtocol(String)} for details.
     *
     * @return the data source network protocol
     */
    @Option.Configured
    Optional<String> networkProtocol();

    /**
     * Sets the configuration string used for remote ONS subscription.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setONSConfiguration(String)} for details.
     *
     * @return the configuration string
     */
    @Option.Configured
    Optional<String> onsConfiguration();

    /**
     * Sets the database port number.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setPortNumber(int)} for details.
     *
     * @return the database port number
     */
    @Option.Configured
    Optional<Integer> portNumber();

    /**
     * Sets the property cycle in seconds.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setPropertyCycle(int)} for details.
     *
     * @return the property cycle
     */
    @Option.Configured
    Optional<Integer> propertyCycle();

    /**
     * Sets the number of seconds the driver will wait for a Statement object to execute
     * to the given number of seconds.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setQueryTimeout(int)} for details.
     *
     * @return the query timeout
     */
    @Option.Configured
    Optional<Integer> queryTimeout();

    /**
     * Sets the read-only instance allowed value on the datasource.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setReadOnlyInstanceAllowed(boolean)} for details.
     *
     * @return the read-only instance allowed value
     */
    @Option.Configured
    Optional<Boolean> readOnlyInstanceAllowed();

    /**
     * Sets the data source role name.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setRoleName(String)} for details.
     *
     * @return the data source role name
     */
    @Option.Configured
    Optional<String> roleName();

    /**
     * Sets the time to trust an idle connection to skip a validation test in seconds.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setSecondsToTrustIdleConnection(int)} for details.
     *
     * @return the time in seconds
     */
    @Option.Configured
    Optional<Integer> secondsToTrustIdleConnection();

    /**
     * Sets the database server name.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setServerName(String)} for details.
     *
     * @return the database server name
     */
    @Option.Configured
    Optional<String> serverName();

    /**
     * Change the mode of UCP when UCP is using a Sharded Database.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setShardingMode(boolean)} for details.
     *
     * @return change the sharding mode
     */
    @Option.Configured
    Optional<Boolean> shardingMode();

    /**
     * Sets the SQL statement to validate the database connection.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setSQLForValidateConnection(String)} for details.
     *
     * @return the SQL statement
     */
    @Option.Configured
    Optional<String> sqlForValidateConnection();

    /**
     * Sets the timeout check interval in seconds.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setTimeoutCheckInterval(int)} for details.
     *
     * @return the timeout check interval
     */
    @Option.Configured
    Optional<Integer> timeoutCheckInterval();

    /**
     * Sets the maximum time a connection may remain in-use in seconds.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setTimeToLiveConnectionTimeout(int)} for details.
     *
     * @return the maximum time a connection may remain in-use
     */
    @Option.Configured
    Optional<Integer> timeToLiveConnectionTimeout();

    /**
     * Makes the pool validate the connection before returning it to the user by calling the JDBC API {@code isValid}.
     * See {@link oracle.ucp.jdbc.PoolDataSource#setValidateConnectionOnBorrow(boolean)} for details.
     *
     * @return whether to validate the connection before returning it to the user
     */
    @Option.Configured
    Optional<Boolean> validateConnectionOnBorrow();

}
