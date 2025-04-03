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

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.data.DataException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.SynchronizationType;

/**
 * JTA transactions thread local storage.
 */
class JtaTransactionStorage {

    private static final System.Logger LOGGER = System.getLogger(JtaTransactionStorage.class.getName());

    // Multiple DataRegistry instances may live in the current thread context transaction
    // and each of them will have it's own EntityManagerFactory
    private final Map<EntityManagerFactory, EntityManager> txManagers;
    private final Map<EntityManagerFactory, EntityManager> nonTxManagers;
    // Transaction methods depth counter.
    private int txMethodsDepth;
    // Transaction depth counter.
    private int txDepth;
    // Transaction depth when transaction was started (before new JTA transaction was eventually started)
    // This must be stack to keep values for individual method call levels.
    private final List<Integer> initialTxDepth;

    JtaTransactionStorage() {
        txManagers = new HashMap<>();
        nonTxManagers = new HashMap<>();
        txMethodsDepth = 0;
        txDepth = 0;
        initialTxDepth = new ArrayList<>();
    }

    void start() {
        txMethodsDepth++;
        initialTxDepth.addLast(txDepth);
        // Initialize to current state before JTA starts transaction handling
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG,
                       String.format("JTA transaction method marked as started on level %d [%d].",
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
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG,
                       String.format("JTA transaction method marked as ended on level %d [%d].",
                                     methodsDepth,
                                     Thread.currentThread().hashCode()));
        }

        // Transaction depth sanity check before current method level ends.
        // If the check fails, there was no commit or rollback after transaction was started.
        // Log warning and fix transaction depth counter.
        int initialDepth = initialTxDepth.removeLast();
        if (initialDepth != txDepth) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                           String.format("New transaction was started but never finished with commit or rollback [%d]",
                                         Thread.currentThread().hashCode()));
            }
            txDepth = initialDepth;
        }

        // Here we may remove all EntityManager instances from the Map with counter == 0
        // (top level transaction call was finished) to limit EntityManager life-cycle to
        // top level transaction scope.
        if (txMethodsDepth == 0) {
            txManagers.values().forEach(EntityManager::close);
            txManagers.clear();
            nonTxManagers.values().forEach(EntityManager::close);
            nonTxManagers.clear();
        }
    }

    void begin() {
        txDepth++;
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG,
                       String.format("New JTA transaction begin on level %d:%d [%d].",
                                     txMethodsDepth,
                                     txDepth,
                                     Thread.currentThread().hashCode()));
        }
    }

    void commit() {
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG,
                       String.format("New JTA transaction commit on level %d:%d [%d].",
                                     txMethodsDepth,
                                     txDepth,
                                     Thread.currentThread().hashCode()));
        }
        txDepth--;
    }

    void rollback() {
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG,
                       String.format("New JTA transaction rollback on level %d:%d [%d].",
                                     txMethodsDepth,
                                     txDepth,
                                     Thread.currentThread().hashCode()));
        }
        txDepth--;
    }

    boolean isTxMethodRunning() {
        return txMethodsDepth > 0;
    }

    /**
     * Get entity manager bound to provided persistence context and thread while in JTA transaction method.
     */
    EntityManager manager(EntityManagerFactory factory) {
        if (txMethodsDepth == 0) {
            throw new DataException("EntityManager requested outside JTA transaction");
        }
        // Entity manager bound to current JTA transaction
        if (txDepth > 0) {
            EntityManager em = txManager(factory);
            em.joinTransaction();
            return em;
        // Entity manager outside JTA transaction scope
        } else {
            return nonTxManager(factory);
        }
    }

    private EntityManager txManager(EntityManagerFactory factory) {
        EntityManager em = txManagers.get(factory);
        if (em != null) {
            return em;
        }
        em = factory.createEntityManager(SynchronizationType.UNSYNCHRONIZED);
        txManagers.put(factory, em);
        return em;
    }

    private EntityManager nonTxManager(EntityManagerFactory factory) {
        EntityManager em = nonTxManagers.get(factory);
        if (em != null) {
            return em;
        }
        em = factory.createEntityManager(SynchronizationType.UNSYNCHRONIZED);
        nonTxManagers.put(factory, em);
        return em;
    }

}
