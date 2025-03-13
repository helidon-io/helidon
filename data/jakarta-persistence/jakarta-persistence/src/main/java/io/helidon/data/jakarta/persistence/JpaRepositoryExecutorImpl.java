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

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;

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
    public <R, E extends Throwable> R call(Functions.CheckedFunction<EntityManager, R, E> task) {
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
                LOGGER.log(Level.DEBUG, String.format("Running transaction task [%x]", this.hashCode()));
            }
            return TRANSACTION.get(transactionType)
                    .call(entityManager(), task);
        } else {
            // The case when explicit transaction is not active
            // Don't have access to thread life-cycle so EntityManager is closed after the task
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, String.format("Running standalone task [%x]", this.hashCode()));
            }
            try (EntityManager em = factory.createEntityManager()) {
                return EXECUTORS.get(transactionType).call(em, task);
            }
        }
    }

    // Execute task with EntityManager instance
    @Override
    public <E extends Throwable> void run(Functions.CheckedConsumer<EntityManager, E> task) {
        // Jakarta Persistence 3.2 compliant code disabled
        // PersistenceUnitTransactionType transactionType = factory.getTransactionType();
        // Jakarta Persistence 3.1 workaround
        PersistenceUnitTransactionType transactionType
                = (PersistenceUnitTransactionType) factory.getProperties().get(TRANSACTION_TYPE);
        if (isTransactionActive()) {
            // The case when explicit transaction is active
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, String.format("Running transaction task [%x]", this.hashCode()));
            }
            TRANSACTION.get(transactionType)
                    .run(entityManager(), task);
        } else {
            // The case when explicit transaction is not active
            // Don't have access to thread life-cycle so EntityManager is closed after the task
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, String.format("Running standalone task [%x]", this.hashCode()));
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

    // Common task runner with PersistenceException mapping
    private abstract static class AbstractDataRunner implements DataRunner {

        private AbstractDataRunner() {
        }

        @Override
        public <R, E extends Throwable> R call(EntityManager em, Functions.CheckedFunction<EntityManager, R, E> task)
                throws DataException {
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

        @Override
        public <E extends Throwable> void run(EntityManager em, Functions.CheckedConsumer<EntityManager, E> task)
                throws DataException {
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
    private static final class JtaDataRunnerInTx extends AbstractDataRunner {
    }

    // Task runner with new JTA transaction
    private static final class JtaDataRunner extends AbstractDataRunner {

        @Override
        public <R, E extends Throwable> R call(EntityManager em, Functions.CheckedFunction<EntityManager, R, E> task) {
            em.joinTransaction();
            return super.call(em, task);
        }

        @Override
        public <E extends Throwable> void run(EntityManager em, Functions.CheckedConsumer<EntityManager, E> task) {
            em.joinTransaction();
            super.run(em, task);
        }
    }

    // Task runner with local transaction already in progress
    private static class ResourceLocalDataRunnerInTx extends AbstractDataRunner {

        @Override
        public <R, E extends Throwable> R call(EntityManager em, Functions.CheckedFunction<EntityManager, R, E> task) {
            try {
                return super.call(em, task);
                // Any exception in the task shall be wrapped by DataException
            } catch (DataException e) {
                setRollbackOnlyTransaction();
                throw e;
                // This shall never be thrown, it means bug in the exception handling code
            } catch (RuntimeException e) {
                setRollbackOnlyTransaction();
                LOGGER.log(Level.WARNING, "Unhandled exception thrown in data repository task.", e);
                throw e;
            }
        }

        @Override
        public <E extends Throwable> void run(EntityManager em, Functions.CheckedConsumer<EntityManager, E> task) {
            try {
                super.run(em, task);
                // Any exception in the task shall be wrapped by DataException
            } catch (DataException e) {
                setRollbackOnlyTransaction();
                throw e;
                // This shall never be thrown, it means bug in the exception handling code
            } catch (RuntimeException e) {
                setRollbackOnlyTransaction();
                LOGGER.log(Level.WARNING, "Unhandled exception thrown in data repository task.", e);
                throw e;
            }
        }
    }

    // Task runner with new local transaction
    private static class ResourceLocalDataRunner extends AbstractDataRunner {

        @Override
        public <R, E extends Throwable> R call(EntityManager em, Functions.CheckedFunction<EntityManager, R, E> task) {
            EntityTransaction et = em.getTransaction();
            et.begin();
            try {
                R result = super.call(em, task);
                et.commit();
                return result;
                // Any exception in the task shall be wrapped by DataException
            } catch (DataException e) {
                if (et.isActive()) {
                    et.rollback();
                }
                throw e;
                // This shall never be thrown, it means bug in the exception handling code
            } catch (RuntimeException e) {
                if (et.isActive()) {
                    et.rollback();
                }
                LOGGER.log(Level.WARNING, "Unhandled exception thrown in data repository task.", e);
                throw e;
            }
        }

        @Override
        public <E extends Throwable> void run(EntityManager em, Functions.CheckedConsumer<EntityManager, E> task) {
            EntityTransaction et = em.getTransaction();
            et.begin();
            try {
                super.run(em, task);
                et.commit();
                // Any exception in the task shall be wrapped by DataException
            } catch (DataException e) {
                if (et.isActive()) {
                    et.rollback();
                }
                throw e;
                // This shall never be thrown, it means bug in the exception handling code
            } catch (RuntimeException e) {
                if (et.isActive()) {
                    et.rollback();
                }
                LOGGER.log(Level.WARNING, "Unhandled exception thrown in data repository task.", e);
                throw e;
            }
        }
    }
}
