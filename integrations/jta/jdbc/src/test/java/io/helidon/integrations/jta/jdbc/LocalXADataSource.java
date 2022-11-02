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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import javax.sql.XAConnection;
import javax.sql.XADataSource;

import io.helidon.integrations.jdbc.AbstractCommonDataSource;

/**
 * An {@link AbstractCommonDataSource}, a {@link
 * ConnectionPoolDataSource} implementation and a very special-purpose
 * {@link XADataSource} implementation that is intended to adapt a
 * non-XA-compliant {@link DataSource} to the {@link XADataSource}
 * interface.
 *
 * @see XADataSource
 *
 * @see LocalXAConnection
 */
public final class LocalXADataSource extends AbstractCommonDataSource implements ConnectionPoolDataSource, XADataSource {


    /*
     * Instance fields.
     */


    private final ConnectionSupplier connectionSupplier;

    private final AuthenticatedConnectionSupplier authenticatedConnectionSupplier;


    /*
     * Constructors.
     */


    /**
     * Creates a {@link LocalXADataSource} whose underlying {@link
     * Connection}s are supplied by the supplied {@link DataSource}.
     *
     * @param dataSource a {@link DataSource}; must not be {@code
     * null}; must not return {@code null} from its {@link
     * DataSource#getConnection()} and {@link
     * DataSource#getConnection(String, String)} methods
     *
     * @exception NullPointerException if {@code dataSource} is {@code
     * null}
     *
     * @see #LocalXADataSource(ConnectionSupplier,
     * AuthenticatedConnectionSupplier)
     */
    public LocalXADataSource(DataSource dataSource) {
        this(dataSource::getConnection, dataSource::getConnection);
    }

    /**
     * Creates a {@link LocalXADataSource} whose underlying {@link
     * Connection}s are supplied by the supplied {@link
     * ConnectionSupplier} and {@link
     * AuthenticatedConnectionSupplier}.
     *
     * @param connectionSupplier a {@link ConnectionSupplier}; must
     * not be {@code null}; must be safe for concurrent use by
     * multiple threads; must not return {@code null} from its {@link
     * ConnectionSupplier#getConnection()} method
     *
     * @param authenticatedConnectionSupplier an {@link
     * AuthenticatedConnectionSupplier}; must not be {@code null};
     * must be safe for concurrent use by multiple threads; must not
     * return {@code null} from its {@link
     * AuthenticatedConnectionSupplier#getConnection(String, String)}
     * method
     *
     * @exception NullPointerException if either argument is {@code
     * null}
     */
    public LocalXADataSource(ConnectionSupplier connectionSupplier,
                             AuthenticatedConnectionSupplier authenticatedConnectionSupplier) {
        super();
        this.connectionSupplier = Objects.requireNonNull(connectionSupplier, "connectionSupplier");
        this.authenticatedConnectionSupplier =
            Objects.requireNonNull(authenticatedConnectionSupplier, "authenticatedConnectionSupplier");
    }


    /*
     * Instance methods.
     */


    /**
     * Returns a new {@link PooledConnection}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return a new {@link PooledConnection}; never {@code null}
     *
     * @exception SQLException if an error occurs
     */
    @Override // ConnectionPoolDataSource
    public PooledConnection getPooledConnection() throws SQLException {
        return new SimplePooledConnection(this.connectionSupplier.getConnection());
    }

    /**
     * Returns a new {@link PooledConnection}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param user the user; may be {@code null}
     *
     * @param password the password; may be {@code null}
     *
     * @return a new {@link PooledConnection}; never {@code null}
     *
     * @exception SQLException if an error occurs
     */
    @Override // ConnectionPoolDataSource
    public PooledConnection getPooledConnection(String user, String password) throws SQLException {
        return new SimplePooledConnection(this.authenticatedConnectionSupplier.getConnection(user, password));
    }

    /**
     * Returns a new {@link LocalXAConnection}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return a new {@link LocalXAConnection}; never {@code null}
     *
     * @exception SQLException if an error occurs
     */
    @Override // XADataSource
    public LocalXAConnection getXAConnection() throws SQLException {
        return new LocalXAConnection(this.connectionSupplier.getConnection());
    }

    /**
     * Returns a new {@link LocalXAConnection}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param user the user; may be {@code null}
     *
     * @param password the password; may be {@code null}
     *
     * @return a new {@link LocalXAConnection}; never {@code null}
     *
     * @exception SQLException if an error occurs
     */
    @Override // XADataSource
    public LocalXAConnection getXAConnection(String user, String password) throws SQLException {
        return new LocalXAConnection(this.authenticatedConnectionSupplier.getConnection(user, password));
    }


    /*
     * Inner and nested classes.
     */


    /**
     * A supplier of {@link Connection}s that requires no
     * authentication.
     *
     * @see #getConnection()
     */
    @FunctionalInterface
    public static interface ConnectionSupplier {

        /**
         * Returns a {@link Connection}.
         *
         * <p>Implementations of this method must not return {@code null}.</p>
         *
         * <p>Implementations of this method must be safe for
         * concurrent use by multiple threads.</p>
         *
         * @return a {@link Connection}; never {@code null}
         *
         * @exception SQLException if an error occurs
         */
        public Connection getConnection() throws SQLException;

    }

    /**
     * A supplier of {@link Connection}s that requires authentication.
     *
     * @see #getConnection(String, String)
     */
    @FunctionalInterface
    public static interface AuthenticatedConnectionSupplier {

        /**
         * Returns a {@link Connection}.
         *
         * <p>Implementations of this method must not return {@code null}.</p>
         *
         * <p>Implementations of this method must be safe for
         * concurrent use by multiple threads.</p>
         *
         * @param user the user; may be {@code null}
         *
         * @param password the password; may be {@code null}
         *
         * @return a {@link Connection}; never {@code null}
         *
         * @exception SQLException if an error occurs
         */
        public Connection getConnection(String user, String password) throws SQLException;

    }

}
