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

import java.util.ArrayList;
import java.util.List;

import io.helidon.data.DataException;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxException;
import io.helidon.transaction.TxLifeCycle;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

// ThreadLocal storage
final class TransactionContext {

    private static final System.Logger LOGGER = System.getLogger(TransactionContext.class.getName());
    private static final LocalContext INSTANCE = new LocalContext();

    @Deprecated
    private EntityManagerStorage storage;

    private final Context context;
    private final JtaTransactionStorage jtaStorage;
    private final LocalTransactionStorage localStorage;

    // Transaction method level (starts from 1)
    @Deprecated
    private int counter;

    private TransactionContext() {
        storage = null;
        context = new Context();
        jtaStorage = new JtaTransactionStorage();
        localStorage = new LocalTransactionStorage();
        counter = 0;
    }

    static TransactionContext getInstance() {
        return INSTANCE.get();
    }

    // Local API: Initialize transaction context and return transaction counter for close call
    // EntityManagerFactory instance is known
    int initTransaction(EntityManagerFactory factory,
                        @SuppressWarnings("deprecation") PersistenceUnitTransactionType transactionType) {
        // Initialize EntityManager and EntityTransaction with top level call
        if (counter == 0) {
            if (storage == null) {
                storage = new EntityManagerStorage(factory, transactionType);
            }
            // Validate entity manager for subsequent calls
        } else {
            if (storage == null) {
                throw new IllegalStateException("EntityManager context shall exist when transaction is active");
            }
        }
        return counter;
    }

    // Validate transaction counter and close transaction
    void closeTransaction(int counter) {
        if (this.counter != counter) {
            throw new IllegalStateException("Transaction life-cycle was not finished properly using commit or rollback");
        }
        if (counter == 0) {
            if (storage == null) {
                throw new IllegalStateException("EntityManager context does not exist while closing top level transaction");
            }
            storage.close();
        }
    }

    // Start transaction and add it to stack
    void beginTransaction(@SuppressWarnings("deprecation") PersistenceUnitTransactionType transactionType,
                          Tx.Type type) {
        counter++;
        switch (transactionType) {
        case PersistenceUnitTransactionType.JTA:
            if (counter == 1) {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE,
                               String.format("JTA manager added [%d]%n", counter));
                }
                storage.addCommonManager(counter, true, false);
            }
            storage.current().joinTransaction();
            break;
        case PersistenceUnitTransactionType.RESOURCE_LOCAL:
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE,
                           String.format("Local transaction begin [%d]%n", counter));
            }
            InitialState state = InitialState.get(counter == 1, storage.active());
            switch (type) {
            case MANDATORY:
                switch (state) {
                // Calling inside transaction context keeps current context
                case ACTIVE:
                    storage.addCurrentManager(counter);
                    break;
                // Calling outside transaction context throws TxException
                case FIRST:
                case INACTIVE:
                    throw new TxException(String.format(
                            "Running transaction of %s Tx.Type outside transaction scope",
                            type.name()));
                default: // Do nothing
                }
                break;
            case NEW:
                // Always starts new transaction
                storage.addCommonManager(counter, true, true);
                storage.transaction().begin();
                break;
            case NEVER:
                switch (state) {
                // Entry level starts without transaction
                case FIRST:
                    storage.addCommonManager(counter, false, false);
                    break;
                // Calling outside transaction context keeps current context
                case INACTIVE:
                    storage.addCurrentManager(counter);
                    break;
                // Calling inside transaction context throws TxException
                case ACTIVE:
                    throw new TxException(String.format(
                            "Running transaction of %s Tx.Type inside transaction scope",
                            type.name()));
                default: // Do nothing
                }
                break;
            case REQUIRED:
                switch (state) {
                // Calling outside transaction context starts new transaction
                case FIRST:
                case INACTIVE:
                    storage.addCommonManager(counter, true, true);
                    storage.transaction().begin();
                    break;
                // Calling inside transaction context keeps current context
                case ACTIVE:
                    storage.addCurrentManager(counter);
                    break;
                default: // Do nothing
                }
                break;
            case SUPPORTED:
                switch (state) {
                // Entry level starts without transaction
                case FIRST:
                    storage.addCommonManager(counter, false, false);
                    break;
                // Second and later level keeps current context
                case ACTIVE:
                case INACTIVE:
                    storage.addCurrentManager(counter);
                    break;
                default: // Do nothing
                }
                break;
            case UNSUPPORTED:
                switch (state) {
                // Entry level starts without transaction
                case FIRST:
                    storage.addCommonManager(counter, false, false);
                    break;
                // Calling inside transaction context suspends transaction
                // This step requires 2nd EntityManager instance until transaction context is resumed
                case ACTIVE:
                    storage.addSuspendedManager(counter);
                    break;
                // Calling outside transaction context keeps current context
                case INACTIVE:
                    storage.addCurrentManager(counter);
                    break;
                default: // Do nothing
                }
                break;
            default: // Do nothing
            }
            break;
        default: // Do nothing
        }
    }

    // Commit transaction and remove it from stack
    void commitTransaction(@SuppressWarnings("deprecation") PersistenceUnitTransactionType transactionType,
                           Tx.Type type) {
        switch (transactionType) {
        case PersistenceUnitTransactionType.JTA:
            if (counter == 0) {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE,
                               String.format("JTA manager removed [%d]%n", counter));
                }
                storage.removeManager(counter);
            }
            break;
        case PersistenceUnitTransactionType.RESOURCE_LOCAL:
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE,
                           String.format("Local transaction commit [%d]%n", counter));
            }
            RunningState state = RunningState.get(storage.newTx(), storage.active());
            switch (type) {
            case MANDATORY:
            case REQUIRED:
                switch (state) {
                // Commit new transaction on current level
                case NEW:
                    storage.transaction().commit();
                    storage.removeManager(counter);
                    break;
                // Keep reused transaction context unchanged
                case ACTIVE:
                    storage.removeManager(counter);
                    break;
                // This should not happen until internal state is damaged
                case INACTIVE:
                    throw new TxException(String.format(
                            "Running transaction of %s Tx.Type outside transaction scope",
                            type.name()));
                default: // Do nothing
                }
                break;
            case NEW:
                switch (state) {
                // Commit new transaction on current level
                case NEW:
                    storage.transaction().commit();
                    storage.removeManager(counter);
                    break;
                // This should not happen until internal state is damaged
                case ACTIVE:
                    throw new TxException(String.format(
                            "Running transaction of %s Tx.Type inside reused transaction scope",
                            type.name()));
                // This should not happen until internal state is damaged
                case INACTIVE:
                    throw new TxException(String.format(
                            "Running transaction of %s Tx.Type outside transaction scope",
                            type.name()));
                default: // Do nothing
                }
                break;
            case NEVER:
            case UNSUPPORTED:
                switch (state) {
                // This should not happen until internal state is damaged
                case NEW:
                    throw new TxException(String.format(
                            "Running transaction of %s Tx.Type inside new transaction scope",
                            type.name()));
                    // This should not happen until internal state is damaged
                case ACTIVE:
                    throw new TxException(String.format(
                            "Running transaction of %s Tx.Type inside transaction scope",
                            type.name()));
                    // Keep non transaction context unchanged
                case INACTIVE:
                    storage.removeManager(counter);
                    break;
                default: // Do nothing
                }
                break;
            case SUPPORTED:
                switch (state) {
                // This should not happen until internal state is damaged
                case NEW:
                    throw new TxException(String.format(
                            "Running transaction of %s Tx.Type inside new transaction scope",
                            type.name()));
                    // Keep current context unchanged
                case ACTIVE:
                case INACTIVE:
                    storage.removeManager(counter);
                    break;
                default: // Do nothing
                }
                break;
            default: // Do nothing
            }
            break;
        default: // Do nothing
        }
        counter--;
    }

    // Rollback transaction and remove it from stack
    void rollbackTransaction(@SuppressWarnings("deprecation") PersistenceUnitTransactionType transactionType,
                             Tx.Type type) {
        try {
            switch (transactionType) {
            case PersistenceUnitTransactionType.JTA:
                if (counter == 0) {
                    if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                        LOGGER.log(System.Logger.Level.TRACE,
                                   String.format("JTA manager removed [%d]%n", counter));
                    }
                    storage.removeManager(counter);
                }
                break;
            case PersistenceUnitTransactionType.RESOURCE_LOCAL:
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE,
                               String.format("Local transaction rollback [%d]%n", counter));
                }
                RunningState state = RunningState.get(storage.newTx(), storage.active());
                switch (type) {
                case MANDATORY:
                case REQUIRED:
                    switch (state) {
                    // Rollback new transaction on current level
                    case NEW:
                        storage.transaction().rollback();
                        storage.removeManager(counter);
                        break;
                    // Mark current transaction as rollback only
                    case ACTIVE:
                        storage.transaction().setRollbackOnly();
                        storage.removeManager(counter);
                        break;
                    // This should not happen until internal state is damaged
                    case INACTIVE:
                        throw new TxException(String.format(
                                "Running transaction of %s Tx.Type outside transaction scope",
                                type.name()));
                    default: // Do nothing
                    }
                    break;
                case NEW:
                    switch (state) {
                    // Rollback new transaction on current level
                    case NEW:
                        storage.transaction().commit();
                        storage.removeManager(counter);
                        break;
                    // This should not happen until internal state is damaged
                    case ACTIVE:
                        throw new TxException(String.format(
                                "Running transaction of %s Tx.Type inside reused transaction scope",
                                type.name()));
                        // This should not happen until internal state is damaged
                    case INACTIVE:
                        throw new TxException(String.format(
                                "Running transaction of %s Tx.Type outside transaction scope",
                                type.name()));
                    default: // Do nothing
                    }
                    break;
                case NEVER:
                case UNSUPPORTED:
                    switch (state) {
                    // This should not happen until internal state is damaged
                    case NEW:
                        throw new TxException(String.format(
                                "Running transaction of %s Tx.Type inside new transaction scope",
                                type.name()));
                        // This should not happen until internal state is damaged
                    case ACTIVE:
                        throw new TxException(String.format(
                                "Running transaction of %s Tx.Type inside transaction scope",
                                type.name()));
                        // Keep non transaction context unchanged
                    case INACTIVE:
                        storage.removeManager(counter);
                        break;
                    default: // Do nothing
                    }
                    break;
                case SUPPORTED:
                    switch (state) {
                    // This should not happen until internal state is damaged
                    case NEW:
                        throw new TxException(String.format(
                                "Running transaction of %s Tx.Type inside new transaction scope",
                                type.name()));
                        // Mark current transaction as rollback only
                    case ACTIVE:
                        storage.transaction().setRollbackOnly();
                        storage.removeManager(counter);
                        break;
                    // Keep non transaction context unchanged
                    case INACTIVE:
                        storage.removeManager(counter);
                        break;
                    default: // Do nothing
                    }
                    break;
                default: // Do nothing
                }
                break;
            default: // Do nothing
            }
            counter--;
        } catch (RuntimeException e) {
            counter--;
            throw e;
        }
    }

    // FIXME: transactionType shall be removed after resource local transactions redesign
    EntityManager entityManager(EntityManagerFactory factory, PersistenceUnitTransactionType transactionType) {
        return switch (transactionType) {
            case JTA -> jtaStorage.manager(factory, context);
            case RESOURCE_LOCAL -> storage.current();
        };
    }

    // Set transaction as rollback only if active
    void setRollbackOnlyTransaction() {
        storage.transaction().setRollbackOnly();
    }

    // FIXME: transactionType shall be removed after resource local transactions redesign
    boolean isTransactionActive(PersistenceUnitTransactionType transactionType) {
        return switch (transactionType) {
            case JTA -> context.isTxMethodRunning();
            case RESOURCE_LOCAL -> counter > 0;
        };
    }

    Context context() {
        return context;
    }

    JtaTransactionStorage jtaStorage() {
        return jtaStorage;
    }

    LocalTransactionStorage localStorage() {
        return localStorage;
    }

    // Used in begin method to simplify internal states dispatching
    enum InitialState {

        FIRST,    // Transaction method on entry level. Nothing was started so far.
        ACTIVE,   // Transaction method on second and later levels with transaction active
        INACTIVE; // Transaction method on second and later levels with transaction not active

        // Internal state depends on entry method level and whether transaction is active
        private static InitialState get(boolean first, boolean active) {
            return first
                    ? FIRST
                    : active ? ACTIVE : INACTIVE;
        }

    }

    // Used in commit/rollback methods to simplify internal states dispatching
    private enum RunningState {

        NEW,      // New transaction was started on current transaction method level
        ACTIVE,   // Current transaction method runs in already existing transaction context
        INACTIVE; // Current transaction method runs outside transaction context

        // Internal state depends on entry method level and whether transaction is active
        private static RunningState get(boolean newTx, boolean active) {
            return newTx
                    ? NEW
                    : active ? ACTIVE : INACTIVE;
        }

    }

    // Current thread context.
    // Contains counters to evaluate transaction methods being called and new transaction levels started.
    static final class Context {

        // Transaction methods depth counter.
        private int txMethodsDepth;
        // Transaction depth counter.
        private int txDepth;
        // Transaction depth when transaction was started (before new JTA transaction was eventually started)
        // This must be stack to keep values for individual method call levels.
        private final List<Integer> initialTxDepth;
        private PersistenceUnitTransactionType txType;

        Context() {
            txMethodsDepth = 0;
            txDepth = 0;
            initialTxDepth = new ArrayList<>();
            txType = null;
        }

        int txMethodsDepth() {
            return txMethodsDepth;
        }

        int txDepth() {
            return txDepth;
        }

        boolean isTxMethodRunning() {
            return txMethodsDepth > 0;
        }

        PersistenceUnitTransactionType txType() {
            // Sanity check, transaction mode is available between top level transaction method start and end calls.
            if (txMethodsDepth <= 0) {
                throw new IllegalStateException("Transaction mode requested outside transaction method execution");
            }
            return txType;
        }

        void start(PersistenceUnitTransactionType txType) {
            // Validate transaction mode. It shall not change while transaction methods are being executed.
            // Transaction mode change means bug int the code.
            if (txMethodsDepth == 0) {
                this.txType = txType;
            } else {
                if (this.txType != txType) {
                    throw new IllegalStateException(
                            String.format("Transaction mode changed from %s to %s while transaction is being executed",
                                          this.txType.name(),
                                          txType.name()));
                }
            }
            txMethodsDepth++;
            initialTxDepth.addLast(txDepth);
            // Initialize to current state before JTA starts transaction handling
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("%s transaction method marked as started on level %d [%d].",
                                         txType.name(),
                                         txMethodsDepth,
                                         Thread.currentThread().hashCode()));
            }
        }

        void end() {
            // Decrement counter first to make it always happen
            int methodsDepth = txMethodsDepth--;
            if (txMethodsDepth < 0) {
                throw new DataException("Closing non existent JTA transaction level");
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("%s transaction method marked as ended on level %d [%d].",
                                         txType.name(),
                                         methodsDepth,
                                         Thread.currentThread().hashCode()));
            }

            // Transaction depth sanity check before current method level ends.
            // If the check fails, there was no commit or rollback after transaction was started.
            // Log warning and fix transaction depth counter.
            int initialDepth = initialTxDepth.removeLast();
            if (initialDepth != txDepth) {
                if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                    LOGGER.log(System.Logger.Level.WARNING,
                               String.format("New transaction was started but never finished with commit or rollback [%d]",
                                             Thread.currentThread().hashCode()));
                }
                txDepth = initialDepth;
            }
        }

        void begin() {
            txDepth++;
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("New %s transaction begin on level %d:%d [%d].",
                                         txType.name(),
                                         txMethodsDepth,
                                         txDepth,
                                         Thread.currentThread().hashCode()));
            }
        }

        void commit() {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("New %s transaction commit on level %d:%d [%d].",
                                         txType.name(),
                                         txMethodsDepth,
                                         txDepth,
                                         Thread.currentThread().hashCode()));
            }
            txDepth--;
        }

        void rollback() {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("New %s transaction rollback on level %d:%d [%d].",
                                         txType.name(),
                                         txMethodsDepth,
                                         txDepth,
                                         Thread.currentThread().hashCode()));
            }
            txDepth--;
        }

   }

    /**
     * Process JTA API transaction life-cycle events.
     */
    @Service.Singleton
    static class JtaLifeCycle implements TxLifeCycle {

        @Override
        public void start(String type) {
            PersistenceUnitTransactionType txType = "jta".equalsIgnoreCase(type)
                    ? PersistenceUnitTransactionType.JTA
                    : PersistenceUnitTransactionType.RESOURCE_LOCAL;
            TransactionContext transactionContext = TransactionContext.getInstance();
            transactionContext.context()
                    .start(txType);
            if (transactionContext.context().txType() == PersistenceUnitTransactionType.RESOURCE_LOCAL) {
                transactionContext.localStorage.start();
            }
        }

        @Override
        public void end() {
            TransactionContext transactionContext = TransactionContext.getInstance();
            PersistenceUnitTransactionType txType = transactionContext.context().txType();
            transactionContext.context().end();
            switch (txType) {
                case JTA -> transactionContext.jtaStorage()
                        .end(transactionContext.context());
                case RESOURCE_LOCAL -> transactionContext.localStorage()
                        .end(transactionContext.context());
            }
        }

        @Override
        public void begin(String txIdentity) {
            TransactionContext transactionContext = TransactionContext.getInstance();
            transactionContext.context().begin();
        }

        @Override
        public void commit(String txIdentity) {
            TransactionContext transactionContext = TransactionContext.getInstance();
            if (transactionContext.context().txType() == PersistenceUnitTransactionType.RESOURCE_LOCAL) {
                transactionContext.localStorage.commit(transactionContext.context());
            }
            transactionContext.context().commit();
        }

        @Override
        public void rollback(String txIdentity) {
            TransactionContext transactionContext = TransactionContext.getInstance();
            if (transactionContext.context().txType() == PersistenceUnitTransactionType.RESOURCE_LOCAL) {
                transactionContext.localStorage.rollback(transactionContext.context());
            }
            transactionContext.context().rollback();
        }

    }

    // Override ThreadLocal to set initial value with no EntityManager
    private static final class LocalContext extends ThreadLocal<TransactionContext> {

        @Override
        public TransactionContext initialValue() {
            return new TransactionContext();
        }

    }

}
