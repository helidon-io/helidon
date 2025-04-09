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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.data.DataException;
import io.helidon.service.registry.Service;
import io.helidon.transaction.TxException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Resource local transactions thread local storage.
 */
class LocalTransactionStorage {

    private static final System.Logger LOGGER = System.getLogger(LocalTransactionStorage.class.getName());

    // Stack sanity check
    private final List<Integer> initialStackSize;
    private final List<Stack> stack;
    private final Map<EntityManagerFactory, EntityManager> nonTxManagers;


    LocalTransactionStorage() {
        initialStackSize = new ArrayList<>();
        stack = new ArrayList<>();
        nonTxManagers = new HashMap<>();
    }

    void start() {
        initialStackSize.addLast(stack.size());
    }
    void end(TransactionContext.Context context) {
        // Here we may remove all EntityManager instances from the Map when
        // top level transaction call was finished to limit EntityManager life-cycle to
        // top level transaction scope.
        if (context.txMethodsDepth() == 0) {
            stack.forEach(Stack::close);
            stack.clear();
            nonTxManagers.values().forEach(EntityManager::close);
            nonTxManagers.clear();
        }
        // Stack sanity check
        int stackSize = initialStackSize.removeLast();
        if (stack.size() > stackSize) {
            throw new DataException("New resource local transaction was started but never finished with commit or rollback");
        }
    }

    void commit(TransactionContext.Context context) {
        stack.getLast().close();
        stack.removeLast();
    }

    void rollback(TransactionContext.Context context) {
        stack.getLast().close();
        stack.removeLast();
    }

    EntityManager manager(EntityManagerFactory factory, TransactionContext.Context context) {
        if (context.txMethodsDepth() == 0) {
            throw new DataException("EntityManager requested outside transaction method");
        }
        if (context.txDepth() == 0 || stack().isSuspend()) {
            return nonTxManager(factory);
        } else {
            return txManager(factory, context);
        }
    }

    private EntityManager txManager(EntityManagerFactory factory, TransactionContext.Context context) {
        Map<EntityManagerFactory, EntityManager> managers = stack().managers();
        EntityManager em = managers.get(factory);
        if (em != null) {
            return em;
        }
        em = factory.createEntityManager();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                       String.format("Resource local transaction begin on EntityManager %d [%d].",
                                     em.hashCode(),
                                     Thread.currentThread().hashCode()));
        }
        em.getTransaction().begin();
        managers.put(factory, em);
        return em;
    }

    private EntityManager nonTxManager(EntityManagerFactory factory) {
        EntityManager em = nonTxManagers.get(factory);
        if (em != null) {
            return em;
        }
        // New entity manager outside transaction scope
        em = factory.createEntityManager();
        nonTxManagers.put(factory, em);
        return em;
    }

    private void addStack() {
        stack.addLast(new Stack());
    }

    private Stack stack() {
        return stack.getLast();
    }

    private boolean newTransaction() {
        return stack.size() == initialStackSize.getLast() + 1;
    }

    @Service.Singleton
    static class LocalTransactionProvider {

        private final LocalTransactionManager transactionManager;

        @Service.Inject
        LocalTransactionProvider(LocalTransactionManager transactionManager) {
            this.transactionManager = transactionManager;
        }

        LocalTransactionManager transactionManager() {
            return transactionManager;
        }

    }

    @Service.Singleton
    static class LocalTransactionManager {

        LocalTransactionManager() {
        }

        void begin() {
            LocalTransactionStorage storage = TransactionContext.getInstance().localStorage();
            storage.addStack();
        }

        LocalTransaction suspend() {
            LocalTransaction transaction = TransactionContext.getInstance()
                    .localStorage()
                    .stack();
            transaction.suspend();
            return transaction;
        }

        void resume(LocalTransaction transaction) {
            transaction.resume();
        }

        LocalTransaction getTransaction() {
            LocalTransactionStorage storage = TransactionContext.getInstance().localStorage();

            return TransactionContext.getInstance()
                    .localStorage()
                    .stack();
        }

    }

    interface LocalTransaction {

        void commit();

        void rollback();

        void setRollbackOnly();

        void suspend();

        void resume();

    }

    private static final class Stack implements LocalTransaction {

        private Map<EntityManagerFactory, EntityManager> managers;
        private boolean suspend;

        Stack() {
            managers = new HashMap<>();
            suspend = false;
        }

        void close() {
            managers.values().forEach(EntityManager::close);
            managers.clear();
        }

        Map<EntityManagerFactory, EntityManager> managers () {
            return managers;
        }

        @Override
        public void commit() {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Resource local transaction commit on EntityManagers [%d].",
                                         Thread.currentThread().hashCode()));
            }
            managers.values().forEach(em -> {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                               String.format("EntityManager %d [%d].",
                                             em.hashCode(),
                                             Thread.currentThread().hashCode()));
                }
                em.getTransaction().commit();
            });
        }

        @Override
        public void rollback() {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Resource local transaction rollback on EntityManagers [%d].",
                                         Thread.currentThread().hashCode()));
            }
            managers.values().forEach(em -> {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                               String.format("EntityManager %d [%d].",
                                             em.hashCode(),
                                             Thread.currentThread().hashCode()));
                }
                em.getTransaction().rollback();
            });
        }

        @Override
        public void setRollbackOnly() {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Set resource local transaction rollback only on EntityManagers [%d].",
                                         Thread.currentThread().hashCode()));
            }
            managers.values().forEach(em -> {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                               String.format("EntityManager %d [%d].",
                                             em.hashCode(),
                                             Thread.currentThread().hashCode()));
                }
                em.getTransaction().setRollbackOnly();
            });
        }

        @Override
        public void suspend() {
            if (suspend) {
                throw new TxException("Cannot suspend already suspended local transaction");
            }
            suspend = true;
        }

        @Override
        public void resume() {
            if (!suspend) {
                throw new TxException("Cannot resume active local transaction");
            }
            suspend = false;
        }

        boolean isSuspend() {
            return suspend;
        }

    }

}
