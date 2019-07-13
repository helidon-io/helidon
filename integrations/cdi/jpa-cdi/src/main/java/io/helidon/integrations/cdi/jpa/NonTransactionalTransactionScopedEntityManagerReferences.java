/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.cdi.jpa;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;

final class NonTransactionalTransactionScopedEntityManagerReferences {

    private static final ThreadLocal<Map<Object, EntityManagerWithReferenceCount>> ENTITY_MANAGERS =
        ThreadLocal.withInitial(() -> new HashMap<>());

    private NonTransactionalTransactionScopedEntityManagerReferences() {
        super();
    }

    static void putIfAbsent(final Object key, final EntityManager entityManager) {
        final Map<Object, EntityManagerWithReferenceCount> map = ENTITY_MANAGERS.get();
        map.putIfAbsent(key,
                        new EntityManagerWithReferenceCount(entityManager,
                                                            () -> map.remove(key)));
    }

    static void incrementReferenceCount(final Object key) {
        final EntityManagerWithReferenceCount ref = ENTITY_MANAGERS.get().get(key);
        if (ref != null) {
            ref.incrementReferenceCount();
        }
    }

    static void decrementReferenceCount(final Object key) {
        final EntityManagerWithReferenceCount ref = ENTITY_MANAGERS.get().get(key);
        if (ref != null) {
            ref.decrementReferenceCount();
        }
    }

    static EntityManager get(final Object key) {
        final EntityManagerWithReferenceCount ref = ENTITY_MANAGERS.get().get(key);
        return ref == null ? null : ref.getEntityManager();
    }

    static void close(final Object key) {
        final EntityManagerWithReferenceCount ref = ENTITY_MANAGERS.get().get(key);
        if (ref != null) {
            ref.close();
        }
    }

    private static final class EntityManagerWithReferenceCount {

        private final EntityManager entityManager;

        private final AtomicInteger referenceCount;

        private final Runnable destroyer;

        private EntityManagerWithReferenceCount(final EntityManager entityManager,
                                                final Runnable destroyer) {
            super();
            this.entityManager = Objects.requireNonNull(entityManager);
            this.referenceCount = new AtomicInteger(1);
            this.destroyer = Objects.requireNonNull(destroyer);
        }

        private void decrementReferenceCount() {
            final int count = this.referenceCount.decrementAndGet();
            if (count <= 0) {
                this.destroyer.run();
                final EntityManager entityManager = this.getEntityManager();
                if (entityManager != null && entityManager.isOpen()) {
                    entityManager.close();
                }
            }
        }

        private void incrementReferenceCount() {
            this.referenceCount.incrementAndGet();
        }

        private void close() {
            this.referenceCount.set(0);
            this.decrementReferenceCount();
        }

        private EntityManager getEntityManager() {
            return this.entityManager;
        }

    }

}
