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
package io.helidon.data.jakarta.persistence;

import java.util.List;
import java.util.concurrent.Callable;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.data.jakarta.persistence.LocalTransactionStorage.LocalTransaction;
import io.helidon.data.jakarta.persistence.LocalTransactionStorage.LocalTransactionManager;
import io.helidon.data.jakarta.persistence.LocalTransactionStorage.LocalTransactionProvider;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.TxLifeCycle;
import io.helidon.transaction.TxSupport;

/**
 * Resource local transaction handling support.
 * This is the resource local TxSupport service linking transaction API with {@link jakarta.persistence.EntityTransaction).
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class LocalTxSupport implements TxSupport {

    private static final System.Logger LOGGER = System.getLogger(LocalTxSupport.class.getName());

    private final LocalTransactionProvider provider;
    private final List<TxLifeCycle> txListeners;

    @Service.Inject
    LocalTxSupport(LocalTransactionProvider provider, List<TxLifeCycle> txListeners) {
        this.provider = provider;
        this.txListeners = txListeners;
    }

    @Override
    public String type() {
        return "resource-local";
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

    /**
     * Transaction handler for {@link Tx.Mandatory}.
     * <p>
     * If called outside a transaction context, the {@link io.helidon.transaction.TxException} must be thrown.
     * If called inside a transaction context, method execution will then continue under that context.
     */
    private <T> T txMandatory(Callable<T> task) throws TxException {
        if (!transactionManager().isTransactionActive()) {
            throw new TxException("Starting @Tx.Mandatory transaction outside transaction scope.");
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                       String.format("Starting @Tx.Mandatory with active resource local transaction [%d].",
                                     Thread.currentThread().hashCode()));
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
        LocalTransaction previous;
        if (transactionManager().isTransactionActive()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Starting @Tx.New with active resource local transaction [%d].",
                                         Thread.currentThread().hashCode()));
            }
            previous = suspend();
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Starting @Tx.New outside transaction scope [%d].",
                                         Thread.currentThread().hashCode()));
            }
            previous = null;
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
        if (transactionManager().isTransactionActive()) {
            throw new TxException("Starting @Tx.Never with active transaction.");
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Starting @Tx.Never outside transaction scope [%d].",
                                         Thread.currentThread().hashCode()));
            }
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
        if (transactionManager().isTransactionActive()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Starting @Tx.Required with active resource local transaction [%d].",
                                         Thread.currentThread().hashCode()));
            }
            return runInCurrentTxScope(transaction(), task);
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Starting @Tx.Required outside transaction scope [%d].",
                                         Thread.currentThread().hashCode()));
            }
            return runInNewTxScope(null, task);
        }
    }

    /**
     * Transaction handler for {@link Tx.Supported}.
     * <p>
     * If called outside a transaction context, method execution must then continue outside a transaction context.
     * If called inside a transaction context, the method execution must then continue inside this transaction context.
     */
    private <T> T txSupported(Callable<T> task) throws TxException {
        if (transactionManager().isTransactionActive()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Starting @Tx.Supported with active resource local transaction [%d].",
                                         Thread.currentThread().hashCode()));
            }
            return runInCurrentTxScope(transaction(), task);
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Starting @Tx.Supported outside transaction scope [%d].",
                                         Thread.currentThread().hashCode()));
            }
            return runOutsideTxScope(task);
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
        if (transactionManager().isTransactionActive()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Starting @Tx.Unsupported with active resource local transaction [%d].",
                                         Thread.currentThread().hashCode()));
            }
            return runInSuspendedTxScope(suspend(), task);
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Starting @Tx.Unsupported outside transaction scope [%d].",
                                         Thread.currentThread().hashCode()));
            }
            return runOutsideTxScope(task);
        }
    }

    private <T> T runOutsideTxScope(Callable<T> task) {
        try {
            return task.call();
        } catch (TxException e) {
            throw e;
        } catch (Exception e) {
            throw new TxException("Resource local transaction task failed.", e);
        }
    }

    private <T> T runInSuspendedTxScope(LocalTransaction suspended, Callable<T> task) {
        try {
            return task.call();
        } catch (TxException e) {
            throw e;
        } catch (Exception e) {
            throw new TxException("Resource local transaction task failed.", e);
        } finally {
            if (suspended != null) {
                resume(suspended);
            }
        }
    }

    private <T> T runInCurrentTxScope(LocalTransaction tx, Callable<T> task) {
        try {
            return task.call();
        } catch (TxException e) {
            rollbackOnly(tx);
            throw e;
        } catch (Exception e) {
            rollbackOnly(tx);
            throw new TxException("Resource local transaction task failed.", e);
        }
    }

    private <T> T runInNewTxScope(LocalTransaction previous, Callable<T> task) {
        LocalTransaction current = begin();
        T result;
        try {
            try {
                result = task.call();
            } catch (TxException e) {
                rollback(current);
                throw e;
            } catch (Exception e) {
                rollback(current);
                throw new TxException("Resource local transaction task failed.", e);
            }
            commit(current);
            return result;
        } finally {
            if (previous != null) {
                resume(previous);
            }
        }
    }

    private LocalTransactionManager transactionManager() {
        return provider.transactionManager();
    }

    private LocalTransaction transaction() {
        return transactionManager().getTransaction();
    }

    private LocalTransaction suspend() {
        LocalTransaction suspended = transactionManager().getTransaction();
        if (suspended == null) {
            throw new NullPointerException("No transaction instance is available.");
        }
        suspend(Integer.toString(System.identityHashCode(suspended)));
        return suspended;
    }

    private void resume(LocalTransaction tx) {
        resume(Integer.toString(System.identityHashCode(tx)));
    }

    // Transaction begin with listeners notification
    private LocalTransaction begin() {
        transactionManager().begin();
        LocalTransaction tx = transactionManager().getTransaction();
        begin(Integer.toString(System.identityHashCode(tx)));
        return tx;
    }

    // Transaction commit with listeners notification
    private void commit(LocalTransaction tx) {
        tx.commit();
        commit(Integer.toString(System.identityHashCode(tx)));
    }

    // Mark transaction as rollback only
    private void rollbackOnly(LocalTransaction tx) {
        tx.setRollbackOnly();
    }

    // Transaction rollback with listeners notification
    private void rollback(LocalTransaction tx) {
        tx.rollback();
        rollback(Integer.toString(System.identityHashCode(tx)));
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
