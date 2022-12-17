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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
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
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import io.helidon.integrations.jdbc.ConditionallyCloseableConnection;
import io.helidon.integrations.jdbc.UncheckedSQLException;

import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionSynchronizationRegistry;

import static javax.transaction.xa.XAResource.TMSUCCESS;

/**
 * A JDBC 4.3-compliant {@link ConditionallyCloseableConnection} that can participate in a {@link Transaction}.
 *
 * @see #connection(TransactionSupplier, TransactionSynchronizationRegistry, boolean, ExceptionConverter, Connection)
 */
class JtaConnection extends ConditionallyCloseableConnection {


    private static final Logger LOGGER = Logger.getLogger(JtaConnection.class.getName());

    private static final String INVALID_TRANSACTION_STATE = "25000";

    // SQL state 40000 ("transaction rollback, no subclass") is unclear whether it means the SQL/local transaction or
    // the XA branch transaction or both. It's a convenient SQLState to use nonetheless for handling converting
    // RollbackExceptions to SQLExceptions.
    private static final String TRANSACTION_ROLLBACK = "40000";

    private static final VarHandle ENLISTMENT;

    static {
        try {
            ENLISTMENT = MethodHandles.lookup().findVarHandle(JtaConnection.class, "enlistment", Enlistment.class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw (ExceptionInInitializerError) new ExceptionInInitializerError(e.getMessage()).initCause(e);
        }
    }


    /*
     * Instance fields.
     */


    /**
     * A supplier of {@link Transaction} objects. Often initialized to {@link
     * jakarta.transaction.TransactionManager#getTransaction() transactionManager::getTransaction}.
     *
     * <p>This field is never {@code null}.</p>
     *
     * @see TransactionSupplier
     *
     * @see jakarta.transaction.TransactionManager#getTransaction()
     */
    private final TransactionSupplier ts;

    /**
     * A {@link TransactionSynchronizationRegistry}.
     *
     * <p>This field is never {@code null}.</p>
     *
     * @see TransactionSynchronizationRegistry
     */
    private final TransactionSynchronizationRegistry tsr;

    /**
     * Whether any {@link Synchronization}s registered by this {@link JtaConnection}
     * should be registered as interposed synchronizations.
     *
     * @see TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization)
     *
     * @see Transaction#registerSynchronization(Synchronization)
     */
    private final boolean interposedSynchronizations;

    /**
     * An {@link ExceptionConverter}.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see ExceptionConverter
     *
     * @see LocalXAResource#LocalXAResource(Function, ExceptionConverter)
     */
    private final ExceptionConverter exceptionConverter;

    private final XAResource xaResource;

    private volatile Enlistment enlistment;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JtaConnection}.
     *
     * @param transactionSupplier a {@link TransactionSupplier}; must not be {@code null}; often {@link
     * jakarta.transaction.TransactionManager#getTransaction() transactionManager::getTransaction}
     *
     * @param transactionSynchronizationRegistry a {@link TransactionSynchronizationRegistry}; must not be {@code null}
     *
     * @param interposedSynchronizations whether any {@link Synchronization}s registered by this {@link JtaConnection}
     * should be registered as interposed synchronizations; see {@link
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization)} and {@link
     * Transaction#registerSynchronization(Synchronization)}
     *
     * @param exceptionConverter an {@link ExceptionConverter}; may be {@code null}
     *
     * @param delegate a {@link Connection} that was not sourced from an invocation of {@link
     * javax.sql.XAConnection#getConnection()}; must not be {@code null}
     *
     * @param immediateEnlistment whether an attempt to enlist the new {@link JtaConnection} in a global transaction, if
     * there is one, will be made immediately
     *
     * @exception NullPointerException if any parameter is {@code null}
     *
     * @exception SQLException if transaction enlistment fails
     */
    JtaConnection(TransactionSupplier transactionSupplier,
                  TransactionSynchronizationRegistry transactionSynchronizationRegistry,
                  boolean interposedSynchronizations,
                  ExceptionConverter exceptionConverter,
                  Connection delegate,
                  boolean immediateEnlistment)
        throws SQLException {
        this(transactionSupplier,
             transactionSynchronizationRegistry,
             interposedSynchronizations,
             exceptionConverter,
             delegate,
             null,
             immediateEnlistment);
    }

    JtaConnection(TransactionSupplier transactionSupplier,
                  TransactionSynchronizationRegistry transactionSynchronizationRegistry,
                  boolean interposedSynchronizations,
                  ExceptionConverter exceptionConverter,
                  Connection delegate,
                  XAResource xaResource,
                  boolean immediateEnlistment)
        throws SQLException {
        super(delegate,
              true, // closeable
              true); // strict isClosed checking; always a good thing
        this.ts = Objects.requireNonNull(transactionSupplier, "transactionSupplier");
        this.tsr = Objects.requireNonNull(transactionSynchronizationRegistry, "transactionSynchronizationRegistry");
        this.interposedSynchronizations = interposedSynchronizations;
        this.exceptionConverter = exceptionConverter; // nullable
        this.xaResource = xaResource; // nullable
        if (immediateEnlistment) {
            this.enlist();
        }
    }


    /*
     * Instance methods.
     */

<<<<<<< HEAD
=======

>>>>>>> 12907b44f4 (Squashable commit; interim work; tried to quash the git case silliness around JtaConnection)
    @Override // ConditionallyCloseableConnection
    public final void setCloseable(boolean closeable) {
        super.setCloseable(closeable);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "setCloseable", closeable);
            LOGGER.exiting(this.getClass().getName(), "setCloseable");
        }
    }

    @Override // ConditionallyCloseableConnection
    public final Statement createStatement() throws SQLException {
        this.enlist();
        return super.createStatement();
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql) throws SQLException {
        this.enlist();
        return super.prepareStatement(sql);
    }

    @Override // ConditionallyCloseableConnection
    public final CallableStatement prepareCall(String sql) throws SQLException {
        this.enlist();
        return super.prepareCall(sql);
    }

    @Override // ConditionallyCloseableConnection
    public final String nativeSQL(String sql) throws SQLException {
        this.enlist();
        return super.nativeSQL(sql);
    }

    @Override // ConditionallyCloseableConnection
    public final void setAutoCommit(boolean autoCommit) throws SQLException {
        this.enlist();
        if (autoCommit && this.enlisted0()) {
            // "SQLException...if...setAutoCommit(true) is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", INVALID_TRANSACTION_STATE);
        }
        super.setAutoCommit(autoCommit);
    }

    @Override // ConditionallyCloseableConnection
    public final boolean getAutoCommit() throws SQLException {
        this.enlist();
        return super.getAutoCommit();
    }

    @Override // ConditionallyCloseableConnection
    public final void commit() throws SQLException {
        this.enlist();
        if (this.enlisted0()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", INVALID_TRANSACTION_STATE);
        }
        super.commit();
    }

    @Override // ConditionallyCloseableConnection
    public final void rollback() throws SQLException {
        this.enlist();
        if (this.enlisted0()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", INVALID_TRANSACTION_STATE);
        }
        super.rollback();
    }

    @Override // ConditionallyCloseableConnection
    public final DatabaseMetaData getMetaData() throws SQLException {
        this.enlist();
        return super.getMetaData();
    }

    @Override // ConditionallyCloseableConnection
    public final void setReadOnly(boolean readOnly) throws SQLException {
        this.enlist();
        super.setReadOnly(readOnly);
    }

    @Override // ConditionallyCloseableConnection
    public final boolean isReadOnly() throws SQLException {
        this.enlist();
        return super.isReadOnly();
    }

    @Override // ConditionallyCloseableConnection
    public final void setCatalog(String catalog) throws SQLException {
        this.enlist();
        super.setCatalog(catalog);
    }

    @Override // ConditionallyCloseableConnection
    public final String getCatalog() throws SQLException {
        this.enlist();
        return super.getCatalog();
    }

    @Override // ConditionallyCloseableConnection
    public final void setTransactionIsolation(int level) throws SQLException {
        this.enlist();
        super.setTransactionIsolation(level);
    }

    @Override // ConditionallyCloseableConnection
    public final int getTransactionIsolation() throws SQLException {
        this.enlist();
        return super.getTransactionIsolation();
    }

    @Override // ConditionallyCloseableConnection
    public final Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        this.enlist();
        return super.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        this.enlist();
        return super.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override // ConditionallyCloseableConnection
    public final CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        this.enlist();
        return super.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override // ConditionallyCloseableConnection
    public final Map<String, Class<?>> getTypeMap() throws SQLException {
        this.enlist();
        return super.getTypeMap();
    }

    @Override // ConditionallyCloseableConnection
    public final void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        this.enlist();
        super.setTypeMap(map);
    }

    @Override // ConditionallyCloseableConnection
    public final void setHoldability(int holdability) throws SQLException {
        this.enlist();
        super.setHoldability(holdability);
    }

    @Override // ConditionallyCloseableConnection
    public final int getHoldability() throws SQLException {
        this.enlist();
        return super.getHoldability();
    }

    @Override // ConditionallyCloseableConnection
    public final Savepoint setSavepoint() throws SQLException {
        this.enlist();
        if (this.enlisted0()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            //
            // Use IBM's very descriptive 3B503 SQL state ("A SAVEPOINT, RELEASE SAVEPOINT, or ROLLBACK TO SAVEPOINT is
            // not allowed in a trigger, function, or global transaction").
            throw new SQLNonTransientException("Connection enlisted in transaction", "3B503");
        }
        return super.setSavepoint();
    }

    @Override // ConditionallyCloseableConnection
    public final Savepoint setSavepoint(String name) throws SQLException {
        this.enlist();
        if (this.enlisted0()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            //
            // Use IBM's very descriptive 3B503 SQL state ("A SAVEPOINT, RELEASE SAVEPOINT, or ROLLBACK TO SAVEPOINT is
            // not allowed in a trigger, function, or global transaction").
            throw new SQLNonTransientException("Connection enlisted in transaction", "3B503");
        }
        return super.setSavepoint(name);
    }

    @Override // ConditionallyCloseableConnection
    public final void rollback(Savepoint savepoint) throws SQLException {
        this.enlist();
        if (this.enlisted0()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            //
            // Use IBM's very descriptive 3B503 SQL state ("A SAVEPOINT, RELEASE SAVEPOINT, or ROLLBACK TO SAVEPOINT is
            // not allowed in a trigger, function, or global transaction").
            throw new SQLNonTransientException("Connection enlisted in transaction", "3B503");
        }
        super.rollback(savepoint);
    }

    @Override // ConditionallyCloseableConnection
    public final void releaseSavepoint(Savepoint savepoint) throws SQLException {
        this.enlist();
        if (this.enlisted0()) {
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
    public final Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException {
        this.enlist();
        return super.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql, int rsType, int rsConcurrency, int rsHoldability)
      throws SQLException {
        this.enlist();
        return super.prepareStatement(sql, rsType, rsConcurrency, rsHoldability);
    }

    @Override // ConditionallyCloseableConnection
    public final CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException {
        this.enlist();
        return super.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        this.enlist();
        return super.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        this.enlist();
        return super.prepareStatement(sql, columnIndexes);
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        this.enlist();
        return super.prepareStatement(sql, columnNames);
    }

    @Override // ConditionallyCloseableConnection
    public final Clob createClob() throws SQLException {
        this.enlist();
        return super.createClob();
    }

    @Override // ConditionallyCloseableConnection
    public final Blob createBlob() throws SQLException {
        this.enlist();
        return super.createBlob();
    }

    @Override // ConditionallyCloseableConnection
    public final NClob createNClob() throws SQLException {
        this.enlist();
        return super.createNClob();
    }

    @Override // ConditionallyCloseableConnection
    public final SQLXML createSQLXML() throws SQLException {
        this.enlist();
        return super.createSQLXML();
    }

    @Override // ConditionallyCloseableConnection
    public final boolean isValid(int timeout) throws SQLException {
        this.enlist();
        return super.isValid(timeout);
    }

    @Override // ConditionallyCloseableConnection
    public final void setClientInfo(String name, String value) throws SQLClientInfoException {
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
    public final void setClientInfo(Properties properties) throws SQLClientInfoException {
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
    public final String getClientInfo(String name) throws SQLException {
        this.enlist();
        return super.getClientInfo(name);
    }

    @Override // ConditionallyCloseableConnection
    public final Properties getClientInfo() throws SQLException {
        this.enlist();
        return super.getClientInfo();
    }

    @Override // ConditionallyCloseableConnection
    public final Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        this.enlist();
        return super.createArrayOf(typeName, elements);
    }

    @Override // ConditionallyCloseableConnection
    public final Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        this.enlist();
        return super.createStruct(typeName, attributes);
    }

    @Override // ConditionallyCloseableConnection
    public final void setSchema(String schema) throws SQLException {
        this.enlist();
        super.setSchema(schema);
    }

    @Override // ConditionallyCloseableConnection
    public final String getSchema() throws SQLException {
        this.enlist();
        return super.getSchema();
    }

    @Override // ConditionallyCloseableConnection
    public final void abort(Executor executor) throws SQLException {
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
    public final void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        this.enlist();
        super.setNetworkTimeout(executor, milliseconds);
    }

    @Override // ConditionallyCloseableConnection
    public final int getNetworkTimeout() throws SQLException {
        this.enlist();
        return super.getNetworkTimeout();
    }

    @Override // ConditionallyCloseableConnection
    public final void beginRequest() throws SQLException {
        this.enlist();
        super.beginRequest();
    }

    @Override // ConditionallyCloseableConnection
    public final void endRequest() throws SQLException {
        this.enlist();
        super.endRequest();
    }

    @Override // ConditionallyCloseableConnection
    public final boolean setShardingKeyIfValid(ShardingKey shardingKey, ShardingKey superShardingKey, int timeout)
        throws SQLException {
        this.enlist();
        return super.setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
    }

    @Override // ConditionallyCloseableConnection
    public final boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout) throws SQLException {
        this.enlist();
        return super.setShardingKeyIfValid(shardingKey, timeout);
    }

    @Override // ConditionallyCloseableConnection
    public final void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey) throws SQLException {
        this.enlist();
        super.setShardingKey(shardingKey, superShardingKey);
    }

    @Override // ConditionallyCloseableConnection
    public final void setShardingKey(ShardingKey shardingKey) throws SQLException {
        this.enlist();
        super.setShardingKey(shardingKey);
    }

    @Override // ConditionallyCloseableConnection
    public final void close() throws SQLException {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "close");
        }
        // The JTA Specification, section 4.2, has a non-normative diagram illustrating that close() is expected,
        // but not required, to call Transaction#delistResource(XAResource). This is, mind you, before the
        // prepare/commit cycle has started.
        if (this.activeOrMarkedRollbackTransaction()) {
            try {
                XAResource xar = (XAResource) this.tsr.getResource(this);
                if (xar != null) {
                    // TMSUCCESS because it's an ordinary close() call, not a delisting due to an exception
                    Transaction t = this.transaction();
                    t.delistResource(xar, TMSUCCESS);
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.logp(Level.FINE,
                                    this.getClass().getName(), "close",
                                    "Delisted {0} from Transaction {1}", new Object[] {xar, t});
                    }
                }
            } catch (IllegalStateException e) {
                // Transaction went from active or marked for rollback to some other state; whatever; we're no longer
                // enlisted
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, this.getClass().getName(), "close", e.getMessage(), e);
                }
            } catch (SystemException | RuntimeException e) {
                // Why do we catch RuntimeException as well here? See
                // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/transaction/arjunacore/TransactionSynchronizationRegistryImple.java#L213-L235;
                // getTransactionImple() is called by Narayana's implementations of getResource() and putResource().
                // getResource() and putResource() are not documented to throw RuntimeException, only
                // IllegalStateException. Nevertheless a RuntimeException is thrown when a SystemException is
                // encountered.
                throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE, e);
            }
        }
        super.close();
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(this.getClass().getName(), "close");
        }
    }

    @Override // Object
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    @Override // Object
    public final boolean equals(Object other) {
        return this == other;
    }

    /**
     * Returns {@code true} if a JTA transaction exists and {@linkplain
     * TransactionSynchronizationRegistry#getTransactionStatus() has a status} equal to either {@link
     * Status#STATUS_ACTIVE} or {@link Status#STATUS_MARKED_ROLLBACK}.
     *
     * @return {@code true} if a JTA transaction exists and {@linkplain
     * TransactionSynchronizationRegistry#getTransactionStatus() has a status} equal to either {@link
     * Status#STATUS_ACTIVE} or {@link Status#STATUS_MARKED_ROLLBACK}; {@code false} in all other cases
     *
     * @exception SQLException if the status could not be acquired
     *
     * @see TransactionSynchronizationRegistry#getTransactionStatus()
     *
     * @see Status
     */
    private boolean activeOrMarkedRollbackTransaction() throws SQLException {
        switch (this.transactionStatus()) {
            // See https://www.eclipse.org/lists/jta-dev/msg00264.html and
            // https://github.com/jakartaee/transactions/issues/211.
        case Status.STATUS_ACTIVE:
        case Status.STATUS_MARKED_ROLLBACK:
            return true;
        default:
            return false;
        }
    }

    private int transactionStatus() throws SQLException {
        try {
            return this.tsr.getTransactionStatus();
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
            throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE, e);
        }
    }

    /**
     * Returns {@code true} if and only if this {@link JtaConnection} is associated with a JTA transaction whose
     * {@linkplain Transaction#getStatus() status} is one of {@link Status#STATUS_ACTIVE} or {@link
     * Status#STATUS_MARKED_ROLLBACK} as a result of a prior {@link #enlist()} invocation on the current thread.
     *
     * @return {@code true} if and only if this {@link JtaConnection} is associated with a {@linkplain
     * #activeOrMarkedRollbackTransaction() JTA transaction whose status is known and not yet prepared}; {@code false}
     * in all other cases
     *
     * @exception SQLException if invoked on a closed connection or the enlisted status could not be acquired
     */
    boolean enlisted() throws SQLException {
        this.failWhenClosed();
        return this.enlisted0();
    }

    private boolean enlisted0() throws SQLException {
        Enlistment enlistment = this.enlistment; // volatile read
        if (enlistment == null) {
            // Not enlisted. Common.
            return false;
        } else if (enlistment.threadId() != Thread.currentThread().getId()) {
            // We're enlisted in a Transaction on thread 1, and a caller from thread 2 is trying to do something with
            // us. This could have unintended side effects. Throw.
            throw new SQLTransientException("Already enlisted (" + enlistment + "); current thread id: "
                                            + Thread.currentThread().getId(), INVALID_TRANSACTION_STATE);
        }

        int enlistmentTransactionStatus;
        try {
            enlistmentTransactionStatus = enlistment.transaction().getStatus();
        } catch (RuntimeException | SystemException e) {
            // Why do we catch RuntimeException as well here? See
            // https://github.com/jbosstm/narayana/blob/c5f02d07edb34964b64341974ab689ea44536603/ArjunaJTA/jta/classes/com/arjuna/ats/internal/jta/transaction/arjunacore/TransactionSynchronizationRegistryImple.java#L213-L235;
            // getTransactionImple() is called by Narayana's implementations of getResource() and putResource().
            // getResource() and putResource() are not documented to throw RuntimeException, only
            // IllegalStateException. Nevertheless a RuntimeException is thrown when a SystemException is
            // encountered.
            throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE, e);
        }

        switch (enlistmentTransactionStatus) {
        case Status.STATUS_ACTIVE:
            // Already enlisted; transaction is active; everything is fine. Return true.
            return true;
        case Status.STATUS_COMMITTED:
        case Status.STATUS_ROLLEDBACK:
            // We *were* enlisted, and sort of still are (there's a non-null Enlistment), but now the transaction is
            // irrevocably completed and the non-null Enlistment will be removed momentarily by another thread, so
            // really for all intents and purposes we're no longer enlisted.
            return false;
        case Status.STATUS_COMMITTING:
        case Status.STATUS_MARKED_ROLLBACK:
        case Status.STATUS_PREPARED:
        case Status.STATUS_PREPARING:
        case Status.STATUS_ROLLING_BACK:
            // Interim or effectively interim. Throw to prevent accidental side effects.
            throw new SQLTransientException("Non-terminal transaction status: " + enlistmentTransactionStatus,
                                            INVALID_TRANSACTION_STATE);
        case Status.STATUS_NO_TRANSACTION:
            // Somehow an Enlistment was created with a Transaction in the Status.STATUS_NO_TRANSACTION status. This
            // should never happen.
            throw new AssertionError();
        case Status.STATUS_UNKNOWN:
        default:
            // Unexpected or illegal. Throw.
            throw new SQLTransientException("Unexpected transaction status: " + enlistmentTransactionStatus,
                                            INVALID_TRANSACTION_STATE);
        }
    }

    private Transaction transaction() throws SQLException {
        try {
            return this.ts.getTransaction();
        } catch (RuntimeException | SystemException e) {
            throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE, e);
        }
    }

    /**
     * Attempts to enlist this {@link JtaConnection} in the current JTA transaction, if there is one, and its status is
     * {@link Status#STATUS_ACTIVE}, and this {@link JtaConnection} is not already {@linkplain #enlisted() enlisted}.
     *
     * @exception SQLException if invoked on a closed connection or a transaction-related error occurs, or if the return
     * value of an invocation of the {@link #getAutoCommit()} method returns {@code false}
     *
     * @see #enlisted()
     */
    @SuppressWarnings("checkstyle:MethodLength")
    private void enlist() throws SQLException {
        this.failWhenClosed();

        int transactionStatus;
        int currentThreadTransactionStatus;

        // First, see if we're already enlisted. We can't just use enlisted0() here for that purpose because we have to
        // check additional things based on the status. So there is some unavoidable duplication of logic.

        Enlistment enlistment = this.enlistment; // volatile read
        if (enlistment != null) {
            // We are, or were, enlisted. Let's see in what way.
            if (enlistment.threadId() != Thread.currentThread().getId()) {
                // We're enlisted in a Transaction on thread 1, and a caller from thread 2 is trying to do something with
                // us. This could have unintended side effects. Throw.
                throw new SQLTransientException("Already enlisted (" + enlistment + "); current thread id: "
                                                + Thread.currentThread().getId(), INVALID_TRANSACTION_STATE);
            }
            // We're enlisted in a Transaction that was created on the current thread. So far so good. Is it active?
            Transaction t = enlistment.transaction();
            transactionStatus = statusFrom(t);
            switch (transactionStatus) {
            case Status.STATUS_ACTIVE:
                // We have been enlisted in an active transaction that was created on this thread. Is the current
                // transaction, whatever it is, active?
                currentThreadTransactionStatus = this.transactionStatus();
                switch (currentThreadTransactionStatus) {
                case Status.STATUS_ACTIVE:
                    // We have been enlisted in an active transaction that was created on this thread AND the current
                    // thread's transaction status is ALSO active. Is the current thread's transaction equal to the one
                    // we're enlisted in?  Or is it a different one (in which case our transaction has been suspended)?
                    if (t.equals(this.transaction())) {
                        // The Transaction associated with the current thread is the active one we're enlisted with, so
                        // we're already enlisted and everything is fine. (Equality is governed by the spec:
                        // https://jakarta.ee/specifications/transactions/2.0/jakarta-transactions-spec-2.0.html#transaction-equality-and-hash-code)
                        return;
                    }
                    throw new SQLTransientException("Attempting to perform work while associated with a suspended transaction",
                                                    INVALID_TRANSACTION_STATE);
                case Status.STATUS_COMMITTED:
                case Status.STATUS_ROLLEDBACK:
                case Status.STATUS_COMMITTING:
                case Status.STATUS_MARKED_ROLLBACK:
                case Status.STATUS_PREPARED:
                case Status.STATUS_PREPARING:
                case Status.STATUS_ROLLING_BACK:
                case Status.STATUS_NO_TRANSACTION:
                    // The current thread's transaction status is not active, so it can't be the same transaction with
                    // which we are enlisted, so by definition we're enlisted in a suspended transaction and no further
                    // work should be done until that transaction is resumed.
                    throw new SQLTransientException("Attempting to perform work while associated with a suspended transaction",
                                                    INVALID_TRANSACTION_STATE);
                case Status.STATUS_UNKNOWN:
                default:
                    // Unexpected or illegal. Throw.
                    throw new SQLTransientException("Unexpected transaction status: " + currentThreadTransactionStatus,
                                                    INVALID_TRANSACTION_STATE);
                }
            case Status.STATUS_COMMITTED:
            case Status.STATUS_ROLLEDBACK:
                // We *were* enlisted, and sort of still are (there's a non-null Enlistment), but now the transaction is
                // irrevocably completed, and the non-null Enlistment will be removed momentarily by another thread
                // executing our transactionCompleted(int) method, so really for all intents and purposes we're no
                // longer enlisted. Keep going.
                break;
            case Status.STATUS_COMMITTING:
            case Status.STATUS_MARKED_ROLLBACK:
            case Status.STATUS_PREPARED:
            case Status.STATUS_PREPARING:
            case Status.STATUS_ROLLING_BACK:
                // Interim or effectively interim. Throw to prevent accidental side effects.
                throw new SQLTransientException("Non-terminal transaction status: " + transactionStatus,
                                                INVALID_TRANSACTION_STATE);
            case Status.STATUS_NO_TRANSACTION:
                // Somehow an Enlistment was created with a Transaction in the Status.STATUS_NO_TRANSACTION status. This
                // should absolutely never happen.
                throw new AssertionError();
            case Status.STATUS_UNKNOWN:
            default:
                // Unexpected or illegal. Throw.
                throw new SQLTransientException("Unexpected transaction status: " + transactionStatus, INVALID_TRANSACTION_STATE);
            }
        }
        assert enlistment == null;

        // We've concluded we are neither enlisted nor in an error condition. Keep going.

        currentThreadTransactionStatus = this.transactionStatus();
        switch (currentThreadTransactionStatus) {
        case Status.STATUS_ACTIVE:
            // There is a global transaction currently active on the current thread. That's good. Keep going.
            break;
        case Status.STATUS_COMMITTED:
        case Status.STATUS_NO_TRANSACTION:
        case Status.STATUS_ROLLEDBACK:
            // There is no global transaction or there is very shortly about to be no global transaction on the current
            // thread; this is an effectively terminal state. Enlistment is impossible or silly. Very common. Return
            // without enlisting.
            return;
        case Status.STATUS_COMMITTING:
        case Status.STATUS_MARKED_ROLLBACK:
        case Status.STATUS_PREPARED:
        case Status.STATUS_PREPARING:
        case Status.STATUS_ROLLING_BACK:
            // Interim or effectively interim state. Uncommon. Throw to prevent accidental side effects.
            throw new SQLTransientException("Non-terminal current thread transaction status: " + currentThreadTransactionStatus,
                                            INVALID_TRANSACTION_STATE);
        case Status.STATUS_UNKNOWN:
        default:
            // Unexpected or illegal state. Throw.
            throw new SQLTransientException("Unexpected current thread transaction status: " + currentThreadTransactionStatus,
                                            INVALID_TRANSACTION_STATE);
        }

        if (!super.getAutoCommit()) {
            // There is, as far as we can tell, an active global transaction on the current thread, and
            // super.getAutoCommit() (super. on purpose, not this.) returned false, and we aren't (yet) enlisted with
            // the active global transaction, so autoCommit must have been disabled on purpose by the caller, not by the
            // transaction enlistment machinery. In such a case, we don't want to permit enlistment, because a local
            // transaction may be in progress and we don't want to have its effects mixed in.
            throw new SQLTransientException("autoCommit was false during active transaction enlistment",
                                            INVALID_TRANSACTION_STATE);
        }

        // Guaranteed not to throw RuntimeException. t is guaranteed by spec to be non-null because the status was, at
        // one point above, Status.STATUS_ACTIVE on the current thread (by definition).
        Transaction t = this.transaction();
        transactionStatus = statusFrom(t);

        switch (transactionStatus) {
        case Status.STATUS_ACTIVE:
            // No one has started or finished the transaction completion process yet. Most common. Keep going.
            break;
        case Status.STATUS_COMMITTED:
            // Terminal. Return without enlisting.
            //
            // (Recall that technically commits (not just rollbacks) can occur on other threads, so it is conceivable
            // that the status could have changed from Status.STATUS_ACTIVE to Status.STATUS_COMMITTED in between when
            // we checked this.transactionStatus() and now.
            return;
        case Status.STATUS_ROLLEDBACK:
            // Terminal. Return without enlisting.
            //
            // Rollbacks often happen on other threads as a result of timeouts.
            return;
        case Status.STATUS_COMMITTING:
        case Status.STATUS_MARKED_ROLLBACK:
        case Status.STATUS_PREPARED:
        case Status.STATUS_PREPARING:
        case Status.STATUS_ROLLING_BACK:
            // Interim or effectively interim. Throw to prevent accidental side effects.
            throw new SQLTransientException("Non-terminal transaction status: " + transactionStatus, INVALID_TRANSACTION_STATE);
        case Status.STATUS_NO_TRANSACTION:
            // Impossible state machine transition since t is non-null and at least at one earlier point on this thread
            // the status was Status.STATUS_ACTIVE. Even if somehow the global transaction is disassociated from the
            // current thread, the Transaction object's status will never, by spec, go to
            // Status.STATUS_NO_TRANSACTION. See
            // https://groups.google.com/g/narayana-users/c/eYVUmhE9QZg/m/xbBh2CsBBQAJ.
            throw new AssertionError(); // per spec
        case Status.STATUS_UNKNOWN:
        default:
            // Unexpected or illegal. Throw.
            throw new SQLTransientException("Unexpected transaction status: " + transactionStatus, INVALID_TRANSACTION_STATE);
        }

        // Point of no return. We ensured that the Transaction at one point had a status of Status.STATUS_ACTIVE and
        // ensured we aren't already enlisted and our autoCommit status is true. The Transaction's status can still
        // change at any point (as a result of asynchronous rollback, for example) through certain permitted state
        // transitions, so we have to watch for exceptions.

        XAResource xar =
            this.xaResource == null ? new LocalXAResource(this::connectionFunction, this.exceptionConverter) : this.xaResource;
        enlistment = new Enlistment(t, xar);
        if (!ENLISTMENT.compareAndSet(this, null, enlistment)) { // atomic volatile write
            // Setting this.enlistment could conceivably fail if another thread already enlisted this JtaConnection.
            // That would be bad.
            //
            // (The this.enlistment read in the exception message is a volatile read, and thanks to the compareAndSet
            // call above we know it is non-null.)
            throw new SQLTransientException("Already enlisted (" + this.enlistment
                                            + "); current transaction: " + t
                                            + "; current thread id: " + Thread.currentThread().getId(),
                                            INVALID_TRANSACTION_STATE);
        }

        try {
            // The XAResource is placed into the TransactionSynchronizationRegistry so that it can be delisted if
            // appropriate during invocation of the close() method (q.v). We do it before the actual
            // Transaction#enlistResource(XAResource) call on purpose.
            this.tsr.putResource(this, xar);
            if (this.interposedSynchronizations) {
                this.tsr.registerInterposedSynchronization((Sync) this::transactionCompleted);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, this.getClass().getName(), "connectionFunction",
                                "Registered interposed synchronization (transactionCompleted(int)) for {0}", t);
                }
            } else {
                t.registerSynchronization((Sync) this::transactionCompleted);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, this.getClass().getName(), "connectionFunction",
                                "Registered synchronization (transactionCompleted(int)) for {0}", t);
                }
            }
            // Don't let our caller close us "for real". close() invocations will be recorded as pending. See #close().
            this.setCloseable(false);
            // (Guaranteed to call xar.start(Xid, int) on this thread.)
            t.enlistResource(xar);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE,
                            this.getClass().getName(), "enlist",
                            "Enlisted {0} in transaction {1}", new Object[] {xar, t});
            }
        } catch (RollbackException e) {
            this.enlistment = null; // volatile write
            // The enlistResource(XAResource) or registerSynchronization(Synchronization) operation failed because the
            // transaction was rolled back.
            throw new SQLNonTransientException(e.getMessage(), TRANSACTION_ROLLBACK, e);
        } catch (RuntimeException | SystemException e) {
            this.enlistment = null; // volatile write
            if (e.getCause() instanceof RollbackException) {
                // The enlistResource(XAResource) operation failed because the transaction was rolled back.
                throw new SQLNonTransientException(e.getMessage(), TRANSACTION_ROLLBACK, e);
            }
            // The t.enlistResource(XAResource) operation failed, or the tsr.putResource(Object, Object) operation
            // failed, or the tsr.registerInterposedSynchronization(Synchronization) operation failed, or the
            // t.registerSynchronization(Synchronization) operation failed. In any case, no XAResource was actually
            // enlisted.
            throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE, e);
        } catch (Error e) {
            this.enlistment = null; // volatile write
            throw e;
        }
    }

    // (Used only by reference by LocalXAResource#start(Xid, int) as a result of calling
    // Transaction#enlistResource(XAResource) in enlist() above.)
    private Connection connectionFunction(Xid xid) {
        this.tsr.putResource(this.tsr.getResource(this), xid);
        return this.delegate();
    }

    // (Used only by reference in enlist() above. Remember, this callback may be called by the TransactionManager on
    // any thread at any time for any reason.)
    private void transactionCompleted(int commitedOrRolledBack) {
        this.enlistment = null; // volatile write
        try {
            if (!super.isClosed()) {
                // Did someone call close() while we were enlisted?  That's permitted by section 4.2 of the JTA
                // specification, but it shouldn't actually close the connection because the prepare/commit cycle
                // wouldn't have started.
                boolean closeWasPending = this.isClosePending(); // volatile read

                // If they did, then we must be non-closeable. If they didn't, we could be either closeable or not
                // closeable.
                assert !closeWasPending || !this.isCloseable();

                // Now the global transaction is over, so blindly set our closeable status to true. (It may already be
                // true.)  This resets the closePending status, per spec.

                this.setCloseable(true);
                assert this.isCloseable();
                assert !this.isClosePending();

                // If there WAS a close() attempt, now it CAN work, so let it work.
                if (closeWasPending) {
                    this.close();
                }
            }
        } catch (SQLException e) {
            // (Synchronization implementations can throw only unchecked exceptions.)
            throw new UncheckedSQLException(e);
        }
    }


    /*
     * Static methods.
     */


    private static int statusFrom(Transaction t) throws SQLException {
        Objects.requireNonNull(t, "t");
        try {
            return t.getStatus();
        } catch (RuntimeException | SystemException e) {
            throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE, e);
        }
    }


    /*
     * Inner and nested classes.
     */


    /**
     * A functional {@link Synchronization}.
     *
     * @see Synchronization
     */
    @FunctionalInterface
    interface Sync extends Synchronization {

        /**
         * Called prior to the start of the two-phase transaction commit process.
         *
         * <p>The default implementation of this method does nothing.</p>
         *
         * @see Synchronization#beforeCompletion()
         */
        default void beforeCompletion() {

        }

    }

    private static final record Enlistment(long threadId, Transaction transaction, XAResource xaResource) {

        private Enlistment(Transaction transaction, XAResource xaResource) {
            this(Thread.currentThread().getId(), transaction, xaResource);
        }

        private Enlistment {
            Objects.requireNonNull(transaction, "transaction");
            Objects.requireNonNull(xaResource, "xaResource");
        }

    }

}
