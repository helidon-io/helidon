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
import java.util.Objects;

import javax.sql.DataSource;

/**
 * An {@link AbstractDataSource} that {@linkplain #getConnection()
 * supplies} the same {@link Connection} over and over again until
 * such time as the {@link #setConnectionCloseable()} method is
 * called.
 * 
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are not safe for concurrent use by
 * multiple threads.  The JDBC specification in recent revisions
 * has deliberately retracted all language related to thread
 * safety, indicating that most if not all JDBC constructs are
 * expected to be used by a single thread only.</p>
 *
 * <p>The one notable exception to this general policy is the {@link
 * #setConnectionCloseable()} method, which is safe for concurrent use
 * by multiple threads.</p>
 *
 * @see #setConnectionCloseable()
 */
class ConnectionPinningDataSource extends AbstractDataSource {

    private PinnableConnection pinnedConnection;

    private final ConnectionSupplier connectionSource;

    private volatile boolean connectionCloseable;

    ConnectionPinningDataSource(final ConnectionSupplier connectionSource) throws SQLException {
        super();
        this.connectionSource = Objects.requireNonNull(connectionSource);
    }

    @Override
    public final Connection getConnection() throws SQLException {
        PinnableConnection returnValue = this.pinnedConnection;
        if (returnValue == null || returnValue.isClosed() || this.isConnectionCloseable()) {
            // If returnValue is not null and is not closed but
            // isConnectionCloseable() returns true, then we are in a
            // situation where a Connection supplied by this method is
            // Out There In The World, but is not closed, but *can* be
            // closed.  Here, we return a new one (and pin it).  This
            // means the extant one is now a leak.
            returnValue = this.createPinnableConnection(this.connectionSource);
            if (returnValue == null) {
                throw new IllegalStateException("createPinnableConnection(ConnectionSupplier) == null");
            }
            this.configureConnection(returnValue);
            this.pinnedConnection = returnValue;
            this.connectionCloseable = false;
        }
        return returnValue;
    }

    protected PinnableConnection createPinnableConnection(final ConnectionSupplier connectionSource) throws SQLException {
        return new PinnableConnection(connectionSource.getConnection());
    }

    protected void configureConnection(final Connection connection) throws SQLException {
        
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
     * <p>This method calls {@link #setConnectionCloseable()}.</p>
     * 
     * <p>The {@link Connection} in question is {@linkplain
     * Connection#close() closed}.</p>
     *
     * @exception SQLException if an error occurs
     */
    private final void reset() throws SQLException {
        final Connection pinnedConnection = this.pinnedConnection;
        this.pinnedConnection = null;
        this.setConnectionCloseable();
        if (pinnedConnection != null) {
            pinnedConnection.close();
        }
    }

    /**
     * Marks the outstanding {@link Connection} (if any) produced by
     * the {@link #getConnection()} method as being
     * <em>closeable</em>, i.e. when its {@link Connection#close()}
     * method is invoked it will actually forward the close operation
     * on to the underlying connection.  In addition, a subsequent
     * call to {@Link #getConnection()} will return a new {@link
     * Connection}.
     *
     * <h2>Thread Safety</h2>
     *
     * <p>This method is safe for concurrent use by multiple
     * {@link Thread}s.</p>
     *
     * <h2>Idempotency</h2>
     *
     * <p>This method is idempotent.</p>
     */
    final void setConnectionCloseable() {
        this.connectionCloseable = true;
    }

    final boolean isConnectionCloseable() {
        return this.connectionCloseable;
    }

    private class PinnableConnection extends ConditionallyCloseableConnection {

        private PinnableConnection(final Connection delegate) {
            super(delegate);
        }
        
        @Override
        protected final boolean isCloseable() throws SQLException {
            return ConnectionPinningDataSource.this.isConnectionCloseable();
        }

        protected final void reset() throws SQLException {
            ConnectionPinningDataSource.this.resetConnection(this);            
        }
        
        @Override
        protected final void closed() throws SQLException {
            assert this.isClosed();
            ConnectionPinningDataSource.this.reset();
        }
    }

    
}
