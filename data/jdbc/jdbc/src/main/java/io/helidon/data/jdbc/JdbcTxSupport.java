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
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxLifeCycle;
import io.helidon.transaction.spi.TxSupport;

/**
 * Applies Helidon transaction propagation rules to local JDBC transactions.
 * <p>
 * This service owns propagation and lifecycle notification.
 * {@link JdbcTransactionConnectionManager} consumes those notifications and
 * associates connections lazily.
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 20)
final class JdbcTxSupport implements TxSupport {

    // Monotonic source of compact transaction identities.
    private static final AtomicLong IDS = new AtomicLong();

    // Lifecycle listeners, copied once to keep notification order stable.
    private final List<TxLifeCycle> listeners;

    // Active and suspended transaction stack for the current thread.
    private final ThreadLocal<ArrayDeque<Transaction>> transactions = new ThreadLocal<>();

    /**
     * Creates the local JDBC propagation service.
     *
     * @param listeners lifecycle listeners
     */
    @Service.Inject
    JdbcTxSupport(List<TxLifeCycle> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    /**
     * Reports whether the current thread retains transaction state.
     * Package access allows focused cleanup tests to verify removal without
     * exposing the transaction stack.
     *
     * @return whether transaction state is present for the current thread
     */
    boolean threadStatePresent() {
        return transactions.get() != null;
    }

    @Override
    public String type() {
        return Jdbc.PROVIDER;
    }

    @Override
    public <T> T transaction(Tx.Type type, Callable<T> task) {
        Objects.requireNonNull(type, "Missing transaction type");
        Objects.requireNonNull(task, "Missing task to run in transaction");
        // Bracket every propagation call so listeners can associate later lifecycle
        // events with this transaction provider.
        try {
            notifyListeners(listener -> listener.start(Jdbc.PROVIDER), JdbcTransactionAction.START.text());
        } catch (RuntimeException | Error startFailure) {
            notifyAfterFailure(TxLifeCycle::end, JdbcTransactionAction.START.cleanupText(), startFailure);
            throw startFailure;
        }
        T result;
        try {
            result = switch (type) {
                case MANDATORY -> mandatory(task);
                case NEW -> requiresNew(task);
                case NEVER -> never(task);
                case REQUIRED -> required(task);
                case SUPPORTED -> supported(task);
                case UNSUPPORTED -> unsupported(task);
            };
        } catch (RuntimeException | Error failure) {
            notifyAfterFailure(TxLifeCycle::end, JdbcTransactionAction.END.text(), failure);
            throw failure;
        }
        notifyListeners(TxLifeCycle::end, JdbcTransactionAction.END.text());
        return result;
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
            throw new TxException("Starting @Tx.Mandatory outside a local JDBC transaction");
        }
        return callJoined(current, task);
    }

    /**
     * Suspends an outer transaction and runs the task in a new one.
     *
     * @param task application task
     * @param <T> result type
     * @return task result
     */
    private <T> T requiresNew(Callable<T> task) {
        Transaction suspended = suspend();
        T result;
        try {
            result = callNew(task);
        } catch (RuntimeException | Error failure) {
            resumeAfterFailure(suspended, failure);
            throw failure;
        }
        resume(suspended);
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
            throw new TxException("Starting @Tx.Never inside a local JDBC transaction");
        }
        return callOutside(task);
    }

    /**
     * Joins the current transaction or starts a new one.
     *
     * @param task application task
     * @param <T> result type
     * @return task result
     */
    private <T> T required(Callable<T> task) {
        Transaction current = current();
        return current == null ? callNew(task) : callJoined(current, task);
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
     * @param <T> result type
     * @return task result
     */
    private <T> T unsupported(Callable<T> task) {
        Transaction suspended = suspend();
        T result;
        try {
            result = callOutside(task);
        } catch (RuntimeException | Error failure) {
            resumeAfterFailure(suspended, failure);
            throw failure;
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
            throw new TxException("Local JDBC transaction task was interrupted", e);
        } catch (Exception e) {
            throw new TxException("Local JDBC transaction task failed", e);
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
            throw new TxException("Local JDBC transaction task was interrupted", e);
        } catch (Exception e) {
            transaction.markRollbackOnly();
            throw new TxException("Local JDBC transaction task failed", e);
        } catch (Error e) {
            transaction.markRollbackOnly();
            throw e;
        }
    }

    /**
     * Starts, invokes, and completes one new local transaction.
     *
     * @param task application task
     * @param <T> result type
     * @return task result
     */
    private <T> T callNew(Callable<T> task) {
        Transaction transaction = begin();
        T result;
        try {
            result = task.call();
        } catch (TxException e) {
            transaction.markRollbackOnly();
            rollback(transaction, e);
            throw e;
        } catch (InterruptedException e) {
            transaction.markRollbackOnly();
            Thread.currentThread().interrupt();
            TxException failure = new TxException("Local JDBC transaction task was interrupted", e);
            rollback(transaction, failure);
            throw failure;
        } catch (Exception e) {
            transaction.markRollbackOnly();
            TxException failure = new TxException("Local JDBC transaction task failed", e);
            rollback(transaction, failure);
            throw failure;
        } catch (Error e) {
            transaction.markRollbackOnly();
            rollback(transaction, e);
            throw e;
        }

        if (transaction.rollbackOnly()) {
            TxException failure = new TxException("Local JDBC transaction was marked rollback-only");
            rollback(transaction, failure);
            throw failure;
        }
        commit(transaction);
        return result;
    }

    /**
     * Pushes a new transaction before notifying lifecycle listeners.
     *
     * @return started transaction
     */
    private Transaction begin() {
        Transaction transaction = new Transaction(Long.toUnsignedString(IDS.incrementAndGet(), 36));
        transactionStack().push(transaction);
        try {
            notifyListeners(listener -> listener.begin(transaction.identity), JdbcTransactionAction.BEGIN.text());
            return transaction;
        } catch (RuntimeException | Error failure) {
            transaction.markRollbackOnly();
            rollback(transaction, failure);
            throw failure;
        }
    }

    /**
     * Removes and commits the current transaction.
     *
     * @param transaction transaction to commit
     */
    private void commit(Transaction transaction) {
        transaction.beginCompletion();
        // Delay a completion failure until thread state is removed, while keeping
        // an Error distinct from an ordinary runtime failure.
        RuntimeException runtimeFailure = null;
        Error errorFailure = null;
        try {
            notifyListeners(listener -> listener.commit(transaction.identity), JdbcTransactionAction.COMMIT.text());
            transaction.committed();
        } catch (RuntimeException failure) {
            transaction.failed();
            runtimeFailure = failure;
        } catch (Error failure) {
            transaction.failed();
            errorFailure = failure;
        }
        Throwable removalFailure = removeCurrent(transaction);
        if (errorFailure != null) {
            suppress(errorFailure, removalFailure);
            throw errorFailure;
        }
        if (runtimeFailure != null) {
            suppress(runtimeFailure, removalFailure);
            throw runtimeFailure;
        }
        throwIfRemovalFailed(removalFailure);
    }

    /**
     * Removes and rolls back the current transaction while preserving the
     * application's primary failure.
     *
     * @param transaction transaction to roll back
     * @param primaryFailure application failure, or {@code null}
     */
    private void rollback(Transaction transaction, Throwable primaryFailure) {
        transaction.beginCompletion();
        Throwable rollbackFailure = null;
        try {
            notifyListeners(listener -> listener.rollback(transaction.identity), JdbcTransactionAction.ROLLBACK.text());
            transaction.rolledBack();
        } catch (RuntimeException | Error failure) {
            transaction.failed();
            rollbackFailure = failure;
        }
        rollbackFailure = merge(rollbackFailure, removeCurrent(transaction));
        if (primaryFailure != null) {
            suppress(primaryFailure, rollbackFailure);
            return;
        }
        if (rollbackFailure instanceof Error error) {
            throw error;
        }
        if (rollbackFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
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
            notifyListeners(listener -> listener.suspend(transaction.identity), JdbcTransactionAction.SUSPEND.text());
        } catch (RuntimeException | Error failure) {
            transaction.restoreAfterSuspendFailure();
            notifyAfterFailure(listener -> listener.resume(transaction.identity),
                               JdbcTransactionAction.SUSPEND.cleanupText(),
                               failure);
            throw failure;
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
            notifyListeners(listener -> listener.resume(transaction.identity), JdbcTransactionAction.RESUME.text());
        } catch (RuntimeException | Error failure) {
            // Keep listeners that already resumed attached so the outer transaction can still roll back.
            transaction.markRollbackOnly();
            throw failure;
        }
    }

    /**
     * Attempts to restore a suspended transaction without replacing a task
     * failure.
     *
     * @param transaction suspended transaction
     * @param primaryFailure task failure
     */
    private void resumeAfterFailure(Transaction transaction, Throwable primaryFailure) {
        try {
            resume(transaction);
        } catch (RuntimeException | Error resumeFailure) {
            suppress(primaryFailure, resumeFailure);
        }
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
            return new IllegalStateException("Local JDBC transaction stack is missing");
        }
        Transaction actual = stack.poll();
        if (actual != expected) {
            return new IllegalStateException("Local JDBC transaction stack is inconsistent");
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
     * @param action listener action
     * @param event event name used in diagnostics
     */
    private void notifyListeners(ListenerAction action, String event) {
        Throwable failure = null;
        for (TxLifeCycle listener : listeners) {
            try {
                action.accept(listener);
            } catch (RuntimeException | Error listenerFailure) {
                if (failure == null) {
                    failure = listenerFailure;
                } else {
                    failure.addSuppressed(listenerFailure);
                }
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof TxException txException) {
            throw txException;
        }
        if (failure != null) {
            throw new TxException("Local JDBC transaction " + event + " notification failed", failure);
        }
    }

    /**
     * Delivers a cleanup notification without replacing an existing failure.
     *
     * @param action listener action
     * @param event event name used in diagnostics
     * @param primaryFailure failure that initiated cleanup
     */
    private void notifyAfterFailure(ListenerAction action, String event, Throwable primaryFailure) {
        try {
            notifyListeners(action, event);
        } catch (RuntimeException | Error cleanupFailure) {
            suppress(primaryFailure, cleanupFailure);
        }
    }

    /**
     * Combines two failures while retaining their encounter order.
     *
     * @param primary first failure
     * @param secondary later failure
     * @return combined failure
     */
    private static Throwable merge(Throwable primary, Throwable secondary) {
        if (primary == null) {
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
     * Throws a structural cleanup failure after an otherwise successful completion.
     *
     * @param failure cleanup failure
     */
    private static void throwIfRemovalFailed(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
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
     * Validated states of one local transaction.
     */
    private enum TransactionState {
        ACTIVE,
        MARKED_ROLLBACK,
        SUSPENDED,
        COMPLETING,
        COMMITTED,
        ROLLED_BACK,
        FAILED
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
         * Records a confirmed commit.
         */
        private void committed() {
            transition(TransactionState.COMPLETING, TransactionState.COMMITTED, JdbcTransactionAction.COMMIT.text());
        }

        /**
         * Records a confirmed rollback.
         */
        private void rolledBack() {
            transition(TransactionState.COMPLETING,
                       TransactionState.ROLLED_BACK,
                       JdbcTransactionAction.ROLLBACK.text());
        }

        /**
         * Records a failed terminal lifecycle notification.
         */
        private void failed() {
            transition(TransactionState.COMPLETING, TransactionState.FAILED, "fail completion");
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
         * Performs one exact state transition.
         *
         * @param expected expected source state
         * @param next target state
         * @param operation operation description
         */
        private void transition(TransactionState expected, TransactionState next, String operation) {
            require(expected, operation);
            state = next;
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
            return new IllegalStateException("Cannot " + operation + " local JDBC transaction "
                                                     + identity + " while it is " + state);
        }
    }
}
