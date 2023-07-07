/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jdbc;

import java.lang.System.Logger.Level;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Transaction context.
 */
class TransactionContext {

    private static final System.Logger LOGGER = System.getLogger(TransactionContext.class.getName());

    // Connection holding the database transaction.
    // Life-cycle: created with 1st statement execution
    //             closed on commit or rollback
    private Connection connection;

    private State state;

    private final Supplier<Connection> connectionFactory;

    /**
     * Create a new instance.
     *
     * @param connectionFactory connection factory
     */
    TransactionContext(Supplier<Connection> connectionFactory) {
        this.connection = null;
        this.state = State.INIT;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Get the connection.
     *
     * @return connection
     */
    Connection connection() {
        switch (state) {
            case INIT -> {
                connection = connectionFactory.get();
                state = State.ACTIVE;
                return connection;
            }
            case ACTIVE -> {
                return connection;
            }
            case COMMIT -> throw new IllegalStateException("Transaction was already committed");
            case ROLLBACK -> throw new IllegalStateException("Transaction was already rolled back");
            default -> throw new IllegalStateException("Unknown transaction state");
        }
    }

    /**
     * Commit and close the connection.
     *
     * @throws SQLException if an error occurred while calling {@link Connection#commit()} or {@link Connection#close()}
     */
    void commit() throws SQLException {
        switch (state) {
            // Commit with no statement being executed.
            case INIT -> {
                state = State.COMMIT;
                LOGGER.log(Level.WARNING, "Transaction commit with no statement being executed.");
            }
            // Commit active transaction.
            case ACTIVE -> {
                state = State.COMMIT;
                try {
                    connection.commit();
                } finally {
                    connection.close();
                }
            }
            case COMMIT -> throw new IllegalStateException("Transaction was already committed");
            case ROLLBACK -> throw new IllegalStateException("Transaction was already rolled back");
            default -> throw new IllegalStateException("Unknown transaction state");
        }
    }

    /**
     * Rollback and close the connection.
     *
     * @throws SQLException if an error occurred while calling {@link Connection#rollback()} or {@link Connection#close()}
     */
    void rollback() throws SQLException {
        switch (state) {
            // Rollback with no statement being executed.
            case INIT -> {
                state = State.ROLLBACK;
                LOGGER.log(Level.WARNING, "Transaction rollback with no statement being executed.");
            }
            // Rollback active transaction.
            case ACTIVE -> {
                state = State.ROLLBACK;
                try {
                    connection.rollback();
                } finally {
                    connection.close();
                }
            }
            case COMMIT -> throw new IllegalStateException("Transaction was already committed");
            case ROLLBACK -> throw new IllegalStateException("Transaction was already rolled back");
            default -> throw new IllegalStateException("Unknown transaction state");
        }
    }

    /**
     * Internal transaction states.
     */
    private enum State {
        /**
         * Transaction was not started yet (waiting for 1st statement).
         */
        INIT,
        /**
         * Transaction is being executed.
         */
        ACTIVE,
        /**
         * Transaction was committed.
         */
        COMMIT,
        /**
         * Transaction was rolled back.
         */
        ROLLBACK
    }

}
