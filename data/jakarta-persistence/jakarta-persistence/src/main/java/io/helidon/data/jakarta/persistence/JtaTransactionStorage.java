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
import io.helidon.transaction.TxException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.SynchronizationType;

/**
 * JTA transactions thread local storage.
 */
class JtaTransactionStorage {

    private static final System.Logger LOGGER = System.getLogger(JtaTransactionStorage.class.getName());

    // Multiple DataRegistry instances may live in the current thread context transaction
    // and each of them will have its own EntityManagerFactory
    private final Map<EntityManagerFactory, EntityManager> txManagers;
    // Value holder for stacked nested Tx.New calls, contains context.txDepth() values
    private final List<Stack> stack;
    private final Map<EntityManagerFactory, EntityManager> nonTxManagers;

    JtaTransactionStorage() {
        super();
        txManagers = new HashMap<>();
        stack = new ArrayList<>();
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
            stack.clear();
        }
    }

    void begin(TransactionContext.Context context, String txIdentity) {
        addStack(txIdentity);
        // Stack size check, failure may be caused only by bug in the code
        if (stack.size() != context.txDepth()) {
            throw new IllegalStateException(
                    String.format("JTA transaction stack size %d does not match transaction depth %d",
                                  stack.size(),
                                  context.txDepth()));
        }
    }

    void commit(TransactionContext.Context context, String txIdentity) {
        stack(context, txIdentity);
        removeStack();
    }

    void rollback(TransactionContext.Context context, String txIdentity) {
        stack(context, txIdentity);
        removeStack();
    }

    void suspend(TransactionContext.Context context, String txIdentity) {
        stack(context, txIdentity).suspend();
    }

    void resume(TransactionContext.Context context, String txIdentity) {
        stack(context, txIdentity).resume();
    }

    /**
     * Get entity manager bound to provided persistence context and thread while in JTA transaction method.
     */
    EntityManager manager(EntityManagerFactory factory, TransactionContext.Context context) {
        if (context.txMethodsDepth() == 0) {
            throw new DataException("EntityManager requested outside transaction method");
        }
        // Entity manager outside JTA transaction scope
        if (context.txDepth() == 0 || stack().isSuspend()) {
            return nonTxManager(factory);
            // Entity manager bound to current JTA transaction
        } else {
            EntityManager em = txManager(factory);
            em.joinTransaction();
            return em;
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

    private void addStack(String identity) {
        stack.addLast(new Stack(identity));
    }

    private void removeStack() {
        stack.removeLast();
    }

    private Stack stack() {
        return stack.getLast();
    }

    // PERF: Stack sanity checks slow down stack access a bit, but code is more safe
    private Stack stack(TransactionContext.Context context, String identity) {
        // Stack size check, failure may be caused only by bug in the code
        if (stack.size() != context.txDepth()) {
            throw new IllegalStateException(
                    String.format("JTA transaction stack size %d does not match transaction depth %d",
                                  stack.size(),
                                  context.txDepth()));
        }
        Stack stack = this.stack.getLast();
        // Stack identity check, failure may be caused only by bug in the code
        if (!stack.checkIdentity(identity)) {
            throw new IllegalStateException(
                    String.format("JTA transaction identity %s does not match provided identity %s",
                                  stack.identity(),
                                  identity));
        }
        return stack;
    }

    private static final class Stack {

        private final String identity;
        private boolean suspend;

        Stack(String identity) {
            if (identity == null) {
                throw new NullPointerException("JTA transaction identity is null");
            }
            this.identity = identity;
            this.suspend = false;
        }

        String identity() {
            return identity;
        }

        boolean checkIdentity(String identity) {
            return this.identity.equals(identity);
        }

        void suspend() {
            if (suspend) {
                throw new TxException("Cannot suspend already suspended JTA transaction");
            }
            suspend = true;
        }

        void resume() {
            if (!suspend) {
                throw new TxException("Cannot resume active JTA transaction");
            }
            suspend = false;
        }

        boolean isSuspend() {
            return suspend;
        }

    }

}
