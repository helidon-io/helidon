/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.data.sql.datasource.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import io.helidon.data.sql.common.SqlDriver;

class JdbcDataSource extends CommonDataSourceBase implements DataSource {
    private final JdbcDataSourceConfig providerConfig;
    private final Driver driver;
    private final Properties properties;

    JdbcDataSource(JdbcDataSourceConfig providerConfig, SqlDriver driver) {
        this.providerConfig = providerConfig;
        this.driver = driver.driver();
        this.properties = new Properties();

        properties.putAll(providerConfig.properties());
        providerConfig.username().ifPresent(it -> properties.put("user", it));
        providerConfig.password().ifPresent(it -> properties.put("password", it));
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(properties);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Properties propertiesCopy = new Properties(this.properties);
        if (username == null) {
            propertiesCopy.remove("username");
        } else {
            propertiesCopy.put("user", username);
        }
        if (password == null) {
            propertiesCopy.remove("password");
        } else {
            propertiesCopy.put("password", password);
        }
        return getConnection(propertiesCopy);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        if (iface.isInstance(driver)) {
            return iface.cast(driver);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName()
                                       + ", current driver class: " + driver.getClass().getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this) || iface.isInstance(driver);
    }

    private Connection getConnection(Properties properties) throws SQLException {
        Connection connection = driver.connect(providerConfig.url(), properties);

        if (providerConfig.autoCommit().isPresent()) {
            connection.setAutoCommit(providerConfig.autoCommit().get());
        }
        if (providerConfig.catalog().isPresent()) {
            connection.setCatalog(providerConfig.catalog().get());
        }
        if (providerConfig.transactionIsolation().isPresent()) {
            connection.setTransactionIsolation(providerConfig.transactionIsolation().get().level());
        }
        if (providerConfig.schema().isPresent()) {
            connection.setSchema(providerConfig.schema().get());
        }
        if (providerConfig.readOnly().isPresent()) {
            connection.setReadOnly(providerConfig.readOnly().get());
        }

        return connection;
    }
}
