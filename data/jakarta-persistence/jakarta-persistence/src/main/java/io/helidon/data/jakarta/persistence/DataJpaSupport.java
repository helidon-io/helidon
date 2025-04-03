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

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.Functions;
import io.helidon.data.DataConfig;
import io.helidon.data.DataException;
import io.helidon.data.jakarta.persistence.spi.JakartaPersistenceExtension;
import io.helidon.data.spi.DataSupport;
import io.helidon.data.spi.RepositoryFactory;
import io.helidon.transaction.Tx;

import jakarta.persistence.EntityManagerFactory;

import static io.helidon.data.jakarta.persistence.JpaExtensionImpl.TRANSACTION_TYPE;

@SuppressWarnings("deprecation")
class DataJpaSupport implements DataSupport {
    private static final System.Logger LOGGER = System.getLogger(DataJpaSupport.class.getName());

    private final DataConfig config;
    private final EntityManagerFactory factory;
    private final Set<Class<?>> entities;

    DataJpaSupport(DataConfig config, EntityManagerFactory factory, Set<Class<?>> entities) {
        this.config = config;
        this.factory = factory;
        this.entities = entities;
    }

    static DataJpaSupport create(DataConfig config, JakartaPersistenceExtension extension) {
        EntityManagerFactory factory = extension.createFactory();
        Set<Class<?>> entities = extension.entities();
        return new DataJpaSupport(config, factory, entities);
    }

    @Override
    public RepositoryFactory repositoryFactory() {
        return new RepositoryFactoryImpl(factory);
    }

    @Override
    public String type() {
        return DataJpaSupportProviderService.PROVIDER_TYPE;
    }

    @Override
    public <T> T transaction(Tx.Type type, Callable<T> task) {
        // Jakarta Persistence 3.2 compliant code disabled
        // PersistenceUnitTransactionType transactionType = factory.getTransactionType();
        // Jakarta Persistence 3.1 workaround
        PersistenceUnitTransactionType transactionType
                = (PersistenceUnitTransactionType) factory.getProperties().get(TRANSACTION_TYPE);
        int counter = initTransaction(factory, transactionType);
        try {
            beginTransaction(transactionType, type);
            T result = task.call();
            commitTransaction(transactionType, type);
            return result;
        } catch (DataException e) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, String.format("Data repository transaction failed: %s", e.getMessage()));
            }
            rollbackTransaction(transactionType, type);
            throw e;
        } catch (Exception e) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, String.format("Data repository transaction failed: %s", e.getMessage()));
            }
            rollbackTransaction(transactionType, type);
            throw new DataException("Execution of transaction task failed", e);
            // Also validates whether transaction life-cycle was handled properly
        } finally {
            closeTransaction(counter);
        }
    }

    @Override
    public <E extends Throwable> void transaction(Tx.Type type, Functions.CheckedRunnable<E> task) {
        // Jakarta Persistence 3.2 compliant code disabled
        // PersistenceUnitTransactionType transactionType = factory.getTransactionType();
        // Jakarta Persistence 3.1 workaround
        PersistenceUnitTransactionType transactionType
                = (PersistenceUnitTransactionType) factory.getProperties().get(TRANSACTION_TYPE);
        int counter = initTransaction(factory, transactionType);
        try {
            beginTransaction(transactionType, type);
            task.run();
            commitTransaction(transactionType, type);
        } catch (DataException e) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, String.format("Data repository transaction failed: %s", e.getMessage()));
            }
            rollbackTransaction(transactionType, type);
            throw e;
        } catch (Throwable e) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, String.format("Data repository transaction failed: %s", e.getMessage()));
            }
            rollbackTransaction(transactionType, type);
            throw new DataException("Execution of transaction task failed", e);
            // Also validates whether transaction life-cycle was handled properly
        } finally {
            closeTransaction(counter);
        }
    }

    @Override
    public <T> T transaction(Tx.Type type, Function<Tx.Transaction, T> task) {
        // Jakarta Persistence 3.2 compliant code disabled
        // PersistenceUnitTransactionType transactionType = factory.getTransactionType();
        // Jakarta Persistence 3.1 workaround
        PersistenceUnitTransactionType transactionType
                = (PersistenceUnitTransactionType) factory.getProperties().get(TRANSACTION_TYPE);
        int counter = initTransaction(factory, transactionType);
        beginManualTransaction(counter, transactionType, type);
        try {
            return task.apply(new ManualTransaction(transactionType, type));
        } catch (RuntimeException e) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, String.format("Data repository transaction failed: %s", e.getMessage()));
            }
            throw e;
            // Also validates whether user managed transaction life-cycle was handled properly
        } finally {
            closeTransaction(counter);
        }
    }

    @Override
    public void transaction(Tx.Type type, Consumer<Tx.Transaction> task) {
        // Jakarta Persistence 3.2 compliant code disabled
        // PersistenceUnitTransactionType transactionType = factory.getTransactionType();
        // Jakarta Persistence 3.1 workaround
        PersistenceUnitTransactionType transactionType
                = (PersistenceUnitTransactionType) factory.getProperties().get(TRANSACTION_TYPE);
        int counter = initTransaction(factory, transactionType);
        beginManualTransaction(counter, transactionType, type);
        try {
            task.accept(new ManualTransaction(transactionType, type));
        } catch (RuntimeException e) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, String.format("Data repository transaction failed: %s", e.getMessage()));
            }
            throw e;
            // Also validates whether user managed transaction life-cycle was handled properly
        } finally {
            closeTransaction(counter);
        }
    }

    @Override
    public Tx.Transaction transaction(Tx.Type type) {
        // Jakarta Persistence 3.2 compliant code disabled
        // PersistenceUnitTransactionType transactionType = factory.getTransactionType();
        // Jakarta Persistence 3.1 workaround
        PersistenceUnitTransactionType transactionType
                = (PersistenceUnitTransactionType) factory.getProperties().get(TRANSACTION_TYPE);
        int counter = initTransaction(factory, transactionType);
        beginManualTransaction(counter, transactionType, type);
        // No validation of transaction life-cycle is possible without UserTransaction being AutoClosable
        return new UserTransaction(counter, transactionType, type);
    }

    @Override
    public void close() {
        factory.close();
    }

    @Override
    public DataConfig dataConfig() {
        return config;
    }

    @Override
    public String toString() {
        return type() + " data support, config: " + config;
    }

    private static void beginManualTransaction(int counter, PersistenceUnitTransactionType transactionType, Tx.Type type) {
        try {
            beginTransaction(transactionType, type);
        } catch (RuntimeException ex) {
            try {
                rollbackTransaction(transactionType, type);
            } finally {
                closeTransaction(counter);
            }
            throw ex;
        }
    }

    private static int initTransaction(EntityManagerFactory factory, PersistenceUnitTransactionType transactionType) {
        return TransactionContext.getInstance().initTransaction(factory, transactionType);
    }

    private static void closeTransaction(int counter) {
        TransactionContext.getInstance().closeTransaction(counter);
    }

    private static void beginTransaction(PersistenceUnitTransactionType transactionType, Tx.Type type) {
        TransactionContext.getInstance().beginTransaction(transactionType, type);
    }

    private static void commitTransaction(PersistenceUnitTransactionType transactionType, Tx.Type type) {
        TransactionContext.getInstance().commitTransaction(transactionType, type);
    }

    private static void rollbackTransaction(PersistenceUnitTransactionType transactionType, Tx.Type type) {
        TransactionContext.getInstance().rollbackTransaction(transactionType, type);
    }

    private static class RepositoryFactoryImpl implements RepositoryFactory {
        private final EntityManagerFactory factory;

        private RepositoryFactoryImpl(EntityManagerFactory factory) {
            this.factory = factory;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public <E, T> T create(Function<E, T> creator) {
            Function creatorErased = creator;
            return (T) creatorErased.apply(new JpaRepositoryExecutorImpl(factory));
        }
    }

    // Manual transaction handler
    private record ManualTransaction(PersistenceUnitTransactionType transactionType,
                                     Tx.Type type) implements Tx.Transaction {

        @Override
        public void commit() {
            commitTransaction(transactionType, type);
        }

        @Override
        public void rollback() {
            rollbackTransaction(transactionType, type);
        }
    }

    // User transaction handler
    // Transaction context cleanup is done as part of commit and rollback calls. Cleanup must be done even with RuntimeException
    // being thrown.
    private record UserTransaction(int counter,
                                   PersistenceUnitTransactionType transactionType,
                                   Tx.Type type) implements Tx.Transaction {

        @Override
        public void commit() {
            try {
                commitTransaction(transactionType, type);
            } finally {
                closeTransaction(counter);
            }
        }

        @Override
        public void rollback() {
            try {
                rollbackTransaction(transactionType, type);
            } finally {
                closeTransaction(counter);
            }
        }
    }
}
