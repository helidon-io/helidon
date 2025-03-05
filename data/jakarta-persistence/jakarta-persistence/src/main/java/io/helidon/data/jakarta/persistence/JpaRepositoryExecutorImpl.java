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

import io.helidon.data.api.DataException;
import io.helidon.data.jakarta.persistence.gapi.JpaRepositoryExecutor;
import io.helidon.function.ThrowingConsumer;
import io.helidon.function.ThrowingFunction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

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
            new JtaDataRunnerInTx(),
            PersistenceUnitTransactionType.RESOURCE_LOCAL,
            new ResourceLocalDataRunnerInTx());

    // Instance shared by all repository instances
    private final EntityManagerFactory factory;

    JpaRepositoryExecutorImpl(EntityManagerFactory factory) {
        this.factory = factory;
    }

    @Override
    public EntityManagerFactory factory() {
        return factory;
    }

    // Execute task with EntityManager instance and return result
    @Override
    public <R> R call(ThrowingFunction<EntityManager, R> task) {
        // Jakarta Persistence 3.2 compliant code disabled
        // PersistenceUnitTransactionType transactionType = factory.getTransactionType();
        // Jakarta Persistence 3.1 workaround
        PersistenceUnitTransactionType transactionType
                = (PersistenceUnitTransactionType) factory.getProperties().get(TRANSACTION_TYPE);
        if (LOGGER.isLoggable(Level.DEBUG)) {
            for (Map.Entry<String, Object> entry : factory.getProperties().entrySet()) {
                LOGGER.log(Level.DEBUG, String.format("Property %s :: %s", entry.getKey(), entry.getValue()));
            }
        }
        if (isTransactionActive()) {
            // The case when explicit transaction is active
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Running transaction task");
            }
            return TRANSACTION.get(transactionType)
                    .call(entityManager(), task);
        } else {
            // The case when explicit transaction is not active
            // Don't have access to thread life-cycle so EntityManager is closed after the task
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Running standalone task");
            }
            try (EntityManager em = factory.createEntityManager()) {
                return EXECUTORS.get(transactionType).call(em, task);
            }
        }
    }

    // Execute task with EntityManager instance
    public void run(ThrowingConsumer<EntityManager> task) {
        // Jakarta Persistence 3.2 compliant code disabled
        // PersistenceUnitTransactionType transactionType = factory.getTransactionType();
        // Jakarta Persistence 3.1 workaround
        PersistenceUnitTransactionType transactionType
                = (PersistenceUnitTransactionType) factory.getProperties().get(TRANSACTION_TYPE);
        if (isTransactionActive()) {
            // The case when explicit transaction is active
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Running transaction task");
            }
            TRANSACTION.get(transactionType)
                    .run(entityManager(), task);
        } else {
            // The case when explicit transaction is not active
            // Don't have access to thread life-cycle so EntityManager is closed after the task
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, "Running standalone task");
            }
            try (EntityManager em = factory.createEntityManager()) {
                EXECUTORS.get(transactionType).run(em, task);
            }
        }
    }

    private static void setRollbackOnlyTransaction() {
        if (isTransactionActive()) {
            TransactionContext.getInstance().setRollbackOnlyTransaction();
        }
    }

    private static boolean isTransactionActive() {
        return TransactionContext.getInstance().isTransactionActive();
    }

    private EntityManager entityManager() {
        return TransactionContext.getInstance().entityManager();
    }

    private static class JtaDataRunnerInTx implements DataRunner {
        private JtaDataRunnerInTx() {
        }

        @Override
        public <R> R call(EntityManager em, ThrowingFunction<EntityManager, R> task) {
            try {
                return task.apply(em);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new DataException("Execution of transaction task failed", e);
            }
        }

        @Override
        public void run(EntityManager em, ThrowingConsumer<EntityManager> task) {
            try {
                task.accept(em);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new DataException("Execution of transaction task failed", e);
            }
        }
    }

    private static class JtaDataRunner implements DataRunner {
        private JtaDataRunner() {
        }

        @Override
        public <R> R call(EntityManager em, ThrowingFunction<EntityManager, R> task) {
            em.joinTransaction();
            try {
                return task.apply(em);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new DataException("Execution of EntityManager task failed", e);
            }
        }

        @Override
        public void run(EntityManager em, ThrowingConsumer<EntityManager> task) {
            em.joinTransaction();
            try {
                task.accept(em);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new DataException("Execution of EntityManager task failed", e);
            }
        }
    }

    private static class ResourceLocalDataRunnerInTx implements DataRunner {
        private ResourceLocalDataRunnerInTx() {
        }

        @Override
        public <R> R call(EntityManager em, ThrowingFunction<EntityManager, R> task) {
            try {
                return task.apply(em);
            } catch (RuntimeException e) {
                setRollbackOnlyTransaction();
                throw e;
            } catch (Exception e) {
                setRollbackOnlyTransaction();
                throw new DataException("Execution of transaction task failed", e);
            }
        }

        @Override
        public void run(EntityManager em, ThrowingConsumer<EntityManager> task) {
            try {
                task.accept(em);
            } catch (RuntimeException e) {
                setRollbackOnlyTransaction();
                throw e;
            } catch (Exception e) {
                setRollbackOnlyTransaction();
                throw new DataException("Execution of transaction task failed", e);
            }
        }
    }

    private static class ResourceLocalDataRunner implements DataRunner {
        private ResourceLocalDataRunner() {
        }

        @Override
        public <R> R call(EntityManager em, ThrowingFunction<EntityManager, R> task) {
            EntityTransaction et = em.getTransaction();
            et.begin();
            try {
                R result = task.apply(em);
                et.commit();
                return result;
            } catch (RuntimeException e) {
                if (et.isActive()) {
                    et.rollback();
                }
                throw e;
            } catch (Exception e) {
                if (et.isActive()) {
                    et.rollback();
                }
                throw new DataException("Execution of EntityManager task failed", e);
            }
        }

        @Override
        public void run(EntityManager em, ThrowingConsumer<EntityManager> task) {
            EntityTransaction et = em.getTransaction();
            et.begin();
            try {
                task.accept(em);
                et.commit();
            } catch (RuntimeException e) {
                if (et.isActive()) {
                    et.rollback();
                }
                throw e;
            } catch (Exception e) {
                if (et.isActive()) {
                    et.rollback();
                }
                throw new DataException("Execution of EntityManager task failed", e);
            }
        }
    }
}
