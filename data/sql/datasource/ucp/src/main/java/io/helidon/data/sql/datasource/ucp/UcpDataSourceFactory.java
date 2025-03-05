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

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import io.helidon.common.config.Config;
import io.helidon.data.DataException;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

/**
 * UCP {@link DataSource} factory.
 * Builds UCP {@link DataSource} instance from config.
 */
public class UcpDataSourceFactory {

    private final UcpDataSourceConfig dataSourceConfig;
    private final PoolDataSource dataSource;

    // Factory class instance keeps config and DataSource context to let us use method references.
    private UcpDataSourceFactory(UcpDataSourceConfig dataSourceConfig) {
        this.dataSourceConfig = dataSourceConfig;
        boolean xaDataSource = dataSourceConfig.xaDataSource().isPresent()
                ? dataSourceConfig.xaDataSource().get() : false;
        this.dataSource = xaDataSource
                ? PoolDataSourceFactory.getPoolXADataSource()
                : PoolDataSourceFactory.getPoolDataSource();
    }

    /**
     * Create new instance of UCP {@link DataSource}.
     *
     * @param config UCP {@link DataSource} specific configuration node.
     * @return new instance of UCP {@link DataSource}
     */
    public static DataSource create(Config config) {
        return create(UcpDataSourceConfig.create(config));
    }

    /**
     * Create new instance of UCP {@link DataSource}.
     *
     * @param dataSourceConfig UCP {@link DataSource} specific configuration.
     * @return new instance of UCP {@link DataSource}
     */
    public static DataSource create(UcpDataSourceConfig dataSourceConfig) {
        return new UcpDataSourceFactory(dataSourceConfig).create();
    }

    private DataSource create() {
        dataSourceConfig.connectionString().ifPresent(this::setURL);
        dataSourceConfig.username().ifPresent(this::setUser);
        dataSourceConfig.password().ifPresent(this::setPassword);
        dataSourceConfig.abandonedConnectionTimeout().ifPresent(this::abandonedConnectionTimeout);
        dataSourceConfig.commitOnConnectionReturn().ifPresent(this::commitOnConnectionReturn);
        dataSourceConfig.connectionFactoryClassName().ifPresent(this::connectionFactoryClassName);
        dataSourceConfig.connectionFactoryProperties().ifPresent(this::connectionFactoryProperties);
        dataSourceConfig.connectionHarvestMaxCount().ifPresent(this::connectionHarvestMaxCount);
        dataSourceConfig.connectionHarvestTriggerCount().ifPresent(this::connectionHarvestTriggerCount);
        dataSourceConfig.connectionLabelingHighCost().ifPresent(this::connectionLabelingHighCost);
        dataSourceConfig.connectionPoolName().ifPresent(this::connectionPoolName);
        dataSourceConfig.connectionProperties().ifPresent(this::connectionProperties);
        dataSourceConfig.connectionRepurposeThreshold().ifPresent(this::connectionRepurposeThreshold);
        dataSourceConfig.connectionValidationTimeout().ifPresent(this::connectionValidationTimeout);
        dataSourceConfig.connectionWaitDuration().ifPresent(this::connectionWaitDuration);
        dataSourceConfig.createConnectionInBorrowThread().ifPresent(this::createConnectionInBorrowThread);
        dataSourceConfig.databaseName().ifPresent(this::databaseName);
        dataSourceConfig.dataSourceName().ifPresent(this::dataSourceName);
        dataSourceConfig.description().ifPresent(this::description);
        dataSourceConfig.fastConnectionFailoverEnabled().ifPresent(this::fastConnectionFailoverEnabled);
        dataSourceConfig.highCostConnectionReuseThreshold().ifPresent(this::highCostConnectionReuseThreshold);
        dataSourceConfig.inactiveConnectionTimeout().ifPresent(this::inactiveConnectionTimeout);
        dataSourceConfig.initialPoolSize().ifPresent(this::initialPoolSize);
        dataSourceConfig.maxConnectionReuseCount().ifPresent(this::maxConnectionReuseCount);
        dataSourceConfig.maxConnectionReuseTime().ifPresent(this::maxConnectionReuseTime);
        dataSourceConfig.maxConnectionsPerShard().ifPresent(this::maxConnectionsPerShard);
        dataSourceConfig.maxIdleTime().ifPresent(this::maxIdleTime);
        dataSourceConfig.maxPoolSize().ifPresent(this::maxPoolSize);
        dataSourceConfig.minPoolSize().ifPresent(this::minPoolSize);
        dataSourceConfig.maxStatements().ifPresent(this::maxStatements);
        dataSourceConfig.minIdle().ifPresent(this::minIdle);
        dataSourceConfig.networkProtocol().ifPresent(this::networkProtocol);
        dataSourceConfig.onsConfiguration().ifPresent(this::onsConfiguration);
        dataSourceConfig.portNumber().ifPresent(this::portNumber);
        dataSourceConfig.propertyCycle().ifPresent(this::propertyCycle);
        dataSourceConfig.queryTimeout().ifPresent(this::queryTimeout);
        dataSourceConfig.readOnlyInstanceAllowed().ifPresent(this::readOnlyInstanceAllowed);
        dataSourceConfig.secondsToTrustIdleConnection().ifPresent(this::secondsToTrustIdleConnection);
        dataSourceConfig.serverName().ifPresent(this::serverName);
        dataSourceConfig.shardingMode().ifPresent(this::shardingMode);
        dataSourceConfig.sqlForValidateConnection().ifPresent(this::sqlForValidateConnection);
        dataSourceConfig.timeoutCheckInterval().ifPresent(this::timeoutCheckInterval);
        dataSourceConfig.timeToLiveConnectionTimeout().ifPresent(this::timeToLiveConnectionTimeout);
        dataSourceConfig.validateConnectionOnBorrow().ifPresent(this::validateConnectionOnBorrow);
        return dataSource;
    }

    private void setURL(String connectionString) {
        try {
            dataSource.setURL(connectionString);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource connection setup failed", ex);
        }
    }

    private void setUser(String username) {
        try {
            dataSource.setUser(username);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource username setup failed", ex);
        }
    }

    private void setPassword(char[] password) {
        try {
            dataSource.setPassword(new String(password));
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource password setup failed", ex);
        }
    }

    private void abandonedConnectionTimeout(int timeout) {
        try {
            dataSource.setAbandonedConnectionTimeout(timeout);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource abandoned connection timeout setup failed", ex);
        }
    }

    private void commitOnConnectionReturn(boolean commit) {
        try {
            dataSource.setCommitOnConnectionReturn(commit);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource commit on connection return setup failed", ex);
        }
    }

    private void connectionFactoryClassName(String className) {
        try {
            dataSource.setConnectionFactoryClassName(className);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource connection factory class name setup failed", ex);
        }
    }

    // This may need refactoring when Map<String, String> won't be enough
    private void connectionFactoryProperties(Map<String, String> propertiesMap) {
        try {
            Properties properties = new Properties(propertiesMap.size());
            properties.putAll(propertiesMap);
            dataSource.setConnectionFactoryProperties(properties);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource connection factory properties setup failed", ex);
        }
    }

    private void connectionHarvestMaxCount(int count) {
        try {
            dataSource.setConnectionHarvestMaxCount(count);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource maximum number of connections for connection harvesting setup failed", ex);
        }
    }

    private void connectionHarvestTriggerCount(int count) {
        try {
            dataSource.setConnectionHarvestTriggerCount(count);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource connection harvesting trigger count setup failed", ex);
        }
    }

    private void connectionLabelingHighCost(int cost) {
        try {
            dataSource.setConnectionLabelingHighCost(cost);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource connection labeling high cost setup failed", ex);
        }
    }

    private void connectionPoolName(String name) {
        try {
            dataSource.setConnectionPoolName(name);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource connection pool name setup failed", ex);
        }
    }

    // This may need refactoring when Map<String, String> won't be enough
    private void connectionProperties(Map<String, String> propertiesMap) {
        try {
            Properties properties = new Properties(propertiesMap.size());
            properties.putAll(propertiesMap);
            dataSource.setConnectionProperties(properties);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource connection properties setup failed", ex);
        }
    }

    private void connectionRepurposeThreshold(int threshold) {
        try {
            dataSource.setConnectionRepurposeThreshold(threshold);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource connection repurpose threshold setup failed", ex);
        }
    }

    private void connectionValidationTimeout(int timeout) {
        try {
            dataSource.setConnectionValidationTimeout(timeout);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource connection validation timeout setup failed", ex);
        }
    }

    private void connectionWaitDuration(Duration duration) {
        try {
            dataSource.setConnectionWaitDuration(duration);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource connection request call wait duration setup failed", ex);
        }
    }

    private void createConnectionInBorrowThread(boolean borrowThread) {
        try {
            dataSource.setCreateConnectionInBorrowThread(borrowThread);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource borrowing thread to create new connections setup failed", ex);
        }
    }

    private void databaseName(String name) {
        try {
            dataSource.setDatabaseName(name);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource database name setup failed", ex);
        }
    }

    private void dataSourceName(String name) {
        try {
            dataSource.setDataSourceName(name);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource data source name setup failed", ex);
        }
    }

    private void description(String name) {
        try {
            dataSource.setDescription(name);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource description setup failed", ex);
        }
    }

    private void fastConnectionFailoverEnabled(boolean failoverEnabled) {
        try {
            dataSource.setFastConnectionFailoverEnabled(failoverEnabled);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource fast connection failover setup failed", ex);
        }
    }

    private void highCostConnectionReuseThreshold(int threshold) {
        try {
            dataSource.setHighCostConnectionReuseThreshold(threshold);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource fast connection failover setup failed", ex);
        }
    }

    private void inactiveConnectionTimeout(int timeout) {
        try {
            dataSource.setInactiveConnectionTimeout(timeout);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource inactive connection timeout setup failed", ex);
        }
    }

    private void initialPoolSize(int size) {
        try {
            dataSource.setInitialPoolSize(size);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource initial pool size setup failed", ex);
        }
    }

    private void maxConnectionReuseCount(int count) {
        try {
            dataSource.setMaxConnectionReuseCount(count);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource maximum connection reuse count setup failed", ex);
        }
    }

    private void maxConnectionReuseTime(long time) {
        try {
            dataSource.setMaxConnectionReuseTime(time);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource maximum connection reuse time setup failed", ex);
        }
    }

    private void maxConnectionsPerShard(int count) {
        try {
            dataSource.setMaxConnectionsPerShard(count);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource max number of connections that can be created per shard setup failed", ex);
        }
    }

    private void maxIdleTime(int time) {
        try {
            dataSource.setMaxIdleTime(time);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource maximum idle time for available connections setup failed", ex);
        }
    }

    private void maxPoolSize(int size) {
        try {
            dataSource.setMaxPoolSize(size);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource maximum number of connections setup failed", ex);
        }
    }

    private void minPoolSize(int size) {
        try {
            dataSource.setMinPoolSize(size);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource minimum number of connections setup failed", ex);
        }
    }

    private void maxStatements(int count) {
        try {
            dataSource.setMaxStatements(count);
        } catch (SQLException ex) {
            throw new DataException(
                    "UCP DataSource maximum number of statements that may be pooled or cached on a connection setup failed",
                    ex);
        }
    }

    private void minIdle(int count) {
        try {
            dataSource.setMinIdle(count);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource minimum number of idle connections setup failed", ex);
        }
    }

    private void networkProtocol(String protocol) {
        try {
            dataSource.setNetworkProtocol(protocol);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource network protocol setup failed", ex);
        }
    }

    private void onsConfiguration(String onsConfiguration) {
        try {
            dataSource.setONSConfiguration(onsConfiguration);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource configuration string used for remote ONS subscription setup failed", ex);
        }
    }

    private void portNumber(int port) {
        try {
            dataSource.setPortNumber(port);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource database port number setup failed", ex);
        }
    }

    private void propertyCycle(int propertyCycle) {
        try {
            dataSource.setPropertyCycle(propertyCycle);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource property cycle setup failed", ex);
        }
    }

    private void queryTimeout(int timeout) {
        try {
            dataSource.setQueryTimeout(timeout);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource query timeout setup failed", ex);
        }
    }

    private void readOnlyInstanceAllowed(boolean allowed) {
        try {
            dataSource.setReadOnlyInstanceAllowed(allowed);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource read-only instance allowed value setup failed", ex);
        }
    }

    private void roleName(String name) {
        try {
            dataSource.setRoleName(name);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource data source role name setup failed", ex);
        }
    }

    private void secondsToTrustIdleConnection(int count) {
        try {
            dataSource.setSecondsToTrustIdleConnection(count);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource time to trust an idle connection setup failed", ex);
        }
    }

    private void serverName(String name) {
        try {
            dataSource.setServerName(name);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource database server name setup failed", ex);
        }
    }

    private void shardingMode(boolean change) {
        try {
            dataSource.setShardingMode(change);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource sharded database UCP mode change setup failed", ex);
        }
    }

    private void sqlForValidateConnection(String sql) {
        try {
            dataSource.setSQLForValidateConnection(sql);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource SQL statement to validate the database connection setup failed", ex);
        }
    }

    private void timeoutCheckInterval(int interval) {
        try {
            dataSource.setTimeoutCheckInterval(interval);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource timeout check interval setup failed", ex);
        }
    }

    private void timeToLiveConnectionTimeout(int ttl) {
        try {
            dataSource.setTimeToLiveConnectionTimeout(ttl);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource maximum time a connection may remain in-use setup failed", ex);
        }
    }

    private void validateConnectionOnBorrow(boolean validate) {
        try {
            dataSource.setValidateConnectionOnBorrow(validate);
        } catch (SQLException ex) {
            throw new DataException("UCP DataSource validate the connection before returning it to the user setup failed", ex);
        }
    }

}
