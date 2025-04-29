/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.sql.SQLNonTransientConnectionException;
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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import io.helidon.integrations.jdbc.ConditionallyCloseableConnection;
import io.helidon.integrations.jdbc.DelegatingConnection;
import io.helidon.integrations.jdbc.SQLSupplier;
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
 */
class JtaConnection extends ConditionallyCloseableConnection {


    /*
     * Static fields.
     */


    private static final Logger LOGGER = Logger.getLogger(JtaConnection.class.getName());

    // The standard SQL state used for unspecified connection exceptions. Used in this class primarily to indicate
    // premature connection closure.
    private static final String CONNECTION_EXCEPTION_NO_SUBCLASS = "08000";

    // The standard SQL state used for transaction-related issues.
    private static final String INVALID_TRANSACTION_STATE_NO_SUBCLASS = "25000";

    // IBM's proprietary but very descriptive, useful, and specific SQL state for when a savepoint operation has been
    // attempted during a global transaction ("A SAVEPOINT, RELEASE SAVEPOINT, or ROLLBACK TO SAVEPOINT is not allowed
    // in a trigger, function, or global transaction").
    private static final String PROHIBITED_SAVEPOINT_OPERATION = "3B503";

    // SQL state 40000 ("transaction rollback, no subclass") is unclear whether it means the SQL/local transaction or
    // the XA branch transaction or both. It's a convenient SQLState to use nonetheless for handling converting
    // RollbackExceptions to SQLExceptions.
    private static final String TRANSACTION_ROLLBACK = "40000";

    // A VarHandle to atomically and efficiently manipulate the enlistment instance field (see below).
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
     * Whether any {@link Synchronization}s registered by this {@link JtaConnection} should be {@linkplain
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization) registered as interposed
     * synchronizations} or {@linkplain Transaction#registerSynchronization(Synchronization) ordinary synchronizations}.
     *
     * @see TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization)
     *
     * @see Transaction#registerSynchronization(Synchronization)
     */
    private final boolean interposedSynchronizations;

    /**
     * Whether preemptive checks for transaction states that are illegal for enlistment should be enforced, or whether
     * instead enforcement should be delegated to the JTA implementation.
     *
     * <p>See the documentation of the exceptions thrown by the {@link
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization)}, {@link
     * TransactionSynchronizationRegistry#putResource(Object, Object)}, and the {@link
     * Transaction#enlistResource(XAResource)} methods for more information about illegal transaction statuses.</p>
     *
     * @see TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization)
     *
     * @see TransactionSynchronizationRegistry#putResource(Object, Object)
     *
     * @see Transaction#enlistResource(XAResource)
     */
    private final boolean preemptiveEnlistmentChecks;

    private final SQLSupplier<? extends XAResource> xaResourceSupplier;

    private final Consumer<? super Xid> xidConsumer;

    /**
     * The {@link Enlistment} representing this {@link JtaConnection}'s enlistment in a global (JTA) transaction, or
     * {@code null} if this {@link JtaConnection} is not enlisted.
     *
     * <p>Write access to this field must occur through the {@link #ENLISTMENT} {@link VarHandle} field or undefined
     * behavior will result.</p>
     *
     * @see #ENLISTMENT
     */
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
     * @exception SQLException if transaction enlistment fails or the supplied {@code delegate} {@linkplain
     * Connection#isClosed() is closed}
     *
     * @see #JtaConnection(TransactionSupplier, TransactionSynchronizationRegistry, boolean, ExceptionConverter,
     * Connection, Supplier, Consumer, boolean, boolean)
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
             null,
             immediateEnlistment);
    }

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
     * @param exceptionConverter an {@link ExceptionConverter}; may be {@code null}; ignored if {@code
     * xaResourceSupplier} is non-{@code null}
     *
     * @param delegate a {@link Connection} that was not sourced from an invocation of {@link
     * javax.sql.XAConnection#getConnection()}; must not be {@code null}
     *
     * @param xaResourceSupplier a {@link SQLSupplier} of an {@link XAResource} to represent this {@link JtaConnection};
     * may be and often is {@code null} in which case a new {@link LocalXAResource} will be used instead
     *
     * @param immediateEnlistment whether an attempt to enlist the new {@link JtaConnection} in a global transaction, if
     * there is one, will be made immediately
     *
     * @exception NullPointerException if {@code transactionSupplier} or {@code transactionSynchronizationRegistry} is
     * {@code null}
     *
     * @exception SQLException if transaction enlistment fails or the supplied {@code delegate} {@linkplain
     * Connection#isClosed() is closed}
     *
     * @see #JtaConnection(TransactionSupplier, TransactionSynchronizationRegistry, boolean, ExceptionConverter,
     * Connection, Supplier, Consumer, boolean, boolean)
     */
    JtaConnection(TransactionSupplier transactionSupplier,
                  TransactionSynchronizationRegistry transactionSynchronizationRegistry,
                  boolean interposedSynchronizations,
                  ExceptionConverter exceptionConverter,
                  Connection delegate,
                  SQLSupplier<? extends XAResource> xaResourceSupplier,
                  boolean immediateEnlistment)
        throws SQLException {
        this(transactionSupplier,
             transactionSynchronizationRegistry,
             interposedSynchronizations,
             exceptionConverter,
             delegate,
             xaResourceSupplier,
             null,
             immediateEnlistment);
    }

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
     * @param exceptionConverter an {@link ExceptionConverter}; may be {@code null}; ignored if {@code
     * xaResourceSupplier} is non-{@code null}
     *
     * @param delegate a {@link Connection} that was not sourced from an invocation of {@link
     * javax.sql.XAConnection#getConnection()}; must not be {@code null}
     *
     * @param xaResourceSupplier a {@link SQLSupplier} of an {@link XAResource} to represent this {@link JtaConnection};
     * may be and often is {@code null} in which case a new {@link LocalXAResource} will be used instead
     *
     * @param xidConsumer a {@link Consumer} of {@link Xid}s that will be invoked when {@code xaResource} is {@code
     * null} and a new {@link LocalXAResource} has been created and enlisted; may be {@code null}; useful mainly for
     * testing
     *
     * @param immediateEnlistment whether an attempt to enlist the new {@link JtaConnection} in a global transaction, if
     * there is one, will be made immediately
     *
     * @exception NullPointerException if {@code transactionSupplier} or {@code transactionSynchronizationRegistry} is
     * {@code null}
     *
     * @exception SQLException if transaction enlistment fails or the supplied {@code delegate} {@linkplain
     * Connection#isClosed() is closed}
     *
     * @see #JtaConnection(TransactionSupplier, TransactionSynchronizationRegistry, boolean, ExceptionConverter,
     * Connection, SQLSupplier, Consumer, boolean, boolean)
     */
    JtaConnection(TransactionSupplier transactionSupplier,
                  TransactionSynchronizationRegistry transactionSynchronizationRegistry,
                  boolean interposedSynchronizations,
                  ExceptionConverter exceptionConverter,
                  Connection delegate,
                  SQLSupplier<? extends XAResource> xaResourceSupplier,
                  Consumer<? super Xid> xidConsumer,
                  boolean immediateEnlistment)
        throws SQLException {
        this(transactionSupplier,
             transactionSynchronizationRegistry,
             interposedSynchronizations,
             exceptionConverter,
             delegate,
             xaResourceSupplier,
             xidConsumer,
             immediateEnlistment,
             true); // preemptive enlistment checks by default
    }

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
     * @param exceptionConverter an {@link ExceptionConverter}; may be {@code null}; ignored if {@code
     * xaResourceSupplier} is non-{@code null}
     *
     * @param delegate a {@link Connection} that was not sourced from an invocation of {@link
     * javax.sql.XAConnection#getConnection()}; must not be {@code null}
     *
     * @param xaResourceSupplier a {@link SQLSupplier} of an {@link XAResource} to represent this {@link JtaConnection};
     * may be and often is {@code null} in which case a new {@link LocalXAResource} will be used instead
     *
     * @param xidConsumer a {@link Consumer} of {@link Xid}s that will be invoked when {@code xaResource} is {@code
     * null} and a new {@link LocalXAResource} has been created and enlisted; may be {@code null}; useful mainly for
     * testing
     *
     * @param immediateEnlistment whether an attempt to enlist the new {@link JtaConnection} in a global transaction, if
     * there is one, will be made immediately
     *
     * @param preemptiveEnlistmentChecks whether early checks will be made to see if an enlistment attempt will succeed,
     * or whether enlistment validation will be performed by the JTA implementation
     *
     * @exception NullPointerException if {@code transactionSupplier} or {@code transactionSynchronizationRegistry} is
     * {@code null}
     *
     * @exception SQLException if transaction enlistment fails or the supplied {@code delegate} {@linkplain
     * Connection#isClosed() is closed}
     */
    JtaConnection(TransactionSupplier transactionSupplier,
                  TransactionSynchronizationRegistry transactionSynchronizationRegistry,
                  boolean interposedSynchronizations,
                  ExceptionConverter exceptionConverter,
                  Connection delegate,
                  SQLSupplier<? extends XAResource> xaResourceSupplier,
                  Consumer<? super Xid> xidConsumer,
                  boolean immediateEnlistment,
                  boolean preemptiveEnlistmentChecks)
        throws SQLException {
        super(delegate,
              true, // closeable
              true); // strict isClosed checking; always a good thing
        this.ts = Objects.requireNonNull(transactionSupplier, "transactionSupplier");
        this.tsr = Objects.requireNonNull(transactionSynchronizationRegistry, "transactionSynchronizationRegistry");
        if (delegate.isClosed()) {
            throw new SQLNonTransientConnectionException("delegate is closed", CONNECTION_EXCEPTION_NO_SUBCLASS);
        }
        this.interposedSynchronizations = interposedSynchronizations;
        this.preemptiveEnlistmentChecks = preemptiveEnlistmentChecks;
        this.xaResourceSupplier =
            xaResourceSupplier == null
            ? () -> new LocalXAResource(this::connectionFunction, exceptionConverter)
            : xaResourceSupplier;
        this.xidConsumer = xidConsumer == null ? JtaConnection::sink : xidConsumer;
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.logp(Level.FINE, this.getClass().getName(), "<init>",
                        "Creating {0} using delegate {1} on thread {2}", new Object[] {this, delegate, Thread.currentThread()});
        }
        if (immediateEnlistment) {
            this.enlist();
        }
    }


    /*
     * Instance methods.
     */


    @Override // ConditionallyCloseableConnection
    public final void setCloseable(boolean closeable) {
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(this.getClass().getName(), "setCloseable", closeable);
            super.setCloseable(closeable);
            LOGGER.exiting(this.getClass().getName(), "setCloseable");
        } else {
            super.setCloseable(closeable);
        }
    }

    @Override // ConditionallyCloseableConnection
    public final Statement createStatement() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.createStatement();
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.prepareStatement(sql);
    }

    @Override // ConditionallyCloseableConnection
    public final CallableStatement prepareCall(String sql) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.prepareCall(sql);
    }

    @Override // ConditionallyCloseableConnection
    public final String nativeSQL(String sql) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.nativeSQL(sql);
    }

    @Override // ConditionallyCloseableConnection
    public final void setAutoCommit(boolean autoCommit) throws SQLException {
        this.failWhenClosed();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.logp(Level.FINE, this.getClass().getName(), "setAutoCommit",
                        "Setting autoCommit on {0} to {1}", new Object[] {this, autoCommit});
        }
        this.enlist();
        if (autoCommit && this.enlisted()) {
            // "SQLException...if...setAutoCommit(true) is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", INVALID_TRANSACTION_STATE_NO_SUBCLASS);
        }
        super.setAutoCommit(autoCommit);
    }

    @Override // ConditionallyCloseableConnection
    public final boolean getAutoCommit() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        boolean ac = super.getAutoCommit();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.logp(Level.FINE, this.getClass().getName(), "getAutoCommit",
                        "Getting autoCommit ({0}) on {1}", new Object[] {ac, this});
        }
        return ac;
    }

    @Override // ConditionallyCloseableConnection
    public final void commit() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        if (this.enlisted()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", INVALID_TRANSACTION_STATE_NO_SUBCLASS);
        }
        super.commit();
    }

    @Override // ConditionallyCloseableConnection
    public final void rollback() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        if (this.enlisted()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", INVALID_TRANSACTION_STATE_NO_SUBCLASS);
        }
        super.rollback();
    }

    @Override // ConditionallyCloseableConnection
    public final DatabaseMetaData getMetaData() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.getMetaData();
    }

    @Override // ConditionallyCloseableConnection
    public final void setReadOnly(boolean readOnly) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        super.setReadOnly(readOnly);
    }

    @Override // ConditionallyCloseableConnection
    public final boolean isReadOnly() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.isReadOnly();
    }

    @Override // ConditionallyCloseableConnection
    public final void setCatalog(String catalog) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        super.setCatalog(catalog);
    }

    @Override // ConditionallyCloseableConnection
    public final String getCatalog() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.getCatalog();
    }

    @Override // ConditionallyCloseableConnection
    public final void setTransactionIsolation(int level) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        super.setTransactionIsolation(level);
    }

    @Override // ConditionallyCloseableConnection
    public final int getTransactionIsolation() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.getTransactionIsolation();
    }

    @Override // ConditionallyCloseableConnection
    public final Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.createStatement(resultSetType, resultSetConcurrency);
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    @Override // ConditionallyCloseableConnection
    public final CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    @Override // ConditionallyCloseableConnection
    public final Map<String, Class<?>> getTypeMap() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.getTypeMap();
    }

    @Override // ConditionallyCloseableConnection
    public final void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        super.setTypeMap(map);
    }

    @Override // ConditionallyCloseableConnection
    public final void setHoldability(int holdability) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        super.setHoldability(holdability);
    }

    @Override // ConditionallyCloseableConnection
    public final int getHoldability() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.getHoldability();
    }

    @Override // ConditionallyCloseableConnection
    public final Savepoint setSavepoint() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        if (this.enlisted()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", PROHIBITED_SAVEPOINT_OPERATION);
        }
        return super.setSavepoint();
    }

    @Override // ConditionallyCloseableConnection
    public final Savepoint setSavepoint(String name) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        if (this.enlisted()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", PROHIBITED_SAVEPOINT_OPERATION);
        }
        return super.setSavepoint(name);
    }

    @Override // ConditionallyCloseableConnection
    public final void rollback(Savepoint savepoint) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        if (this.enlisted()) {
            // "SQLException...if...this method is called while participating in a distributed transaction"
            throw new SQLNonTransientException("Connection enlisted in transaction", PROHIBITED_SAVEPOINT_OPERATION);
        }
        super.rollback(savepoint);
    }

    @Override // ConditionallyCloseableConnection
    public final void releaseSavepoint(Savepoint savepoint) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        if (this.enlisted()) {
            // "SQLException...if...the given Savepoint object is not a valid savepoint in the current transaction"
            //
            // Interestingly JDBC doesn't mandate an exception being thrown here if the connection is enlisted in a
            // global transaction, but it looks like a SQL state such as 3B503 is often thrown in this case.
            throw new SQLNonTransientException("Connection enlisted in transaction", PROHIBITED_SAVEPOINT_OPERATION);
        }
        super.releaseSavepoint(savepoint);
    }

    @Override // ConditionallyCloseableConnection
    public final Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql, int rsType, int rsConcurrency, int rsHoldability)
      throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.prepareStatement(sql, rsType, rsConcurrency, rsHoldability);
    }

    @Override // ConditionallyCloseableConnection
    public final CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.prepareStatement(sql, autoGeneratedKeys);
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.prepareStatement(sql, columnIndexes);
    }

    @Override // ConditionallyCloseableConnection
    public final PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.prepareStatement(sql, columnNames);
    }

    @Override // ConditionallyCloseableConnection
    public final Clob createClob() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.createClob();
    }

    @Override // ConditionallyCloseableConnection
    public final Blob createBlob() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.createBlob();
    }

    @Override // ConditionallyCloseableConnection
    public final NClob createNClob() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.createNClob();
    }

    @Override // ConditionallyCloseableConnection
    public final SQLXML createSQLXML() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.createSQLXML();
    }

    @Override // ConditionallyCloseableConnection
    public final boolean isValid(int timeout) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.isValid(timeout);
    }

    @Override // ConditionallyCloseableConnection
    public final void setClientInfo(String name, String value) throws SQLClientInfoException {
        try {
            this.failWhenClosed();
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
            this.failWhenClosed();
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
        this.failWhenClosed();
        this.enlist();
        return super.getClientInfo(name);
    }

    @Override // ConditionallyCloseableConnection
    public final Properties getClientInfo() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.getClientInfo();
    }

    @Override // ConditionallyCloseableConnection
    public final Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.createArrayOf(typeName, elements);
    }

    @Override // ConditionallyCloseableConnection
    public final Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.createStruct(typeName, attributes);
    }

    @Override // ConditionallyCloseableConnection
    public final void setSchema(String schema) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        super.setSchema(schema);
    }

    @Override // ConditionallyCloseableConnection
    public final String getSchema() throws SQLException {
        this.failWhenClosed();
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
        this.failWhenClosed();
        this.enlist();
        super.setNetworkTimeout(executor, milliseconds);
    }

    @Override // ConditionallyCloseableConnection
    public final int getNetworkTimeout() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.getNetworkTimeout();
    }

    @Override // ConditionallyCloseableConnection
    public final void beginRequest() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        super.beginRequest();
    }

    @Override // ConditionallyCloseableConnection
    public final void endRequest() throws SQLException {
        this.failWhenClosed();
        this.enlist();
        super.endRequest();
    }

    @Override // ConditionallyCloseableConnection
    public final boolean setShardingKeyIfValid(ShardingKey shardingKey, ShardingKey superShardingKey, int timeout)
        throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
    }

    @Override // ConditionallyCloseableConnection
    public final boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        return super.setShardingKeyIfValid(shardingKey, timeout);
    }

    @Override // ConditionallyCloseableConnection
    public final void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey) throws SQLException {
        this.failWhenClosed();
        this.enlist();
        super.setShardingKey(shardingKey, superShardingKey);
    }

    @Override // ConditionallyCloseableConnection
    public final void setShardingKey(ShardingKey shardingKey) throws SQLException {
        this.failWhenClosed();
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
        // prepare/commit completion cycle has started.
        Enlistment enlistment = this.enlistment; // volatile read
        if (enlistment != null) {
            try {
                // TMSUCCESS because it's an ordinary close() call, not a delisting due to an exception
                boolean delisted = enlistment.transaction().delistResource(enlistment.xaResource(), TMSUCCESS);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE,
                                this.getClass().getName(), "close",
                                "{0} {1} from {2}",
                                new Object[] {delisted ? "Delisted" : "Failed to delist",
                                              enlistment.xaResource(),
                                              enlistment.transaction()});
                }
            } catch (IllegalStateException e) {
                // Transaction went from active or marked for rollback to some other state; whatever; we're no longer
                // enlisted so we didn't delist.
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, this.getClass().getName(), "close", e.getMessage(), e);
                }
            } catch (SystemException e) {
                throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE_NO_SUBCLASS, e);
            }
        }
        super.close(); // basically this.delegate().close()
        if (LOGGER.isLoggable(Level.FINE)) {
            if (!this.isClosePending()) {
                // If a close is not pending then that means it actually happened.
                LOGGER.logp(Level.FINE, this.getClass().getName(), "close",
                            "Closed {0} on thread {1}", new Object[] {this, Thread.currentThread()});
            }
        }
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
     * Returns {@code true} if and only if this {@link JtaConnection} is associated with a global (JTA) transaction (in
     * any of its possible {@linkplain Status valid states}).
     *
     * @return {@code true} if and only if this {@link JtaConnection} is associated with a global (JTA) transaction (in
     * any of its possible {@linkplain Status valid states}); {@code false} in all other cases
     *
     * @exception SQLException if the status could not be computed
     */
    boolean enlisted() throws SQLException {
        return this.enlistment != null; // volatile read
    }

    private int currentTransactionStatus() throws SQLException {
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
            throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE_NO_SUBCLASS, e);
        }
    }

    private Transaction currentTransaction() throws SQLTransientException {
        try {
            return this.ts.getTransaction();
        } catch (RuntimeException | SystemException e) {
            throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE_NO_SUBCLASS, e);
        }
    }

    /**
     * Attempts to enlist this {@link JtaConnection} in the current global (JTA) transaction, if there is one, and its
     * current state permits enlistment, and this {@link JtaConnection} is not already {@linkplain #enlisted()
     * enlisted}.
     *
     * <p>If these preconditions are not met, this method either does nothing or throws an appropriate {@link
     * SQLException}.</p>
     *
     * @exception SQLException if a transaction-related error occurs
     *
     * @see #enlisted()
     *
     * @see #continueEnlisting()
     *
     * @see #validateTransactionStatusForEnlistment(Transaction)
     */
    private void enlist() throws SQLException {

        if (!this.continueEnlisting()) {
            // There is no global transaction or we're already (validly) enlisted, so don't enlist. Extremely common.
            return;
        }

        Transaction currentTransaction = currentTransaction();

        // continueEnlisting() (invoked above) rules out the Status.STATUS_NO_TRANSACTION case; the Jakarta Transactions
        // specification therefore ensures that currentTransaction here cannot be null.

        // Transaction statuses can change at almost any time as a result of other TransactionManager-related threads
        // operating on them. Do a quick cheap status check here to avoid expensive logic below if it's not needed.
        validateTransactionStatusForEnlistment(currentTransaction);

        // One last cheap check to see if we've been actually closed or requested to close asynchronously.
        this.failWhenClosed();

        // Point of no return. We ensured that the Transaction at one point had a status of Status.STATUS_ACTIVE (or
        // Status.STATUS_MARKED_ROLLBACK) and ensured we aren't already enlisted and our autoCommit status is true. The
        // Transaction's status can still change at any point (as a result of asynchronous rollback, for example)
        // through certain permitted state transitions, so we have to watch for exceptions.

        // Get an XAResource and create an Enlistment that records the association of the current global (JTA)
        // transaction with this thread and the XAResource, and install it atomically to indicate that enlistment is
        // occurring or has occurred on this thread.
        XAResource xar = this.xaResourceSupplier.get();
        if (xar == null) {
            throw new SQLTransientException("xaResourceSupplier.get() == null");
        }
        Enlistment enlistment;
        try {
            enlistment = new Enlistment(Thread.currentThread().threadId(), currentTransaction, xar);
        } catch (UncheckedSQLException e) {
            throw (SQLTransientException) e.getCause();
        }
        if (!ENLISTMENT.compareAndSet(this, null, enlistment)) { // atomic volatile write
            // Setting this.enlistment could conceivably fail if another thread already enlisted this JtaConnection.
            // That would be bad.
            //
            // (The this.enlistment read in the exception message below is a volatile read, and thanks to the
            // compareAndSet call above we know it is non-null.)
            throw new SQLTransientException("Already enlisted (" + this.enlistment
                                            + "); current transaction: " + currentTransaction
                                            + "; current thread id: " + Thread.currentThread().threadId(),
                                            INVALID_TRANSACTION_STATE_NO_SUBCLASS);
        }

        // At this point we're effectively single-threaded, i.e. no more than one thread will ever execute the rest of
        // this method, per the rules of the Jakarta Transactions specification and the semantics of the compareAndSet
        // call above.

        // Actually enlist the XAResource.
        try {
            // Install a Synchronization that will close this connection and delist its associated XAResource when the
            // transaction completes. See close() for more details.
            final Sync sync = this::transactionCompleted;
            if (this.interposedSynchronizations) {
                this.tsr.registerInterposedSynchronization(sync);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, this.getClass().getName(), "connectionFunction",
                                "Registered interposed synchronization (transactionCompleted(int)) for {0}", currentTransaction);
                }
            } else {
                currentTransaction.registerSynchronization(sync);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, this.getClass().getName(), "connectionFunction",
                                "Registered synchronization (transactionCompleted(int)) for {0}", currentTransaction);
                }
            }

            // Don't let our caller close us "for real". close() invocations will be recorded as pending and resolved
            // properly in the transactionCompleted(int) method, registered as a Synchronization above. Also see
            // #close().
            this.setCloseable(false);

            // (Guaranteed to call xar.start(Xid, int) on this thread.)
            currentTransaction.enlistResource(xar);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE,
                            this.getClass().getName(), "enlist",
                            "Enlisted {0} in transaction {1}", new Object[] {xar, currentTransaction});
            }
        } catch (RollbackException e) {
            this.enlistment = null; // volatile write
            // The enlistResource(XAResource) or registerInterposedSynchronization(Synchronization) or
            // registerSynchronization(Synchronization) operation failed because the transaction was rolled back.
            throw new SQLNonTransientException(e.getMessage(), TRANSACTION_ROLLBACK, e);
        } catch (RuntimeException | SystemException e) {
            this.enlistment = null; // volatile write
            if (e.getCause() instanceof RollbackException) {
                // The enlistResource(XAResource) operation failed because the transaction was rolled back.
                throw new SQLNonTransientException(e.getMessage(), TRANSACTION_ROLLBACK, e);
            }
            // The t.enlistResource(XAResource) operation failed, or the
            // tsr.registerInterposedSynchronization(Synchronization) operation failed, or the
            // t.registerSynchronization(Synchronization) operation failed. In any case, no XAResource was actually
            // enlisted.
            throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE_NO_SUBCLASS, e);
        } catch (Error e) {
            this.enlistment = null; // volatile write
            throw e;
        }
    }

    // (Used only by reference by LocalXAResource#start(Xid, int) as a result of calling
    // Transaction#enlistResource(XAResource) in enlist() above.)
    private Connection connectionFunction(Xid xid) {
        this.xidConsumer.accept(xid);
        return new DelegatingConnection(this.delegate()) {
            @Override
            public void setAutoCommit(boolean autoCommit) throws SQLException {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, "<anonymous DelegatingConnection>", "setAutoCommit",
                                "Setting autoCommit on anonymous DelegatingConnection {0} to {1}",
                                new Object[] {this, autoCommit});
                }
                super.setAutoCommit(autoCommit);
            }
        };
    }

    // (Used only by reference in enlist() above. Remember, this callback may be called by the TransactionManager on
    // any thread at any time for any reason.)
    private void transactionCompleted(int committedOrRolledBack) {
        this.enlistment = null; // volatile write
        try {
            boolean closeWasPending = this.isClosePending(); // volatile state read

            // This connection is now closeable "for real". (It may have already been closeable "for real"; see
            // closeWasPending to find out.) Transition to that state.
            this.setCloseable(true); // idempotent volatile state write

            // The internal state machine is now in the CLOSEABLE state.

            if (closeWasPending) {
                // (We transitioned from CLOSE_PENDING -> CLOSEABLE.)
                //
                // If a close was pending, then a "real" close() did not happen, but the user did in fact call close()
                // and believes the connection is now closed. In reality, this connection is now closeable, so we must
                // now actually close it.
                //
                // (Because we are currently in the CLOSEABLE state, which permits a transition to the NOT_CLOSEABLE
                // state, there is a pathological edge case where the close() call we're about to make on this thread
                // might not actually close the connection. This occurs only if (a) this code is running on thread 2, and
                // the user's code is running on thread 1, and (b) the user's code at this very moment for no good
                // reason calls setCloseable(false). Note that this would be after the user had called close(), so she
                // would be in violation of its JDBC-defined contract anyway.)
                this.close();
            }
        } catch (SQLException e) {
            // (Synchronization implementations can throw only unchecked exceptions.)
            throw new UncheckedSQLException(e);
        }
    }

    /**
     * Called from the {@link #enlist()} method to indicate whether an attempt to enlist in the (possibly non-existent)
     * global (JTA) transaction should continue.
     *
     * <p>This method will return {@code false} if an invocation of the {@link #currentTransactionStatus()} method
     * returns an {@code int} equal to {@link Status#STATUS_NO_TRANSACTION} (there's no point in trying to enlist in the
     * global (JTA) transaction when none exists).</p>
     *
     * <p>This method will return {@code false} if this {@link JtaConnection} is already enlisted in the global (JTA)
     * transaction (there's no point in re-enlisting).</p>
     *
     * <p>This method will throw a {@link SQLException} if this {@link JtaConnection} is already enlisted with a global
     * (JTA) transaction and the {@linkplain Thread#currentThread() current <code>Thread</code>} is not the same as the
     * {@link Thread} on which enlistment previously occurred (JDBC connections are never obligated to be thread-safe
     * and enlistment in two transactions at once is illegal).</p>
     *
     * <p>This method will throw a {@link SQLException} if this {@link JtaConnection} is already enlisted with a global
     * (JTA) transaction and the global (JTA) transaction on the {@linkplain Thread#currentThread() current
     * <code>Thread</code>} is not {@linkplain Object#equals(Object) equal to} the enlistment's transaction (this
     * represents an illegal attempt to enlist in a new transaction while already enlisted in a suspended
     * transaction).</p>
     *
     * <p>If {@linkplain #preemptiveEnlistmentChecks preemptive enlistment checks} are enabled, this method will throw a
     * {@link SQLException} if the global (JTA) transaction's status is invalid for enlistment.</p>
     *
     * <p>This method will throw a {@link SQLException} if, upon enlistment, the {@linkplain
     * ConditionallyCloseableConnection#getAutoCommit() autocommit status} was set to {@code false} (the initial state
     * of this {@link JtaConnection} would be unknown, illegally, otherwise).</p>
     *
     * @return {@code true} if an {@link #enlist()} attempt should continue; {@code false} otherwise
     *
     * @exception SQLException if an error occurs
     *
     * @see #validateTransactionStatusForEnlistment(int)
     */
    private boolean continueEnlisting() throws SQLException {
        int currentThreadTransactionStatus = this.currentTransactionStatus();
        if (currentThreadTransactionStatus == Status.STATUS_NO_TRANSACTION) {
            return false;
        }

        Enlistment enlistment = this.enlistment; // volatile read
        if (enlistment != null) {
            if (enlistment.threadId() != Thread.currentThread().threadId()) {
                // Error scenario. We're enlisted in a Transaction on thread 1, and a caller from thread 2 is trying to
                // do something with us. This could have unintended side effects. JDBC Connections are under no
                // obligation to support multithreaded access. Throw.
                throw new SQLTransientException("Already enlisted (" + enlistment + "); current thread id: "
                                                + Thread.currentThread().threadId(), INVALID_TRANSACTION_STATE_NO_SUBCLASS);
            }

            // We've already enlisted in a Transaction that was created on the current thread. So far so good. Is it active
            // or marked-for-rollback, i.e. still in play? (See also: https://www.eclipse.org/lists/jta-dev/msg00316.html)
            Transaction enlistmentTransaction = enlistment.transaction();
            String errorMessage;
            int enlistmentStatus = statusFrom(enlistmentTransaction);
            switch (enlistmentStatus) {
            case Status.STATUS_ACTIVE:
            case Status.STATUS_MARKED_ROLLBACK: // ok; see below
                // We have been enlisted in an active or marked-for-rollback transaction that was created on this
                // thread. So far so good.
                //
                // (Status.STATUS_MARKED_ROLLBACK is OK here because we enlisted in a Status.STATUS_ACTIVE transaction,
                // which then got marked later as rollback only.)
                //
                // Is the current transaction "the same" as the one we enlisted in? It better be.
                //
                // We can check for "not the same" very quickly by comparing statuses: t1 can't be "the same" as t2 if
                // the statuses are different. If the statuses *are* the same, then we use Transaction#equals(Object) to
                // make sure the transactions are "the same". "The same" is defined, sort of, by the Jakarta Transaction
                // specification
                // (https://jakarta.ee/specifications/transactions/2.0/jakarta-transactions-spec-2.0.html#transaction-equality-and-hash-code).
                //
                // You can't actually enlist in a transaction that is marked for rollback, but we'll let the actual JTA
                // implementation enforce that later.
                if (enlistmentStatus == currentThreadTransactionStatus && enlistmentTransaction.equals(currentTransaction())) {
                    // The Transaction associated with the current thread (currentTransaction()) is "the same" active or
                    // marked-for-rollback one we're enlisted with, so we're already enlisted, and nothing is
                    // suspended. So there's no point continuing to enlist. Very common.
                    return false; // no need to continue enlisting; we're already enlisted and in a valid state
                }
                // Error scenario. We now know we're going to throw an exception because the transaction we were
                // enlisted with has become suspended (the current transaction is not the same as the one we were
                // enlisted with, though it is on the same thread), so this connection should not *also* be enlisted in
                // the current transaction); that would be a double enlistment which is forbidden.
                validateTransactionStatus(currentThreadTransactionStatus);
                throw new SQLTransientException("Attempting to enlist in a transaction while also associated with a suspended"
                                                + " transaction (" + enlistment + ")",
                                                INVALID_TRANSACTION_STATE_NO_SUBCLASS);

            //
            // The remaining statuses below are interesting. Enlistment for all of them is (definitionally) doomed. When
            // preemptive enlistment checks are enabled, we preemptively reject enlistment when the state is anything
            // other than the two "in play" statuses (Status.STATUS_ACTIVE and Status.STATUS_MARKED_ROLLBACK). But the
            // JTA implementation will enforce this anyway, so with preemptive enlistment checks disabled we let the JTA
            // implementation do the enforcement instead.
            //
            // The chief benefit is (comparative) logic simplicity in this class and responsibility for validation
            // shifted to the JTA implementation which has to honor the Jakarta Transactions specification anyway.
            //

            case Status.STATUS_COMMITTED:
            case Status.STATUS_ROLLEDBACK:
                // Completed or heuristically completed (the Transaction has been completed (and serves as a tombstone)
                // or heuristically completed (XA's "Heuristically Completed" state (s5)). In either case, the non-null
                // Enlistment will be removed momentarily by another thread executing our transactionCompleted(int)
                // method.).
                if (this.preemptiveEnlistmentChecks) {
                    errorMessage = "Completion or heuristic completion transaction status: " + enlistmentStatus;
                    break;
                }
                return false;
            case Status.STATUS_COMMITTING:
            case Status.STATUS_PREPARED:
            case Status.STATUS_PREPARING:
            case Status.STATUS_ROLLING_BACK:
                // Completing. Throw to prevent accidental side effects.
                if (this.preemptiveEnlistmentChecks) {
                    errorMessage = "Interim transaction status: " + enlistmentStatus;
                    break;
                }
                return false;
            case Status.STATUS_UNKNOWN:
                if (this.preemptiveEnlistmentChecks) {
                    errorMessage = "Unknown transaction status";
                    break;
                }
                return false;
            case Status.STATUS_NO_TRANSACTION:
                // Somehow an Enlistment reported a Status.STATUS_NO_TRANSACTION status. The state tables do not permit
                // a transition to this state, so the Transaction must have been created initially with this status,
                // which is a severe violation of the XA and JTA specifications. This should absolutely never happen.
                throw new AssertionError();
            default:
                // Unexpected or illegal.
                errorMessage = "Unknown or illegal transaction status: " + enlistmentStatus;
                break;
            }
            throw new SQLTransientException(errorMessage, INVALID_TRANSACTION_STATE_NO_SUBCLASS);
        }

        this.validateTransactionStatusForEnlistment(currentThreadTransactionStatus);

        if (this.preemptiveEnlistmentChecks && !super.getAutoCommit()) {
            // There is, as far as we can tell, a global transaction on the current thread, and super.getAutoCommit()
            // (super. on purpose, not this., to prevent a circular call to enlist()) returned false, and we aren't
            // (yet) enlisted with the global transaction, so autoCommit must have been disabled on purpose by the
            // caller, not by the transaction enlistment machinery. In such a case, we must prevent enlistment, because
            // a local transaction may be in progress, and therefore we cannot know what statements will be issued at
            // global transaction commit() time.
            throw new SQLTransientException("autoCommit was false during transaction enlistment",
                                            INVALID_TRANSACTION_STATE_NO_SUBCLASS);
        }

        return true;
    }

    private void validateTransactionStatusForEnlistment(Transaction t) throws SQLTransientException {
        validateTransactionStatusForEnlistment(statusFrom(t));
    }

    private void validateTransactionStatusForEnlistment(int transactionStatus) throws SQLTransientException {
        switch (transactionStatus) {
        case Status.STATUS_ACTIVE:
            // No one has started or finished the transaction completion process yet. Most common. Keep going.
            break;
        case Status.STATUS_COMMITTED: // completion or imminent completion (possibly heuristic)
        case Status.STATUS_COMMITTING: // interim
        case Status.STATUS_MARKED_ROLLBACK: // "in play" but doomed
        case Status.STATUS_PREPARED: // interim
        case Status.STATUS_PREPARING: // interim
        case Status.STATUS_ROLLEDBACK: // completion or imminent completion (possibly heuristic)
        case Status.STATUS_ROLLING_BACK: // interim
            // Interim or heuristic completion. Throw to prevent accidental side effects.
            if (this.preemptiveEnlistmentChecks) {
                throw new SQLTransientException("Non-terminal transaction status: " + transactionStatus,
                                                INVALID_TRANSACTION_STATE_NO_SUBCLASS);
            }
            break;
        case Status.STATUS_NO_TRANSACTION:
            if (this.preemptiveEnlistmentChecks) {
                throw new SQLTransientException("No transaction available for enlistment",
                                                INVALID_TRANSACTION_STATE_NO_SUBCLASS);
            }
            break;
        case Status.STATUS_UNKNOWN:
            if (this.preemptiveEnlistmentChecks) {
                throw new SQLTransientException("Unknown transaction status",
                                                INVALID_TRANSACTION_STATE_NO_SUBCLASS);
            }
            break;
        default:
            // Unexpected or illegal. Throw no matter what (we don't check the preemptiveEnlistmentChecks flag).
            throw new SQLTransientException("Illegal transaction status: " + transactionStatus,
                                            INVALID_TRANSACTION_STATE_NO_SUBCLASS);
        }
    }


    /*
     * Static methods.
     */


    /**
     * Returns the {@linkplain Transaction#getStatus() status} associated with the supplied {@link Transaction},
     * converting exceptions as appropriate.
     *
     * @param t a {@link Transaction}; must not be {@code null}
     *
     * @return the {@linkplain Transaction#getStatus() status} associated with the supplied {@link Transaction}
     *
     * @exception NullPointerException if {@code t} is {@code null}
     *
     * @exception SQLTransientException if invoking the {@link Transaction#getStatus()} method on the supplied {@link
     * Transaction} throws either a {@link RuntimeException} or a {@link SystemException}
     *
     * @see Transaction#getStatus()
     */
    private static int statusFrom(Transaction t) throws SQLTransientException {
        // Yes, we dereference t later in this method, so this isn't strictly speaking necessary, but we want to make
        // sure the NullPointerException that is thrown is not wrapped by a SQLTransientException.
        Objects.requireNonNull(t, "t");
        try {
            return t.getStatus();
        } catch (RuntimeException | SystemException e) {
            throw new SQLTransientException(e.getMessage(), INVALID_TRANSACTION_STATE_NO_SUBCLASS, e);
        }
    }

    /**
     * Validates the supplied {@link Transaction} by ensuring that {@linkplain Transaction#getStatus() its status}
     * {@linkplain #validateTransactionStatus(int) is valid}.
     *
     * @param t a {@link Transaction}; must not be {@code null}
     *
     * @exception NullPointerException if {@code t} is {@code null}
     *
     * @exception SQLTransientException if {@code t} is invalid
     *
     * @see #validateTransactionStatus(int)
     *
     * @see Transaction#getStatus()
     */
    private static void validate(Transaction t) throws SQLTransientException {
        int transactionStatus = statusFrom(t);
        validateTransactionStatus(transactionStatus);
        // ...and:
        if (transactionStatus == Status.STATUS_NO_TRANSACTION) {
            // Absolutely impossible unless the Transaction implementation (or mock, or whatever) is severely
            // broken.
            throw new AssertionError("Illegal transaction status (Status.STATUS_NO_TRANSACTION)");
        }
    }

    /**
     * Ensure that the supplied transaction status is equal to one of the {@code int} constants defined by the {@link
     * Status} class.
     *
     * @param transactionStatus the transaction status
     *
     * @exception SQLTransientException if {@code transactionStatus} is not equal to one of the {@code int} constants
     * defined by the {@link Status} class
     *
     * @see Status
     */
    private static void validateTransactionStatus(int transactionStatus) throws SQLTransientException {
        switch (transactionStatus) {
        case Status.STATUS_ACTIVE:
        case Status.STATUS_COMMITTED:
        case Status.STATUS_COMMITTING:
        case Status.STATUS_MARKED_ROLLBACK:
        case Status.STATUS_NO_TRANSACTION:
        case Status.STATUS_PREPARED:
        case Status.STATUS_PREPARING:
        case Status.STATUS_ROLLEDBACK:
        case Status.STATUS_ROLLING_BACK:
        case Status.STATUS_UNKNOWN:
            return;
        default:
            throw new SQLTransientException("Unexpected or illegal transaction status: " + transactionStatus,
                                            INVALID_TRANSACTION_STATE_NO_SUBCLASS);
        }
    }

    /**
     * A {@link Consumer}-like method invoked only by method reference that does nothing.
     *
     * @param ignored an {@link Object} that is entirely ignored
     */
    private static void sink(Object ignored) {

    }


    /*
     * Inner and nested classes.
     */


    /**
     * A {@linkplain FunctionalInterface functional} {@link Synchronization}.
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
        @Override // Synchronization
        default void beforeCompletion() {

        }

    }

    /**
     * A pairing of a {@link Thread} {@linkplain Thread#threadId() identifier}, a {@link Transaction} and an {@link
     * XAResource}, used to indicate successful enlistment in a global (JTA) transaction.
     *
     * @param threadId a {@link Thread} {@linkplain Thread#threadId() identifier}
     *
     * @param transaction a {@link Transaction} (must not be {@code null})
     *
     * @param xaResource an {@link XAResource} (must not be {@code null})
     */
    private record Enlistment(long threadId, Transaction transaction, XAResource xaResource) {

        private Enlistment {
            Objects.requireNonNull(xaResource, "xaResource");
            try {
                validate(transaction);
            } catch (SQLTransientException e) {
                // (Compact constructors can't throw checked exceptions.)
                throw new UncheckedSQLException(e);
            }
        }

    }

}
