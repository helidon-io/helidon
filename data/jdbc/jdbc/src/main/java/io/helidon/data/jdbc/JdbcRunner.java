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
import java.sql.Statement;
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
 * connection leasing, preparation, binding, cleanup, and exception translation
 * identical for every terminal. A transaction lease may retain the physical
 * connection, but result sets and statements always close before the terminal
 * returns.
 * <p>
 * Warning handling is retained behind a compile-time policy for future use.
 * The disabled policy does not retrieve, clear, traverse, retain, or display
 * JDBC warnings. JDBC cleanup failures remain sanitized before attachment to
 * the application-visible failure tree.
 */
final class JdbcRunner {

    private static final WarningPolicy WARNING_POLICY = WarningPolicy.DISCARD;

    // A singular terminal needs the second row as evidence of non-unique cardinality.
    private static final ExecutionOptions SINGULAR_QUERY = new ExecutionOptions(2);

    // Updates, list terminals, and generated-key operations must not impose a provider-selected JDBC row limit.
    private static final ExecutionOptions UNBOUNDED = new ExecutionOptions(0);

    private final DataSource dataSource;
    private final JdbcConnectionLease.Provider leaseProvider;
    private final JdbcQueryHandler queryHandler;
    private final JdbcUpdateHandler updateHandler;

    /**
     * Creates a runner with a fixed datasource and lease policy.
     *
     * @param dataSource operation datasource
     * @param leaseProvider connection lease provider
     */
    JdbcRunner(DataSource dataSource, JdbcConnectionLease.Provider leaseProvider) {
        this.dataSource = Objects.requireNonNull(dataSource, "The datasource must not be null.");
        this.leaseProvider = Objects.requireNonNull(leaseProvider,
                                                   "The connection lease provider must not be null.");
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
        return run(operation, UNBOUNDED, updateHandler::execute);
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
        case QUERY -> run(operation, SINGULAR_QUERY, scope -> queryHandler.one(scope, mapper));
        case GENERATED_KEYS -> run(operation, UNBOUNDED, scope -> updateHandler.one(scope, mapper));
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
        case QUERY -> run(operation, SINGULAR_QUERY, scope -> queryHandler.optional(scope, mapper));
        case GENERATED_KEYS -> run(operation, UNBOUNDED, scope -> updateHandler.optional(scope, mapper));
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
        case QUERY -> run(operation, SINGULAR_QUERY, scope -> queryHandler.optionalScalar(scope, scalarType));
        case GENERATED_KEYS -> run(operation, UNBOUNDED, scope -> updateHandler.optionalScalar(scope, scalarType));
        default -> throw new IllegalStateException("Optional scalar mapping requires an operation that produces rows.");
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
        case QUERY -> run(operation, UNBOUNDED, scope -> queryHandler.list(scope, mapper));
        case GENERATED_KEYS -> run(operation, UNBOUNDED, scope -> updateHandler.list(scope, mapper));
        case UPDATE -> throw incompatibleTerminal(operation, "list");
        };
    }

    /**
     * Runs one terminal inside the provider-owned resource boundary.
     *
     * @param operation immutable operation
     * @param options execution options selected by the terminal
     * @param action result-specific handler action
     * @param <T> terminal result type
     * @return terminal result
     */
    private <T> T run(JdbcOperation operation,
                      ExecutionOptions options,
                      HandlerAction<T> action) {
        // Explicit cleanup preserves every failure in resource ownership order.
        JdbcConnectionLease lease = null;
        PreparedStatement statement = null;
        ExecutionScope scope = null;
        T result = null;
        Throwable failure = null;
        List<Throwable> warningDiagnostics = WARNING_POLICY == WarningPolicy.CAPTURE ? new ArrayList<>() : List.of();
        try {
            lease = JdbcExceptionTranslator.invoke("acquiring a connection lease",
                                                   () -> leaseProvider.acquire(dataSource));
            Connection connection = lease.connection();
            if (WARNING_POLICY == WarningPolicy.CAPTURE) {
                resetWarnings("connection warning", connection::clearWarnings, warningDiagnostics);
            }
            statement = prepare(connection, operation);
            applyOptions(statement, options);
            bind(statement, operation.binds());
            if (WARNING_POLICY == WarningPolicy.CAPTURE) {
                resetWarnings("statement warning", statement::clearWarnings, warningDiagnostics);
            }
            scope = new ExecutionScope(operation, statement, warningDiagnostics);
            result = action.execute(scope);
        } catch (Throwable caught) {
            failure = caught;
        }

        Connection connection = lease == null ? null : lease.connection();
        ResultSet resultSet = scope == null ? null : scope.resultSet();
        if (WARNING_POLICY == WarningPolicy.CAPTURE) {
            // Some drivers discard warnings when their owning resource closes.
            failure = preserveWarnings(failure,
                                       connection,
                                       statement,
                                       resultSet,
                                       warningDiagnostics);
        }
        failure = closeAll(operation, resultSet, statement, lease, failure);
        if (failure != null) {
            if (WARNING_POLICY == WarningPolicy.CAPTURE) {
                failure = addWarningDiagnostics(failure, warningDiagnostics);
            }
            rethrow(operation, failure);
        }
        return result;
    }

    /**
     * Applies portable statement-level execution controls. A driver which
     * explicitly reports row limits as unsupported retains the cursor-based
     * cardinality check. All other failures remain operation failures.
     *
     * @param statement prepared statement
     * @param options terminal execution options
     * @throws SQLException when a supported JDBC option cannot be applied
     */
    private static void applyOptions(PreparedStatement statement,
                                     ExecutionOptions options) throws SQLException {
        if (options.maxRows() == 0) {
            return;
        }
        try {
            statement.setMaxRows(options.maxRows());
        } catch (SQLFeatureNotSupportedException unsupported) {
            // The result cursor still reads at most two rows before reporting cardinality.
        } catch (RuntimeException runtimeException) {
            throw (RuntimeException) JdbcExceptionTranslator.sanitize("setting the maximum row count for a query",
                                                                       runtimeException);
        }
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
            return JdbcExceptionTranslator.invoke("preparing a JDBC statement",
                                                  () -> connection.prepareStatement(operation.sql()));
        }
        List<String> columns = plan.generatedColumns();
        return columns.isEmpty()
                ? JdbcExceptionTranslator.invoke("preparing a generated-keys JDBC statement",
                                                 () -> connection.prepareStatement(operation.sql(),
                                                                                   Statement.RETURN_GENERATED_KEYS))
                : JdbcExceptionTranslator.invoke("preparing a generated-keys JDBC statement",
                                                 () -> connection.prepareStatement(operation.sql(),
                                                                                   columns.toArray(String[]::new)));
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
                JdbcExceptionTranslator.invokeVoid("binding a typed null JDBC parameter",
                                                   () -> statement.setNull(position,
                                                                           bind.type().getVendorTypeNumber()));
            } else if (bind.value() instanceof byte[] bytes) {
                JdbcExceptionTranslator.invokeVoid("binding a binary JDBC parameter",
                                                   () -> statement.setBytes(position, bytes));
            } else {
                JdbcExceptionTranslator.invokeVoid("binding a JDBC parameter",
                                                   () -> statement.setObject(position, bind.value()));
            }
        }
    }

    /**
     * Rejects any result channel following the accepted primary result.
     *
     * @param scope execution scope
     * @throws SQLException when result advancement fails
     */
    private static void rejectFollowingResults(ExecutionScope scope) throws SQLException {
        if (WARNING_POLICY == WarningPolicy.CAPTURE
                && scope.operation().preparationPlan().resultKind() == JdbcPreparationPlan.ResultKind.QUERY) {
            // Baseline result advancement closes the current result set. Capture its warnings first because some
            // drivers discard them during close.
            scope.captureCurrentResultWarnings();
        }
        // The provider never retains multiple open results, so the baseline method has the required close behavior
        // without relying on the optional result-retention controls of getMoreResults(int).
        boolean nextIsResultSet = JdbcExceptionTranslator.invoke("advancing to the next JDBC result",
                                                                scope.statement()::getMoreResults);
        if (scope.operation().preparationPlan().resultKind() == JdbcPreparationPlan.ResultKind.QUERY) {
            // The driver closed the exhausted result set during advancement.
            scope.clearCurrentResultSet();
        }
        if (drainFromCurrent(scope, nextIsResultSet)) {
            throw new DataException("The " + JdbcExceptionTranslator.operationDescription(scope.operation())
                                            + " returned unexpected additional results.");
        }
    }

    /**
     * Closes and advances through incompatible JDBC result channels.
     *
     * @param scope current execution scope
     * @param currentIsResultSet whether the current channel is a result set
     * @return whether any result channel was present
     * @throws SQLException when result closure or advancement fails
     */
    private static boolean drainFromCurrent(ExecutionScope scope,
                                            boolean currentIsResultSet) throws SQLException {
        PreparedStatement statement = scope.statement();
        boolean found = false;
        boolean resultSet = currentIsResultSet;
        while (true) {
            if (resultSet) {
                found = true;
                ResultSet current = JdbcExceptionTranslator.invoke("reading the current JDBC result set",
                                                                  statement::getResultSet);
                if (current != null) {
                    JdbcExceptionTranslator.invokeVoid("closing an unexpected JDBC result set", current::close);
                }
            } else {
                long count = scope.largeUpdateCount();
                if (count == -1) {
                    return found;
                }
                found = true;
            }
            // Baseline advancement closes the current result before exposing the next result channel.
            resultSet = JdbcExceptionTranslator.invoke("advancing to the next JDBC result",
                                                       statement::getMoreResults);
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
        return new DataException("The " + JdbcExceptionTranslator.operationDescription(operation)
                                         + " returned " + detail + ".");
    }

    /**
     * Reads and clears warnings while their JDBC owners are still open.
     * Warning-access failures are diagnostics only when another failure already
     * exists. Warning handling never turns successful work into a failure.
     *
     * @param primary primary failure, or {@code null} on success
     * @param connection current connection
     * @param statement current statement
     * @param resultSet current result set
     * @param diagnostics sanitized warning diagnostics
     * @return primary failure after warning processing
     */
    private static Throwable preserveWarnings(Throwable primary,
                                              Connection connection,
                                              PreparedStatement statement,
                                              ResultSet resultSet,
                                              List<Throwable> diagnostics) {
        if (resultSet != null) {
            primary = preserveWarnings(primary,
                                       "result set warning",
                                       resultSet::getWarnings,
                                       resultSet::clearWarnings,
                                       diagnostics);
        }
        if (statement != null) {
            primary = preserveWarnings(primary,
                                       "statement warning",
                                       statement::getWarnings,
                                       statement::clearWarnings,
                                       diagnostics);
        }
        if (connection != null) {
            primary = preserveWarnings(primary,
                                       "connection warning",
                                       connection::getWarnings,
                                       connection::clearWarnings,
                                       diagnostics);
        }
        return primary;
    }

    /**
     * Reads, sanitizes, and clears warnings for one JDBC owner. Reading and
     * clearing are isolated so a broken getter does not prevent a best-effort
     * clear or warning processing for later owners.
     *
     * @param primary primary failure, or {@code null} on success
     * @param owner stable warning-owner label
     * @param source warning accessor
     * @param clearAction warning clear action
     * @param diagnostics sanitized warning diagnostics
     * @return primary failure after warning processing
     */
    private static Throwable preserveWarnings(Throwable primary,
                                              String owner,
                                              WarningSource source,
                                              WarningClearAction clearAction,
                                              List<Throwable> diagnostics) {
        SQLWarning first = null;
        try {
            first = source.getWarnings();
        } catch (SQLException | RuntimeException warningFailure) {
            diagnostics.add(JdbcExceptionTranslator.warningFailure(owner, warningFailure));
        } catch (Error warningFailure) {
            primary = warningError(primary, owner, warningFailure, diagnostics);
        }
        try {
            diagnostics.addAll(JdbcExceptionTranslator.sanitizeWarnings(owner, first));
        } catch (Error warningFailure) {
            primary = warningError(primary, owner, warningFailure, diagnostics);
        }
        try {
            clearAction.clearWarnings();
        } catch (SQLException | RuntimeException warningFailure) {
            diagnostics.add(JdbcExceptionTranslator.warningFailure(owner, warningFailure));
        } catch (Error warningFailure) {
            primary = warningError(primary, owner, warningFailure, diagnostics);
        }
        return primary;
    }

    /**
     * Preserves a fatal warning-processing error without replacing an earlier
     * operation failure.
     *
     * @param primary primary failure, or {@code null} on success
     * @param owner stable warning-owner label
     * @param warningFailure fatal warning-processing failure
     * @param diagnostics sanitized warning diagnostics
     * @return primary failure after recording the error
     */
    private static Throwable warningError(Throwable primary,
                                          String owner,
                                          Error warningFailure,
                                          List<Throwable> diagnostics) {
        if (primary == null) {
            return warningFailure;
        }
        diagnostics.add(JdbcExceptionTranslator.warningFailure(owner, warningFailure));
        return primary;
    }

    /**
     * Clears warnings without making an ordinary warning-processing failure
     * fatal to the JDBC operation.
     *
     * @param owner stable warning-owner label
     * @param clearAction warning clear action
     * @param diagnostics sanitized warning diagnostics
     */
    private static void resetWarnings(String owner,
                                      WarningClearAction clearAction,
                                      List<Throwable> diagnostics) {
        try {
            clearAction.clearWarnings();
        } catch (SQLException | RuntimeException warningFailure) {
            diagnostics.add(JdbcExceptionTranslator.warningFailure(owner, warningFailure));
        }
    }

    /**
     * Attaches sanitized warning diagnostics after the terminal outcome is
     * known. Successful operations intentionally discard these diagnostics.
     *
     * @param primary receiving failure
     * @param diagnostics sanitized warning diagnostics
     */
    private static Throwable addWarningDiagnostics(Throwable primary, List<Throwable> diagnostics) {
        for (Throwable diagnostic : diagnostics) {
            if (primary != diagnostic) {
                primary = JdbcExceptionTranslator.suppress(primary, "processing a JDBC warning", diagnostic);
            }
        }
        return primary;
    }

    /**
     * Closes resources in the supplied ownership order.
     *
     * @param operation operation metadata used to translate a grouped cleanup failure
     * @param resultSet result set to close first
     * @param statement statement to close second
     * @param lease connection lease to close last
     * @param previousFailure earlier operation or cleanup failure
     * @return first failure, or {@code null}
     */
    private static Throwable closeAll(JdbcOperation operation,
                                      ResultSet resultSet,
                                      PreparedStatement statement,
                                      JdbcConnectionLease lease,
                                      Throwable previousFailure) {
        if (previousFailure instanceof SQLException) {
            Throwable failure = close(resultSet, "closing a result set", previousFailure);
            failure = close(statement, "closing a statement", failure);
            return close(lease, "closing a connection lease", failure);
        }
        Throwable failure = close(resultSet, "closing a result set", null);
        failure = close(statement, "closing a statement", failure);
        failure = close(lease, "closing a connection lease", failure);
        if (previousFailure == null) {
            return failure;
        }
        if (failure != null) {
            previousFailure.addSuppressed(cleanupException(operation, failure));
        }
        return previousFailure;
    }

    /**
     * Closes one resource and preserves failures in ownership order.
     *
     * @param resource resource to close
     * @param operation stable cleanup operation label
     * @param previousFailure earlier cleanup failure
     * @return first cleanup failure, or {@code null}
     */
    private static Throwable close(AutoCloseable resource, String operation, Throwable previousFailure) {
        if (resource == null) {
            return previousFailure;
        }
        try {
            resource.close();
        } catch (Throwable closeFailure) {
            // Preserve a primary fatal error. An error attached below another failure is sanitized.
            if (previousFailure == null && closeFailure instanceof Error) {
                return closeFailure;
            }
            if (previousFailure == null) {
                return JdbcExceptionTranslator.prepare(operation, closeFailure);
            }
            previousFailure = JdbcExceptionTranslator.suppress(previousFailure, operation, closeFailure);
        }
        return previousFailure;
    }

    /**
     * Translates a grouped cleanup failure before attaching it to an
     * application failure.
     *
     * @param operation operation metadata
     * @param failure cleanup failure
     * @return unchecked cleanup failure
     */
    private static Throwable cleanupException(JdbcOperation operation, Throwable failure) {
        if (failure instanceof SQLException sqlException) {
            return JdbcExceptionTranslator.translate(operation, sqlException);
        }
        return JdbcExceptionTranslator.sanitize("resource cleanup", failure);
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
        throw new DataException("The JDBC operation failed.", failure);
    }

    /**
     * Creates a diagnostic when an internal terminal and plan disagree.
     *
     * @param operation operation metadata
     * @param terminal terminal name
     * @return state exception
     */
    private static IllegalStateException incompatibleTerminal(JdbcOperation operation, String terminal) {
        return new IllegalStateException("The " + JdbcExceptionTranslator.operationDescription(operation)
                                                 + " cannot use the '" + terminal + "' terminal operation.");
    }

    /**
     * Runner-owned view of one prepared operation.
     */
    static final class ExecutionScope {

        private final JdbcOperation operation;
        private final PreparedStatement statement;
        private final List<Throwable> warningDiagnostics;

        // Registered here so the runner closes it on every exit path.
        private ResultSet resultSet;

        // Capability is statement-instance scoped because wrappers from one pool may behave differently by instance.
        private boolean largeUpdateCountsUnsupported;

        /**
         * Creates an execution scope.
         *
         * @param operation immutable operation
         * @param statement prepared statement
         * @param warningDiagnostics operation warning diagnostics
         */
        private ExecutionScope(JdbcOperation operation,
                               PreparedStatement statement,
                               List<Throwable> warningDiagnostics) {
            this.operation = operation;
            this.statement = statement;
            this.warningDiagnostics = warningDiagnostics;
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
                throw new IllegalStateException("The JDBC handler expected result kind '" + expected
                                                        + "' but received '" + actual + "'.");
            }
        }

        /**
         * Registers the operation's result set for cleanup.
         *
         * @param resultSet current result set
         */
        void resultSet(ResultSet resultSet) {
            if (this.resultSet != null && this.resultSet != resultSet) {
                throw new IllegalStateException("A JDBC operation cannot own two live result sets.");
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
         * Reads the current large update count, falling back to the legacy
         * integer accessor when the driver reports that large counts are not
         * supported. Java SE's default implementation reports this with
         * {@link UnsupportedOperationException}. Drivers may instead use
         * {@link SQLFeatureNotSupportedException}.
         * <p>
         * Once either signal is observed, this statement scope uses the legacy
         * accessor directly so result draining does not repeatedly use an
         * exception as capability detection.
         *
         * @return update count
         * @throws SQLException when JDBC access fails
         */
        long largeUpdateCount() throws SQLException {
            if (largeUpdateCountsUnsupported) {
                return JdbcExceptionTranslator.invoke("reading a JDBC update count", statement::getUpdateCount);
            }
            try {
                return statement.getLargeUpdateCount();
            } catch (SQLFeatureNotSupportedException | UnsupportedOperationException unsupported) {
                largeUpdateCountsUnsupported = true;
                return JdbcExceptionTranslator.invoke("reading a JDBC update count", statement::getUpdateCount);
            } catch (RuntimeException runtimeException) {
                throw (RuntimeException) JdbcExceptionTranslator.sanitize("reading a JDBC large update count",
                                                                           runtimeException);
            }
        }

        /**
         * Drains an incompatible primary channel.
         *
         * @param currentIsResultSet whether the current channel is a result set
         * @return whether a channel was present
         * @throws SQLException when result advancement fails
         */
        boolean drainFromCurrent(boolean currentIsResultSet) throws SQLException {
            return JdbcRunner.drainFromCurrent(this, currentIsResultSet);
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
         * Every warning and ordinary warning-processing failure is sanitized
         * before it enters the operation diagnostic list. Fatal errors remain
         * fatal and return through the runner's resource-cleanup path.
         */
        private void captureCurrentResultWarnings() {
            if (resultSet == null) {
                return;
            }
            SQLWarning first = null;
            try {
                first = resultSet.getWarnings();
            } catch (SQLException | RuntimeException warningFailure) {
                warningDiagnostics.add(JdbcExceptionTranslator.warningFailure("result set warning", warningFailure));
            }
            warningDiagnostics.addAll(JdbcExceptionTranslator.sanitizeWarnings("result set warning", first));
            try {
                resultSet.clearWarnings();
            } catch (SQLException | RuntimeException warningFailure) {
                warningDiagnostics.add(JdbcExceptionTranslator.warningFailure("result set warning", warningFailure));
            }
        }

        /**
         * Clears the result-set reference after JDBC closes the current query
         * result during advancement.
         */
        private void clearCurrentResultSet() {
            resultSet = null;
        }

    }

    /**
     * Statement controls selected by a terminal before JDBC resources are
     * acquired. A zero maximum leaves the driver's row limit unchanged.
     *
     * @param maxRows maximum rows exposed by the statement, or zero for no limit
     */
    private record ExecutionOptions(int maxRows) {

        ExecutionOptions {
            if (maxRows < 0) {
                throw new IllegalArgumentException("The maximum row count must not be negative.");
            }
        }
    }

    /**
     * Fixed private warning policy. Warning capture remains unavailable to
     * applications until its public behavior is approved.
     */
    private enum WarningPolicy {
        DISCARD,
        CAPTURE
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
     * Reads the first warning from one JDBC owner.
     */
    @FunctionalInterface
    private interface WarningSource {

        /**
         * Returns the first current warning.
         *
         * @return first warning, or {@code null}
         * @throws SQLException when warning access fails
         */
        SQLWarning getWarnings() throws SQLException;
    }

    /**
     * Clears warnings from one JDBC owner.
     */
    @FunctionalInterface
    private interface WarningClearAction {

        /**
         * Clears current warnings.
         *
         * @throws SQLException when warning clearing fails
         */
        void clearWarnings() throws SQLException;
    }

}
