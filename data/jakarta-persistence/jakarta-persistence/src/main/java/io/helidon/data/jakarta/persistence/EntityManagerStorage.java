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

import java.io.Closeable;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.SynchronizationType;

// Instances of EntityManagerStorage are bound with ThreadLocal so this class shall be thread safe

/**
 *
 */
final class EntityManagerStorage implements Closeable {
    private static final System.Logger LOGGER = System.getLogger(EntityManagerStorage.class.getName());

    @SuppressWarnings("deprecation")
    private final PersistenceUnitTransactionType transactionType;
    private final EntityManagerFactory factory;
    // Current EntityManager instance is at the top of the stack
    private final Stack<StackItem> current;
    // Pool of unused EntityManager instances to recycle them
    // Currently EntityManager instance life-cycle is bound to 1st level transaction method call.
    // It may be extended to thread level only when we'll get access to thread shutdown event.
    private final List<EntityManager> managers;
    // Entity manager used while transaction is suspended
    private EntityManager suspended;

    EntityManagerStorage(EntityManagerFactory factory,
                         @SuppressWarnings("deprecation") PersistenceUnitTransactionType transactionType) {
        this.transactionType = transactionType;
        this.factory = factory;
        this.suspended = null;
        this.current = new Stack<>();
        this.managers = new ArrayList<>(4);
    }

    @Override
    public void close() {
        try {
            managers.forEach(manager -> {
                // This shall not happen until user code hacks something
                if (LOGGER.isLoggable(Level.DEBUG) && !manager.isOpen()) {
                    LOGGER.log(Level.DEBUG,
                               "Closing already closed EntityManager");
                }
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE, "Closing EntityManager");
                }
                manager.close();
            });
        } finally {
            managers.clear();
        }
        if (suspended != null) {
            try {
                // This shall not happen until user code hacks something
                if (LOGGER.isLoggable(Level.DEBUG) && !suspended.isOpen()) {
                    LOGGER.log(Level.DEBUG,
                               "Closing already closed EntityManager");
                }
                if (LOGGER.isLoggable(Level.TRACE)) {
                    LOGGER.log(Level.TRACE,
                               "Closing EntityManager of suspended state");
                }
                suspended.close();
            } finally {
                suspended = null;
            }
        }
        if (!current.isEmpty()) {
            throw new IllegalStateException("Transaction stack is not empty");
        }
    }

    // EntityManager to be used in common state while transaction is not suspended
    void addCommonManager(int counter, boolean active, boolean newTx) {
        // 1st level (stack is empty) or new transaction requested
        if (current.isEmpty() || newTx) {
            EntityManager manager = getManager();
            current.push(new StackItem(counter, manager, active, newTx));
            // 2nd and next level without new transaction
        } else {
            StackItem stackItem = current.peek();
            if (stackItem.active() && !active) {
                throw new IllegalStateException("addSuspendedManager() shall be called instead");
            }
            current.push(new StackItem(counter, stackItem.entityManager, stackItem.active(), false));
        }
    }

    // EntityManager to be used while transaction is suspended
    void addSuspendedManager(int counter) {
        if (suspended == null) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE,
                           "Creating EntityManager of suspended state");
            }
            suspended = factory.createEntityManager();
        }
        current.push(new StackItem(counter, suspended, false, false));
    }

    // Current EntityManager to be used at the beginning of current transaction method scope
    // Resets new transaction flag to indicate that current transaction state is reused.
    void addCurrentManager(int counter) {
        StackItem stackItem = current.peek();
        current.push(new StackItem(counter, stackItem.entityManager(), stackItem.active, false));
    }

    // Remove EntityManager from the stack at the end of current transaction method scope
    void removeManager(int counter) {
        if (current.isEmpty()) {
            throw new IllegalStateException("Current EntityManager scope is empty");
        }
        if (LOGGER.isLoggable(Level.DEBUG) && current.size() != counter) {
            LOGGER.log(Level.DEBUG,
                       "Transaction level %d and internal stack size %d do not match",
                       counter,
                       current.size());
        }
        StackItem stackItem = current.pop();
        // Store entity manager into pool after finishing the new transaction
        if (stackItem.newTx || counter == 1) {
            if (!stackItem.entityManager().isOpen()) {
                throw new IllegalStateException("EntityManager being added to pool is closed");
            }
            if (stackItem.entityManager().getTransaction().isActive()) {
                throw new IllegalStateException("EntityManager being added to pool has transaction in progress");
            }
            managers.addFirst(stackItem.entityManager());
        }
    }

    // Current EntityManager
    EntityManager current() {
        if (current.isEmpty()) {
            throw new IllegalStateException("Current EntityManager scope is empty");
        }
        return current.peek().entityManager();
    }

    boolean active() {
        return !current.isEmpty() && current.peek().active();
    }

    boolean newTx() {
        return !current.isEmpty() && current.peek().newTx();
    }

    // EntityTransaction from current EntityManager
    EntityTransaction transaction() {
        return current().getTransaction();
    }

    // Get EntityManager.
    // Use from pool if not empty or create a new one.
    private EntityManager getManager() {
        if (managers.isEmpty()) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Creating EntityManager");
            }
            return transactionType == PersistenceUnitTransactionType.RESOURCE_LOCAL
                    ? factory.createEntityManager()
                    : factory.createEntityManager(SynchronizationType.SYNCHRONIZED);
        } else {
            EntityManager manager = managers.removeLast();
            if (!manager.isOpen()) {
                throw new IllegalStateException("EntityManager from pool is closed");
            }
            if (!manager.getTransaction().isActive()) {
                throw new IllegalStateException("EntityManager from pool has transaction in progress");
            }
            return manager;
        }
    }

    // Current transaction method call level
    //     counter: transaction method level (starts from 1)
    //     entityManager: current active EntityManager
    //     active: whether transaction is active
    //     newTx: whether new transaction was started on this level
    private record StackItem(int counter, EntityManager entityManager, boolean active, boolean newTx) {
    }

}
