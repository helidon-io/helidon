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
package io.helidon.data.jdbc.tests.chaos.support;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.h2.jdbc.JdbcConnection;

/**
 * Creates live H2 connections and injects one selected JDBC connection lifecycle failure.
 */
public final class FaultInjectingH2DataSource implements JdbcLifecycleFaultDataSource {
    private final String url;
    private final Set<JdbcLifecycleFault> armed = ConcurrentHashMap.newKeySet();
    private final Map<JdbcLifecycleFault, AtomicLong> calls = new EnumMap<>(JdbcLifecycleFault.class);
    private final List<FaultConnection> connections = new CopyOnWriteArrayList<>();
    private final AtomicLong connectionsCreated = new AtomicLong();

    private volatile PrintWriter logWriter;
    private volatile int loginTimeout;

    /**
     * Creates a fault-injecting datasource for one H2 database URL.
     *
     * @param url H2 JDBC URL
     */
    public FaultInjectingH2DataSource(String url) {
        this.url = url;
        for (JdbcLifecycleFault fault : JdbcLifecycleFault.values()) {
            calls.put(fault, new AtomicLong());
        }
    }

    @Override
    public void arm(JdbcLifecycleFault... faults) {
        if (!armed.isEmpty()) {
            throw new IllegalStateException("A JDBC lifecycle fault is already armed.");
        }
        for (JdbcLifecycleFault fault : faults) {
            if (!armed.add(fault)) {
                throw new IllegalArgumentException("A JDBC lifecycle fault was specified more than once.");
            }
        }
    }

    @Override
    public long calls(JdbcLifecycleFault fault) {
        return calls.get(fault).get();
    }

    @Override
    public long connectionsCreated() {
        return connectionsCreated.get();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return newConnection(new Properties());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        return newConnection(properties);
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
    public void setLoginTimeout(int seconds) {
        loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("H2 test datasource does not expose a parent logger.");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("The requested datasource type is not available.");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    @Override
    public void close() {
        armed.clear();
        for (FaultConnection connection : connections) {
            connection.forceClose();
        }
        connections.clear();
    }

    private Connection newConnection(Properties properties) throws SQLException {
        FaultConnection connection = new FaultConnection(url, properties);
        connections.add(connection);
        connectionsCreated.incrementAndGet();
        return connection;
    }

    private void record(JdbcLifecycleFault fault) throws SQLException {
        calls.get(fault).incrementAndGet();
        if (armed.remove(fault)) {
            throw new SQLException("Injected JDBC lifecycle failure.");
        }
    }

    private final class FaultConnection extends JdbcConnection {
        private FaultConnection(String url, Properties properties) throws SQLException {
            super(url, properties, null, null, false);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            record(JdbcLifecycleFault.GET_AUTO_COMMIT);
            return super.getAutoCommit();
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            record(autoCommit ? JdbcLifecycleFault.RESET_AUTO_COMMIT : JdbcLifecycleFault.DISABLE_AUTO_COMMIT);
            super.setAutoCommit(autoCommit);
        }

        @Override
        public void commit() throws SQLException {
            record(JdbcLifecycleFault.COMMIT);
            super.commit();
        }

        @Override
        public void rollback() throws SQLException {
            record(JdbcLifecycleFault.ROLLBACK);
            super.rollback();
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return new FaultPreparedStatement(super.prepareStatement(sql));
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            return new FaultPreparedStatement(super.prepareStatement(sql, autoGeneratedKeys));
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            return new FaultPreparedStatement(super.prepareStatement(sql, columnIndexes));
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            return new FaultPreparedStatement(super.prepareStatement(sql, columnNames));
        }

        @Override
        public PreparedStatement prepareStatement(String sql,
                                                  int resultSetType,
                                                  int resultSetConcurrency) throws SQLException {
            return new FaultPreparedStatement(super.prepareStatement(sql, resultSetType, resultSetConcurrency));
        }

        @Override
        public PreparedStatement prepareStatement(String sql,
                                                  int resultSetType,
                                                  int resultSetConcurrency,
                                                  int resultSetHoldability) throws SQLException {
            return new FaultPreparedStatement(
                    super.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        }

        @Override
        public void abort(Executor executor) {
            calls.get(JdbcLifecycleFault.ABORT).incrementAndGet();
            if (armed.remove(JdbcLifecycleFault.ABORT)) {
                throw new IllegalStateException("Injected JDBC lifecycle failure.");
            }
            super.abort(executor);
        }

        @Override
        public void close() throws SQLException {
            record(JdbcLifecycleFault.CLOSE);
            super.close();
        }

        private void forceClose() {
            try {
                super.close();
            } catch (SQLException _) {
                // Best effort after a test deliberately made the ordinary cleanup path fail.
            }
        }
    }

    private final class FaultPreparedStatement extends ForwardingPreparedStatement {
        private FaultPreparedStatement(PreparedStatement delegate) {
            super(delegate);
        }

        @Override
        public void close() throws SQLException {
            record(JdbcLifecycleFault.STATEMENT_CLOSE);
            super.close();
        }
    }
}
