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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Map;

import io.helidon.common.Functions;
import io.helidon.data.DataException;
import io.helidon.data.EntityExistsException;
import io.helidon.data.EntityNotFoundException;
import io.helidon.data.NoResultException;
import io.helidon.data.NonUniqueResultException;
import io.helidon.data.OptimisticLockException;
import io.helidon.data.jakarta.persistence.gapi.JpaRepositoryExecutor;
import io.helidon.service.registry.Services;
import io.helidon.transaction.Tx;
import io.helidon.transaction.TxSupport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.SynchronizationType;

import static io.helidon.data.jakarta.persistence.JpaExtensionImpl.TRANSACTION_TYPE;

/**
 * Jakarta Persistence specific repository tasks executor.
 */
@SuppressWarnings("deprecation")
class JpaRepositoryExecutorImpl implements JpaRepositoryExecutor {

    private static final Logger LOGGER = System.getLogger(JpaRepositoryExecutorImpl.class.getName());

    // Execute persistence session task outside active transaction scope
    // Transaction handling depends on current PersistenceUnitTransactionType
    private static final Map<PersistenceUnitTransactionType, DataRunner> EXECUTORS = Map.of(
            PersistenceUnitTransactionType.JTA,
            new JtaDataRunner(),
            PersistenceUnitTransactionType.RESOURCE_LOCAL,
            new ResourceLocalDataRunner());
    // Execute persistence session task in active transaction scope
    private static final Map<PersistenceUnitTransactionType, DataRunner> TRANSACTION = Map.of(
            PersistenceUnitTransactionType.JTA,
            new DataRunnerInTx(PersistenceUnitTransactionType.JTA),
            PersistenceUnitTransactionType.RESOURCE_LOCAL,
            new DataRunnerInTx(PersistenceUnitTransactionType.RESOURCE_LOCAL));

    // Instance shared by all repository instances
    private final EntityManagerFactory factory;
    private final PersistenceUnitTransactionType transactionType;

    JpaRepositoryExecutorImpl(EntityManagerFactory factory) {
        this.factory = factory;
        if (factory.getProperties().containsKey(TRANSACTION_TYPE)) {
            this.transactionType = (PersistenceUnitTransactionType) factory.getProperties().get(TRANSACTION_TYPE);
        } else {
            throw new DataException(String.format("Missing %s property in EntityManagerFactory.", TRANSACTION_TYPE));
        }
    }

    @Override
    public EntityManagerFactory factory() {
        return factory;
    }

    // Execute task with EntityManager instance and return result
    @Override
    public <R, E extends Throwable> R call(Functions.CheckedFunction<EntityManager, R, E> task) {
        // Jakarta Persistence 3.2 compliant code disabled, using Jakarta Persistence 3.1 workaround
        if (LOGGER.isLoggable(Level.DEBUG)) {
            for (Map.Entry<String, Object> entry : factory.getProperties().entrySet()) {
                LOGGER.log(Level.DEBUG, String.format("Property %s :: %s", entry.getKey(), entry.getValue()));
            }
        }
        if (isTransactionActive()) {
            // The case when explicit transaction is active
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, String.format("Running transaction task [%x]", this.hashCode()));
            }
            return TRANSACTION.get(transactionType)
                    .call(this, task);
        } else {
            // The case when explicit transaction is not active
            // Don't have access to thread life-cycle so EntityManager is closed after the task
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, String.format("Running standalone task [%x]", this.hashCode()));
            }
            return EXECUTORS.get(transactionType)
                    .call(this, task);
        }
    }

    // Execute task with EntityManager instance
    @Override
    public <E extends Throwable> void run(Functions.CheckedConsumer<EntityManager, E> task) {
        // Jakarta Persistence 3.2 compliant code disabled, using Jakarta Persistence 3.1 workaround
        if (isTransactionActive()) {
            // The case when explicit transaction is active
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, String.format("Running transaction task [%x]", this.hashCode()));
            }
            TRANSACTION.get(transactionType)
                    .run(this, task);
        } else {
            // The case when explicit transaction is not active
            // Don't have access to thread life-cycle so EntityManager is closed after the task
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, String.format("Running standalone task [%x]", this.hashCode()));
            }
            EXECUTORS.get(transactionType)
                    .run(this, task);
        }
    }

    // Get EntityManager outside transaction scope
    // In JTA environment this EntityManager must be synchronized manually with new transaction started in executor
    private EntityManager getNonTxManager() {
        return transactionType == PersistenceUnitTransactionType.RESOURCE_LOCAL
                ? factory.createEntityManager()
                : factory.createEntityManager(SynchronizationType.UNSYNCHRONIZED);
    }

    // Get EntityManager inside transaction scope
    // In JTA environment this EntityManager is already synchronized with current transaction
    private EntityManager entityManager() {
        return TransactionContext.getInstance().entityManager(factory, transactionType);
    }

    // JTA provider shall be present when JTA transaction type is active.
    private boolean isTransactionActive() {
        return TransactionContext.getInstance().isTransactionActive();
    }

    // Common task runner with PersistenceException mapping
    private abstract static class AbstractDataRunner implements DataRunner {

        private AbstractDataRunner() {
        }

        public <R, E extends Throwable> R call(JpaRepositoryExecutor executor,
                                               EntityManager em,
                                               Functions.CheckedFunction<EntityManager, R, E> task) {
            try {
                return task.apply(em);
            } catch (PersistenceException e) {
                handlePersistenceException(e);
                // Unreachable code
                throw new IllegalStateException("Unhandled PersistenceException: " + e.getClass().getName());
            } catch (Throwable e) {
                throw new DataException("Execution of persistence session task failed.", e);
            }
        }

        public <E extends Throwable> void run(JpaRepositoryExecutor executor,
                                              EntityManager em,
                                              Functions.CheckedConsumer<EntityManager, E> task) {
            try {
                task.accept(em);
            } catch (PersistenceException e) {
                handlePersistenceException(e);
                // Unreachable code
                throw new IllegalStateException("Unhandled PersistenceException: " + e.getClass().getName());
            } catch (Throwable e) {
                throw new DataException("Execution of persistence session task failed.", e);
            }
        }

        // PersistenceException mapping
        private static void handlePersistenceException(PersistenceException pe) throws DataException {
            switch (pe) {
                case jakarta.persistence.EntityExistsException ex:
                    throw new EntityExistsException("Entity already exists.", ex);
                case jakarta.persistence.EntityNotFoundException ex:
                    throw new EntityNotFoundException("Entity was not found.", ex);
                case jakarta.persistence.NoResultException ex:
                    throw new NoResultException("Query returned no result.", ex);
                case jakarta.persistence.NonUniqueResultException ex:
                    throw new NonUniqueResultException("Query returned multiple result.", ex);
                case jakarta.persistence.OptimisticLockException ex:
                    throw new OptimisticLockException("Optimistic locking conflict.", ex);
                default:
                    throw new DataException("Persistence session task failed.", pe);
            }
        }
    }

    // Task runner with JTA transaction already in progress
    private static final class DataRunnerInTx extends AbstractDataRunner {

        private final PersistenceUnitTransactionType txType;

        DataRunnerInTx(PersistenceUnitTransactionType txType) {
            this.txType = txType;
        }

        @Override
        public <R, E extends Throwable> R call(JpaRepositoryExecutor executor,
                                               Functions.CheckedFunction<EntityManager, R, E> task) {
            EntityManager em = TransactionContext.getInstance()
                    .entityManager(executor.factory(), txType);
            return call(executor, em, task);
        }

        @Override
        public <E extends Throwable> void run(JpaRepositoryExecutor executor, Functions.CheckedConsumer<EntityManager, E> task) {
            EntityManager em = TransactionContext.getInstance()
                    .entityManager(executor.factory(), txType);
            run(executor, em, task);
        }

    }

    // Task runner with new JTA transaction. Delegates transaction handling to JtaTxSupport.
    private static final class JtaDataRunner extends AbstractDataRunner {

        private final TxSupport txSupport;

        JtaDataRunner() {
            super();
            txSupport = initJtaTxSupport();
        }

        @Override
        public <R, E extends Throwable> R call(JpaRepositoryExecutor executor,
                                               Functions.CheckedFunction<EntityManager, R, E> task) {
            checkTxSupport();
            return txSupport.transaction(Tx.Type.REQUIRED, () -> {
                EntityManager em = TransactionContext.getInstance()
                        .entityManager(executor.factory(), PersistenceUnitTransactionType.JTA);
                em.joinTransaction();
                return call(executor, em, task);
            });
       }

        @Override
        public <E extends Throwable> void run(JpaRepositoryExecutor executor,
                                              Functions.CheckedConsumer<EntityManager, E> task) {
            checkTxSupport();
            txSupport.transaction(Tx.Type.REQUIRED, () -> {
                EntityManager em = TransactionContext.getInstance()
                        .entityManager(executor.factory(), PersistenceUnitTransactionType.JTA);
                em.joinTransaction();
                run(executor, em, task);
                return null;
            });
        }

        // PERF: This check is being run with every task execution but doing it in constructor will crash
        //       the whole application with RESOURCE_LOCAL TxSupport
        // This must be done better after RESOURCE_LOCAL code is rewritten.
        private void checkTxSupport() {
            if (!"jta".equalsIgnoreCase(txSupport.type())) {
                throw new DataException(String.format("Cannot handle JTA transaction with %s TxSupport.",
                                                      txSupport.type()));
            }
        }

        private static TxSupport initJtaTxSupport() {
            return Services.first(TxSupport.class).orElse(null);
        }

    }

    // Task runner with new local transaction
    private static class ResourceLocalDataRunner extends AbstractDataRunner {

        private final TxSupport txSupport;

        ResourceLocalDataRunner() {
            super();
            txSupport = initJtaTxSupport();
        }

        @Override
        public <R, E extends Throwable> R call(JpaRepositoryExecutor executor,
                                               Functions.CheckedFunction<EntityManager, R, E> task) {
            checkTxSupport();
            return txSupport.transaction(Tx.Type.REQUIRED, () -> {
                EntityManager em = TransactionContext.getInstance()
                        .entityManager(executor.factory(), PersistenceUnitTransactionType.RESOURCE_LOCAL);
                return call(executor, em, task);
            });
        }

        @Override
        public <E extends Throwable> void run(JpaRepositoryExecutor executor,
                                              Functions.CheckedConsumer<EntityManager, E> task) {
            checkTxSupport();
            txSupport.transaction(Tx.Type.REQUIRED, () -> {
                EntityManager em = TransactionContext.getInstance()
                        .entityManager(executor.factory(), PersistenceUnitTransactionType.RESOURCE_LOCAL);
                run(executor, em, task);
                return null;
            });
        }

        // PERF: This check is being run with every task execution but doing it in constructor will crash
        //       the whole application with RESOURCE_LOCAL TxSupport
        // This must be done better after RESOURCE_LOCAL code is rewritten.
        private void checkTxSupport() {
            if (!"resource-local".equalsIgnoreCase(txSupport.type())) {
                throw new DataException(String.format("Cannot handle JTA transaction with %s TxSupport.",
                                                      txSupport.type()));
            }
        }

        private static TxSupport initJtaTxSupport() {
            return Services.first(TxSupport.class).orElse(null);
        }

    }

}
