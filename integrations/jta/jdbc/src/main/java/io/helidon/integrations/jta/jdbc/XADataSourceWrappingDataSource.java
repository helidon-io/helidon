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
import java.util.Objects;
import java.util.function.Consumer;

import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;

import io.helidon.integrations.jdbc.AbstractDataSource;

/**
 * An {@link AbstractDataSource} that adapts an {@link XADataSource}
 * to the {@link javax.sql.DataSource} contract.
 *
 * <p>The {@link XADataSource} being adapted must guarantee that when
 * {@link Connection#close() close()} is called on any {@link
 * Connection} that an {@link XAConnection} supplied by the {@link
 * XADataSource#getXAConnection()} method {@linkplain
 * XAConnection#getConnection() supplies}, the closing operation is
 * {@linkplain XAConnection#close() propagated to the
 * <code>XAConnection</code>}, or undefined behavior will result.</p>
 */
public final class XADataSourceWrappingDataSource extends AbstractDataSource {

    private final XADataSource xaDataSource;

    private final Consumer<? super XAResource> resourceEnlister;

    /**
     * Creates a new {@link XADataSourceWrappingDataSource}.
     *
     * @param xaDataSource the {@link XADataSource} to wrap; must not
     * be {@code null}
     *
     * @param resourceEnlister a {@link Consumer} of {@link
     * XAResource} instances that enlists them in an active XA
     * transaction; must not be {@code null}
     */
    public XADataSourceWrappingDataSource(final XADataSource xaDataSource,
                                          final Consumer<? super XAResource> resourceEnlister) {
        super();
        this.xaDataSource = Objects.requireNonNull(xaDataSource, "xaDataSource");
        this.resourceEnlister = Objects.requireNonNull(resourceEnlister, "resourceEnlister");
    }

    @Override // AbstractDataSource
    public Connection getConnection() throws SQLException {
        return this.getConnection(null, null, true);
    }

    @Override // AbstractDataSource
    public Connection getConnection(final String username, final String password) throws SQLException {
        return this.getConnection(username, password, false);
    }

    private Connection getConnection(final String username,
                                     final String password,
                                     final boolean useZeroArgumentForm)
        throws SQLException {
        final XAConnection xaConnection;
        if (useZeroArgumentForm) {
            xaConnection = this.xaDataSource.getXAConnection();
        } else {
            xaConnection = this.xaDataSource.getXAConnection(username, password);
        }
        try {
            this.resourceEnlister.accept(xaConnection.getXAResource());
        } catch (final RuntimeException e) {
            throw new SQLException(e.getMessage(), e);
        }
        // I am not confident about this.  Note that the xaConnection
        // is not closed.  And yet, the end consumer knows nothing of
        // XAConnections so cannot close it herself.  So is
        // Connection#close() invoked on the return value of
        // XAConnection#getConnection() guaranteed to call through to
        // XAConnection#close()?  Using H2 as an arbitrary example, we
        // can see this is the case:
        // https://github.com/h2database/h2database/blob/12fcf4c219e26176d4027e72eb5f9f0c797f0152/h2/src/main/org/h2/jdbcx/JdbcXAConnection.java#L74-L89
        // Is that mandated anywhere?  I'm honestly not sure.
        return xaConnection.getConnection();
    }

}
