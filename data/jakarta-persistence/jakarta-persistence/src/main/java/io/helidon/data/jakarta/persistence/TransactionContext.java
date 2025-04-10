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
import io.helidon.transaction.TxLifeCycle;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

// ThreadLocal storage
final class TransactionContext {

    private static final System.Logger LOGGER = System.getLogger(TransactionContext.class.getName());
    private static final LocalContext INSTANCE = new LocalContext();

    private final Context context;
    private final JtaTransactionStorage jtaStorage;
    private final LocalTransactionStorage localStorage;


    private TransactionContext() {
        context = new Context();
        jtaStorage = new JtaTransactionStorage();
        localStorage = new LocalTransactionStorage();
    }

    static TransactionContext getInstance() {
        return INSTANCE.get();
    }

    // FIXME: transactionType shall be removed after resource local transactions redesign
    EntityManager entityManager(EntityManagerFactory factory, PersistenceUnitTransactionType transactionType) {
        return switch (transactionType) {
            case JTA -> jtaStorage.manager(factory, context);
            case RESOURCE_LOCAL -> localStorage.manager(factory, context);
        };
    }

    boolean isTransactionActive() {
        return context.isTxMethodRunning();
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

        // Transaction depth:
        //   - txDepth == 0 when no transaction is active
        //   - txDepth > 0 when transaction is active and txDepth contains number of transactions
        int txDepth() {
            return txDepth;
        }

        // When transaction depth is used as array index
        int txDepthIndex() {
            return txDepth - 1;
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
            // Transaction type must be retrieved before end method call
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
            switch (transactionContext.context().txType()) {
                case JTA -> transactionContext.jtaStorage()
                        .begin(transactionContext.context(), txIdentity);
                case RESOURCE_LOCAL -> transactionContext.localStorage()
                        .begin(transactionContext.context(), txIdentity);
            }
        }

        @Override
        public void commit(String txIdentity) {
            TransactionContext transactionContext = TransactionContext.getInstance();
            switch (transactionContext.context().txType()) {
                case JTA -> transactionContext.jtaStorage()
                        .commit(transactionContext.context(), txIdentity);
                case RESOURCE_LOCAL -> transactionContext.localStorage()
                        .commit(transactionContext.context(), txIdentity);
            }
            transactionContext.context().commit();
        }

        @Override
        public void rollback(String txIdentity) {
            TransactionContext transactionContext = TransactionContext.getInstance();
            switch (transactionContext.context().txType()) {
                case JTA -> transactionContext.jtaStorage()
                        .rollback(transactionContext.context(), txIdentity);
                case RESOURCE_LOCAL -> transactionContext.localStorage()
                        .rollback(transactionContext.context(), txIdentity);
            }
            transactionContext.context().rollback();
        }

        @Override
        public void suspend(String txIdentity) {
            TransactionContext transactionContext = TransactionContext.getInstance();
            switch (transactionContext.context().txType()) {
                case JTA -> transactionContext.jtaStorage()
                        .suspend(transactionContext.context(), txIdentity);
                case RESOURCE_LOCAL -> transactionContext.localStorage()
                        .suspend(transactionContext.context(), txIdentity);
            }
        }

        @Override
        public void resume(String txIdentity) {
            TransactionContext transactionContext = TransactionContext.getInstance();
            switch (transactionContext.context().txType()) {
                case JTA -> transactionContext.jtaStorage()
                        .resume(transactionContext.context(), txIdentity);
                case RESOURCE_LOCAL -> transactionContext.localStorage()
                        .resume(transactionContext.context(), txIdentity);
            }
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
