/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.jdbc;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * A <a href="https://download.oracle.com/otn-pub/jcp/jdbc-4_3-mrel3-spec/jdbc4.3-fr-spec.pdf" target="_parent">JDBC
 * 4.3</a>-compliant {@link Connection} that delegates to another JDBC 4.3-compliant {@link Connection}.
 */
public class DelegatingConnection implements Connection {

    private final Connection delegate;

    /**
     * Creates a new {@link DelegatingConnection}.
     *
     * @param delegate the {@link Connection} to which all operations will be delegated; must not be {@code null}
     *
     * @exception NullPointerException if {@code delegate} is {@code null}
     */
    public DelegatingConnection(Connection delegate) {
        super();
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * Returns this {@link DelegatingConnection}'s underlying {@link Connection}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return this {@link DelegatingConnection}'s underlying {@link Connection}; never {@code null}
     */
    public final Connection delegate() {
        return this.delegate;
    }

    @Override
    public Statement createStatement() throws SQLException {
        return
            new DelegatingStatement<>(this, // NOTE
                                      this.delegate().createStatement(),
                                      true,
                                      true);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return
            new DelegatingPreparedStatement<>(this, // NOTE
                                              this.delegate().prepareStatement(sql),
                                              true,
                                              true);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        return new DelegatingCallableStatement(this, // NOTE
                                               this.delegate().prepareCall(sql),
                                               true,
                                               true);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return this.delegate().nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.delegate().setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return this.delegate().getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        this.delegate().commit();
    }

    @Override
    public void rollback() throws SQLException {
        this.delegate().rollback();
    }

    @Override
    public void close() throws SQLException {
        // (No need to check isClosed().)
        this.delegate().close();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return this.delegate().isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return
            new DelegatingDatabaseMetaData(this, // NOTE
                                           this.delegate().getMetaData());
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        this.delegate().setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return this.delegate().isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        this.delegate().setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return this.delegate().getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        this.delegate().setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return this.delegate().getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return this.delegate().getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        this.delegate().clearWarnings();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return
            new DelegatingStatement<>(this, // NOTE
                                      this.delegate().createStatement(resultSetType, resultSetConcurrency),
                                      true,
                                      true);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return
            new DelegatingPreparedStatement<>(this, // NOTE
                                              this.delegate().prepareStatement(sql, resultSetType, resultSetConcurrency),
                                              true,
                                              true);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return
            new DelegatingCallableStatement(this, // NOTE
                                            this.delegate().prepareCall(sql, resultSetType, resultSetConcurrency),
                                            true,
                                            true);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return this.delegate().getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        this.delegate().setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        this.delegate().setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return this.delegate().getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return this.delegate().setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return this.delegate().setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        this.delegate().rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        this.delegate().releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return
            new DelegatingStatement<>(this, // NOTE
                                      this.delegate().createStatement(resultSetType,
                                                                      resultSetConcurrency,
                                                                      resultSetHoldability),
                                      true,
                                      true);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException {
        return
            new DelegatingPreparedStatement<>(this, // NOTE
                                              this.delegate().prepareStatement(sql,
                                                                               resultSetType,
                                                                               resultSetConcurrency,
                                                                               resultSetHoldability),
                                              true,
                                              true);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException {
        return
            new DelegatingCallableStatement(this, // NOTE
                                            this.delegate().prepareCall(sql,
                                                                        resultSetType,
                                                                        resultSetConcurrency,
                                                                        resultSetHoldability),
                                            true,
                                            true);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return
            new DelegatingPreparedStatement<>(this, // NOTE
                                              this.delegate().prepareStatement(sql, autoGeneratedKeys),
                                              true,
                                              true);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return
            new DelegatingPreparedStatement<>(this, // NOTE
                                              this.delegate().prepareStatement(sql, columnIndexes),
                                              true,
                                              true);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return
            new DelegatingPreparedStatement<>(this, // NOTE
                                              this.delegate().prepareStatement(sql, columnNames),
                                              true,
                                              true);
    }

    @Override
    public Clob createClob() throws SQLException {
        return this.delegate().createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return this.delegate().createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return this.delegate().createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return this.delegate().createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        // (No need to check isClosed().)
        return this.delegate().isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        this.delegate().setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        this.delegate().setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return this.delegate().getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return this.delegate().getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return this.delegate().createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return this.delegate().createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        this.delegate().setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return this.delegate().getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        // (No need to check isClosed().)
        this.delegate().abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        this.delegate().setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return this.delegate().getNetworkTimeout();
    }

    @Override
    public void beginRequest() throws SQLException {
        // (No need to check isClosed().)
        this.delegate().beginRequest();
    }

    @Override
    public void endRequest() throws SQLException {
        // (No need to check isClosed().)
        this.delegate().endRequest();
    }

    @Override
    public boolean setShardingKeyIfValid(ShardingKey shardingKey, ShardingKey superShardingKey, int timeout)
        throws SQLException {
        return this.delegate().setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
    }

    @Override
    public boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout) throws SQLException {
        return this.delegate().setShardingKeyIfValid(shardingKey, timeout);
    }

    @Override
    public void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey) throws SQLException {
        this.delegate().setShardingKey(shardingKey, superShardingKey);
    }

    @Override
    public void setShardingKey(ShardingKey shardingKey) throws SQLException {
        this.delegate().setShardingKey(shardingKey);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return iface.isInstance(this) ? iface.cast(this) : this.delegate().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || this.delegate().isWrapperFor(iface);
    }

}
