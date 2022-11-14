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
import java.sql.SQLNonTransientException;
import java.sql.SQLTransientException;
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

import javax.transaction.xa.Xid;

import io.helidon.integrations.jdbc.ConditionallyCloseableConnection;

import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionSynchronizationRegistry;

final class JTAConnection extends ConditionallyCloseableConnection {


    /*
     * Static fields.
     */


    private static final LocalXAResource XA_RESOURCE = new LocalXAResource(JTAConnection::connection);

    private static final ReentrantLock HANDOFF_LOCK = new ReentrantLock();

    // Deliberately not final.
    // Deliberately not volatile.
    // null most of the time on purpose.
    // When not null, will contain either a Connection or a Xid.
    // @GuardedBy("HANDOFF_LOCK")
    private static Object handoff;


    /*
     * Instance fields.
     */


    private final TransactionSupplier tm;

    private final TransactionSynchronizationRegistry tsr;


    /*
     * Constructors.
     */


    private JTAConnection(TransactionSupplier tm,
                          TransactionSynchronizationRegistry tsr,
                          Connection delegate) {
        super(delegate, true, true);
        this.tm = tm;
        this.tsr = tsr;
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
        // NOTE
        if (autoCommit && this.enlisted()) {
            // "SQLException...if...setAutoCommit(true) is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", "25000");
        }
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
        // NOTE
        if (this.enlisted()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", "25000");
        }
        super.commit();
    }

    @Override // ConditionallyCloseableConnection
    public void rollback() throws SQLException {
        this.enlist();
        // NOTE
        if (this.enlisted()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", "25000");
        }
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
        // NOTE
        if (this.enlisted()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            //
            // Use IBM's very descriptive 3B503 SQL state ("A SAVEPOINT, RELEASE SAVEPOINT, or ROLLBACK TO SAVEPOINT is
            // not allowed in a trigger, function, or global transaction").
            throw new SQLNonTransientException("Connection enlisted in transaction", "3B503");
        }
        return super.setSavepoint();
    }

    @Override // ConditionallyCloseableConnection
    public Savepoint setSavepoint(String name) throws SQLException {
        this.enlist();
        // NOTE
        if (this.enlisted()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            //
            // Use IBM's very descriptive 3B503 SQL state ("A SAVEPOINT, RELEASE SAVEPOINT, or ROLLBACK TO SAVEPOINT is
            // not allowed in a trigger, function, or global transaction").
            throw new SQLNonTransientException("Connection enlisted in transaction", "3B503");
        }
        return super.setSavepoint(name);
    }

    @Override // ConditionallyCloseableConnection
    public void rollback(Savepoint savepoint) throws SQLException {
        this.enlist();
        // NOTE
        if (this.enlisted()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            //
            // Use IBM's very descriptive 3B503 SQL state ("A SAVEPOINT, RELEASE SAVEPOINT, or ROLLBACK TO SAVEPOINT is
            // not allowed in a trigger, function, or global transaction").
            throw new SQLNonTransientException("Connection enlisted in transaction", "3B503");
        }
        super.rollback(savepoint);
    }

    @Override // ConditionallyCloseableConnection
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        this.enlist();
        // NOTE
        if (this.enlisted()) {
            // "SQLException...if...the given Savepoint object is not a valid savepoint in the current transaction"
            //
            // Use IBM's very descriptive 3B503 SQL state ("A SAVEPOINT, RELEASE SAVEPOINT, or ROLLBACK TO SAVEPOINT is
            // not allowed in a trigger, function, or global transaction").
            //
            // Interestingly JDBC doesn't mandate an exception being thrown here if the connection is enlisted in a
            // global transaction, but it looks like SQLState 3B503 is often thrown in this case.
            throw new SQLNonTransientException("Connection enlisted in transaction", "3B503");
        }
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
        // this.enlist(); // Deliberately omitted, but not by spec.

        // NOTE
        //
        // abort(Executor) is a method that seems to be designed for an administrator, and so even if there is a
        // transaction in progress we probably should allow closing.
        //
        // TO DO: should we heuristically roll back? Purge the Xid?
        this.setCloseable(true);
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

    @Override
    public boolean isCloseable() throws SQLException {
        return super.isCloseable() && !this.enlisted();
    }

    @Override
    public void setCloseable(boolean closeable) {
        if (closeable) {
            try {
                if (this.enlisted()) {
                    throw new IllegalArgumentException("closeable: " + closeable);
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        super.setCloseable(closeable);
    }

    // At the moment of this call, can this connection enlist in the transaction? (Note that because the transaction may
    // be rolled back at any point from any thread, a subsequent attempt to *actually* enlist may very well fail
    // anyway.)
    private boolean enlistable() throws SQLException {
        try {
            return this.activeTransaction() && this.tsr.getResource("xid") == null;
        } catch (RuntimeException e) {
            // See
            // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/transaction/arjunacore/TransactionSynchronizationRegistryImple.java#L213-L235;
            // getTransactionImple() is called by Narayana's implementation of getResource().  getResource() is not
            // documented to throw RuntimeException, only IllegalStateException. Nevertheless it does when a
            // SystemException is encountered.
            throw new SQLTransientException(e.getMessage(),
                                            "25000", // invalid transaction state
                                            e);
        }
    }

    // Does a global transaction exist in Status.STATUS_ACTIVE state?
    private boolean activeTransaction() throws SQLException {
        try {
            return this.tsr.getTransactionStatus() == Status.STATUS_ACTIVE;
        } catch (RuntimeException e) {
            // See
            // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/transaction/arjunacore/TransactionSynchronizationRegistryImple.java#L153-L164;
            // despite it not being documented, TCK-passing implementations of
            // TransactionSynchronizationRegistry#getTransactionStatus() can apparently throw RuntimeException. Since
            // getTransactionStatus() is specified to return "the result of executing TransactionManager.getStatus() in
            // the context of the transaction bound to the current thread at the time this method is called", it follows
            // that possible SystemExceptions thrown by TransactionManager#getStatus() implementations will have to be
            // dealt with in *some* way, even though the javadoc for
            // TransactionSynchronizationRegistry#getTransactionStatus() does not account for such a thing.
            throw new SQLTransientException(e.getMessage(),
                                            "25000", // invalid transaction state
                                            e);
        }
    }

    // Is the supplied global transaction both non-null and in the Status.STATUS_ACTIVE state?
    private boolean active(Transaction t) throws SQLException {
        try {
            return t != null && t.getStatus() == Status.STATUS_ACTIVE;
        } catch (SystemException e) {
            throw new SQLTransientException(e.getMessage(),
                                            "25000", // invalid transaction state
                                            e);
        }
    }

    Xid xid() {
        try {
            return (Xid) this.tsr.getResource("xid");
        } catch (IllegalStateException e) {
            return null;
        }
    }

    // Is the usage of this connection on this thread already enlisted with a non-completed global transaction?
    private boolean enlisted() throws SQLException {
        try {
            return this.activeTransaction() && this.tsr.getResource("xid") != null;
        } catch (IllegalStateException noActiveTransaction) {
            return false;
        } catch (RuntimeException e) {
            // Why do we catch RuntimeException as well here? See
            // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/transaction/arjunacore/TransactionSynchronizationRegistryImple.java#L213-L235;
            // getTransactionImple() is called by Narayana's implementations of getResource() and putResource().
            // getResource() and putResource() are not documented to throw RuntimeException, only
            // IllegalStateException. Nevertheless a RuntimeException is thrown when a SystemException is encountered.
            throw new SQLTransientException(e.getMessage(),
                                            "25000", // invalid transaction state
                                            e);
        }
    }

    // Enlist the usage of this connection on this thread with the global transaction if possible and not already
    // enlisted.
    private void enlist() throws SQLException {
        this.failWhenClosed();

        if (!this.activeTransaction() || this.enlisted()) {
            // We have determined either:
            // * there is no transaction at all
            // * there is a transaction but its state is not suitable
            // * there is a transaction in a suitable state but this
            //   connection is already enlisted
            return;
        }

        if (!super.getAutoCommit()) {
            // super.getAutoCommit() (super on purpose) returned false. We don't want to permit enlistment because a
            // local transaction may be in progress.
            //
            // The exception is transient because a retry of the operation without any application-initiated change
            // *might* work, however unlikely, because the global transaction might be rolled back or otherwise ended on
            // another thread, in which case the condition causing this exception will not be triggered.
            throw new SQLTransientException("autoCommit was false during transaction enlistment", "25000");
        }

        // Now go try to get the transaction, which may fail.
        Transaction t;
        try {
            t = this.tm.getTransaction();
        } catch (RuntimeException | SystemException e) {
            throw new SQLTransientException(e.getMessage(),
                                            "25000", // invalid transaction state, no subclass
                                            e);
        }

        // We got the Transaction, and although we checked the status earlier, it might be null or its status might have
        // changed.
        if (!active(t)) {
            // See active(Transaction).  Either t was null or t was in an unsuitable state.
            //
            // Although we called activeTransaction() earlier, a transaction may be rolled back at any moment by any
            // thread so that's why we do another cheap check here.
            return;
        }

        // Point of no return; we've made a best effort to ensure that (a) the Transaction we have is non-null, (b) the
        // Transaction we have is active and (c) no Xid resource is present in the
        // TransactionSynchronizationRegistry. The Transaction's status can still change at any point so we have to
        // watch for various exceptions when we invoke methods on it.

        boolean enlisted;

        HANDOFF_LOCK.lock();
        try {
            assert handoff == null;
            handoff = this.delegate();

            enlisted = t.enlistResource(XA_RESOURCE);
            if (enlisted) {
                // We called enlistResource(XAResource) successfully, which means its start(Xid, int) method was called
                // on this thread (per spec), so under lock, and our connection function (see connection(Xid) elsewhere
                // in this class) ran to completion. That means it safely set handoff to the Xid identifying the
                // transaction.

                // Put the Xid into the TransactionSynchronizationRegistry which will auto-clear it on transaction
                // completion.  If this putResource() operation fails, we haven't changed state.
                assert handoff instanceof Xid;
                this.tsr.putResource("xid", handoff);
            }

        } catch (RollbackException e) {
            // The enlistResource(XAResource) operation failed.
            //
            // We use SQL state 40000 ("transaction rollback, no subclass") even though it's unclear whether this
            // indicates the SQL/local transaction or the XA branch transaction or both.
            //
            // It is unclear to me whether the exception is transient or not. I think it is non-transient because
            // transactions are associated with a single thread, and once a transaction has entered the rolled back
            // state it cannot move out of that state (only TransactionManager#rollback() can do that, not
            // Transaction#rollback()), and a new transaction on the same thread cannot occur (because
            // TransactionManager#begin() "[creates] a new transaction and [associates] it with the current thread"), so
            // a retry of the same JDBC operation causing this condition on the same thread can never succeed.
            throw new SQLNonTransientException(e.getMessage(),
                                               "40000", // transaction rollback, no subclass
                                               e);
        } catch (RuntimeException | SystemException e) {
            // The enlistResource(XAResource) operation failed, or the
            // putResource(Object, Object) operation failed.
            //
            // Why do we catch RuntimeException and not something more specific?  See
            // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/transaction/arjunacore/TransactionSynchronizationRegistryImple.java#L213-L235;
            // getTransactionImple() is called by Narayana's implementations of getResource() and putResource().
            // getResource() and putResource() are not documented to throw RuntimeException, only
            // IllegalStateException. Nevertheless a RuntimeException is thrown when a SystemException is encountered.
            //
            // Hard to know what SQLState to use here. 25000 ("invalid transaction state") seems close, even though it's
            // unclear whether this indicates the SQL/local transaction or the XA branch transaction or both.
            //
            // I think the exception is transient because either (a) the RuntimeException occurred because "the
            // transaction in the target object [was] in the prepared state or the transaction [was] inactive" or (b)
            // "the transaction manager [encountered] an unexpected error condition".
            throw new SQLTransientException(e.getMessage(),
                                            "25000", // invalid transaction state, no subclass
                                            e);
        } finally {
            // The handoff has served its purpose (it exchanged our
            // underlying "real" connection for a Xid). Set it back to
            // null.
            handoff = null;
            HANDOFF_LOCK.unlock();
        }

        if (enlisted && super.isCloseable()) {

            // Register a synchronization that restores closability to the connection after the transaction has
            // completed.  If this fails, we haven't changed state.
            try {
                this.tsr.registerInterposedSynchronization((Sync) status -> super.setCloseable(true));
            } catch (RuntimeException e) {
                // Why do we catch RuntimeException and not something more specific?  See
                // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/transaction/arjunacore/TransactionSynchronizationRegistryImple.java#L213-L235;
                // getTransactionImple() is called by Narayana's implementation of
                // registerInterposedSynchronization(Synchronization), and although a generic RuntimeException is
                // not supposed to be thrown by registerInterposedSynchronization(Synchronization) implementations,
                // it is thrown anyway.
                throw new SQLTransientException(e.getMessage(),
                                                "25000", // invalid transaction state, no subclass
                                                e);
            }

            // Make the connection not closeable while the transaction is not completed.  If this fails, the
            // synchronization we registered will still run, but our state won't change.
            super.setCloseable(false);

        }

    }


    /*
     * Static methods.
     */


    static Connection connection(TransactionSupplier tm,
                                 TransactionSynchronizationRegistry tsr,
                                 Connection c) {
        return new JTAConnection(tm, tsr, c);
    }

    // (Method reference.)
    private static Connection connection(Xid xid) {
        assert HANDOFF_LOCK.isHeldByCurrentThread();
        try {
            return (Connection) handoff;
        } finally {
            handoff = xid;
        }
    }

    // (Method reference.)
    private static void sink(Object ignored0, Object ignored1) {

    }


    /*
     * Inner and nested classes.
     */


    @FunctionalInterface
    interface Sync extends Synchronization {

        default void beforeCompletion() {

        }

    }

    @FunctionalInterface
    interface TransactionSupplier {

        Transaction getTransaction() throws SystemException;

    }

}
