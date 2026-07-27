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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import io.helidon.data.DataException;

/**
 * Owns the complete lifecycle of a prepared JDBC operation.
 * <p>
 * Query and update handlers decide result semantics, while this runner keeps
 * connection leasing, preparation, binding, warning capture, cleanup, and
 * exception translation identical for every terminal. A transaction lease may
 * retain the physical connection, but result sets and statements always close
 * before the terminal returns.
 */
final class JdbcRunner {
    /** Datasource used to acquire operation leases. */
    private final DataSource dataSource;
    /** Provider of owned or transaction-bound connection leases. */
    private final JdbcConnectionLease.Provider leaseProvider;
    /** Materialized query semantics. */
    private final JdbcQueryHandler queryHandler;
    /** Update and generated-key semantics. */
    private final JdbcUpdateHandler updateHandler;

    /**
     * Creates a runner with a fixed datasource and lease policy.
     *
     * @param dataSource operation datasource
     * @param leaseProvider connection lease provider
     */
    JdbcRunner(DataSource dataSource, JdbcConnectionLease.Provider leaseProvider) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource must not be null");
        this.leaseProvider = Objects.requireNonNull(leaseProvider, "Connection lease provider must not be null");
        this.queryHandler = new JdbcQueryHandler();
        this.updateHandler = new JdbcUpdateHandler(queryHandler);
    }

    /**
     * Executes an ordinary update.
     *
     * @param operation immutable operation
     * @return large update count
     */
    long execute(JdbcOperation operation) {
        return run(operation, updateHandler::execute);
    }

    /**
     * Executes an exactly-one query or generated-key terminal.
     *
     * @param operation immutable operation
     * @param mapper row mapper
     * @param <T> mapped type
     * @return mapped value
     */
    <T> T one(JdbcOperation operation, JdbcClient.RowMapper<T> mapper) {
        return switch (operation.preparationPlan().resultKind()) {
        case QUERY -> run(operation, scope -> queryHandler.one(scope, mapper));
        case GENERATED_KEYS -> run(operation, scope -> updateHandler.one(scope, mapper));
        case UPDATE -> throw incompatibleTerminal(operation, "one");
        };
    }

    /**
     * Executes a zero-or-one query or generated-key terminal.
     *
     * @param operation immutable operation
     * @param mapper row mapper
     * @param <T> mapped type
     * @return optional mapped value
     */
    <T> Optional<T> optional(JdbcOperation operation, JdbcClient.RowMapper<T> mapper) {
        return switch (operation.preparationPlan().resultKind()) {
        case QUERY -> run(operation, scope -> queryHandler.optional(scope, mapper));
        case GENERATED_KEYS -> run(operation, scope -> updateHandler.optional(scope, mapper));
        case UPDATE -> throw incompatibleTerminal(operation, "optional");
        };
    }

    /**
     * Executes a scalar operation whose optional result also represents SQL
     * {@code NULL}.
     *
     * @param operation immutable operation
     * @param scalarType mapped scalar type
     * @param <T> scalar type
     * @return empty for no row or one SQL {@code NULL}, otherwise the value
     */
    <T> Optional<T> optionalScalar(JdbcOperation operation, Class<T> scalarType) {
        return switch (operation.preparationPlan().resultKind()) {
        case QUERY -> run(operation, scope -> queryHandler.optionalScalar(scope, scalarType));
        case GENERATED_KEYS -> run(operation, scope -> updateHandler.optionalScalar(scope, scalarType));
        default -> throw new IllegalStateException("Optional scalar mapping requires a row-producing operation");
        };
    }

    /**
     * Executes an all-rows query or generated-key terminal.
     *
     * @param operation immutable operation
     * @param mapper row mapper
     * @param <T> mapped type
     * @return materialized values
     */
    <T> List<T> list(JdbcOperation operation, JdbcClient.RowMapper<T> mapper) {
        return switch (operation.preparationPlan().resultKind()) {
        case QUERY -> run(operation, scope -> queryHandler.list(scope, mapper));
        case GENERATED_KEYS -> run(operation, scope -> updateHandler.list(scope, mapper));
        case UPDATE -> throw incompatibleTerminal(operation, "list");
        };
    }

    /**
     * Runs one terminal inside the provider-owned resource boundary.
     *
     * @param operation immutable operation
     * @param action result-specific handler action
     * @param <T> terminal result type
     * @return terminal result
     */
    private <T> T run(JdbcOperation operation, HandlerAction<T> action) {
        JdbcConnectionLease lease = null;
        PreparedStatement statement = null;
        ExecutionScope scope = null;
        T result = null;
        Throwable failure = null;
        try {
            lease = leaseProvider.acquire(dataSource);
            Connection connection = lease.connection();
            connection.clearWarnings();
            statement = prepare(connection, operation);
            bind(statement, operation.binds());
            statement.clearWarnings();
            scope = new ExecutionScope(operation, statement);
            result = action.execute(scope);
        } catch (Throwable caught) {
            failure = caught;
        }

        Connection connection = lease == null ? null : lease.connection();
        ResultSet resultSet = scope == null ? null : scope.resultSet();
        preserveWarnings(failure, connection, statement, resultSet);
        Throwable cleanupFailure = closeAll(resultSet, statement, lease);
        if (cleanupFailure != null) {
            if (failure == null) {
                failure = cleanupFailure;
            } else if (failure instanceof SQLException) {
                failure.addSuppressed(cleanupFailure);
            } else {
                failure.addSuppressed(cleanupException(operation, cleanupFailure));
            }
        }
        if (scope != null) {
            scope.addCapturedWarnings(failure);
        }
        if (failure != null) {
            rethrow(operation, failure);
        }
        return result;
    }

    /**
     * Prepares an ordinary or generated-key statement.
     *
     * @param connection leased connection
     * @param operation operation metadata
     * @return prepared statement
     * @throws SQLException when preparation fails
     */
    private static PreparedStatement prepare(Connection connection,
                                             JdbcOperation operation) throws SQLException {
        JdbcPreparationPlan plan = operation.preparationPlan();
        if (plan.resultKind() != JdbcPreparationPlan.ResultKind.GENERATED_KEYS) {
            return connection.prepareStatement(operation.sql());
        }
        List<String> columns = plan.generatedColumns();
        return columns.isEmpty()
                ? connection.prepareStatement(operation.sql(), java.sql.Statement.RETURN_GENERATED_KEYS)
                : connection.prepareStatement(operation.sql(), columns.toArray(String[]::new));
    }

    /**
     * Applies immutable bindings in ascending JDBC position order.
     *
     * @param statement prepared statement
     * @param binds ordered bindings
     * @throws SQLException when a JDBC setter fails
     */
    private static void bind(PreparedStatement statement, JdbcOperation.Bind[] binds) throws SQLException {
        for (int index = 0; index < binds.length; index++) {
            int position = index + 1;
            JdbcOperation.Bind bind = binds[index];
            if (bind.typed()) {
                statement.setNull(position, bind.type().getVendorTypeNumber());
            } else if (bind.value() instanceof byte[] bytes) {
                statement.setBytes(position, bytes);
            } else {
                statement.setObject(position, bind.value());
            }
        }
    }

    /**
     * Reads a large update count with the legacy integer fallback.
     *
     * @param statement prepared statement
     * @return update count
     * @throws SQLException when neither accessor succeeds
     */
    private static long largeUpdateCount(PreparedStatement statement) throws SQLException {
        try {
            return statement.getLargeUpdateCount();
        } catch (SQLFeatureNotSupportedException e) {
            return statement.getUpdateCount();
        }
    }

    /**
     * Rejects any result channel following the accepted primary result.
     *
     * @param scope execution scope
     * @throws SQLException when result advancement fails
     */
    private static void rejectFollowingResults(ExecutionScope scope) throws SQLException {
        if (scope.operation().preparationPlan().resultKind() == JdbcPreparationPlan.ResultKind.QUERY) {
            scope.captureCurrentResultWarnings();
        }
        boolean nextIsResultSet = scope.statement().getMoreResults(java.sql.Statement.CLOSE_CURRENT_RESULT);
        if (scope.operation().preparationPlan().resultKind() == JdbcPreparationPlan.ResultKind.QUERY) {
            scope.clearCurrentResultSet();
        }
        if (drainFromCurrent(scope.statement(), nextIsResultSet)) {
            throw new DataException("JDBC " + scope.operation().preparationPlan().resultKind()
                                            + " returned unexpected additional results");
        }
    }

    /**
     * Closes and advances through incompatible JDBC result channels.
     *
     * @param statement prepared statement
     * @param currentIsResultSet whether the current channel is a result set
     * @return whether any result channel was present
     * @throws SQLException when result closure or advancement fails
     */
    private static boolean drainFromCurrent(PreparedStatement statement,
                                            boolean currentIsResultSet) throws SQLException {
        boolean found = false;
        boolean resultSet = currentIsResultSet;
        while (true) {
            if (resultSet) {
                found = true;
                ResultSet current = statement.getResultSet();
                if (current != null) {
                    current.close();
                }
            } else {
                long count = largeUpdateCount(statement);
                if (count == -1) {
                    return found;
                }
                found = true;
            }
            resultSet = statement.getMoreResults(java.sql.Statement.CLOSE_CURRENT_RESULT);
        }
    }

    /**
     * Builds an error for a missing or incompatible primary result.
     *
     * @param operation operation metadata
     * @param resultPresent whether an incompatible channel was present
     * @return data exception
     */
    private static DataException unexpectedResult(JdbcOperation operation, boolean resultPresent) {
        String detail = resultPresent ? "an incompatible result" : "no expected result";
        return new DataException("JDBC " + operation.preparationPlan().resultKind() + " returned " + detail);
    }

    /**
     * Reads and clears warnings while their JDBC owners are still open.
     *
     * @param primary primary failure, or {@code null} on success
     * @param connection current connection
     * @param statement current statement
     * @param resultSet current result set
     */
    private static void preserveWarnings(Throwable primary,
                                         Connection connection,
                                         PreparedStatement statement,
                                         ResultSet resultSet) {
        try {
            if (resultSet != null) {
                addWarnings(primary, resultSet.getWarnings());
                resultSet.clearWarnings();
            }
            if (statement != null) {
                addWarnings(primary, statement.getWarnings());
                statement.clearWarnings();
            }
            if (connection != null) {
                addWarnings(primary, connection.getWarnings());
                connection.clearWarnings();
            }
        } catch (Throwable warningFailure) {
            if (primary != null) {
                primary.addSuppressed(warningFailure);
            }
        }
    }

    /**
     * Adds a JDBC warning chain to an existing failure.
     *
     * @param primary receiving failure
     * @param warning first warning
     */
    private static void addWarnings(Throwable primary, SQLWarning warning) {
        if (primary == null) {
            return;
        }
        for (SQLWarning current = warning; current != null; current = current.getNextWarning()) {
            primary.addSuppressed(current);
        }
    }

    /**
     * Closes resources in the supplied ownership order.
     *
     * @param resultSet result set to close first
     * @param statement statement to close second
     * @param lease connection lease to close last
     * @return first cleanup failure, or {@code null}
     */
    private static Throwable closeAll(ResultSet resultSet,
                                      PreparedStatement statement,
                                      JdbcConnectionLease lease) {
        Throwable failure = close(resultSet, null);
        failure = close(statement, failure);
        return close(lease, failure);
    }

    /**
     * Closes one resource and preserves failures in ownership order.
     *
     * @param resource resource to close
     * @param previousFailure earlier cleanup failure
     * @return first cleanup failure, or {@code null}
     */
    private static Throwable close(AutoCloseable resource, Throwable previousFailure) {
        if (resource == null) {
            return previousFailure;
        }
        try {
            resource.close();
        } catch (Throwable closeFailure) {
            if (previousFailure == null) {
                return closeFailure;
            }
            previousFailure.addSuppressed(closeFailure);
        }
        return previousFailure;
    }

    /**
     * Translates a cleanup failure before attaching it to an application
     * failure.
     *
     * @param operation operation metadata
     * @param failure cleanup failure
     * @return unchecked cleanup failure
     */
    private static Throwable cleanupException(JdbcOperation operation, Throwable failure) {
        if (failure instanceof SQLException sqlException) {
            return JdbcExceptionTranslator.translate(operation, sqlException);
        }
        if (failure instanceof RuntimeException || failure instanceof Error) {
            return failure;
        }
        return new DataException("JDBC resource cleanup failed", failure);
    }

    /**
     * Rethrows a captured failure without losing its category.
     *
     * @param operation operation metadata
     * @param failure captured failure
     */
    private static void rethrow(JdbcOperation operation, Throwable failure) {
        if (failure instanceof SQLException sqlException) {
            throw JdbcExceptionTranslator.translate(operation, sqlException);
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new DataException("JDBC operation failed", failure);
    }

    /**
     * Creates a diagnostic when an internal terminal and plan disagree.
     *
     * @param operation operation metadata
     * @param terminal terminal name
     * @return state exception
     */
    private static IllegalStateException incompatibleTerminal(JdbcOperation operation, String terminal) {
        return new IllegalStateException("JDBC " + operation.preparationPlan().resultKind()
                                                 + " operation cannot use the " + terminal + " terminal");
    }

    /**
     * Operation-specific work performed inside the resource boundary.
     *
     * @param <T> terminal result type
     */
    @FunctionalInterface
    private interface HandlerAction<T> {

        /**
         * Executes against a prepared and bound statement.
         *
         * @param scope runner-owned execution scope
         * @return terminal result
         * @throws SQLException when JDBC work fails
         */
        T execute(ExecutionScope scope) throws SQLException;
    }

    /**
     * Runner-owned view of one prepared operation.
     */
    static final class ExecutionScope {
        /** Immutable operation metadata. */
        private final JdbcOperation operation;
        /** Prepared and bound statement. */
        private final PreparedStatement statement;
        /** Warnings captured before an exhausted query result is closed. */
        private final List<Throwable> capturedWarnings = new ArrayList<>();
        /** Current provider-owned result set. */
        private ResultSet resultSet;

        /**
         * Creates an execution scope.
         *
         * @param operation immutable operation
         * @param statement prepared statement
         */
        private ExecutionScope(JdbcOperation operation, PreparedStatement statement) {
            this.operation = operation;
            this.statement = statement;
        }

        /**
         * Returns operation metadata.
         *
         * @return operation
         */
        JdbcOperation operation() {
            return operation;
        }

        /**
         * Returns the prepared statement.
         *
         * @return statement
         */
        PreparedStatement statement() {
            return statement;
        }

        /**
         * Verifies the result kind expected by a handler.
         *
         * @param expected expected kind
         */
        void require(JdbcPreparationPlan.ResultKind expected) {
            JdbcPreparationPlan.ResultKind actual = operation.preparationPlan().resultKind();
            if (actual != expected) {
                throw new IllegalStateException("JDBC handler expected " + expected + " but received " + actual);
            }
        }

        /**
         * Registers the operation's result set for cleanup.
         *
         * @param resultSet current result set
         */
        void resultSet(ResultSet resultSet) {
            if (this.resultSet != null && this.resultSet != resultSet) {
                throw new IllegalStateException("A JDBC operation cannot own two live result sets");
            }
            this.resultSet = resultSet;
        }

        /**
         * Returns the current result set.
         *
         * @return current result set, or {@code null}
         */
        ResultSet resultSet() {
            return resultSet;
        }

        /**
         * Reads the current large update count.
         *
         * @return update count
         * @throws SQLException when JDBC access fails
         */
        long largeUpdateCount() throws SQLException {
            return JdbcRunner.largeUpdateCount(statement);
        }

        /**
         * Drains an incompatible primary channel.
         *
         * @param currentIsResultSet whether the current channel is a result set
         * @return whether a channel was present
         * @throws SQLException when result advancement fails
         */
        boolean drainFromCurrent(boolean currentIsResultSet) throws SQLException {
            return JdbcRunner.drainFromCurrent(statement, currentIsResultSet);
        }

        /**
         * Rejects a result channel after the accepted primary result.
         *
         * @throws SQLException when result advancement fails
         */
        void rejectFollowingResults() throws SQLException {
            JdbcRunner.rejectFollowingResults(this);
        }

        /**
         * Creates an incompatible-result diagnostic.
         *
         * @param resultPresent whether a channel was present
         * @return data exception
         */
        DataException unexpectedResult(boolean resultPresent) {
            return JdbcRunner.unexpectedResult(operation, resultPresent);
        }

        /**
         * Captures warnings before JDBC closes an exhausted query result set.
         */
        private void captureCurrentResultWarnings() {
            if (resultSet == null) {
                return;
            }
            try {
                for (SQLWarning warning = resultSet.getWarnings(); warning != null; warning = warning.getNextWarning()) {
                    capturedWarnings.add(warning);
                }
                resultSet.clearWarnings();
            } catch (Throwable warningFailure) {
                capturedWarnings.add(warningFailure);
            }
        }

        /**
         * Clears the result-set reference after JDBC closes the current query
         * result during advancement.
         */
        private void clearCurrentResultSet() {
            resultSet = null;
        }

        /**
         * Adds previously captured warnings to a terminal failure.
         *
         * @param failure terminal failure, or {@code null}
         */
        private void addCapturedWarnings(Throwable failure) {
            if (failure == null) {
                return;
            }
            for (Throwable warning : capturedWarnings) {
                if (warning != failure) {
                    failure.addSuppressed(warning);
                }
            }
        }
    }
}
