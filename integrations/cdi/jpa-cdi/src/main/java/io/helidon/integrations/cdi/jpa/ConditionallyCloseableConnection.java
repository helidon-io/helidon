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

/**
 * A {@link DelegatingConnection} whose {@link #close()} method
 * performs a close only if the {@link #isCloseable()} method returns
 * {@code true}.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are not necessarily safe for concurrent
 * use by multiple threads.</p>
 *
 * @see #isCloseable()
 *
 * @see #setCloseable(boolean)
 *
 * @see #close()
 */
class ConditionallyCloseableConnection extends DelegatingConnection {


    /*
     * Instance fields.
     */


    /**
     * Whether or not the {@link #close()} method will actually close
     * this {@link DelegatingConnection}.
     *
     * @see #isCloseable()
     *
     * @see #setCloseable(boolean)
     */
    private boolean closeable;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ConditionallyCloseableConnection} and
     * {@linkplain #setCloseable(boolean) sets its closeable status to
     * <code>true</code>}.
     *
     * @param delegate the {@link Connection} to wrap; must not be
     * {@code null}
     *
     * @exception NullPointerException if {@code delegate} is {@code
     * null}
     *
     * @see #ConditionallyCloseableConnection(Connection, boolean)
     *
     * @see #setCloseable(boolean)
     */
    ConditionallyCloseableConnection(final Connection delegate) {
        this(delegate, true);
    }

    /**
     * Creates a new {@link ConditionallyCloseableConnection}.
     *
     * @param delegate the {@link Connection} to wrap; must not be
     * {@code null}
     *
     * @param closeable the initial value for this {@link
     * ConditionallyCloseableConnection}'s {@linkplain #isCloseable()
     * closeable} status
     *
     * @exception NullPointerException if {@code delegate} is {@code
     * null}
     *
     * @see #setCloseable(boolean)
     *
     * @see DelegatingConnection#DelegatingConnection(Connection)
     */
    ConditionallyCloseableConnection(final Connection delegate, final boolean closeable) {
        super(delegate);
        this.setCloseable(closeable);
    }


    /*
     * Instance methods.
     */


    /**
     * Overrides the {@link DelegatingConnection#close()} method so
     * that when it is invoked this {@link
     * ConditionallyCloseableConnection} is {@linkplain
     * Connection#close() closed} only if {@linkplain
     * Connection#isClosed() it is not already closed} and if it
     * {@linkplain #isCloseable() is closeable} and if the {@link
     * #reset()} method completes normally.
     *
     * <p>If the {@link DelegatingConnection#close()} method is
     * invoked successfully, then the {@link #closed()} method is
     * called.</p>
     *
     * @exception SQLException if an error occurs
     *
     * @see #isClosed()
     *
     * @see #isCloseable()
     *
     * @see #reset()
     *
     * @see #closed()
     */
    @Override
    public void close() throws SQLException {
        if (this.isCloseable()) {
            assert !this.isClosed();
            this.reset();
            super.close();
            this.closed();
            assert this.isClosed();
        }
    }

    /**
     * Returns {@code true} if a call to {@link #close()} will
     * actually close this {@link ConditionallyCloseableConnection}.
     *
     * <p>This method returns {@code true} when {@link
     * #setCloseable(boolean)} has been called with a value of {@code
     * true} and the {@link #isClosed()} method returns {@code
     * false}.</p>
     *
     * @return {@code true} if a call to {@link #close()} will
     * actually close this {@link ConditionallyCloseableConnection};
     * {@code false} in all other cases
     *
     * @exception SQLException if {@link #isClosed()} throws a {@link
     * SQLException}
     *
     * @see #setCloseable(boolean)
     *
     * @see #close()
     *
     * @see #isClosed()
     */
    public final boolean isCloseable() throws SQLException {
        return this.closeable && !this.isClosed();
    }

    /**
     * Sets the closeable status of this {@link
     * ConditionallyCloseableConnection}.
     *
     * <p>Note that calling this method with a value of {@code true}
     * does not necessarily mean that the {@link #isCloseable()}
     * method will subsequently return {@code true}, since the {@link
     * #isClosed()} method may return {@code true}.</p>
     *
     * @param closeable whether or not a call to {@link #close()} will
     * actually close this {@link ConditionallyCloseableConnection}
     *
     * @see #isCloseable()
     *
     * @see #close()
     *
     * @see Connection#close()
     *
     * @see #isClosed()
     */
    public final void setCloseable(final boolean closeable) {
        this.closeable = closeable;
    }

    /**
     * Called immediately before an actual {@link Connection#close()}
     * operation is actually going to take place.
     *
     * <p>The default implementation of this method calls {@link
     * #setCloseable(boolean)} with {@code true} as a parameter value
     * ensuring that the actual {@link Connection#close()} operation
     * will not be blocked or reimplemented in any way.</p>
     *
     * <p>Overrides must not call the {@link #close()} method or
     * undefined behavior will result.</p>
     *
     * @exception SQLException if an error occurs
     *
     * @see #close()
     */
    protected void reset() throws SQLException {
        this.setCloseable(true);
    }

    /**
     * Called immediately after an actual {@link Connection#close()}
     * operation has actually completed successfully.
     *
     * <p>The default implementation of this method does nothing.</p>
     *
     * <p>Overrides must not call the {@link #close()} method or
     * undefined behavior will result.</p>
     *
     * <p>It is guaranteed that from within this method {@link
     * #isClosed()} will return {@code true}.</p>
     *
     * @exception SQLException if an error occurs
     *
     * @see #close()
     *
     * @see #isClosed()
     */
    protected void closed() throws SQLException {

    }

}
