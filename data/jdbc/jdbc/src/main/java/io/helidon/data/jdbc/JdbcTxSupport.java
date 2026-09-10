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

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.data.jdbc.JdbcTransactionConnectionManager.CompletionOutcome;
import io.helidon.data.jdbc.JdbcTransactionConnectionManager.CompletionReceipt;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxLifeCycle;
import io.helidon.transaction.spi.TxSupport;

/**
 * Applies Helidon transaction propagation rules to local JDBC transactions.
 * <p>
 * This service owns propagation and lifecycle notification.
 * {@link JdbcTransactionConnectionManager} completes the provider-owned JDBC
 * resource directly and also consumes lifecycle notifications to detect
 * transaction contexts owned by other providers.
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 20)
final class JdbcTxSupport implements TxSupport {

    // Generate compact process-local identities so lifecycle listeners can correlate events without application state.
    private static final AtomicLong IDS = new AtomicLong();

    // Dedicated owner of local JDBC resource completion.
    private final JdbcTransactionConnectionManager connectionManager;

    // Lifecycle listeners, copied once to keep notification order stable.
    private final List<TxLifeCycle> listeners;

    // Completion observers retain service order but cannot obscure the JDBC outcome.
    private final List<TxLifeCycle> completionObservers;

    // Active and suspended transaction stack for the current thread.
    private final ThreadLocal<ArrayDeque<Transaction>> transactions = new ThreadLocal<>();

    /**
     * Creates the local JDBC propagation service.
     *
     * @param connectionManager local JDBC completion participant
     * @param listeners lifecycle listeners
     */
    @Service.Inject
    JdbcTxSupport(JdbcTransactionConnectionManager connectionManager, List<TxLifeCycle> listeners) {
        this.connectionManager = connectionManager;
        this.listeners = List.copyOf(listeners);
        this.completionObservers = this.listeners.stream()
                .filter(listener -> listener != connectionManager)
                .toList();
    }

    @Override
    public String type() {
        return Jdbc.PROVIDER;
    }

    @Override
    public <T> T transaction(Tx.Type type, Callable<T> task) {
        Objects.requireNonNull(type, "The transaction type must not be null.");
        Objects.requireNonNull(task, "The transaction task must not be null.");
        InvocationCompletion completion = new InvocationCompletion();
        // Bracket every propagation call so listeners can associate later lifecycle
        // events with this transaction provider.
        try {
            notifyListeners(listeners,
                            listener -> listener.start(Jdbc.PROVIDER),
                            JdbcTransactionAction.START.text());
        } catch (RuntimeException | Error startFailure) {
            throw propagate(notifyAfterInfrastructureFailure(TxLifeCycle::end,
                                                             JdbcTransactionAction.START.cleanupText(),
                                                             startFailure));
        }
        Callable<T> trackedTask = () -> {
            try {
                return task.call();
            } catch (Exception | Error taskFailure) {
                completion.recordTaskFailure();
                throw taskFailure;
            }
        };
        T result = null;
        Throwable invocationFailure = null;
        try {
            result = switch (type) {
                case MANDATORY -> mandatory(trackedTask);
                case NEW -> requiresNew(trackedTask, completion);
                case NEVER -> never(trackedTask);
                case REQUIRED -> required(trackedTask, completion);
                case SUPPORTED -> supported(trackedTask);
                case UNSUPPORTED -> unsupported(trackedTask, completion);
            };
        } catch (RuntimeException | Error failure) {
            invocationFailure = failure;
        }
        Throwable endFailure = null;
        try {
            notifyListeners(listeners, TxLifeCycle::end, JdbcTransactionAction.END.text());
        } catch (RuntimeException | Error failure) {
            endFailure = failure;
        }
        if (invocationFailure != null) {
            if (completion.taskFailed()) {
                suppress(invocationFailure, endFailure);
            } else {
                invocationFailure = merge(invocationFailure, endFailure);
            }
            throw propagate(invocationFailure);
        }
        if (endFailure instanceof RuntimeException && completion.committed()) {
            throw committedLifecycleFailure(JdbcTransactionAction.END.text(), endFailure);
        }
        throwFailure(endFailure);
        return result;
    }

    /**
     * Combines two infrastructure failures without allowing a recoverable
     * failure to obscure a fatal error. The first error remains primary
     * regardless of whether it was encountered before or after a runtime
     * exception.
     *
     * @param primary earlier failure
     * @param secondary later failure
     * @return combined failure
     */
    private static Throwable merge(Throwable primary, Throwable secondary) {
        if (primary == null) {
            return secondary;
        }
        if (secondary instanceof Error && !(primary instanceof Error)) {
            suppress(secondary, primary);
            return secondary;
        }
        suppress(primary, secondary);
        return primary;
    }

    /**
     * Adds a later failure to a primary failure when both are present.
     *
     * @param primary primary failure
     * @param secondary later failure
     */
    private static void suppress(Throwable primary, Throwable secondary) {
        if (primary != null && secondary != null && primary != secondary) {
            primary.addSuppressed(secondary);
        }
    }

    /**
     * Creates an explicit failure for a lifecycle notification which followed
     * a confirmed database commit.
     *
     * @param event failed lifecycle event
     * @param cause notification failure
     * @return transaction failure which preserves the confirmed commit
     */
    private static TxException committedLifecycleFailure(String event, Throwable cause) {
        return new TxException("The local JDBC transaction was committed, but a later transaction lifecycle "
                                       + "notification failed during " + event
                                       + ". The committed work must not be retried automatically.",
                               cause);
    }

    /**
     * Throws a retained runtime or fatal failure.
     *
     * @param failure failure to throw, or {@code null}
     */
    private static void throwFailure(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
    }

    /**
     * Returns a recoverable failure for throwing or propagates a fatal error unchanged.
     *
     * @param failure runtime or fatal failure
     * @return recoverable failure
     */
    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        return (RuntimeException) failure;
    }

    /**
     * Runs a task only when a local transaction is active.
     *
     * @param task application task
     * @param <T> result type
     * @return task result
     */
    private <T> T mandatory(Callable<T> task) {
        Transaction current = current();
        if (current == null) {
            throw new TxException("@Tx.Mandatory requires an active local JDBC transaction.");
        }
        return callJoined(current, task);
    }

    /**
     * Suspends an outer transaction and runs the task in a new one.
     *
     * @param task application task
     * @param completion invocation completion state
     * @param <T> result type
     * @return task result
     */
    private <T> T requiresNew(Callable<T> task, InvocationCompletion completion) {
        Transaction suspended = suspend();
        T result;
        try {
            result = callNew(task, completion);
        } catch (RuntimeException | Error failure) {
            throw propagate(resumeAfterFailure(suspended, failure, completion.taskFailed()));
        }
        try {
            resume(suspended);
        } catch (RuntimeException resumeFailure) {
            if (completion.committed()) {
                throw committedLifecycleFailure(JdbcTransactionAction.RESUME.text(), resumeFailure);
            }
            throw resumeFailure;
        }
        return result;
    }

    /**
     * Runs a task only when no local transaction is active.
     *
     * @param task application task
     * @param <T> result type
     * @return task result
     */
    private <T> T never(Callable<T> task) {
        if (current() != null) {
            throw new TxException("@Tx.Never cannot run inside an active local JDBC transaction.");
        }
        return callOutside(task);
    }

    /**
     * Joins the current transaction or starts a new one.
     *
     * @param task application task
     * @param completion invocation completion state
     * @param <T> result type
     * @return task result
     */
    private <T> T required(Callable<T> task, InvocationCompletion completion) {
        Transaction current = current();
        return current == null ? callNew(task, completion) : callJoined(current, task);
    }

    /**
     * Joins an active transaction and otherwise runs without one.
     *
     * @param task application task
     * @param <T> result type
     * @return task result
     */
    private <T> T supported(Callable<T> task) {
        Transaction current = current();
        return current == null ? callOutside(task) : callJoined(current, task);
    }

    /**
     * Suspends an active transaction while the task runs outside it.
     *
     * @param task application task
     * @param completion invocation completion state
     * @param <T> result type
     * @return task result
     */
    private <T> T unsupported(Callable<T> task, InvocationCompletion completion) {
        Transaction suspended = suspend();
        T result;
        try {
            result = callOutside(task);
        } catch (RuntimeException | Error failure) {
            throw propagate(resumeAfterFailure(suspended, failure, completion.taskFailed()));
        }
        resume(suspended);
        return result;
    }

    /**
     * Invokes a task without transaction completion rules.
     *
     * @param task application task
     * @param <T> result type
     * @return task result
     */
    private <T> T callOutside(Callable<T> task) {
        try {
            return task.call();
        } catch (TxException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TxException("The local JDBC transaction task was interrupted.", e);
        } catch (Exception e) {
            throw new TxException("The local JDBC transaction task failed.", e);
        }
    }

    /**
     * Invokes a task joined to an existing transaction and marks failures for
     * rollback.
     *
     * @param transaction joined transaction
     * @param task application task
     * @param <T> result type
     * @return task result
     */
    private <T> T callJoined(Transaction transaction, Callable<T> task) {
        try {
            return task.call();
        } catch (TxException e) {
            transaction.markRollbackOnly();
            throw e;
        } catch (InterruptedException e) {
            transaction.markRollbackOnly();
            Thread.currentThread().interrupt();
            throw new TxException("The local JDBC transaction task was interrupted.", e);
        } catch (Exception e) {
            transaction.markRollbackOnly();
            throw new TxException("The local JDBC transaction task failed.", e);
        } catch (Error e) {
            transaction.markRollbackOnly();
            throw e;
        }
    }

    /**
     * Starts, invokes, and completes one new local transaction.
     *
     * @param task application task
     * @param completion invocation completion state
     * @param <T> result type
     * @return task result
     */
    private <T> T callNew(Callable<T> task, InvocationCompletion completion) {
        Transaction transaction = begin(completion);
        T result;
        try {
            result = task.call();
        } catch (TxException e) {
            transaction.markRollbackOnly();
            rollback(transaction, e, completion);
            throw e;
        } catch (InterruptedException e) {
            transaction.markRollbackOnly();
            Thread.currentThread().interrupt();
            TxException failure = new TxException("The local JDBC transaction task was interrupted.", e);
            rollback(transaction, failure, completion);
            throw failure;
        } catch (Exception e) {
            transaction.markRollbackOnly();
            TxException failure = new TxException("The local JDBC transaction task failed.", e);
            rollback(transaction, failure, completion);
            throw failure;
        } catch (Error e) {
            transaction.markRollbackOnly();
            rollback(transaction, e, completion);
            throw e;
        }

        if (transaction.rollbackOnly()) {
            TxException failure = new TxException("The local JDBC transaction was marked for rollback.");
            rollback(transaction, failure, completion);
            throw failure;
        }
        commit(transaction, completion);
        return result;
    }

    /**
     * Pushes a new transaction before notifying lifecycle listeners.
     *
     * @param completion invocation completion state
     * @return started transaction
     */
    private Transaction begin(InvocationCompletion completion) {
        Transaction transaction = new Transaction(Long.toUnsignedString(IDS.incrementAndGet(), 36));
        transactionStack().push(transaction);
        try {
            notifyListeners(listeners,
                            listener -> listener.begin(transaction.identity),
                            JdbcTransactionAction.BEGIN.text());
            return transaction;
        } catch (RuntimeException | Error failure) {
            transaction.markRollbackOnly();
            rollback(transaction, failure, completion);
            throw failure;
        }
    }

    /**
     * Removes and commits the current transaction.
     *
     * @param transaction transaction to commit
     * @param completion invocation completion state
     */
    private void commit(Transaction transaction, InvocationCompletion completion) {
        transaction.beginCompletion();
        CompletionReceipt receipt;
        try {
            receipt = connectionManager.commitLocal(transaction.identity);
        } catch (RuntimeException | Error failure) {
            receipt = new CompletionReceipt(CompletionOutcome.UNKNOWN, failure);
        }
        transaction.completed(receipt.outcome());
        completion.completed(receipt.outcome());

        Throwable observerFailure = null;
        try {
            notifyListeners(completionObservers,
                            listener -> listener.commit(transaction.identity),
                            JdbcTransactionAction.COMMIT.text());
        } catch (RuntimeException | Error failure) {
            observerFailure = failure;
        }

        Throwable completionFailure = receipt.failure();
        if (completionFailure == null
                && observerFailure instanceof RuntimeException
                && receipt.outcome() == CompletionOutcome.COMMITTED) {
            completionFailure = committedLifecycleFailure(JdbcTransactionAction.COMMIT.text(), observerFailure);
        } else {
            completionFailure = merge(completionFailure, observerFailure);
        }
        Throwable removalFailure = removeCurrent(transaction);
        if (completionFailure == null
                && removalFailure instanceof RuntimeException
                && receipt.outcome() == CompletionOutcome.COMMITTED) {
            completionFailure = committedLifecycleFailure("transaction state cleanup", removalFailure);
        } else {
            completionFailure = merge(completionFailure, removalFailure);
        }
        throwFailure(completionFailure);
    }

    /**
     * Removes and rolls back the current transaction. Application task
     * failures remain primary; infrastructure failures are combined using
     * fatal-aware precedence.
     *
     * @param transaction transaction to roll back
     * @param primaryFailure failure which initiated rollback, or {@code null}
     * @param completion invocation completion state
     */
    private void rollback(Transaction transaction, Throwable primaryFailure, InvocationCompletion completion) {
        transaction.beginCompletion();
        CompletionReceipt receipt;
        try {
            receipt = connectionManager.rollbackLocal(transaction.identity);
        } catch (RuntimeException | Error failure) {
            receipt = new CompletionReceipt(CompletionOutcome.UNKNOWN, failure);
        }
        transaction.completed(receipt.outcome());
        completion.completed(receipt.outcome());

        Throwable observerFailure = null;
        try {
            notifyListeners(completionObservers,
                            listener -> listener.rollback(transaction.identity),
                            JdbcTransactionAction.ROLLBACK.text());
        } catch (RuntimeException | Error failure) {
            observerFailure = failure;
        }

        Throwable rollbackFailure = merge(receipt.failure(), observerFailure);
        rollbackFailure = merge(rollbackFailure, removeCurrent(transaction));
        if (primaryFailure != null && completion.taskFailed()) {
            suppress(primaryFailure, rollbackFailure);
            return;
        }
        throwFailure(merge(primaryFailure, rollbackFailure));
    }

    /**
     * Removes and suspends the current transaction, if one exists.
     *
     * @return suspended transaction, or {@code null}
     */
    private Transaction suspend() {
        Transaction transaction = current();
        if (transaction == null) {
            return null;
        }
        transaction.suspend();
        try {
            notifyListeners(listeners,
                            listener -> listener.suspend(transaction.identity),
                            JdbcTransactionAction.SUSPEND.text());
        } catch (RuntimeException | Error failure) {
            transaction.restoreAfterSuspendFailure();
            throw propagate(notifyAfterInfrastructureFailure(listener -> listener.resume(transaction.identity),
                                                             JdbcTransactionAction.SUSPEND.cleanupText(),
                                                             failure));
        }
        return transaction;
    }

    /**
     * Restores a suspended transaction and its listener associations.
     *
     * @param transaction suspended transaction, or {@code null}
     */
    private void resume(Transaction transaction) {
        if (transaction == null) {
            return;
        }
        transaction.resume();
        try {
            notifyListeners(listeners,
                            listener -> listener.resume(transaction.identity),
                            JdbcTransactionAction.RESUME.text());
        } catch (RuntimeException | Error failure) {
            // Keep listeners that already resumed attached so the outer transaction can still roll back.
            transaction.markRollbackOnly();
            throw failure;
        }
    }

    /**
     * Attempts to restore a suspended transaction after a failed invocation.
     * Application task failures remain primary, while infrastructure failures
     * yield to a later fatal resume error.
     *
     * @param transaction suspended transaction
     * @param primaryFailure invocation failure
     * @param taskFailed whether the application task failed
     * @return combined failure
     */
    private Throwable resumeAfterFailure(Transaction transaction, Throwable primaryFailure, boolean taskFailed) {
        try {
            resume(transaction);
        } catch (RuntimeException | Error resumeFailure) {
            if (taskFailed) {
                suppress(primaryFailure, resumeFailure);
            } else {
                primaryFailure = merge(primaryFailure, resumeFailure);
            }
        }
        return primaryFailure;
    }

    /**
     * Returns the active transaction for this thread.
     *
     * @return current transaction, or {@code null}
     */
    private Transaction current() {
        ArrayDeque<Transaction> stack = transactions.get();
        if (stack == null) {
            return null;
        }
        Transaction transaction = stack.peek();
        if (transaction == null || transaction.suspended()) {
            return null;
        }
        transaction.requireUsable();
        return transaction;
    }

    /**
     * Removes the expected transaction and verifies stack consistency.
     *
     * @param expected expected current transaction
     */
    private Throwable removeCurrent(Transaction expected) {
        ArrayDeque<Transaction> stack = transactions.get();
        if (stack == null) {
            return new IllegalStateException("The local JDBC transaction stack is missing.");
        }
        Transaction actual = stack.poll();
        if (actual != expected) {
            return new IllegalStateException("The local JDBC transaction stack is inconsistent.");
        }
        removeThreadStateIfEmpty();
        return null;
    }

    /**
     * Clears empty thread-local state after completion or suspension.
     */
    private void removeThreadStateIfEmpty() {
        ArrayDeque<Transaction> stack = transactions.get();
        if (stack != null && stack.isEmpty()) {
            transactions.remove();
        }
    }

    /**
     * Returns the current transaction stack, creating it only when a new local
     * transaction is about to begin.
     *
     * @return current transaction stack
     */
    private ArrayDeque<Transaction> transactionStack() {
        ArrayDeque<Transaction> stack = transactions.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            transactions.set(stack);
        }
        return stack;
    }

    /**
     * Notifies every listener in registration order and combines failures.
     *
     * @param recipients listeners receiving the event
     * @param action listener action
     * @param event event name used in diagnostics
     */
    private void notifyListeners(List<TxLifeCycle> recipients, ListenerAction action, String event) {
        Throwable failure = null;
        // Continue after a listener fails so it cannot prevent later listeners from releasing their resources.
        for (TxLifeCycle listener : recipients) {
            try {
                action.accept(listener);
            } catch (RuntimeException | Error listenerFailure) {
                failure = merge(failure, listenerFailure);
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof TxException txException) {
            throw txException;
        }
        if (failure != null) {
            throw new TxException("A local JDBC transaction lifecycle notification failed during " + event + ".",
                                  failure);
        }
    }

    /**
     * Delivers cleanup after an infrastructure failure while preserving the first fatal error.
     *
     * @param action listener action
     * @param event event name used in diagnostics
     * @param primaryFailure failure that initiated cleanup
     * @return combined infrastructure failure
     */
    private Throwable notifyAfterInfrastructureFailure(ListenerAction action,
                                                       String event,
                                                       Throwable primaryFailure) {
        try {
            notifyListeners(listeners, action, event);
        } catch (RuntimeException | Error cleanupFailure) {
            primaryFailure = merge(primaryFailure, cleanupFailure);
        }
        return primaryFailure;
    }

    /**
     * Validated states of one local transaction.
     */
    private enum TransactionState {
        ACTIVE,
        MARKED_ROLLBACK,
        SUSPENDED,
        COMPLETING,
        COMMITTED,
        ROLLED_BACK,
        UNKNOWN
    }

    @FunctionalInterface
    private interface ListenerAction {
        /**
         * Delivers one lifecycle event.
         *
         * @param listener receiving listener
         */
        void accept(TxLifeCycle listener);
    }

    /**
     * Completion outcome retained until every invocation-level lifecycle event
     * has finished.
     */
    private static final class InvocationCompletion {
        private CompletionOutcome outcome;
        private boolean taskFailed;

        /**
         * Returns whether this invocation committed its new transaction.
         *
         * @return whether commit was confirmed
         */
        private boolean committed() {
            return outcome == CompletionOutcome.COMMITTED;
        }

        /**
         * Returns whether the application task failed during this invocation.
         *
         * @return whether the task failed
         */
        private boolean taskFailed() {
            return taskFailed;
        }

        /**
         * Records that the application task failed during this invocation.
         */
        private void recordTaskFailure() {
            taskFailed = true;
        }

        /**
         * Records the single transaction completed by this invocation.
         *
         * @param completionOutcome authoritative completion outcome
         */
        private void completed(CompletionOutcome completionOutcome) {
            if (outcome != null) {
                throw new IllegalStateException("The transaction invocation already has a completion outcome.");
            }
            outcome = completionOutcome;
        }
    }

    /**
     * Mutable state for one transaction while it remains on the thread's transaction stack.
     */
    private static final class Transaction {
        /**
         * Identity shared with lifecycle listeners.
         */
        private final String identity;
        /**
         * Current validated transaction state.
         */
        private TransactionState state = TransactionState.ACTIVE;
        /**
         * State restored after a successful suspension.
         */
        private TransactionState resumeState;

        /**
         * Creates transaction state.
         *
         * @param identity transaction identity
         */
        private Transaction(String identity) {
            this.identity = identity;
        }

        /**
         * Returns whether completion must roll back.
         *
         * @return whether rollback is required
         */
        private boolean rollbackOnly() {
            return state == TransactionState.MARKED_ROLLBACK;
        }

        /**
         * Marks a usable transaction for rollback at its outer boundary.
         */
        private void markRollbackOnly() {
            switch (state) {
                case ACTIVE -> state = TransactionState.MARKED_ROLLBACK;
                case MARKED_ROLLBACK -> {
                }
                default -> throw invalidTransition("mark rollback-only");
            }
        }

        /**
         * Moves a usable transaction to its suspended state.
         */
        private void suspend() {
            requireUsable();
            // Preserve rollback-only state so suspension cannot make the transaction
            // eligible for commit again.
            resumeState = state;
            state = TransactionState.SUSPENDED;
        }

        /**
         * Restores the pre-suspension state.
         */
        private void resume() {
            require(TransactionState.SUSPENDED, JdbcTransactionAction.RESUME.text());
            state = resumeState;
            resumeState = null;
        }

        /**
         * Restores a transaction after suspension failed and makes rollback mandatory.
         */
        private void restoreAfterSuspendFailure() {
            require(TransactionState.SUSPENDED, "restore after suspend failure");
            resumeState = null;
            state = TransactionState.MARKED_ROLLBACK;
        }

        /**
         * Returns whether this transaction is currently suspended.
         *
         * @return whether the transaction is suspended
         */
        private boolean suspended() {
            return state == TransactionState.SUSPENDED;
        }

        /**
         * Starts terminal transaction completion.
         */
        private void beginCompletion() {
            requireUsable();
            state = TransactionState.COMPLETING;
        }

        /**
         * Records the authoritative resource-completion outcome independently
         * from later lifecycle notification failures.
         *
         * @param outcome completion outcome
         */
        private void completed(CompletionOutcome outcome) {
            require(TransactionState.COMPLETING, JdbcTransactionAction.COMPLETE.text());
            state = switch (outcome) {
                case COMMITTED -> TransactionState.COMMITTED;
                case ROLLED_BACK -> TransactionState.ROLLED_BACK;
                case UNKNOWN -> TransactionState.UNKNOWN;
            };
        }

        /**
         * Verifies that application work may use or join this transaction.
         */
        private void requireUsable() {
            if (state != TransactionState.ACTIVE && state != TransactionState.MARKED_ROLLBACK) {
                throw invalidTransition("join");
            }
        }

        /**
         * Verifies the exact source state for an operation.
         *
         * @param expected expected state
         * @param operation operation description
         */
        private void require(TransactionState expected, String operation) {
            if (state != expected) {
                throw invalidTransition(operation);
            }
        }

        /**
         * Creates a consistent state-transition diagnostic.
         *
         * @param operation rejected operation
         * @return state exception
         */
        private IllegalStateException invalidTransition(String operation) {
            return new IllegalStateException("The local JDBC transaction cannot " + operation
                                                     + " while its state is '" + state + "'.");
        }
    }
}
