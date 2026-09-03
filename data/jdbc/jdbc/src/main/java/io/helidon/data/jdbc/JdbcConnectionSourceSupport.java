/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.common.SqlDriver;

/**
 * Resolves JDBC connection configuration into provider owned data sources.
 */
final class JdbcConnectionSourceSupport {

    private static final String USER_PROPERTY = "user";
    private static final String PASSWORD_PROPERTY = "password";

    private JdbcConnectionSourceSupport() {
    }

    /**
     * Adapts direct connection configuration to a data source.
     *
     * @param componentDescription safe component description
     * @param config direct connection configuration
     * @return data source adapter
     */
    static DataSource directDataSource(String componentDescription, ConnectionConfig config) {
        Driver driver;
        try {
            driver = SqlDriver.create(config).driver();
        } catch (RuntimeException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            throw new DataException(componentDescription + " could not resolve a JDBC driver for its direct connection.",
                                    JdbcExceptionTranslator.sanitize("resolving a JDBC driver", cause));
        }
        return new DirectDataSource(config, driver);
    }

    /**
     * Data source adapter for direct JDBC connection configuration.
     */
    private static final class DirectDataSource implements DataSource, JdbcTransactionConnectionManager.IdentitySource {

        private final String url;
        private final Driver driver;
        private final Properties defaults;
        private final DirectIdentity transactionIdentity;

        private volatile PrintWriter logWriter;

        private DirectDataSource(ConnectionConfig config, Driver driver) {
            this.url = config.url();
            this.driver = driver;
            this.defaults = new Properties();
            String username = config.username().orElse(null);
            char[] passwordCharacters = config.password().map(value -> value.clone()).orElse(null);
            String password = passwordCharacters == null ? null : new String(passwordCharacters);
            if (username != null) {
                defaults.setProperty(USER_PROPERTY, username);
            }
            if (password != null) {
                defaults.setProperty(PASSWORD_PROPERTY, password);
            }
            this.transactionIdentity = new DirectIdentity(url,
                                                          driver.getClass().getName(),
                                                          username,
                                                          passwordCharacters);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return connect(copy(defaults));
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Properties properties = copy(defaults);
            if (username == null) {
                properties.remove(USER_PROPERTY);
            } else {
                properties.setProperty(USER_PROPERTY, username);
            }
            if (password == null) {
                properties.remove(PASSWORD_PROPERTY);
            } else {
                properties.setProperty(PASSWORD_PROPERTY, password);
            }
            return connect(properties);
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            if (seconds < 0) {
                throw new IllegalArgumentException("The login timeout must not be negative.");
            }
            if (seconds != 0) {
                throw new SQLFeatureNotSupportedException(
                        "The direct JDBC data source does not support login timeouts.");
            }
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            try {
                return driver.getParentLogger();
            } catch (RuntimeException failure) {
                throw (RuntimeException) JdbcExceptionTranslator.sanitize("reading a JDBC driver parent logger",
                                                                           failure);
            }
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            Objects.requireNonNull(type, "The unwrap type must not be null.");
            if (type.isInstance(this)) {
                return type.cast(this);
            }
            if (type.isInstance(driver)) {
                return type.cast(driver);
            }
            throw new SQLException("The direct JDBC data source cannot be unwrapped as '" + type.getName() + "'.");
        }

        @Override
        public boolean isWrapperFor(Class<?> type) {
            Objects.requireNonNull(type, "The wrapper type must not be null.");
            return type.isInstance(this) || type.isInstance(driver);
        }

        @Override
        public DirectIdentity transactionIdentity() {
            return transactionIdentity;
        }

        private Connection connect(Properties properties) throws SQLException {
            Connection connection = JdbcExceptionTranslator.invoke("opening a direct JDBC connection",
                                                                   () -> driver.connect(url, properties));
            if (connection == null) {
                throw new SQLException("The JDBC driver does not accept the configured URL.");
            }
            return connection;
        }

        private static Properties copy(Properties source) {
            Properties copy = new Properties();
            copy.putAll(source);
            return copy;
        }
    }

    /**
     * Immutable transaction identity for equivalent direct connections.
     */
    private static final class DirectIdentity implements JdbcTransactionConnectionManager.StableIdentity {

        private final String url;
        private final String driverClass;
        private final String username;
        private final char[] password;

        private DirectIdentity(String url, String driverClass, String username, char[] password) {
            this.url = url;
            this.driverClass = driverClass;
            this.username = username;
            this.password = password == null ? null : password.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof DirectIdentity that
                    && url.equals(that.url)
                    && driverClass.equals(that.driverClass)
                    && Objects.equals(username, that.username)
                    && Arrays.equals(password, that.password);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(url, driverClass, username);
            return 31 * result + Arrays.hashCode(password);
        }
    }
}
