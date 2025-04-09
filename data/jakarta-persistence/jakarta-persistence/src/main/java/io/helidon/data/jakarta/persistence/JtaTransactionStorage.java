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

import java.util.HashMap;
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

    JtaTransactionStorage() {
        super();
        txManagers = new HashMap<>();
        nonTxManagers = new HashMap<>();
    }


    void end(TransactionContext.Context context) {
        // Here we may remove all EntityManager instances from the Map when
        // top level transaction call was finished to limit EntityManager life-cycle to
        // top level transaction scope.
        if (context.txMethodsDepth() == 0) {
            txManagers.values().forEach(EntityManager::close);
            txManagers.clear();
            nonTxManagers.values().forEach(EntityManager::close);
            nonTxManagers.clear();
        }
    }

    /**
     * Get entity manager bound to provided persistence context and thread while in JTA transaction method.
     */
    EntityManager manager(EntityManagerFactory factory, TransactionContext.Context context) {
        if (context.txMethodsDepth() == 0) {
            throw new DataException("EntityManager requested outside transaction method");
        }
        // Entity manager bound to current JTA transaction
        if (context.txDepth() > 0) {
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
