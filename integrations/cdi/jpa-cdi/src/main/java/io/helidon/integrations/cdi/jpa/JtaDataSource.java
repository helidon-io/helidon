/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.jpa;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.enterprise.inject.Vetoed;
import javax.sql.DataSource;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

/**
 * An {@link AbstractDataSource} and a {@link Synchronization} that
 * wraps another {@link DataSource} that is known to not behave
 * correctly in the presence of JTA transaction management, such as
 * one supplied by any of several freely and commercially available
 * connection pools.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are safe for concurrent use by multiple
 * threads.  No such guarantee obviously can be made about the {@link
 * DataSource} wrapped any given instance of this class.</p>
 *
 * <p>Note that the JDBC specification places no requirement on any
 * implementor to make any implementations of any JDBC constructs
 * thread-safe.</p>
 */
@Vetoed
final class JtaDataSource extends AbstractDataSource implements Synchronization {


    /*
     * Static fields.
     */


    private static final Object UNAUTHENTICATED_CONNECTION_IDENTIFIER = new Object();

    private static final ThreadLocal<? extends Map<JtaDataSource, Map<Object, TransactionSpecificConnection>>>
        CONNECTION_STORAGE =
        ThreadLocal.withInitial(() -> new HashMap<>());


    /*
     * Instance fields.
     */


    private final DataSource delegate;

    private final String dataSourceName;

    private final TransactionManager transactionManager;


    /*
     * Constructors.
     */


    JtaDataSource(final DataSource delegate,
                  final String dataSourceName,
                  final TransactionManager transactionManager) {
        super();
        this.delegate = Objects.requireNonNull(delegate);
        this.dataSourceName = dataSourceName;
        this.transactionManager = Objects.requireNonNull(transactionManager);
    }


    /*
     * Instance methods.
     */


    /**
     * Implements the {@link Synchronization#beforeCompletion()}
     * method to do nothing.
     */
    @Override
    public void beforeCompletion() {

    }

    /**
     * Ensures that any thread-associated connections are properly
     * committed, restored to their initial states, closed where
     * appropriate and removed from the system when a definitionally
     * thread-scoped JTA transaction commits or rolls back.
     *
     * @param status the status of the transaction after completion;
     * must be either {@link Status#STATUS_COMMITTED} or {@link
     * Status#STATUS_ROLLEDBACK}
     *
     * @exception IllegalArgumentException if {@code status} is
     * neither {@link Status#STATUS_COMMITTED} nor {@link
     * Status#STATUS_ROLLEDBACK}
     */
    @Override
    public void afterCompletion(final int status) {
        // Validate the status coming in, but make sure that no matter
        // what we remove any transaction-specific connections from
        // the ThreadLocal storing such connections.  Doing this right
        // is the reason for deferring any throwing of an
        // IllegalArgumentException in invalid cases.
        final IllegalArgumentException badStatusException;
        final CheckedConsumer<? super Connection> consumer;
        switch (status) {
        case Status.STATUS_COMMITTED:
            badStatusException = null;
            consumer = Connection::commit;
            break;
        case Status.STATUS_ROLLEDBACK:
            badStatusException = null;
            consumer = Connection::rollback;
            break;
        default:
            badStatusException = new IllegalArgumentException("Unexpected transaction status after completion: " + status);
            consumer = null;
            break;
        }

        // Get all of the TransactionSpecificConnections we have
        // released into the world via our getConnection() and
        // getConnection(String, String) methods, and inform them that
        // the transaction is over.  Then remove them from the map
        // since the transaction is over.  These particular
        // Connections out in the world will not participate in future
        // JTA transactions, even if such transactions are started on
        // this thread.
        @SuppressWarnings("unchecked")
        final Map<?, ? extends TransactionSpecificConnection> myThreadLocalConnectionMap =
            (Map<?, ? extends TransactionSpecificConnection>) CONNECTION_STORAGE.get().get(this);

        if (myThreadLocalConnectionMap != null && !myThreadLocalConnectionMap.isEmpty()) {
            final Collection<? extends TransactionSpecificConnection> myConnections = myThreadLocalConnectionMap.values();
            assert myConnections != null;
            try {
                if (badStatusException != null) {
                    throw badStatusException;
                } else {
                    complete(myConnections, consumer);
                }
            } finally {
                myConnections.clear();
            }
        }
    }

    /**
     * Given an {@link Iterable} of {@link
     * TransactionSpecificConnection} instances and a {@link
     * CheckedConsumer} of {@link Connection} instances, ensures that
     * the {@link CheckedConsumer#accept(Connection)} method is
     * invoked on each reachable {@link
     * TransactionSpecificConnection}, properly handling all
     * exceptional conditions.
     *
     * <p>The {@link TransactionSpecificConnection} instances will
     * have their auto-commit statuses reset and their closeable
     * statuses set to {@code true}, even in the presence of
     * exceptional conditions.</p>
     *
     * <p>The {@link TransactionSpecificConnection}s will also be
     * closed if a caller has requested their closing prior to this
     * method executing.</p>
     *
     * <p>If a user has not requested their closing prior to this
     * method executing, the {@link TransactionSpecificConnection}s
     * will not be closed, but will become closeable by the end user
     * (allowing them to be released back to any backing connection
     * pool that might exist).  They will no longer take part in any
     * new JTA transactions from this point forward (a new {@link
     * Connection} will have to be acquired while a JTA transaction is
     * active for that behavior).</p>
     *
     * @param connections an {@link Iterable} of {@link
     * TransactionSpecificConnection} instances; may be {@code null}
     * in which case no action will be taken
     *
     * @param consumer a {@link CheckedConsumer} that will be invoked
     * on each connection, even in the presence of exceptional
     * conditions; may be {@code null}
     *
     * @exception IllegalStateException if an error occurs
     */
    private static void complete(final Iterable<? extends TransactionSpecificConnection> connections,
                                 final CheckedConsumer<? super Connection> consumer) {
        if (connections != null) {
            RuntimeException runtimeException = null;
            for (final TransactionSpecificConnection connection : connections) {
                try {
                    if (consumer != null) {
                        consumer.accept(connection);
                    }
                } catch (final RuntimeException exception) {
                    if (runtimeException == null) {
                        runtimeException = exception;
                    } else {
                        runtimeException.addSuppressed(exception);
                    }
                } catch (final Exception exception) {
                    if (runtimeException == null) {
                        runtimeException = new IllegalStateException(exception.getMessage(), exception);
                    } else {
                        runtimeException.addSuppressed(exception);
                    }
                } finally {
                    try {
                        connection.restoreAutoCommit();
                    } catch (final SQLException sqlException) {
                        if (runtimeException == null) {
                            runtimeException = new IllegalStateException(sqlException.getMessage(), sqlException);
                        } else {
                            runtimeException.addSuppressed(sqlException);
                        }
                    } finally {
                        connection.setCloseable(true);
                        try {
                            if (connection.isCloseCalled()) {
                                connection.close();
                                assert connection.isClosed();
                            }
                        } catch (final SQLException sqlException) {
                            if (runtimeException == null) {
                                runtimeException = new IllegalStateException(sqlException.getMessage(), sqlException);
                            } else {
                                runtimeException.addSuppressed(sqlException);
                            }
                        }
                    }
                }
            }
            if (runtimeException != null) {
                throw runtimeException;
            }
        }
    }

    /**
     * Returns a special kind of {@link Connection} that is sourced
     * from an underlying {@link DataSource}.
     *
     * <p>The {@link Connection} returned by this method:</p>
     *
     * <ul>
     *
     * <li>is never {@code null} (unless the underlying {@link
     * DataSource} is not JDBC-compliant)</li>
     *
     * <li>is exactly the {@link Connection} returned by the
     * underlying {@link DataSource} when there is no JTA transaction
     * in effect at the time that this method is invoked</li>
     *
     * </ul>
     *
     * <p>Otherwise, when a JTA transaction is in effect, the {@link
     * Connection} returned by this method:</p>
     *
     * <ul>
     *
     * <li>is the same {@link Connection} returned by prior
     * invocations of this method on the same thread during the
     * lifespan of a JTA transaction.  That is, the {@link Connection}
     * is "pinned" to the current thread for the lifespan of the
     * transaction.</li>
     *
     * <li>is not actually closeable when a JTA transaction is in
     * effect.  The {@link Connection#close()} method will behave from
     * the standpoint of the caller as if it functions normally, but
     * its invocation will not actually be propagated to the
     * underlying {@link DataSource}'s connection.  The fact that it
     * was in fact invoked will be stored, and at such time that the
     * JTA transaction completes this {@link Connection} will be
     * closed at that point.</li>
     *
     * <li>has its autocommit status set to {@code false}</li>
     *
     * <li>will have {@link Connection#commit()} called on it when the
     * JTA transaction commits</li>
     *
     * <li>will have {@link Connection#rollback()} called on it when
     * the JTA transaction rolls back</li>
     *
     * <li>will have its autocommit status restored to its original
     * value after the transaction completes</li>
     *
     * </ul>
     *
     * @param username the username to use to acquire an underlying
     * {@link Connection}; may be {@code null}
     *
     * @param password the password to use to acquire an underlying
     * {@link Connection}; may be {@code null}
     *
     * @param useZeroArgumentForm whether the underlying {@link
     * DataSource}'s {@link DataSource#getConnection()} method should
     * be called
     *
     * @see DataSource#getConnection()
     *
     * @see DataSource#getConnection(String, String)
     */
    @Override
    public Connection getConnection() throws SQLException {
        return this.getConnection(null, null, true);
    }

    /**
     * Returns a special kind of {@link Connection} that is sourced
     * from an underlying {@link DataSource}.
     *
     * <p>The {@link Connection} returned by this method:</p>
     *
     * <ul>
     *
     * <li>is never {@code null} (unless the underlying {@link
     * DataSource} is not JDBC-compliant)</li>
     *
     * <li>is exactly the {@link Connection} returned by the
     * underlying {@link DataSource} when there is no JTA transaction
     * in effect at the time that this method is invoked</li>
     *
     * </ul>
     *
     * <p>Otherwise, when a JTA transaction is in effect, the {@link
     * Connection} returned by this method:</p>
     *
     * <ul>
     *
     * <li>is the same {@link Connection} returned by prior
     * invocations of this method with the same credentials (or no
     * credentials) on the same thread during the lifespan of a JTA
     * transaction.  That is, the {@link Connection} is "pinned" to
     * the current thread for the lifespan of the transaction</li>
     *
     * <li>is not actually closeable when a JTA transaction is in
     * effect.  The {@link Connection#close()} method will behave from
     * the standpoint of the caller as if it functions normally, but
     * its invocation will not actually be propagated to the
     * underlying {@link DataSource}'s connection.  The fact that it
     * was in fact invoked will be stored, and at such time that the
     * JTA transaction completes this {@link Connection} will be
     * closed at that point.</li>
     *
     * <li>has its autocommit status set to {@code false}</li>
     *
     * <li>will have {@link Connection#commit()} called on it when the
     * JTA transaction commits</li>
     *
     * <li>will have {@link Connection#rollback()} called on it when
     * the JTA transaction rolls back</li>
     *
     * <li>will have its autocommit status restored to its original
     * value after the transaction completes</li>
     *
     * </ul>
     *
     * @param username the username to use to acquire an underlying
     * {@link Connection}; may be {@code null}
     *
     * @param password the password to use to acquire an underlying
     * {@link Connection}; may be {@code null}
     *
     * @return a non-{@code null} {@link Connection}
     *
     * @exception SQLException if an error occurs
     *
     * @see DataSource#getConnection()
     *
     * @see DataSource#getConnection(String, String)
     */
    @Override
    public Connection getConnection(final String username, final String password) throws SQLException {
        return this.getConnection(username, password, false);
    }

    /**
     * Returns a special kind of {@link Connection} that is sourced
     * from an underlying {@link DataSource}.
     *
     * <p>The {@link Connection} returned by this method:</p>
     *
     * <ul>
     *
     * <li>is never {@code null} (unless the underlying {@link
     * DataSource} is not JDBC-compliant)</li>
     *
     * <li>is exactly the {@link Connection} returned by the
     * underlying {@link DataSource} when there is no JTA transaction
     * in effect at the time that this method is invoked</li>
     *
     * </ul>
     *
     * <p>Otherwise, when a JTA transaction is in effect, the {@link
     * Connection} returned by this method:</p>
     *
     * <ul>
     *
     * <li>is the same {@link Connection} returned by prior
     * invocations of this method with the same credentials (or no
     * credentials) on the same thread during the lifespan of a JTA
     * transaction.  That is, the {@link Connection} is "pinned" to
     * the current thread for the lifespan of the transaction.</li>
     *
     * <li>is not actually closeable when a JTA transaction is in
     * effect.  The {@link Connection#close()} method will behave from
     * the standpoint of the caller as if it functions normally, but
     * its invocation will not actually be propagated to the
     * underlying {@link DataSource}'s connection.  The fact that it
     * was in fact invoked will be stored, and at such time that the
     * JTA transaction completes this {@link Connection} will be
     * closed at that point.</li>
     *
     * <li>has its autocommit status set to {@code false}</li>
     *
     * <li>will have {@link Connection#commit()} called on it when the
     * JTA transaction commits</li>
     *
     * <li>will have {@link Connection#rollback()} called on it when
     * the JTA transaction rolls back</li>
     *
     * <li>will have its autocommit status restored to its original
     * value after the transaction completes</li>
     *
     * </ul>
     *
     * @param username the username to use to acquire an underlying
     * {@link Connection}; may be {@code null}
     *
     * @param password the password to use to acquire an underlying
     * {@link Connection}; may be {@code null}
     *
     * @param useZeroArgumentForm whether the underlying {@link
     * DataSource}'s {@link DataSource#getConnection()} method should
     * be called
     *
     * @return a non-{@code null} {@link Connection}
     *
     * @exception SQLException if an error occurs
     *
     * @see DataSource#getConnection()
     *
     * @see DataSource#getConnection(String, String)
     */
    private Connection getConnection(final String username,
                                     final String password,
                                     final boolean useZeroArgumentForm)
        throws SQLException {
        final Connection returnValue;
        int status = -1;
        try {
            status = this.transactionManager.getStatus();
        } catch (final SystemException exception) {
            throw new SQLException(exception.getMessage(), exception);
        }
        if (status == Status.STATUS_ACTIVE) {
            final Map<JtaDataSource, Map<Object, TransactionSpecificConnection>> connectionStorageMap = CONNECTION_STORAGE.get();
            assert connectionStorageMap != null;
            Map<Object, TransactionSpecificConnection> myConnectionMap = connectionStorageMap.get(this);
            if (myConnectionMap == null) {
                myConnectionMap = new HashMap<>();
                connectionStorageMap.put(this, myConnectionMap);
            }
            assert myConnectionMap != null;
            final Object id;
            if (useZeroArgumentForm) {
                id = UNAUTHENTICATED_CONNECTION_IDENTIFIER;
            } else {
                id = new AuthenticatedConnectionIdentifier(username, password);
            }
            TransactionSpecificConnection tsc = myConnectionMap.get(id);
            if (tsc == null) {
                if (useZeroArgumentForm) {
                    tsc = new TransactionSpecificConnection(this.delegate.getConnection());
                } else {
                    tsc = new TransactionSpecificConnection(this.delegate.getConnection(username, password));
                }
                myConnectionMap.put(id, tsc);
            } else {
                tsc.setCloseCalled(false);
            }
            returnValue = tsc;
        } else if (useZeroArgumentForm) {
            returnValue = this.delegate.getConnection();
        } else {
            returnValue = this.delegate.getConnection(username, password);
        }
        return returnValue;
    }


    /*
     * Inner and nested classes.
     */


    /**
     * A functional interface that processes a payload and may throw
     * an {@link Exception} as a result.
     *
     * @param <T> the type of payload
     *
     * @see #accept(Object)
     */
    @FunctionalInterface
    private interface CheckedConsumer<T> {

        /**
         * Processes the supplied payload in some way.
         *
         * @param payload the object to process; may be {@code null}
         *
         * @exception Exception if an error occurs
         */
        void accept(T payload) throws Exception;

    }

    private static final class TransactionSpecificConnection extends ConditionallyCloseableConnection {

        private final boolean oldAutoCommit;

        private boolean closeCalled;

        private TransactionSpecificConnection(final Connection delegate) throws SQLException {
            super(delegate, false /* not closeable */);
            assert !this.isCloseable();
            this.oldAutoCommit = this.getAutoCommit();
            this.setAutoCommit(false);
        }

        private void restoreAutoCommit() throws SQLException {
            this.setAutoCommit(this.oldAutoCommit);
        }

        @Override
        public void close() throws SQLException {
            this.setCloseCalled(true);
            super.close();
        }

        private boolean isCloseCalled() throws SQLException {
            return this.closeCalled || this.isClosed();
        }

        private void setCloseCalled(final boolean closeCalled) {
            this.closeCalled = closeCalled;
        }

    }

    private static final class AuthenticatedConnectionIdentifier {

        private final String username;

        private final String password;

        private final int hashCode;

        private AuthenticatedConnectionIdentifier(final String username,
                                                  final String password) {
            super();
            this.username = username;
            this.password = password;
            int hashCode = 17;
            int c = username == null ? 0 : username.hashCode();
            hashCode = 37 * hashCode + c;
            c = password == null ? 0 : password.hashCode();
            this.hashCode = 37 * hashCode + c;
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }

        @Override
        public boolean equals(final Object other) {
            if (other == this) {
                return true;
            } else if (other instanceof AuthenticatedConnectionIdentifier) {
                final AuthenticatedConnectionIdentifier her = (AuthenticatedConnectionIdentifier) other;
                if (this.username == null) {
                    if (her.username != null) {
                        return false;
                    }
                } else if (!this.username.equals(her.username)) {
                    return false;
                }
                if (this.password == null) {
                    if (her.password != null) {
                        return false;
                    }
                } else if (!this.password.equals(her.password)) {
                    return false;
                }
                return true;
            } else {
                return false;
            }
        }

    }

}
