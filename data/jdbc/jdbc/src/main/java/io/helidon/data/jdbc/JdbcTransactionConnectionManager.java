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
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import io.helidon.data.DataException;
import io.helidon.service.registry.Service;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxLifeCycle;

/**
 * Associates one lazily acquired connection with a local JDBC transaction.
 * <p>
 * Transaction propagation and JDBC resource association are separate
 * responsibilities. {@link JdbcTxSupport} owns propagation and sends
 * {@link TxLifeCycle} events without depending on JDBC APIs. This listener
 * consumes those events and manages the datasource identity, connection
 * association, and internal connection leases used by {@code JdbcClient}.
 * <p>
 * The listener belongs to Data JDBC because the provider owns every JDBC
 * resource. Keeping transaction propagation and connection association in the
 * provider also avoids a public cross-module SPI for internal connection
 * leases. Generated repositories and the transaction API never receive the
 * physical connection.
 * <p>
 * Lifecycle events may come from another transaction provider. This listener
 * records such a transaction as foreign and rejects local JDBC acquisition
 * rather than implying that an ordinary connection joined that transaction.
 * <p>
 * Each transaction may use one datasource identity. A confirmed completion
 * restores auto commit before closing the connection. An unknown outcome
 * invalidates the connection instead of returning it for normal reuse.
 */
@Service.Singleton
final class JdbcTransactionConnectionManager implements TxLifeCycle, JdbcConnectionLease.Provider {

    // Transaction context is synchronous and does not propagate to another thread.
    private final ThreadLocal<State> local = new ThreadLocal<>();

    @Override
    public JdbcConnectionLease acquire(DataSource dataSource) throws SQLException {
        Objects.requireNonNull(dataSource, "The JDBC datasource must not be null.");
        State state = local.get();
        if (state == null) {
            return JdbcConnectionLease.Owned.acquire(dataSource);
        }
        if (state.failedJdbc != null) {
            throw new DataException("The active local JDBC transaction cannot be used after a lifecycle failure.");
        }
        if (state.failedForeign != null) {
            throw new DataException("A local JDBC connection cannot join a failed transaction from another provider.");
        }
        if (state.activeForeign != null) {
            throw new DataException("A local JDBC connection cannot join the active transaction type '"
                                            + state.foreignTransactions.get(state.activeForeign) + "'.");
        }
        // Lifecycle state may exist without an active transaction, such as during
        // supported work or while an outer transaction is suspended.
        if (state.activeJdbc == null) {
            return JdbcConnectionLease.Owned.acquire(dataSource);
        }

        Association association = state.jdbcTransactions.get(state.activeJdbc);
        if (association == null) {
            throw new IllegalStateException("The active JDBC transaction has no lifecycle association.");
        }
        association.require(AssociationState.ACTIVE, "acquire a connection");
        Object identity = transactionIdentity(dataSource);
        if (association.dataSourceIdentitySet && !sameIdentity(association.dataSourceIdentity, identity)) {
            throw new DataException("A local JDBC transaction cannot use more than one datasource.");
        }
        if (!association.dataSourceIdentitySet) {
            // Fix the identity before acquisition so a failed first data source cannot be replaced by another one.
            association.dataSourceIdentity = identity;
            association.dataSourceIdentitySet = true;
        }
        if (association.connection == null) {
            Connection connection;
            try {
                connection = JdbcExceptionTranslator.invoke("acquiring a transaction connection",
                                                            dataSource::getConnection);
            } catch (SQLException | RuntimeException | Error failure) {
                failJdbcAssociation(state, state.activeJdbc, association);
                throw failure;
            }
            try {
                if (!JdbcExceptionTranslator.invoke("inspecting transaction automatic commit mode",
                                                    connection::getAutoCommit)) {
                    throw JdbcExceptionTranslator.safeException(
                            "Datasources used for local JDBC transactions must provide connections with auto-commit enabled.");
                }
                JdbcExceptionTranslator.invokeVoid("disabling automatic commit mode",
                                                   () -> connection.setAutoCommit(false));
                // Publish the connection only after it is ready for transaction use.
                association.connection = connection;
            } catch (SQLException | RuntimeException | Error failure) {
                Throwable reportedFailure = JdbcConnectionInvalidator.invalidate(connection, failure);
                failJdbcAssociation(state, state.activeJdbc, association);
                throw rethrowConnectionFailure(reportedFailure);
            }
        }
        return new TransactionLease(association.connection);
    }

    @Override
    public void start(String type) {
        State state = local.get();
        if (state == null) {
            state = new State();
            local.set(state);
        }
        state.invocationTypes.push(Objects.requireNonNull(type, "The transaction support type must not be null."));
    }

    @Override
    public void end() {
        State state = requireState("end a transaction lifecycle");
        if (state.invocationTypes.isEmpty()) {
            throw new IllegalStateException("The transaction lifecycle end has no matching start.");
        }
        state.invocationTypes.pop();
        removeIfEmpty(state);
    }

    @Override
    public void begin(String txIdentity) {
        Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        State state = requireState("begin a transaction");
        requireNoActiveTransaction(state, "begin a transaction");
        if (state.jdbcTransactions.containsKey(txIdentity) || state.foreignTransactions.containsKey(txIdentity)) {
            throw new IllegalStateException("The transaction identity is already active.");
        }
        String type = currentType(state);
        if (Jdbc.PROVIDER.equals(type)) {
            state.jdbcTransactions.put(txIdentity, new Association());
            state.activeJdbc = txIdentity;
        } else {
            // Record foreign transactions so JDBC acquisition fails instead of claiming
            // that a local connection joined another provider's transaction.
            state.foreignTransactions.put(txIdentity, type);
            state.foreignStates.put(txIdentity, AssociationState.ACTIVE);
            state.activeForeign = txIdentity;
        }
    }

    @Override
    public void commit(String txIdentity) {
        complete(txIdentity, true);
    }

    @Override
    public void rollback(String txIdentity) {
        complete(txIdentity, false);
    }

    @Override
    public void suspend(String txIdentity) {
        Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        State state = requireState("suspend a transaction");
        if (txIdentity.equals(state.activeJdbc)) {
            Association association = requireAssociation(state, txIdentity);
            try {
                association.transition(AssociationState.ACTIVE,
                                       AssociationState.SUSPENDED,
                                       JdbcTransactionAction.SUSPEND.text());
            } catch (RuntimeException | Error failure) {
                failJdbcAssociation(state, txIdentity, association);
                throw failure;
            }
            state.activeJdbc = null;
        } else if (txIdentity.equals(state.activeForeign)) {
            transitionForeign(state,
                              txIdentity,
                              AssociationState.ACTIVE,
                              AssociationState.SUSPENDED,
                              JdbcTransactionAction.SUSPEND.text());
            state.activeForeign = null;
        } else {
            Association association = state.jdbcTransactions.get(txIdentity);
            if (association != null) {
                failJdbcAssociation(state, txIdentity, association);
            } else {
                failKnownContext(state);
            }
            throw new IllegalStateException("The transaction cannot be suspended because it is not active.");
        }
    }

    @Override
    public void resume(String txIdentity) {
        Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        State state = requireState("resume a transaction");
        requireNoActiveTransaction(state, "resume a transaction");
        Association association = state.jdbcTransactions.get(txIdentity);
        if (association != null) {
            try {
                association.transition(AssociationState.SUSPENDED,
                                       AssociationState.ACTIVE,
                                       JdbcTransactionAction.RESUME.text());
            } catch (RuntimeException | Error failure) {
                failJdbcAssociation(state, txIdentity, association);
                throw failure;
            }
            state.activeJdbc = txIdentity;
        } else if (state.foreignTransactions.containsKey(txIdentity)) {
            transitionForeign(state,
                              txIdentity,
                              AssociationState.SUSPENDED,
                              AssociationState.ACTIVE,
                              JdbcTransactionAction.RESUME.text());
            state.activeForeign = txIdentity;
        } else {
            failKnownContext(state);
            throw new IllegalStateException("The transaction cannot be resumed because its identity is not recognized.");
        }
    }

    /**
     * Completes either a JDBC association or a recorded foreign transaction.
     *
     * @param txIdentity transaction identity
     * @param commit whether a JDBC association should commit
     */
    private void complete(String txIdentity, boolean commit) {
        Objects.requireNonNull(txIdentity, "The transaction identity must not be null.");
        State state = requireState("complete a transaction");
        Association association = state.jdbcTransactions.get(txIdentity);
        if (association == null) {
            String foreignType = state.foreignTransactions.get(txIdentity);
            if (foreignType == null) {
                throw new IllegalStateException("The transaction cannot be completed because its identity is not recognized.");
            }
            completeForeign(state, txIdentity);
            return;
        }

        boolean failedContext = association.state == AssociationState.FAILED;
        if (!failedContext && !txIdentity.equals(state.activeJdbc)) {
            failJdbcAssociation(state, txIdentity, association);
            failedContext = true;
        }
        // A failed context can only roll back, even when the lifecycle requests commit.
        boolean effectiveCommit = commit && !failedContext;
        association.beginCompletion();
        RuntimeException runtimeFailure = null;
        Error errorFailure = null;
        try {
            completeConnection(association, effectiveCommit);
            if (failedContext && commit) {
                runtimeFailure = new TxException("The failed local JDBC transaction was rolled back instead of committed.");
            }
        } catch (RuntimeException failure) {
            runtimeFailure = failure;
        } catch (Error failure) {
            errorFailure = failure;
        } finally {
            // Clear thread state before propagating a completion failure.
            state.jdbcTransactions.remove(txIdentity);
            if (txIdentity.equals(state.activeJdbc)) {
                state.activeJdbc = null;
            }
            if (txIdentity.equals(state.failedJdbc)) {
                state.failedJdbc = null;
            }
            removeIfEmpty(state);
        }
        if (errorFailure != null) {
            throw errorFailure;
        }
        if (runtimeFailure != null) {
            throw runtimeFailure;
        }
    }

    /**
     * Completes and closes a lazily acquired physical connection.
     * <p>
     * A failed commit has an unknown database outcome even when the following
     * best-effort rollback succeeds. Unknown outcomes bypass auto-commit
     * restoration and ordinary pooled close and instead invalidate the
     * connection. Cleanup after a confirmed commit or rollback records the
     * confirmed outcome separately before invalidation.
     *
     * @param association JDBC transaction association
     * @param commit whether to commit rather than roll back
     */
    private static void completeConnection(Association association, boolean commit) {
        Connection connection = association.connection;
        if (connection == null) {
            association.completed(commit ? CompletionOutcome.COMMITTED : CompletionOutcome.ROLLED_BACK);
            return;
        }

        Throwable completionFailure = null;
        try {
            if (commit) {
                connection.commit();
            } else {
                connection.rollback();
            }
        } catch (SQLException | RuntimeException | Error failure) {
            completionFailure = failure;
            if (commit) {
                // Best-effort rollback releases server work. It cannot determine the outcome of a failed commit.
                try {
                    connection.rollback();
                } catch (SQLException | RuntimeException | Error rollbackFailure) {
                    completionFailure = JdbcExceptionTranslator.suppress(
                            completionFailure,
                            "rolling back after a failed transaction commit",
                            rollbackFailure);
                }
            }
        }

        if (completionFailure != null) {
            association.failed(CompletionOutcome.UNKNOWN);
            // Restoring auto commit after an unknown outcome could commit pending work.
            completionFailure = JdbcConnectionInvalidator.invalidate(connection, completionFailure);
            throwTransactionFailure(commit
                                            ? "The local JDBC transaction commit failed, and the outcome is unknown."
                                            : "The local JDBC transaction rollback failed, and the outcome is unknown.",
                                    completionFailure);
            return;
        }

        CompletionOutcome outcome = commit ? CompletionOutcome.COMMITTED : CompletionOutcome.ROLLED_BACK;
        association.completed(outcome);
        try {
            // Auto commit is the only connection setting changed by this provider.
            connection.setAutoCommit(true);
        } catch (SQLException | RuntimeException | Error restoreFailure) {
            association.failed(outcome);
            Throwable reportedFailure = JdbcConnectionInvalidator.invalidate(connection, restoreFailure);
            throwTransactionFailure(completionCleanupMessage(association.outcome, "restore automatic commit mode"),
                                    reportedFailure);
            return;
        }

        try {
            connection.close();
        } catch (SQLException | RuntimeException | Error closeFailure) {
            association.failed(outcome);
            Throwable reportedFailure = JdbcConnectionInvalidator.invalidate(connection, closeFailure);
            throwTransactionFailure(completionCleanupMessage(association.outcome, "close the connection"),
                                    reportedFailure);
        }
    }

    /**
     * Restores the declared or unchecked category of a connection failure.
     *
     * @param failure failure to throw
     * @return never returns
     * @throws SQLException when the failure is an SQL exception
     */
    private static SQLException rethrowConnectionFailure(Throwable failure) throws SQLException {
        if (failure instanceof SQLException sqlException) {
            throw sqlException;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw (Error) failure;
    }

    /**
     * Creates a diagnostic for a known transaction outcome followed by unsafe cleanup.
     *
     * @param outcome confirmed transaction outcome
     * @param action failed cleanup action
     * @return cleanup failure message
     */
    private static String completionCleanupMessage(CompletionOutcome outcome, String action) {
        return "The local JDBC transaction was "
                + (outcome == CompletionOutcome.COMMITTED ? "committed" : "rolled back")
                + ", but the provider failed to " + action + ".";
    }

    /**
     * Throws a completion failure while preserving fatal JVM errors. Non-fatal
     * JDBC failures are sanitized before becoming the transaction exception's
     * cause.
     *
     * @param message transaction diagnostic
     * @param cause original failure
     */
    private static void throwTransactionFailure(String message, Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        throw new TxException(message, JdbcExceptionTranslator.sanitize("completing a transaction", cause));
    }

    /**
     * Obtains the stable identity exposed by an internal direct datasource.
     *
     * @param dataSource datasource in use
     * @return identity used for transaction consistency
     */
    private static Object transactionIdentity(DataSource dataSource) {
        return dataSource instanceof IdentitySource source
                ? Objects.requireNonNull(source.transactionIdentity(), "The stable datasource identity must not be null.")
                : dataSource;
    }

    /**
     * Compares configured identities by value and ordinary datasources by
     * instance identity.
     *
     * @param first first identity
     * @param second second identity
     * @return whether both identities represent the same datasource
     */
    private static boolean sameIdentity(Object first, Object second) {
        if (first instanceof StableIdentity || second instanceof StableIdentity) {
            return Objects.equals(first, second);
        }
        return first == second;
    }

    /**
     * Returns the transaction support that issued the current lifecycle call.
     *
     * @param state thread state
     * @return current support type
     */
    private static String currentType(State state) {
        if (state.invocationTypes.isEmpty()) {
            throw new IllegalStateException("A transaction begin event has no active transaction support.");
        }
        return state.invocationTypes.peek();
    }

    /**
     * Verifies that no transaction association is currently active.
     *
     * @param state thread state
     * @param operation requested operation
     */
    private static void requireNoActiveTransaction(State state, String operation) {
        if (state.activeJdbc != null || state.activeForeign != null
                || state.failedJdbc != null || state.failedForeign != null) {
            throw new IllegalStateException("The provider cannot " + operation
                                                    + " while another transaction association is active.");
        }
    }

    /**
     * Returns a required JDBC association.
     *
     * @param state thread state
     * @param txIdentity transaction identity
     * @return matching association
     */
    private static Association requireAssociation(State state, String txIdentity) {
        Association association = state.jdbcTransactions.get(txIdentity);
        if (association == null) {
            throw new IllegalStateException("The local JDBC transaction identity is not recognized.");
        }
        return association;
    }

    /**
     * Marks an association and its thread context unusable after a lifecycle mismatch.
     *
     * @param state thread state
     * @param txIdentity transaction identity
     * @param association failed association
     */
    private static void failJdbcAssociation(State state, String txIdentity, Association association) {
        association.failLifecycle();
        state.failedJdbc = txIdentity;
    }

    /**
     * Fails the only known suspended context when a lifecycle identity cannot be resolved.
     *
     * @param state thread state
     */
    private static void failKnownContext(State state) {
        // An unmatched lifecycle identity makes the only known context unsafe
        // because its actual state can no longer be proven.
        if (state.jdbcTransactions.size() == 1) {
            Map.Entry<String, Association> entry = state.jdbcTransactions.entrySet().iterator().next();
            failJdbcAssociation(state, entry.getKey(), entry.getValue());
        } else if (state.foreignTransactions.size() == 1) {
            String identity = state.foreignTransactions.keySet().iterator().next();
            state.foreignStates.put(identity, AssociationState.FAILED);
            state.failedForeign = identity;
        }
    }

    /**
     * Performs one validated foreign-transaction transition.
     *
     * @param state thread state
     * @param txIdentity transaction identity
     * @param expected expected state
     * @param next target state
     * @param operation operation description
     */
    private static void transitionForeign(State state,
                                          String txIdentity,
                                          AssociationState expected,
                                          AssociationState next,
                                          String operation) {
        AssociationState actual = state.foreignStates.get(txIdentity);
        if (actual != expected) {
            state.failedForeign = txIdentity;
            if (actual != null) {
                state.foreignStates.put(txIdentity, AssociationState.FAILED);
            }
            throw new IllegalStateException("The provider cannot " + operation
                                                    + " the transaction from another provider while its state is '"
                                                    + actual + "'.");
        }
        state.foreignStates.put(txIdentity, next);
    }

    /**
     * Completes and removes a foreign transaction association.
     *
     * @param state thread state
     * @param txIdentity transaction identity
     */
    private void completeForeign(State state, String txIdentity) {
        AssociationState associationState = state.foreignStates.get(txIdentity);
        if (associationState != AssociationState.ACTIVE && associationState != AssociationState.FAILED) {
            state.failedForeign = txIdentity;
            throw new IllegalStateException("The transaction from another provider cannot be completed while its "
                                                    + "state is '" + associationState + "'.");
        }
        // Apply the same terminal state sequence used for local associations before
        // discarding the foreign association.
        state.foreignStates.put(txIdentity, AssociationState.COMPLETING);
        state.foreignStates.put(txIdentity, AssociationState.COMPLETED);
        state.foreignStates.remove(txIdentity);
        state.foreignTransactions.remove(txIdentity);
        if (txIdentity.equals(state.activeForeign)) {
            state.activeForeign = null;
        }
        if (txIdentity.equals(state.failedForeign)) {
            state.failedForeign = null;
        }
        removeIfEmpty(state);
    }

    /**
     * Returns the lifecycle state already established by {@link #start(String)}.
     *
     * @param operation lifecycle operation requiring state
     * @return current thread state
     */
    private State requireState(String operation) {
        State state = local.get();
        if (state == null) {
            throw new IllegalStateException("The provider cannot " + operation
                                                    + " without a matching transaction lifecycle start.");
        }
        return state;
    }

    /**
     * Removes empty state so completed invocations do not remain attached to
     * a pooled thread.
     *
     * @param state current thread state
     */
    private void removeIfEmpty(State state) {
        if (state.invocationTypes.isEmpty()
                && state.jdbcTransactions.isEmpty()
                && state.foreignTransactions.isEmpty()
                && state.foreignStates.isEmpty()
                && state.activeJdbc == null
                && state.activeForeign == null
                && state.failedJdbc == null
                && state.failedForeign == null) {
            local.remove();
        }
    }

    /**
     * Implemented only by internal datasource adapters whose configuration defines a stable transaction identity.
     */
    interface IdentitySource {
        /**
         * Returns the immutable identity used across equivalent adapters.
         *
         * @return stable datasource identity
         */
        StableIdentity transactionIdentity();
    }

    /**
     * Marker for immutable value identities.
     * Ordinary pooled datasources continue to use object identity.
     */
    interface StableIdentity {
    }

    /**
     * Validated lifecycle states of one connection association.
     */
    private enum AssociationState {

        ACTIVE,
        SUSPENDED,
        COMPLETING,
        COMPLETED,
        FAILED
    }

    /**
     * Outcome of JDBC transaction completion, independent of connection cleanup.
     */
    private enum CompletionOutcome {

        COMMITTED,
        ROLLED_BACK,
        UNKNOWN
    }

    /**
     * All lifecycle state associated with one thread.
     */
    private static final class State {

        private final ArrayDeque<String> invocationTypes = new ArrayDeque<>();
        private final Map<String, Association> jdbcTransactions = new HashMap<>();

        // Foreign transactions are tracked so JDBC cannot claim local participation.
        private final Map<String, String> foreignTransactions = new HashMap<>();
        private final Map<String, AssociationState> foreignStates = new HashMap<>();

        private String activeJdbc;
        private String activeForeign;

        // Failed contexts remain visible so later acquisition fails closed.
        private String failedJdbc;
        private String failedForeign;
    }

    /**
     * Lazily populated connection state for one JDBC transaction.
     */
    private static final class Association {

        private AssociationState state = AssociationState.ACTIVE;

        // The first operation fixes the only data source identity allowed in the transaction.
        private Object dataSourceIdentity;
        private boolean dataSourceIdentitySet;

        private Connection connection;

        // Cleanup decisions depend on whether the database outcome is known.
        private CompletionOutcome outcome;

        /**
         * Creates an active association.
         */
        private Association() {
        }

        /**
         * Verifies an exact association state.
         *
         * @param expected expected state
         * @param operation requested operation
         */
        private void require(AssociationState expected, String operation) {
            if (state != expected) {
                throw new IllegalStateException("The provider cannot " + operation
                                                        + " while the local JDBC transaction association is in state '"
                                                        + state + "'.");
            }
        }

        /**
         * Performs one exact state transition.
         *
         * @param expected expected state
         * @param next target state
         * @param operation requested operation
         */
        private void transition(AssociationState expected, AssociationState next, String operation) {
            require(expected, operation);
            state = next;
        }

        // A failed association may still own a connection that requires rollback.
        private void beginCompletion() {
            if (state != AssociationState.ACTIVE && state != AssociationState.FAILED) {
                failLifecycle();
            }
            state = AssociationState.COMPLETING;
        }

        /**
         * Records a confirmed transaction outcome.
         *
         * @param completionOutcome confirmed outcome
         */
        private void completed(CompletionOutcome completionOutcome) {
            transition(AssociationState.COMPLETING,
                       AssociationState.COMPLETED,
                       JdbcTransactionAction.COMPLETE.text());
            outcome = completionOutcome;
        }

        /**
         * Records an unsafe connection after either an unknown outcome or cleanup failure.
         *
         * @param completionOutcome known or unknown transaction outcome
         */
        private void failed(CompletionOutcome completionOutcome) {
            if (state != AssociationState.COMPLETING && state != AssociationState.COMPLETED) {
                throw new IllegalStateException("The local JDBC transaction cannot be marked as failed while its "
                                                        + "association is in state '" + state + "'.");
            }
            state = AssociationState.FAILED;
            outcome = completionOutcome;
        }

        // Completed associations remain terminal, while every other invalid transition fails closed.
        private void failLifecycle() {
            if (state != AssociationState.COMPLETED) {
                state = AssociationState.FAILED;
            }
        }
    }

    /**
     * Logical operation lease.
     * Its close is a no-op because transaction completion owns the physical connection.
     */
    private static final class TransactionLease implements JdbcConnectionLease {

        private final Connection connection;
        private boolean closed;

        /**
         * Creates a logical lease over the transaction connection.
         *
         * @param connection transaction connection
         */
        private TransactionLease(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection connection() {
            if (closed) {
                throw new IllegalStateException("The connection lease is closed.");
            }
            return connection;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
