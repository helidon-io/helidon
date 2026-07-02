/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.openapi;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

final class OpenApiLockCoordinator {
    private static final AtomicLong LOCK_IDS = new AtomicLong();
    private static final ReferenceQueue<Object> STALE_OWNERS = new ReferenceQueue<>();
    private static final ConcurrentMap<IdentityWeakReference, CoordinationLock> LOCKS = new ConcurrentHashMap<>();

    private OpenApiLockCoordinator() {
    }

    static CoordinationLock coordinationLock(Object owner) {
        IdentityWeakReference staleOwner;
        while ((staleOwner = (IdentityWeakReference) STALE_OWNERS.poll()) != null) {
            LOCKS.remove(staleOwner);
        }
        return LOCKS.computeIfAbsent(new IdentityWeakReference(owner, STALE_OWNERS),
                                     _ -> new CoordinationLock(LOCK_IDS.getAndIncrement()));
    }

    static LockHandle lock(Iterable<CoordinationLock> requestedLocks) {
        Set<CoordinationLock> uniqueLocks = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        requestedLocks.forEach(uniqueLocks::add);
        List<CoordinationLock> locks = new ArrayList<>(uniqueLocks);
        locks.sort(Comparator.comparingLong(lock -> lock.id));
        locks.forEach(lock -> lock.lock().lock());
        return new LockHandle(locks);
    }

    static final class CoordinationLock {
        private final long id;
        private final ReentrantLock lock = new ReentrantLock();

        private CoordinationLock(long id) {
            this.id = id;
        }

        ReentrantLock lock() {
            return lock;
        }
    }

    static final class LockHandle implements AutoCloseable {
        private final List<CoordinationLock> locks;

        private LockHandle(List<CoordinationLock> locks) {
            this.locks = locks;
        }

        @Override
        public void close() {
            for (int i = locks.size() - 1; i >= 0; i--) {
                locks.get(i).lock().unlock();
            }
        }
    }

    private static final class IdentityWeakReference extends WeakReference<Object> {
        private final int hashCode;

        private IdentityWeakReference(Object referent, ReferenceQueue<Object> referenceQueue) {
            super(referent, referenceQueue);
            this.hashCode = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof IdentityWeakReference other)) {
                return false;
            }
            Object owner = get();
            return owner != null && owner == other.get();
        }
    }
}
