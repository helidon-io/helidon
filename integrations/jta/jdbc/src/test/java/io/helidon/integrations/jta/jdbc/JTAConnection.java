/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.jta.jdbc;

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
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import io.helidon.integrations.jdbc.ConditionallyCloseableConnection;

import static java.lang.reflect.Proxy.newProxyInstance;

final class JTAConnection extends ConditionallyCloseableConnection implements Enlisted {


    /*
     * Static fields.
     */


    private static final LocalXAResource XA_RESOURCE = new LocalXAResource(JTAConnection::connection);

    private static final ReentrantLock HANDOFF_LOCK = new ReentrantLock();

    // Deliberately not volatile.
    // Null most of the time on purpose.
    // When not null, will contain either a Connection or a Xid.
    // @GuardedBy("HANDOFF_LOCK")
    private static Object HANDOFF;


    /*
     * Instance fields.
     */


    private final TransactionSupplier tm;

    private final BiConsumer<? super Enableable, ? super Object> closedNotifier;

    private volatile Xid xid;


    /*
     * Constructors.
     */


    private JTAConnection(TransactionSupplier tm,
                          BiConsumer<? super Enableable, ? super Object> closedNotifier,
                          Connection delegate) {
        super(delegate, true, true);
        this.tm = tm;
        this.closedNotifier = closedNotifier == null ? JTAConnection::sink : closedNotifier;
    }


    /*
     * Instance methods.
     */


    @Override // ConditionallyCloseableConnection
    public Statement createStatement() throws SQLException {
        this.enlist();
        return super.createStatement();
    }

    @Override // ConditionallyCloseableConnection
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        this.enlist();
        return super.prepareStatement(sql);
    }

    @Override // ConditionallyCloseableConnection
    public CallableStatement prepareCall(String sql) throws SQLException {
        this.enlist();
        return super.prepareCall(sql);
    }

    @Override // ConditionallyCloseableConnection
    public String nativeSQL(String sql) throws SQLException {
        this.enlist();
        return super.nativeSQL(sql);
    }

    @Override // ConditionallyCloseableConnection
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        this.enlist();
        super.setAutoCommit(autoCommit);
    }

    @Override // ConditionallyCloseableConnection
    public boolean getAutoCommit() throws SQLException {
        this.enlist();
        return super.getAutoCommit();
    }

    @Override // ConditionallyCloseableConnection
    public void commit() throws SQLException {
        this.enlist();
        super.commit();
    }

    @Override // ConditionallyCloseableConnection
    public void rollback() throws SQLException {
        this.enlist();
        super.rollback();
    }

    @Override // ConditionallyCloseableConnection
    public void close() throws SQLException {
        super.close();
    }

    @Override // ConditionallyCloseableConnection
    public boolean isClosed() throws SQLException {
        return super.isClosed();
    }

    @Override // ConditionallyCloseableConnection
    public DatabaseMetaData getMetaData() throws SQLException {
        this.enlist();
        return super.getMetaData();
    }

    @Override // ConditionallyCloseableConnection
    public void setReadOnly(boolean readOnly) throws SQLException {
        this.enlist();
        super.setReadOnly(readOnly);
    }

    @Override // ConditionallyCloseableConnection
    public boolean isReadOnly() throws SQLException {
        this.enlist();
        return super.isReadOnly();
    }

    @Override // ConditionallyCloseableConnection
    public void setCatalog(String catalog) throws SQLException {
        this.enlist();
        super.setCatalog(catalog);
    }

    @Override // ConditionallyCloseableConnection
    public String getCatalog() throws SQLException {
        this.enlist();
        return super.getCatalog();
    }

    @Override // ConditionallyCloseableConnection
    public void setTransactionIsolation(int level) throws SQLException {
        this.enlist();
        super.setTransactionIsolation(level);
    }

    @Override // ConditionallyCloseableConnection
    public int getTransactionIsolation() throws SQLException {
        this.enlist();
        return super.getTransactionIsolation();
    }

    @Override // ConditionallyCloseableConnection
    public SQLWarning getWarnings() throws SQLException {
        this.enlist();
        return super.getWarnings();
    }

    @Override // ConditionallyCloseableConnection
    public void clearWarnings() throws SQLException {
        this.enlist();
        super.clearWarnings();
    }

    @Override // ConditionallyCloseableConnection
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        this.enlist();
        return super.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override // ConditionallyCloseableConnection
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        this.enlist();
        return super.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override // ConditionallyCloseableConnection
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        this.enlist();
        return super.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override // ConditionallyCloseableConnection
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        this.enlist();
        return super.getTypeMap();
    }

    @Override // ConditionallyCloseableConnection
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        this.enlist();
        super.setTypeMap(map);
    }

    @Override // ConditionallyCloseableConnection
    public void setHoldability(int holdability) throws SQLException {
        this.enlist();
        super.setHoldability(holdability);
    }

    @Override // ConditionallyCloseableConnection
    public int getHoldability() throws SQLException {
        this.enlist();
        return super.getHoldability();
    }

    @Override // ConditionallyCloseableConnection
    public Savepoint setSavepoint() throws SQLException {
        this.enlist();
        return super.setSavepoint();
    }

    @Override // ConditionallyCloseableConnection
    public Savepoint setSavepoint(String name) throws SQLException {
        this.enlist();
        return super.setSavepoint(name);
    }

    @Override // ConditionallyCloseableConnection
    public void rollback(Savepoint savepoint) throws SQLException {
        this.enlist();
        super.rollback(savepoint);
    }

    @Override // ConditionallyCloseableConnection
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        this.enlist();
        super.releaseSavepoint(savepoint);
    }

    @Override // ConditionallyCloseableConnection
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        this.enlist();
        return super.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // ConditionallyCloseableConnection
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {
        this.enlist();
        return super.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // ConditionallyCloseableConnection
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException {
        this.enlist();
        return super.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // ConditionallyCloseableConnection
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        this.enlist();
        return super.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override // ConditionallyCloseableConnection
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        this.enlist();
        return super.prepareStatement(sql, columnIndexes);
    }

    @Override // ConditionallyCloseableConnection
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        this.enlist();
        return super.prepareStatement(sql, columnNames);
    }

    @Override // ConditionallyCloseableConnection
    public Clob createClob() throws SQLException {
        this.enlist();
        return super.createClob();
    }

    @Override // ConditionallyCloseableConnection
    public Blob createBlob() throws SQLException {
        this.enlist();
        return super.createBlob();
    }

    @Override // ConditionallyCloseableConnection
    public NClob createNClob() throws SQLException {
        this.enlist();
        return super.createNClob();
    }

    @Override // ConditionallyCloseableConnection
    public SQLXML createSQLXML() throws SQLException {
        this.enlist();
        return super.createSQLXML();
    }

    @Override // ConditionallyCloseableConnection
    public boolean isValid(int timeout) throws SQLException {
        this.enlist();
        return super.isValid(timeout);
    }

    @Override // ConditionallyCloseableConnection
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        try {
            this.enlist();
            super.setClientInfo(name, value);
        } catch (SQLClientInfoException e) {
            throw e;
        } catch (SQLException e) {
            throw new SQLClientInfoException(e.getMessage(), e.getSQLState(), e.getErrorCode(), Map.of(), e);
        }
    }

    @Override // ConditionallyCloseableConnection
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        try {
            this.enlist();
            super.setClientInfo(properties);
        } catch (SQLClientInfoException e) {
            throw e;
        } catch (SQLException e) {
            throw new SQLClientInfoException(e.getMessage(), e.getSQLState(), e.getErrorCode(), Map.of(), e);
        }
    }

    @Override // ConditionallyCloseableConnection
    public String getClientInfo(String name) throws SQLException {
        this.enlist();
        return super.getClientInfo(name);
    }

    @Override // ConditionallyCloseableConnection
    public Properties getClientInfo() throws SQLException {
        this.enlist();
        return super.getClientInfo();
    }

    @Override // ConditionallyCloseableConnection
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        this.enlist();
        return super.createArrayOf(typeName, elements);
    }

    @Override // ConditionallyCloseableConnection
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        this.enlist();
        return super.createStruct(typeName, attributes);
    }

    @Override // ConditionallyCloseableConnection
    public void setSchema(String schema) throws SQLException {
        this.enlist();
        super.setSchema(schema);
    }

    @Override // ConditionallyCloseableConnection
    public String getSchema() throws SQLException {
        this.enlist();
        return super.getSchema();
    }

    @Override // ConditionallyCloseableConnection
    public void abort(Executor executor) throws SQLException {
        this.enlist();
        super.abort(executor);
    }

    @Override // ConditionallyCloseableConnection
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        this.enlist();
        super.setNetworkTimeout(executor, milliseconds);
    }

    @Override // ConditionallyCloseableConnection
    public int getNetworkTimeout() throws SQLException {
        this.enlist();
        return super.getNetworkTimeout();
    }

    @Override // ConditionallyCloseableConnection
    public void beginRequest() throws SQLException {
        this.enlist();
        super.beginRequest();
    }

    @Override // ConditionallyCloseableConnection
    public void endRequest() throws SQLException {
        this.enlist();
        super.endRequest();
    }

    @Override // ConditionallyCloseableConnection
    public boolean setShardingKeyIfValid(ShardingKey shardingKey, ShardingKey superShardingKey, int timeout)
        throws SQLException {
        this.enlist();
        return super.setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
    }

    @Override // ConditionallyCloseableConnection
    public boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout) throws SQLException {
        this.enlist();
        return super.setShardingKeyIfValid(shardingKey, timeout);
    }

    @Override // ConditionallyCloseableConnection
    public void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey) throws SQLException {
        this.enlist();
        super.setShardingKey(shardingKey, superShardingKey);
    }

    @Override // ConditionallyCloseableConnection
    public void setShardingKey(ShardingKey shardingKey) throws SQLException {
        this.enlist();
        super.setShardingKey(shardingKey);
    }

    @Override // ConditionallyCloseableConnection
    public <T> T unwrap(Class<T> iface) throws SQLException {
        // this.enlist(); // Deliberately omitted per spec.
        return super.unwrap(iface);
    }

    @Override // ConditionallyCloseableConnection
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // this.enlist(); // Deliberately omitted per spec.
        return super.isWrapperFor(iface);
    }

    @Override // Enlisted
    public Xid xid() {
        return this.xid;
    }

    private void enlist() throws SQLException {
        this.checkOpen();
        if (this.tm != null && this.xid() == null) {
            try {
                XAResourceEnlister activeEnlister = this.activeEnlister();
                if (activeEnlister != null) {
                    HANDOFF_LOCK.lock();
                    try {
                        HANDOFF = this.delegate();
                        if (activeEnlister.enlist(XA_RESOURCE)) {
                            this.xid = (Xid) HANDOFF;
                            // TO DO: uncomment this once we have
                            // registered an appropriate
                            // synchronization to handle in-flight
                            // close requests
                            //
                            // this.setCloseable(false);
                        }
                    } finally {
                        HANDOFF = null;
                        HANDOFF_LOCK.unlock();
                    }
                }
            } catch (RollbackException e) {
                throw new SQLException(e.getMessage(),
                                       "40000" /* transaction rollback, no subclass */,
                                       e);
            } catch (SystemException e) {
                // Hard to know what SQLState to use here. Either
                // 25000 or 35000.
                throw new SQLException(e.getMessage(),
                                       "25000" /* invalid transaction state, no subclass */,
                                       e);
            }
        }
    }

    private XAResourceEnlister activeEnlister() throws SystemException {
        Transaction t = this.tm.getTransaction();
        return t == null || t.getStatus() != Status.STATUS_ACTIVE ? null : t::enlistResource;
    }


    /*
     * Static methods.
     */


    @Deprecated
    public static Connection connection(TransactionManager tm, BiConsumer<? super Enableable, ? super Object> closedNotifier, Connection c) {
        return connection(Thread.currentThread().getContextClassLoader(), tm, closedNotifier, c);
    }

    @Deprecated
    public static Connection connection(ClassLoader classLoader, TransactionManager tm, BiConsumer<? super Enableable, ? super Object> closedNotifier, Connection c) {
        return (Connection) newProxyInstance(classLoader,
                                             new Class<?>[] { Connection.class, Enlisted.class },
                                             new JTAHandler(c, tm::getTransaction, closedNotifier));
    }

    public static Connection connection2(TransactionManager tm, BiConsumer<? super Enableable, ? super Object> closedNotifier, Connection c) {
        return new JTAConnection(tm::getTransaction, closedNotifier, c);
    }

    // (Method reference.)
    private static Connection connection(Xid xid) {
        assert HANDOFF_LOCK.isHeldByCurrentThread();
        try {
            return (Connection) HANDOFF;
        } finally {
            HANDOFF = xid;
        }
    }

    private static void sink(Object ignored0, Object ignored1) {}


    /*
     * Inner and nested classes.
     */


    @FunctionalInterface
    static interface TransactionSupplier {

        Transaction getTransaction() throws SystemException;

    }

    @FunctionalInterface
    static interface XAResourceEnlister {

        boolean enlist(XAResource resource) throws RollbackException, SystemException;

    }

}
