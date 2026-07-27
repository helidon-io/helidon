/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * Logical ownership of a connection for one JDBC terminal operation.
 * <p>
 * Outside a transaction, closing the lease closes the connection (or returns
 * it to its pool). During a local transaction, operation cleanup closes its
 * result set and statement while transaction completion retains ownership of
 * the physical connection. Keeping that distinction here lets the runner use
 * the same cleanup order in both cases.
 */
interface JdbcConnectionLease extends AutoCloseable {

    /**
     * Returns the connection available to the current operation.
     *
     * @return leased connection
     */
    Connection connection();

    /** {@inheritDoc} */
    @Override
    void close() throws SQLException;

    /** Supplies an owned or transaction-bound lease for one operation. */
    @FunctionalInterface
    interface Provider {
        /**
         * Acquires a logical connection lease.
         *
         * @param dataSource operation datasource
         * @return acquired lease
         * @throws SQLException when a connection cannot be acquired
         */
        JdbcConnectionLease acquire(DataSource dataSource) throws SQLException;
    }

    /**
     * Returns the standard lease policy used outside local transactions.
     *
     * @return provider of physically owned connections
     */
    static Provider ownedProvider() {
        return dataSource -> new Owned(dataSource.getConnection());
    }

    /** Lease that closes its physical connection when the operation ends. */
    final class Owned implements JdbcConnectionLease {
        /** Connection owned by this lease. */
        private final Connection connection;
        /** Whether ownership has already been released. */
        private boolean closed;

        /**
         * Creates a lease with physical connection ownership.
         *
         * @param connection owned connection
         */
        Owned(Connection connection) {
            this.connection = connection;
        }

        /** {@inheritDoc} */
        @Override
        public Connection connection() {
            if (closed) {
                throw new IllegalStateException("Connection lease is closed");
            }
            return connection;
        }

        /** {@inheritDoc} */
        @Override
        public void close() throws SQLException {
            if (!closed) {
                closed = true;
                connection.close();
            }
        }
    }
}
