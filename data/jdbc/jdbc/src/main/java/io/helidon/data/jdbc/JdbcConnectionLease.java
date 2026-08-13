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
     * Returns the standard lease policy used outside local transactions.
     *
     * @return provider of physically owned connections
     */
    static Provider ownedProvider() {
        return Owned::acquire;
    }

    /**
     * Returns the connection available to the current operation.
     *
     * @return leased connection
     */
    Connection connection();

    @Override
    void close() throws SQLException;

    /**
     * Supplies an owned or transaction-bound lease for one operation.
     */
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
     * Lease that closes its physical connection when the operation ends.
     */
    final class Owned implements JdbcConnectionLease {

        private static final String AUTO_COMMIT_REQUIRED =
                "Datasources used for JDBC operations must provide connections with auto-commit enabled.";

        private final Connection connection;
        private boolean closed;

        /**
         * Creates a lease with physical connection ownership.
         *
         * @param connection owned connection
         */
        private Owned(Connection connection) {
            this.connection = connection;
        }

        /**
         * Acquires and validates a physically owned connection. Operations that
         * own their connection require auto-commit so an ordinary lease close cannot leave
         * transaction completion to driver-specific {@link Connection#close()}
         * behavior. A rejected connection is closed before the acquisition
         * failure is rethrown.
         *
         * @param dataSource source of the owned connection
         * @return validated connection lease
         * @throws SQLException when acquisition or validation fails
         */
        static Owned acquire(DataSource dataSource) throws SQLException {
            Connection connection = dataSource.getConnection();
            try {
                if (!connection.getAutoCommit()) {
                    throw JdbcExceptionTranslator.safeException(AUTO_COMMIT_REQUIRED);
                }
                return new Owned(connection);
            } catch (SQLException | RuntimeException | Error failure) {
                Throwable reportedFailure = failure;
                try {
                    connection.close();
                } catch (SQLException | RuntimeException | Error closeFailure) {
                    // Preserve the acquisition failure and prevent driver cleanup details from escaping.
                    reportedFailure = JdbcExceptionTranslator.suppress(failure,
                                                                       "closing a rejected connection",
                                                                       closeFailure);
                }
                throw rethrow(reportedFailure);
            }
        }

        /**
         * Restores the declared or unchecked category of an acquisition failure.
         *
         * @param failure failure to throw
         * @return never returns
         * @throws SQLException when the failure is an SQL exception
         */
        private static SQLException rethrow(Throwable failure) throws SQLException {
            if (failure instanceof SQLException sqlException) {
                throw sqlException;
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw (Error) failure;
        }

        @Override
        public Connection connection() {
            if (closed) {
                throw new IllegalStateException("The connection lease is closed.");
            }
            return connection;
        }

        @Override
        public void close() throws SQLException {
            if (!closed) {
                closed = true;
                connection.close();
            }
        }
    }
}
