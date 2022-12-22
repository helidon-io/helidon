/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
import java.sql.SQLTransientException;
import java.util.Objects;
import java.util.function.Consumer;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;

import io.helidon.integrations.jdbc.AbstractDataSource;

/**
 * An {@link AbstractDataSource} that adapts an {@link XADataSource}
 * to the {@link javax.sql.DataSource} contract.
 *
 * @deprecated This class is slated for removal with no replacement.
 */
@Deprecated(forRemoval = true, since = "3.0.3")
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
    public XADataSourceWrappingDataSource(XADataSource xaDataSource,
                                          Consumer<? super XAResource> resourceEnlister) {
        super();
        this.xaDataSource = Objects.requireNonNull(xaDataSource, "xaDataSource");
        this.resourceEnlister = Objects.requireNonNull(resourceEnlister, "resourceEnlister");
    }

    @Override // AbstractDataSource
    public Connection getConnection() throws SQLException {
        return this.getConnection(null, null, true);
    }

    @Override // AbstractDataSource
    public Connection getConnection(String username, String password) throws SQLException {
        return this.getConnection(username, password, false);
    }

    private Connection getConnection(String username,
                                     String password,
                                     boolean useZeroArgumentForm)
        throws SQLException {
        XAConnection xaConnection =
            useZeroArgumentForm ? this.xaDataSource.getXAConnection() : this.xaDataSource.getXAConnection(username, password);
        ConnectionEventListener l = new ConnectionEventListener() {
                @Override
                public void connectionClosed(ConnectionEvent event) {
                    try {
                        ((XAConnection) event.getSource()).close();
                    } catch (SQLException e) {
                        try {
                            ((XAConnection) event.getSource()).removeConnectionEventListener(this);
                        } catch (RuntimeException e2) {
                            e.addSuppressed(e2);
                        }
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                    ((XAConnection) event.getSource()).removeConnectionEventListener(this);
                }
                @Override
                public void connectionErrorOccurred(ConnectionEvent event) {
                    try {
                        ((XAConnection) event.getSource()).close();
                    } catch (SQLException e) {
                        SQLException original = event.getSQLException();
                        if (original != null) {
                            original.addSuppressed(e);
                            e = original;
                        }
                        try {
                            ((XAConnection) event.getSource()).removeConnectionEventListener(this);
                        } catch (RuntimeException e2) {
                            e.addSuppressed(e2);
                        }
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                    ((XAConnection) event.getSource()).removeConnectionEventListener(this);
                }
            };
        xaConnection.addConnectionEventListener(l);
        try {
            this.resourceEnlister.accept(xaConnection.getXAResource());
        } catch (RuntimeException e) {
            try {
                xaConnection.removeConnectionEventListener(l);
            } catch (RuntimeException e2) {
                e.addSuppressed(e2);
            }
            throw new SQLTransientException(e.getMessage(), e);
        }
        return xaConnection.getConnection();
    }

}
