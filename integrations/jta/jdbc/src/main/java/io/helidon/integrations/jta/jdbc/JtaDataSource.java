/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.sql.DataSource;
import javax.transaction.Status;
import javax.transaction.Synchronization;

import io.helidon.integrations.jdbc.AbstractDataSource;
import io.helidon.integrations.jdbc.ConditionallyCloseableConnection;

/**
 * An {@link AbstractDataSource} and a {@link Synchronization} that
 * wraps another {@link DataSource} that is known to not behave
 * correctly in the presence of JTA transaction management, such as
 * one supplied by any of several freely and commercially available
 * connection pools, and that makes such a non-JTA-aware {@link
 * DataSource} behave as sensibly as possible in the presence of a
 * JTA-managed transaction.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are safe for concurrent use by multiple
 * threads.  No such guarantee obviously can be made about the {@link
 * DataSource} wrapped by any given instance of this class.</p>
 *
 * <p>Note that the JDBC specification places no requirement on any
 * implementor to make any implementations of any JDBC constructs
 * thread-safe.</p>
 */
public final class JtaDataSource extends AbstractDataSource implements Synchronization {


    /*
     * Static fields.
     */


    private static final Object UNAUTHENTICATED_CONNECTION_IDENTIFIER = new Object();

    private static final ThreadLocal<? extends Map<JtaDataSource, Map<Object, TransactionSpecificConnection>>> CONNECTIONS_TL =
        ThreadLocal.withInitial(() -> new HashMap<>());


    /*
     * Instance fields.
     */


    private final Supplier<? extends DataSource> delegateSupplier;

    private final BooleanSupplier transactionIsActiveSupplier;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JtaDataSource}.
     *
     * @param dataSource the {@link DataSource} instance to which
     * operations will be delegated; must not be {@code null}
     *
     * @param transactionIsActiveSupplier a {@link BooleanSupplier}
     * that returns {@code true} only if the current transaction, if
     * any, is active; must not be {@code null}
     *
     * @exception NullPointerException if either parameter is {@code
     * null}
     *
     * @see #JtaDataSource(Supplier, BooleanSupplier)
     */
    public JtaDataSource(final DataSource dataSource,
                         final BooleanSupplier transactionIsActiveSupplier) {
        this(() -> dataSource, transactionIsActiveSupplier);
    }

    /**
     * Creates a new {@link JtaDataSource}.
     *
     * @param delegateSupplier a {@link Supplier} of {@link
     * DataSource} instances to which operations will be delegated;
     * must not be {@code null}
     *
     * @param transactionIsActiveSupplier an {@link BooleanSupplier} that
     * returns {@code true} only if the current transaction, if any, is
     * active; must not be {@code null}
     *
     * @exception NullPointerException if either parameter is {@code
     * null}
     */
    public JtaDataSource(final Supplier<? extends DataSource> delegateSupplier,
                         final BooleanSupplier transactionIsActiveSupplier) {
        super();
        this.delegateSupplier = Objects.requireNonNull(delegateSupplier, "delegateSupplier");
        this.transactionIsActiveSupplier = Objects.requireNonNull(transactionIsActiveSupplier, "transactionIsActiveSupplier");
    }


    /*
     * Instance methods.
     */


    /**
     * If there is an active transaction, registers this {@link
     * JtaDataSource} with the supplied registrar, which is most
     * commonly&mdash;but is not required to be&mdash;a reference to
     * the {@link
     * javax.transaction.TransactionSynchronizationRegistry#registerInterposedSynchronization(Synchronization)}
     * method.
     *
     * <p>If there is no currently active transaction, no action is taken.</p>
     *
     * @param registrar a {@link Consumer} that may {@linkplain
     * Consumer#accept(Object) accept} this {@link JtaDataSource} if
     * there is a currently active transaction; must not be {@code
     * null}
     *
     * @return {@code true} if registration occurred; {@code false}
     * otherwise
     *
     * @exception NullPointerException if {@code registrar} is {@code null}
     *
     * @exception RuntimeException if the supplied {@code registrar}'s
     * {@link Consumer#accept(Object) accept} method throws a {@link
     * RuntimeException}
     */
    public boolean registerWith(final Consumer<? super Synchronization> registrar) {
        if (this.transactionIsActiveSupplier.getAsBoolean()) {
            registrar.accept(this);
            return true;
        }
        return false;
    }

    /**
     * Implements the {@link Synchronization#beforeCompletion()}
     * method to do nothing.
     */
    @Override // Synchronization
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
    @Override // Synchronization
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
        final Map<?, ? extends TransactionSpecificConnection> extantConnectionsMap = CONNECTIONS_TL.get().get(this);
        if (extantConnectionsMap != null) {
            final Collection<? extends TransactionSpecificConnection> extantConnections = extantConnectionsMap.values();
            try {
                if (badStatusException == null) {
                    complete(extantConnections, consumer);
                } else {
                    throw badStatusException;
                }
            } finally {
                extantConnections.clear();
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
     * TransactionSpecificConnection} instances; must not be {@code
     * null}
     *
     * @param consumer a {@link CheckedConsumer} that will be invoked
     * on each connection, even in the presence of exceptional
     * conditions; must not be {@code null}
     *
     * @exception NullPointerException if {@code connections} or
     * {@code consumer} is {@code null}
     *
     * @exception IllegalStateException if an error occurs
     */
    private static void complete(final Iterable<? extends TransactionSpecificConnection> connections,
                                 final CheckedConsumer<? super Connection> consumer) {
        RuntimeException runtimeException = null;
        for (final TransactionSpecificConnection connection : connections) {
            try {
                consumer.accept(connection);
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
                } catch (final RuntimeException exception) {
                    if (runtimeException == null) {
                        runtimeException = exception;
                    } else {
                        runtimeException.addSuppressed(exception);
                    }
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
     * @return a non-{@code null} {@link Connection}
     *
     * @exception SQLException if an error occurs
     *
     * @exception RuntimeException if the {@link BooleanSupplier}
     * supplied at construction time that reports a transaction's
     * status throws a {@link RuntimeException}, or if the {@link
     * Supplier} supplied at construction time that retrieves a
     * delegate {@link DataSource} throws a {@link RuntimeException}
     *
     * @see DataSource#getConnection()
     *
     * @see DataSource#getConnection(String, String)
     */
    @Override // AbstractDataSource
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
     * @exception RuntimeException if the {@link BooleanSupplier}
     * supplied at construction time that reports a transaction's
     * status throws a {@link RuntimeException}, or if the {@link
     * Supplier} supplied at construction time that retrieves a
     * delegate {@link DataSource} throws a {@link RuntimeException}
     *
     * @see DataSource#getConnection()
     *
     * @see DataSource#getConnection(String, String)
     */
    @Override // AbstractDataSource
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
     * @exception RuntimeException if the {@link BooleanSupplier}
     * supplied at construction time that reports a transaction's
     * status throws a {@link RuntimeException}, or if the {@link
     * Supplier} supplied at construction time that retrieves a
     * delegate {@link DataSource} throws a {@link RuntimeException}
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
        if (this.transactionIsActiveSupplier.getAsBoolean()) {
            final Map<Object, TransactionSpecificConnection> extantConnections =
                CONNECTIONS_TL.get().computeIfAbsent(this, k -> new HashMap<>());
            final Object id;
            if (useZeroArgumentForm) {
                id = UNAUTHENTICATED_CONNECTION_IDENTIFIER;
            } else {
                id = new AuthenticatedConnectionIdentifier(username, password);
            }
            TransactionSpecificConnection tsc = extantConnections.get(id);
            if (tsc == null) {
                if (useZeroArgumentForm) {
                    tsc = new TransactionSpecificConnection(this.delegateSupplier.get().getConnection());
                } else {
                    tsc = new TransactionSpecificConnection(this.delegateSupplier.get().getConnection(username, password));
                }
                extantConnections.put(id, tsc);
            } else {
                tsc.setCloseCalled(false);
            }
            returnValue = tsc;
        } else if (useZeroArgumentForm) {
            returnValue = this.delegateSupplier.get().getConnection();
        } else {
            returnValue = this.delegateSupplier.get().getConnection(username, password);
        }
        return returnValue;
    }

    /**
     * A method conforming to the {@link Consumer} contract, used in
     * this class only via a method reference, that deliberately does
     * nothing.
     *
     * @param ignored ignored
     *
     * @see Consumer#accept(Object)
     */
    private static void sink(final Object ignored) {

    }


    /*
     * Inner and nested classes.
     */


    /**
     * A functional interface that accepts a payload and may throw an
     * {@link Exception} as a result.
     *
     * @param <T> the type of payload
     *
     * @see #accept(Object)
     */
    @FunctionalInterface
    private interface CheckedConsumer<T> {

        /**
         * Accepts the supplied payload in some way.
         *
         * @param payload the object to accept; may be {@code null}
         *
         * @exception Exception if an error occurs
         */
        void accept(T payload) throws Exception;

    }

    /**
     * A {@link ConditionallyCloseableConnection} that tracks when the
     * {@link #close()} method has been called and that handles
     * auto-commit gracefully.
     */
    private static final class TransactionSpecificConnection extends ConditionallyCloseableConnection {

        private final boolean oldAutoCommit;

        private boolean closeCalled;

        private TransactionSpecificConnection(final Connection delegate) throws SQLException {
            super(delegate, false /* not closeable */);
            this.oldAutoCommit = this.getAutoCommit();
            this.setAutoCommit(false);
        }

        private void restoreAutoCommit() throws SQLException {
            this.setAutoCommit(this.oldAutoCommit);
        }

        @Override // ConditionallyCloseableConnection
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

        private AuthenticatedConnectionIdentifier(final String username,
                                                  final String password) {
            super();
            this.username = username;
            this.password = password;
        }

        @Override // Object
        public int hashCode() {
            return Objects.hash(this.username, this.password);
        }

        @Override // Object
        public boolean equals(final Object other) {
            if (other == this) {
                return true;
            } else if (other != null && other.getClass().equals(AuthenticatedConnectionIdentifier.class)) {
                final AuthenticatedConnectionIdentifier her = (AuthenticatedConnectionIdentifier) other;
                return Objects.equals(this.username, her.username)
                    && Objects.equals(this.password, her.password);
            } else {
                return false;
            }
        }

    }

}
