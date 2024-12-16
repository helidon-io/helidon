/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.util.Objects;

import javax.sql.DataSource;
import javax.sql.XAConnection;
import javax.sql.XADataSource;

import io.helidon.integrations.jdbc.AbstractDataSource;

import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * An {@link AbstractDataSource} that wraps another {@link DataSource} that might not behave correctly in the presence
 * of JTA transaction management, such as one supplied by any of several freely and commercially available connection
 * pools, and that makes such a non-JTA-aware {@link DataSource} behave as sensibly as possible in the presence of a
 * JTA-managed transaction.
 */
public final class JtaAdaptingDataSource extends AbstractDataSource {


    /*
     * Instance fields.
     */


    private final AuthenticatedConnectionSupplier acs;

    private final UnauthenticatedConnectionSupplier uacs;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JtaAdaptingDataSource} that wraps the supplied {@link DataSource} and helps its {@linkplain
     * DataSource#getConnection() connections} participate in XA transactions.
     *
     * <p>Behavior is left deliberately undefined if the supplied {@link DataSource}'s {@link
     * DataSource#getConnection()} or {@link DataSource#getConnection(String, String)} methods are implemented to return
     * or augment the return value of an invocation of the {@link javax.sql.PooledConnection#getConnection()
     * XAConnection#getConnection()} method.  Less formally, and in general, this class is deliberately not designed to
     * work with JDBC constructs that are already XA-aware.</p>
     *
     * @param ts a {@link TransactionSupplier}; must not be {@code null}
     *
     * @param tsr a {@link TransactionSynchronizationRegistry}; must not be {@code null}
     *
     * @param interposedSynchronizations whether any {@link jakarta.transaction.Synchronization Synchronization}s
     * registered should be registered as interposed synchronizations; see {@link
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(jakarta.transaction.Synchronization)} and
     * {@link jakarta.transaction.Transaction#registerSynchronization(jakarta.transaction.Synchronization)}
     *
     * @param ec an {@link ExceptionConverter}; may be {@code null} in which case a default implementation will be used
     * instead
     *
     * @param ds a {@link DataSource} that may not be XA-compliant; must not be {@code null}; normally supplied by a
     * connection pool implementation
     *
     * @param immediateEnlistment whether attempts to enlist new {@link Connection}s in a global transaction should be
     * made immediately upon {@link Connection} allocation
     *
     * @exception NullPointerException if {@code ts}, {@code tsr} or {@code ds} is {@code null}
     *
     * @see #JtaAdaptingDataSource(TransactionSupplier, TransactionSynchronizationRegistry, boolean, ExceptionConverter,
     * DataSource, boolean, boolean)
     */
    public JtaAdaptingDataSource(TransactionSupplier ts,
                                 TransactionSynchronizationRegistry tsr,
                                 boolean interposedSynchronizations,
                                 ExceptionConverter ec,
                                 DataSource ds,
                                 boolean immediateEnlistment) {
        this(ts, tsr, interposedSynchronizations, ec, ds, immediateEnlistment, true);
    }

    /**
     * Creates a new {@link JtaAdaptingDataSource} that wraps the supplied {@link DataSource} and helps its {@linkplain
     * DataSource#getConnection() connections} participate in XA transactions.
     *
     * <p>Behavior is left deliberately undefined if the supplied {@link DataSource}'s {@link
     * DataSource#getConnection()} or {@link DataSource#getConnection(String, String)} methods are implemented to return
     * or augment the return value of an invocation of the {@link javax.sql.PooledConnection#getConnection()
     * XAConnection#getConnection()} method.  Less formally, and in general, this class is deliberately not designed to
     * work with JDBC constructs that are already XA-aware.</p>
     *
     * @param ts a {@link TransactionSupplier}; must not be {@code null}
     *
     * @param tsr a {@link TransactionSynchronizationRegistry}; must not be {@code null}
     *
     * @param interposedSynchronizations whether any {@link jakarta.transaction.Synchronization Synchronization}s
     * registered should be registered as interposed synchronizations; see {@link
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(jakarta.transaction.Synchronization)} and
     * {@link jakarta.transaction.Transaction#registerSynchronization(jakarta.transaction.Synchronization)}
     *
     * @param ec an {@link ExceptionConverter}; may be {@code null} in which case a default implementation will be used
     * instead
     *
     * @param ds a {@link DataSource} that may not be XA-compliant; must not be {@code null}; normally supplied by a
     * connection pool implementation
     *
     * @param immediateEnlistment whether attempts to enlist new {@link Connection}s in a global transaction should be
     * made immediately upon {@link Connection} allocation
     *
     * @param preemptiveEnlistmentChecks whether early checks will be made to see if an enlistment attempt will succeed,
     * or whether enlistment validation will be performed by the JTA implementation
     *
     * @exception NullPointerException if {@code ts}, {@code tsr} or {@code ds} is {@code null}
     */
    public JtaAdaptingDataSource(TransactionSupplier ts,
                                 TransactionSynchronizationRegistry tsr,
                                 boolean interposedSynchronizations,
                                 ExceptionConverter ec,
                                 DataSource ds,
                                 boolean immediateEnlistment,
                                 boolean preemptiveEnlistmentChecks) {
        super();
        Objects.requireNonNull(ts, "ts");
        Objects.requireNonNull(tsr, "tsr");
        if (ds instanceof XADataSource xads) {
            // Some connection pools supply XADataSource objects that pool XAConnections while also unconventionally
            // exposing them to end users, and also implement the javax.sql.DataSource interface by throwing exceptions
            // when its methods are invoked. Although XAConnections are not intended for end users, when logical
            // representations of pooled XAConnections *are* supplied to end users by such a connection pool, those
            // "borrowed" representations must be "returned" via their close() methods, counter to the documentation of
            // the (inherited) PooledConnection#close() method, which reads, in part: "An application never calls this
            // method directly; it is called by the connection pool module, or manager."  As of this writing this branch
            // of this constructor implements this non-standard behavior.
            this.acs = (u, p) ->
                xa(ts,
                   tsr,
                   interposedSynchronizations,
                   ec,
                   xads.getXAConnection(u, p),
                   immediateEnlistment,
                   preemptiveEnlistmentChecks,
                   true);
            this.uacs = () ->
                xa(ts,
                   tsr,
                   interposedSynchronizations,
                   ec,
                   xads.getXAConnection(),
                   immediateEnlistment,
                   preemptiveEnlistmentChecks,
                   true);
        } else {
            Objects.requireNonNull(ds, "ds");
            this.acs =
                (u, p) -> new JtaConnection(ts, tsr, interposedSynchronizations, ec, ds.getConnection(u, p), immediateEnlistment);
            this.uacs = () -> new JtaConnection(ts, tsr, interposedSynchronizations, ec, ds.getConnection(), immediateEnlistment);
        }
    }

    /**
     * Creates a new {@link JtaAdaptingDataSource} that adapts the supplied {@link XADataSource} and helps {@link
     * Connection}s it indirectly supplies (by way of its {@linkplain XADataSource#getXAConnection() associated
     * <code>XAConnection</code>}) participate in XA transactions.
     *
     * @param ts a {@link TransactionSupplier}; must not be {@code null}
     *
     * @param tsr a {@link TransactionSynchronizationRegistry}; must not be {@code null}
     *
     * @param interposedSynchronizations whether any {@link jakarta.transaction.Synchronization Synchronization}s
     * registered should be registered as interposed synchronizations; see {@link
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(jakarta.transaction.Synchronization)} and
     * {@link jakarta.transaction.Transaction#registerSynchronization(jakarta.transaction.Synchronization)}
     *
     * @param ec an {@link ExceptionConverter}; may be {@code null} in which case a default implementation will be used
     * instead
     *
     * @param xads an {@link XADataSource} supplied by a connection pool implementation; must not be {@code null}
     *
     * @param immediateEnlistment whether attempts to enlist new {@link Connection}s in a global transaction should be
     * made immediately upon {@link Connection} allocation
     *
     * @param closeXac whether or not {@link XAConnection}s {@linkplain XADataSource#getXAConnection() supplied} by the
     * supplied {@link XADataSource} should be {@linkplain javax.sql.PooledConnection#close() closed} when {@linkplain
     * XAConnection#getConnection() their <code>Connection</code>}s are {@linkplain Connection#close() closed} (in a
     * non-standard fashion)
     *
     * @exception NullPointerException if {@code ts}, {@code tsr} or {@code xads} is {@code null}
     *
     * @deprecated This constructor exists only to handle certain XA-aware connection pools that allow an end-user
     * caller to "borrow" {@link XAConnection}s and to "return" them using their {@link
     * javax.sql.PooledConnection#close() close()} methods, a non-standard practice which is discouraged by the
     * documentation of {@link javax.sql.PooledConnection} (from which {@link XAConnection} inherits).  For
     * such connection pools, {@link XAConnection}s that are "borrowed" must be returned in this manner to avoid leaks.
     * This constructor implements this behavior.  Before using it, you should make sure that the connection pool in
     * question implementing or supplying the {@link XADataSource} has the behavior described above; normally an {@link
     * XAConnection} should not be used directly or closed by end-user code.
     */
    @Deprecated(since = "3.1.0")
    public JtaAdaptingDataSource(TransactionSupplier ts,
                                 TransactionSynchronizationRegistry tsr,
                                 boolean interposedSynchronizations,
                                 ExceptionConverter ec,
                                 XADataSource xads,
                                 boolean immediateEnlistment,
                                 boolean closeXac) {
        this(ts, tsr, interposedSynchronizations, ec, xads, immediateEnlistment, true, closeXac);
    }

    /**
     * Creates a new {@link JtaAdaptingDataSource} that adapts the supplied {@link XADataSource} and helps {@link
     * Connection}s it indirectly supplies (by way of its {@linkplain XADataSource#getXAConnection() associated
     * <code>XAConnection</code>}) participate in XA transactions.
     *
     * @param ts a {@link TransactionSupplier}; must not be {@code null}
     *
     * @param tsr a {@link TransactionSynchronizationRegistry}; must not be {@code null}
     *
     * @param interposedSynchronizations whether any {@link jakarta.transaction.Synchronization Synchronization}s
     * registered should be registered as interposed synchronizations; see {@link
     * TransactionSynchronizationRegistry#registerInterposedSynchronization(jakarta.transaction.Synchronization)} and
     * {@link jakarta.transaction.Transaction#registerSynchronization(jakarta.transaction.Synchronization)}
     *
     * @param ec an {@link ExceptionConverter}; may be {@code null} in which case a default implementation will be used
     * instead
     *
     * @param xads an {@link XADataSource} supplied by a connection pool implementation; must not be {@code null}
     *
     * @param immediateEnlistment whether attempts to enlist new {@link Connection}s in a global transaction should be
     * made immediately upon {@link Connection} allocation
     *
     * @param preemptiveEnlistmentChecks whether early checks will be made to see if an enlistment attempt will succeed,
     * or whether enlistment validation will be performed by the JTA implementation
     *
     * @param closeXac whether or not {@link XAConnection}s {@linkplain XADataSource#getXAConnection() supplied} by the
     * supplied {@link XADataSource} should be {@linkplain javax.sql.PooledConnection#close() closed} when {@linkplain
     * XAConnection#getConnection() their <code>Connection</code>}s are {@linkplain Connection#close() closed} (in a
     * non-standard fashion)
     *
     * @exception NullPointerException if {@code ts}, {@code tsr} or {@code xads} is {@code null}
     *
     * @deprecated This constructor exists only to handle certain XA-aware connection pools that allow an end-user
     * caller to "borrow" {@link XAConnection}s and to "return" them using their {@link
     * javax.sql.PooledConnection#close() close()} methods, a non-standard practice which is discouraged by the
     * documentation of {@link javax.sql.PooledConnection} (from which {@link XAConnection} inherits).  For
     * such connection pools, {@link XAConnection}s that are "borrowed" must be returned in this manner to avoid leaks.
     * This constructor implements this behavior.  Before using it, you should make sure that the connection pool in
     * question implementing or supplying the {@link XADataSource} has the behavior described above; normally an {@link
     * XAConnection} should not be used directly or closed by end-user code.
     */
    @Deprecated(since = "3.1.0")
    public JtaAdaptingDataSource(TransactionSupplier ts,
                                 TransactionSynchronizationRegistry tsr,
                                 boolean interposedSynchronizations,
                                 ExceptionConverter ec,
                                 XADataSource xads,
                                 boolean immediateEnlistment,
                                 boolean preemptiveEnlistmentChecks,
                                 boolean closeXac) {
        super();
        Objects.requireNonNull(xads, "xads");
        Objects.requireNonNull(ts, "ts");
        Objects.requireNonNull(tsr, "tsr");
        // Some connection pools supply XADataSource objects that pool XAConnections. In all likelihood, they should not
        // do this, given that XAConnections are supposed to be for the innards of connection pools, not for end users.
        // Nevertheless, when such XAConnections are pooled and logical representations of them are supplied by such a
        // connection pool, those representations must be closed, counter to the documentation of the (inherited)
        // PooledConnection#close() method, which reads, in part: "An application never calls this method directly; it
        // is called by the connection pool module, or manager."  As of this writing this constructor permits this
        // non-standard behavior when closeXac is true.
        this.acs =
            (u, p) ->
            xa(ts,
               tsr,
               interposedSynchronizations,
               ec,
               xads.getXAConnection(u, p),
               immediateEnlistment,
               preemptiveEnlistmentChecks,
               closeXac);
        this.uacs = () ->
            xa(ts,
               tsr,
               interposedSynchronizations,
               ec,
               xads.getXAConnection(),
               immediateEnlistment,
               preemptiveEnlistmentChecks,
               closeXac);
    }

    @Override // DataSource
    public Connection getConnection(String username, String password) throws SQLException {
        return this.acs.getConnection(username, password);
    }

    @Override // DataSource
    public Connection getConnection() throws SQLException {
        return this.uacs.getConnection();
    }


    /*
     * Static methods.
     */


    @Deprecated
    @SuppressWarnings("ParameterNumber")
    private static JtaConnection xa(TransactionSupplier ts,
                                    TransactionSynchronizationRegistry tsr,
                                    boolean interposedSynchronizations,
                                    ExceptionConverter ec,
                                    XAConnection xac,
                                    boolean immediateEnlistment,
                                    boolean preemptiveEnlistmentChecks,
                                    boolean closeXac)
        throws SQLException {
        if (closeXac) {
            // Some connection pools allow you to "borrow" XAConnections. XAConnections were never intended to be
            // exposed to end users in this fashion. To return a "borrowed" XAConnection, you invoke its close() method,
            // which violates the contract of PooledConnection#close(), which XAConnection inherits, whose documentation
            // reads: "An application never calls this method directly; it is called by the connection pool module, or
            // manager." This branch of this method implements this non-standard behavior, ensuring that both the
            // Connection and its sourcing XAConnection are closed appropriately.
            return
                new JtaConnection(ts,
                                  tsr,
                                  interposedSynchronizations,
                                  ec,
                                  xac.getConnection(),
                                  xac::getXAResource,
                                  null, // no Xid consumer
                                  immediateEnlistment,
                                  preemptiveEnlistmentChecks) {
                @Override
                protected void onClose() throws SQLException {
                    xac.close();
                }
            };
        }
        return
            new JtaConnection(ts,
                              tsr,
                              interposedSynchronizations,
                              ec,
                              xac.getConnection(),
                              xac::getXAResource,
                              null, // no Xid consumer
                              immediateEnlistment,
                              preemptiveEnlistmentChecks);
    }


    /*
     * Inner and nested classes.
     */


    @FunctionalInterface
    private interface UnauthenticatedConnectionSupplier {

        Connection getConnection() throws SQLException;

    }

    @FunctionalInterface
    private interface AuthenticatedConnectionSupplier {

        Connection getConnection(String username, String password) throws SQLException;

    }

}
