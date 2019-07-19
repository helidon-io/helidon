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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.persistence.EntityManager;

// Revisit: this is just a context.  This is not going to work.  We
// should just use ReferenceCountedContext here as well.
final class NonTransactionalTransactionScopedEntityManagerReferences {

    private static final ThreadLocal<Map<Object, EntityManagerWithReferenceCount>> ENTITY_MANAGERS =
        ThreadLocal.withInitial(() -> new HashMap<>());

    private NonTransactionalTransactionScopedEntityManagerReferences() {
        super();
    }

    static void incrementReferenceCount(final Object key) {
        final Map<Object, EntityManagerWithReferenceCount> map = ENTITY_MANAGERS.get();
        EntityManagerWithReferenceCount ref = map.get(key);
        if (ref == null) {
            map.put(key, new EntityManagerWithReferenceCount(null, () -> map.remove(key)));
        } else {
            ref.incrementReferenceCount();
        }
    }

    static void decrementReferenceCount(final Object key) {
        final EntityManagerWithReferenceCount ref = ENTITY_MANAGERS.get().get(key);
        if (ref != null) {
            ref.decrementReferenceCount();
        }
    }

    static Map<?, ?> getContents() {
        return Collections.unmodifiableMap(ENTITY_MANAGERS.get());
    }

    static EntityManager compute(final Object key, final Supplier<? extends EntityManager> supplier) {
        final Map<Object, EntityManagerWithReferenceCount> map = ENTITY_MANAGERS.get();
        EntityManagerWithReferenceCount ref = map.get(key);
        if (ref == null) {
            ref = new EntityManagerWithReferenceCount(supplier.get(), () -> map.remove(key));
            map.put(key, ref);
        }
        assert ref != null;
        EntityManager returnValue = ref.getEntityManager();
        if (returnValue == null) {
            returnValue = supplier.get();
            ref.setEntityManager(returnValue);
        }
        return returnValue;
    }

    static void close(final Object key) {
        final EntityManagerWithReferenceCount ref = ENTITY_MANAGERS.get().get(key);
        if (ref != null) {
            ref.close();
        }
    }

    private static final class EntityManagerWithReferenceCount {

        private EntityManager entityManager;

        private final AtomicInteger referenceCount;

        private final Runnable destroyer;

        private EntityManagerWithReferenceCount(final Runnable destroyer) {
            this(null, destroyer);
        }

        private EntityManagerWithReferenceCount(final EntityManager entityManager,
                                                final Runnable destroyer) {
            super();
            this.entityManager = entityManager;
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

        private void setEntityManager(final EntityManager entityManager) {
            assert this.referenceCount.get() > 0;
            this.entityManager = Objects.requireNonNull(entityManager);
        }

    }

}
