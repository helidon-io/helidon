/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.WeakHashMap;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

/**
 * An {@link AbstractDataSource} that {@linkplain #getConnection()
 * supplies} the same {@link Connection} over and over again to the
 * same {@link Thread} until such time as the {@link
 * #setConnectionCloseable()} method is called.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are safe for concurrent use by multiple
 * threads.  Note, however, that the JDBC specification in recent
 * revisions has deliberately retracted all language related to thread
 * safety, indicating that most if not all JDBC constructs are
 * expected to be used by a single thread only.</p>
 *
 * @see #setConnectionCloseable()
 */
class ConnectionPinningDataSource extends AbstractDataSource {

    private static final ThreadLocal<? extends Map<ConnectionPinningDataSource, ConditionallyCloseableConnection>>
        PINNED_CONNECTION_MAP_THREAD_LOCAL = ThreadLocal.withInitial(() -> new WeakHashMap<>());

    private final ConnectionSupplier connectionSource;

    ConnectionPinningDataSource(final ConnectionSupplier connectionSource) {
        super();
        this.connectionSource = Objects.requireNonNull(connectionSource);
        PINNED_CONNECTION_MAP_THREAD_LOCAL.get().put(this, null);
    }

    protected final ConditionallyCloseableConnection getPinnedConnection() {
        final Map<ConnectionPinningDataSource, ConditionallyCloseableConnection> map = PINNED_CONNECTION_MAP_THREAD_LOCAL.get();
        assert map != null;
        assert map.containsKey(this);
        final ConditionallyCloseableConnection returnValue = map.get(this);
        return returnValue;
    }

    /**
     * @exception IllegalStateException if an override of either the
     * {@link #createPinnableConnection(ConnectionSupplier)} method or
     * the {@link
     * #configureConnection(ConditionallyCloseableConnection)} method
     * returned {@code null}
     *
     * @see #createPinnableConnection(ConnectionSupplier)
     *
     * @see #configureConnection(ConditionallyCloseableConnection)
     */
    @Override
    public final Connection getConnection() throws SQLException {
        ConditionallyCloseableConnection returnValue = this.getPinnedConnection();
        if (returnValue == null || returnValue.isClosed() || returnValue.isCloseable()) {
            // Get a new connection for this thread because the
            // existing one is null, closed or is not closed but can
            // be closed by its current user whom we know nothing
            // about.
            returnValue = this.configureConnection(this.createPinnableConnection(this.connectionSource));
            if (returnValue == null) {
                throw new IllegalStateException("configureConnection(ConditionallyCloseableConnection) == null");
            }
            PINNED_CONNECTION_MAP_THREAD_LOCAL.get().put(this, returnValue);
        }
        return returnValue;
    }

    /**
     * Returns a new, non-{@code null} {@link
     * ConditionallyCloseableConnection} when invoked.
     *
     * <p>The default implementation of this method returns a new,
     * non-{@code null} {@link PinnableConnection}.</p>
     *
     * <p>Note that after this method is called, the {@link
     * #configureConnection(ConditionallyCloseableConnection)} method
     * will be called with the return value of this method as its sole
     * parameter.</p>
     *
     * @param connectionSupplier a {@link ConnectionSupplier} that can
     * be used to produce a {@link Connection} that might be wrapped
     * by the {@link ConditionallyCloseableConnection} that will be
     * returned; will not be {@code null}
     *
     * @return a new, non-{@code null} {@link
     * ConditionallyCloseableConnection}
     *
     * @exception SQLException if an error occurs
     *
     * @exception NullPointerException if somehow {@code
     * connectionSupplier} is {@code null}
     *
     * @see ConnectionSupplier
     *
     * @see #configureConnection(ConditionallyCloseableConnection)
     */
    protected ConditionallyCloseableConnection createPinnableConnection(final ConnectionSupplier connectionSupplier)
        throws SQLException {
        return new PinnableConnection(connectionSupplier.getConnection());
    }

    protected ConditionallyCloseableConnection configureConnection(final ConditionallyCloseableConnection connection)
        throws SQLException {
        return connection;
    }

    protected void resetConnection(final Connection connection) throws SQLException {

    }

    @Override
    public Connection getConnection(final String username, final String password) {
        throw new UnsupportedOperationException();
    }

    /**
     * Forcibly but gracefully removes internal references to the sole
     * {@link Connection} {@linkplain #getConnection() supplied} by
     * this {@link ConnectionPinningDataSource}.
     *
     * <p>The {@link Connection} in question is {@linkplain
     * Connection#close() closed}.</p>
     *
     * @exception SQLException if an error occurs
     */
    final void reset() throws SQLException {
        final ConditionallyCloseableConnection pinnedConnection = PINNED_CONNECTION_MAP_THREAD_LOCAL.get().put(this, null);
        if (pinnedConnection != null && !pinnedConnection.isClosed()) {
            pinnedConnection.setCloseable(true);
            pinnedConnection.close();
        }
    }

    protected class PinnableConnection extends ConditionallyCloseableConnection {

        protected PinnableConnection(final Connection delegate) {
            super(delegate);
        }

        @Override
        protected final void reset() throws SQLException {
            super.reset();
            ConnectionPinningDataSource.this.resetConnection(this);
        }

        @Override
        protected final void closed() throws SQLException {
            super.closed();
            assert this.isClosed();
            ConnectionPinningDataSource.this.reset();
        }
    }


}
