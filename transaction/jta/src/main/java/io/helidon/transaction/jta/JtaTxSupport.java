/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.transaction.jta;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.concurrent.Callable;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.spi.TxLifeCycle;
import io.helidon.transaction.spi.TxSupport;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

/**
 * JTA transaction handling support.
 * This is the default TxSupport service linking transaction API with JTA.
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT)
class JtaTxSupport implements TxSupport {

    private static final System.Logger LOGGER = System.getLogger(JtaTxSupport.class.getName());

    private final JtaProvider provider;
    private final List<TxLifeCycle> txListeners;

    @Service.Inject
    JtaTxSupport(JtaProvider provider, List<TxLifeCycle> txListeners) {
        this.provider = provider;
        this.txListeners = txListeners;
    }

    @Override
    public String type() {
        return "jta";
    }

    @Override
    public <T> T transaction(Tx.Type type, Callable<T> task) {
        start();
        try {
            return switch (type) {
                case MANDATORY -> txMandatory(task);
                case NEW -> txNew(task);
                case NEVER -> txNever(task);
                case REQUIRED -> txRequired(task);
                case SUPPORTED -> txSupported(task);
                case UNSUPPORTED -> txUnsupported(task);
            };
        // end() must always happen after start
        } finally {
            end();
        }
    }

/* Removed: need to decide whether this is needed at all

    @Override
    public <T> T transaction(Tx.Type type, Function<Tx.Transaction, T> task) {
        return null;
    }

    @Override
    public Tx.Transaction transaction(Tx.Type type) {
        return null;
    }

*/

    /**
     * Transaction handler for {@link Tx.Mandatory}.
     * <p>
     * If called outside a transaction context, the {@link TxException} must be thrown.
     * If called inside a transaction context, method execution will then continue under that context.
     */
    private <T> T txMandatory(Callable<T> task) throws TxException {
        switch (txState()) {
            case Status.STATUS_ACTIVE:
                // Just continue with active transaction
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.Mandatory with active transaction [%d].",
                                             Thread.currentThread().hashCode()));
                }
                break;
            case Status.STATUS_MARKED_ROLLBACK:
                // This will cause some exception sooner or later so we may log warning because user code is probably wrong.
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               String.format("Starting @Tx.Mandatory with transaction marked for rollback [%d].",
                                             Thread.currentThread().hashCode()));
                }
                break;
            case Status.STATUS_PREPARING:
                throw new TxException("Starting @Tx.Mandatory with transaction being prepared for commit.");
            case Status.STATUS_PREPARED:
                throw new TxException("Starting @Tx.Mandatory with transaction already prepared for commit.");
            case Status.STATUS_COMMITTED:
                throw new TxException("Starting @Tx.Mandatory with transaction already commited.");
            case Status.STATUS_COMMITTING:
                throw new TxException("Starting @Tx.Mandatory with transaction already being commited.");
            case Status.STATUS_ROLLEDBACK:
                throw new TxException("Starting @Tx.Mandatory with transaction rolled back.");
            case Status.STATUS_ROLLING_BACK:
                throw new TxException("Starting @Tx.Mandatory with transaction being rolled back.");
            case Status.STATUS_NO_TRANSACTION:
                throw new TxException("Starting @Tx.Mandatory transaction outside transaction scope.");
            default:
                throw new TxException(String.format("Unknown Jakarta Transactions state code %d.", txState()));
        }
        return runInCurrentTxScope(transaction(), task);
    }

    /**
     * Transaction handler for {@link Tx.New}.
     * <p>
     * If called outside a transaction context, the interceptor must begin a new transaction. The managed bean
     * method execution must then continue inside this transaction context, and the transaction must be completed
     * by the interceptor.
     * If called inside a transaction context, the current transaction context must be suspended. New transaction
     * will begin, the managed method execution must then continue inside this transaction context. The transaction
     * must be completed, and the previously suspended transaction must be resumed.
     */
    private <T> T txNew(Callable<T> task) throws TxException {

        Transaction previous;
        switch (txState()) {
            case Status.STATUS_ACTIVE:
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.New with active transaction [%d].",
                                             Thread.currentThread().hashCode()));
                }
                previous = suspend(true);
                break;
            case Status.STATUS_NO_TRANSACTION:
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.New outside transaction scope [%d].",
                                             Thread.currentThread().hashCode()));
                }
                previous = null;
                break;
            case Status.STATUS_MARKED_ROLLBACK:
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               String.format("Starting @Tx.New with transaction marked for rollback [%d].",
                                             Thread.currentThread().hashCode()));
                }
                previous = suspend(true);
                break;
            case Status.STATUS_PREPARING:
                throw new TxException("Starting @Tx.New with transaction being prepared for commit.");
            case Status.STATUS_PREPARED:
                throw new TxException("Starting @Tx.New with transaction already prepared for commit.");
            case Status.STATUS_COMMITTED:
                throw new TxException("Starting @Tx.New with transaction already commited.");
            case Status.STATUS_COMMITTING:
                throw new TxException("Starting @Tx.New with transaction already being commited.");
            case Status.STATUS_ROLLEDBACK:
                throw new TxException("Starting @Tx.New with transaction rolled back.");
            case Status.STATUS_ROLLING_BACK:
                throw new TxException("Starting @Tx.New with transaction being rolled back.");
            default:
                throw new TxException(String.format("Unknown Jakarta Transactions state code %d.", txState()));
        }
        return runInNewTxScope(previous, task);
    }

    /**
     * Transaction handler for {@link Tx.Never}.
     * <p>
     * If called outside a transaction context, method execution must then continue outside a transaction context.
     * If called inside a transaction context, the {@link TxException} must be thrown.
     */
    private <T> T txNever(Callable<T> task) throws TxException {
        switch (txState()) {
            case Status.STATUS_ACTIVE:
                throw new TxException("Starting @Tx.Never with active transaction.");
            case Status.STATUS_NO_TRANSACTION:
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.Never outside transaction scope [%d].",
                                             Thread.currentThread().hashCode()));
                }
                break;
            case Status.STATUS_MARKED_ROLLBACK:
                throw new TxException("Starting @Tx.Never with transaction marked for rollback.");
            case Status.STATUS_PREPARING:
                throw new TxException("Starting @Tx.Never with transaction being prepared for commit.");
            case Status.STATUS_PREPARED:
                throw new TxException("Starting @Tx.Never with transaction already prepared for commit.");
            case Status.STATUS_COMMITTED:
                throw new TxException("Starting @Tx.Never with transaction already commited.");
            case Status.STATUS_COMMITTING:
                throw new TxException("Starting @Tx.Never with transaction already being commited.");
            case Status.STATUS_ROLLEDBACK:
                throw new TxException("Starting @Tx.Never with transaction rolled back.");
            case Status.STATUS_ROLLING_BACK:
                throw new TxException("Starting @Tx.Never with transaction being rolled back.");
            default:
                throw new TxException(String.format("Unknown Jakarta Transactions state code %d.", txState()));
        }
        return runOutsideTxScope(task);
    }

    /**
     * Transaction handler for {@link Tx.Required}.
     * <p>
     * If called outside a transaction context, the interceptor must begin a new transaction. The managed bean
     * method execution must then continue inside this transaction context, and the transaction must be completed
     * by the interceptor.
     * If called inside a transaction context, the method execution must then continue inside this transaction context.
     */
    private <T> T txRequired(Callable<T> task) throws TxException {
        switch (txState()) {
            case Status.STATUS_ACTIVE:
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.Required with active transaction [%d].",
                                             Thread.currentThread().hashCode()));
                }
                return runInCurrentTxScope(transaction(), task);
            case Status.STATUS_NO_TRANSACTION:
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.Required outside transaction scope [%d].",
                                             Thread.currentThread().hashCode()));
                }
                return runInNewTxScope(null, task);
            case Status.STATUS_MARKED_ROLLBACK:
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               String.format("Starting @Tx.Required with transaction marked for rollback [%d].",
                                             Thread.currentThread().hashCode()));
                }
                return runInCurrentTxScope(transaction(), task);
            case Status.STATUS_PREPARING:
                throw new TxException("Starting @Tx.Required with transaction being prepared for commit.");
            case Status.STATUS_PREPARED:
                throw new TxException("Starting @Tx.Required with transaction already prepared for commit.");
            case Status.STATUS_COMMITTED:
                throw new TxException("Starting @Tx.Required with transaction already commited.");
            case Status.STATUS_COMMITTING:
                throw new TxException("Starting @Tx.Required with transaction already being commited.");
            case Status.STATUS_ROLLEDBACK:
                throw new TxException("Starting @Tx.Required with transaction rolled back.");
            case Status.STATUS_ROLLING_BACK:
                throw new TxException("Starting @Tx.Required with transaction being rolled back.");
            default:
                throw new TxException(String.format("Unknown Jakarta Transactions state code %d.", txState()));
        }

    }

    /**
     * Transaction handler for {@link Tx.Supported}.
     * <p>
     * If called outside a transaction context, method execution must then continue outside a transaction context.
     * If called inside a transaction context, the method execution must then continue inside this transaction context.
     */
    private <T> T txSupported(Callable<T> task) throws TxException {
        switch (txState()) {
            case Status.STATUS_ACTIVE:
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.Supported with active transaction [%d].",
                                             Thread.currentThread().hashCode()));
                }
                return runInCurrentTxScope(transaction(), task);
            case Status.STATUS_NO_TRANSACTION:
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.Supported outside transaction scope [%d].",
                                             Thread.currentThread().hashCode()));
                }
                return runOutsideTxScope(task);
            case Status.STATUS_MARKED_ROLLBACK:
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               String.format("Starting @Tx.Supported with transaction marked for rollback [%d].",
                                             Thread.currentThread().hashCode()));
                }
                return runInCurrentTxScope(transaction(), task);
            case Status.STATUS_PREPARING:
                throw new TxException("Starting @Tx.Supported with transaction being prepared for commit.");
            case Status.STATUS_PREPARED:
                throw new TxException("Starting @Tx.Supported with transaction already prepared for commit.");
            case Status.STATUS_COMMITTED:
                throw new TxException("Starting @Tx.Supported with transaction already commited.");
            case Status.STATUS_COMMITTING:
                throw new TxException("Starting @Tx.Supported with transaction already being commited.");
            case Status.STATUS_ROLLEDBACK:
                throw new TxException("Starting @Tx.Supported with transaction rolled back.");
            case Status.STATUS_ROLLING_BACK:
                throw new TxException("Starting @Tx.Supported with transaction being rolled back.");
            default:
                throw new TxException(String.format("Unknown Jakarta Transactions state code %d.", txState()));
        }
    }

    /**
     * Transaction handler for {@link Tx.Unsupported}.
     * <p>
     * If called outside a transaction context, method execution must then continue outside a transaction context.
     * If called inside a transaction context, the current transaction context must be suspended. The method execution
     * must then continue outside a transaction context, and the previously suspended transaction must be resumed
     * by the interceptor that suspended it after the method execution has completed.
     */
    private <T> T txUnsupported(Callable<T> task) throws TxException {
        switch (txState()) {
            case Status.STATUS_ACTIVE:
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.Unsupported with active transaction [%d].",
                                             Thread.currentThread().hashCode()));
                }
                return runInSuspendedTxScope(suspend(true), task);
            case Status.STATUS_MARKED_ROLLBACK:
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.Unsupported outside transaction scope [%d].",
                                             Thread.currentThread().hashCode()));
                }
                return runInSuspendedTxScope(suspend(true), task);
            // This seems to be valid use-case, do not log warning
            case Status.STATUS_NO_TRANSACTION:
                if (LOGGER.isLoggable(Level.DEBUG)) {
                    LOGGER.log(Level.DEBUG,
                               String.format("Starting @Tx.Unsupported with transaction marked for rollback [%d].",
                                             Thread.currentThread().hashCode()));
                }
                return runOutsideTxScope(task);
            // NEXT VERSION: Lukas? Maybe we can run the task even in those states. It should work.
            case Status.STATUS_PREPARING:
                throw new TxException("Starting @Tx.Unsupported with transaction being prepared for commit.");
            case Status.STATUS_PREPARED:
                throw new TxException("Starting @Tx.Unsupported with transaction already prepared for commit.");
            case Status.STATUS_COMMITTED:
                throw new TxException("Starting @Tx.Unsupported with transaction already commited.");
            case Status.STATUS_COMMITTING:
                throw new TxException("Starting @Tx.Unsupported with transaction already being commited.");
            case Status.STATUS_ROLLEDBACK:
                throw new TxException("Starting @Tx.Unsupported with transaction rolled back.");
            case Status.STATUS_ROLLING_BACK:
                throw new TxException("Starting @Tx.Unsupported with transaction being rolled back.");
            default:
                throw new TxException(String.format("Unknown Jakarta Transactions state code %d.", txState()));
        }
    }

    private <T> T runOutsideTxScope(Callable<T> task) {
        try {
            return task.call();
        } catch (TxException e) {
            throw e;
        } catch (Exception e) {
            throw new TxException("Transaction task failed.", e);
        }
    }

    private <T> T runInSuspendedTxScope(Transaction suspended, Callable<T> task) {
        try {
            return task.call();
        } catch (TxException e) {
            throw e;
        } catch (Exception e) {
            throw new TxException("Transaction task failed.", e);
        } finally {
            if (suspended != null) {
                resume(suspended, true);
            }
        }
    }

    private <T> T runInCurrentTxScope(Transaction tx, Callable<T> task) {
        try {
            return task.call();
        } catch (TxException e) {
            rollbackOnly(tx);
            throw e;
        } catch (Exception e) {
            rollbackOnly(tx);
            throw new TxException("Transaction task failed.", e);
        }
    }

    private <T> T runInNewTxScope(Transaction previous, Callable<T> task) {
        Transaction current = begin();
        T result;
        try {
            try {
                result = task.call();
            } catch (TxException e) {
                rollback(current);
                throw e;
            } catch (Exception e) {
                rollback(current);
                throw new TxException("Transaction task failed.", e);
            }
            commit(current);
            return result;
        } finally {
            try {
                suspend(false);
            } finally {
                if (previous != null) {
                    resume(previous, true);
                }
            }
        }
    }

    private int txState() {
        try {
            // Used only when JtaProvider is present
            int txStatus = transactionManager().getStatus();
            // According to javadoc of STATUS_UNKNOWN:
            // A transaction is associated with the target object but its current status cannot be determined.
            // This is a transient condition and a subsequent invocation will ultimately return a different status.
            if (txStatus == Status.STATUS_UNKNOWN) {
                txStatus = transactionManager().getStatus();
            }
            return txStatus;
        } catch (SystemException e) {
            throw new TxException("Transaction status check failed", e);
        }
    }

    private TransactionManager transactionManager() {
        return provider.transactionManager();
    }

    private Transaction transaction() {
        try {
            return transactionManager().getTransaction();
        } catch (SystemException e) {
            throw new TxException("Transaction retrieval failed", e);
        }
    }

    // boolean propagate - whether to propagate to event listeners
    private Transaction suspend(boolean propagate) {
        try {
            Transaction suspended = transactionManager().suspend();
            if (suspended == null) {
                throw new NullPointerException("No transaction instance is available.");
            }
            if (propagate) {
                suspend(Integer.toString(System.identityHashCode(suspended)));
            }
            return suspended;
        } catch (SystemException e) {
            throw new TxException("Transaction suspend failed", e);
        }
    }

    // boolean propagate - whether to propagate to event listeners
    private void resume(Transaction tx, boolean propagate) {
        try {
            transactionManager().resume(tx);
        } catch (SystemException | InvalidTransactionException e) {
            throw new TxException("Transaction resume failed", e);
        } finally {
            if (propagate) {
                resume(Integer.toString(System.identityHashCode(tx)));
            }
        }
    }

    // Transaction begin with listeners notification
    private Transaction begin() {
        try {
            transactionManager().begin();
            Transaction tx = transactionManager().getTransaction();
            begin(Integer.toString(System.identityHashCode(tx)));
            return tx;
        } catch (SystemException | NotSupportedException e) {
            throw new TxException("Transaction begin failed", e);
        }
    }

    // Transaction commit with listeners notification
    private void commit(Transaction tx) {
        try {
            tx.commit();
        } catch (HeuristicRollbackException | SystemException | HeuristicMixedException | RollbackException e) {
            throw new TxException("Transaction commit failed", e);
        } finally {
            commit(Integer.toString(System.identityHashCode(tx)));
        }
    }

    // Mark transaction as rollback only
    private void rollbackOnly(Transaction tx) {
        try {
            tx.setRollbackOnly();
        } catch (SystemException e) {
            throw new TxException("Setting transaction as rollback only failed", e);
        }
    }

    // Transaction rollback with listeners notification
    private void rollback(Transaction tx) {
        try {
            tx.rollback();
        } catch (SystemException e) {
            throw new TxException("Transaction rollback failed", e);
        } finally {
            rollback(Integer.toString(System.identityHashCode(tx)));
        }
    }

    // Notify TxLifeCycle listeners about transaction method start
    private void start() {
        txListeners.forEach(listener -> listener.start(this.type()));
    }

    // Notify TxLifeCycle listeners about transaction method end
    private void end() {
        txListeners.forEach(TxLifeCycle::end);
    }

    // Notify TxLifeCycle listeners about transaction begin call
    private void begin(String txIdentity) {
        txListeners.forEach(l -> l.begin(txIdentity));
    }

    // Notify TxLifeCycle listeners about transaction commit call
    private void commit(String txIdentity) {
        txListeners.forEach(l -> l.commit(txIdentity));
    }

    // Notify TxLifeCycle listeners about transaction rollback call
    private void rollback(String txIdentity) {
        txListeners.forEach(l -> l.rollback(txIdentity));
    }

    // Notify TxLifeCycle listeners about transaction being suspended
    private void suspend(String txIdentity) {
        txListeners.forEach(l -> l.suspend(txIdentity));
    }

    // Notify TxLifeCycle listeners about transaction being resumed
    private void resume(String txIdentity) {
        txListeners.forEach(l -> l.resume(txIdentity));
    }

}
