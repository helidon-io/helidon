/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * The ReferenceHoldingQueue is an enhanced reference queue that allows a post
 * mortem execution such as a releasing of memory that would otherwise cause a
 * memory leak.
 *
 * @param <T> the referent type
 * @see ReferenceHoldingQueue.ReleasableReference
 */
class ReferenceHoldingQueue<T> extends ReferenceQueue<T> {
    private static final Logger LOGGER = Logger.getLogger(ReferenceHoldingQueue.class.getName());

    /**
     * This set is used to keep references on the {@code ReleasableReference}
     * references. I.e, the items of this set are effectively "phantom
     * reachable", meaning that when the GC runs, the references will be added
     * to the actual reference queue.
     */
    private final Set<ReleasableReference<T>> set = ConcurrentHashMap.newKeySet();
    private volatile boolean down = false;

    /**
     * Release all the associated {@link ReleasableReference} objects that were
     * added into this reference queue when their associated objects were
     * garbage collected.
     *
     * @return whether all the associated objects with this reference holding
     * queue were released and there is nothing else to release left
     */
    boolean release() {
        while (true) {
            Reference<?> poll = poll();
            if (poll == null) {
                break;
            }

            hookOnAutoRelease();

            if (poll instanceof ReleasableReference) {
                ((ReleasableReference<?>) poll).release();
            } else {
                LOGGER.warning(() -> "Unexpected type detected: " + poll.getClass());
            }
        }
        return set.isEmpty();
    }

    /**
     * Hook invoked before invoking {@link ReleasableReference#release()}
     * for each object referenced by a {@link ReleasableReference} that
     * was garbage collected where {@link ReleasableReference#release()} was
     * not invoked.
     */
    protected void hookOnAutoRelease() {
    }

    /**
     * Shutdown this reference queue. Once shut down, all the managed references
     * are forcibly cleared and no more items are accepted to be managed by this
     * queue.
     */
    public void shutdown() {
        down = true;
        for (ReleasableReference<T> reference : set) {
            reference.release();
        }
    }

    /**
     * Link the specified reference with this reference queue.
     * This keeps at least one hard reference to the linked reference
     * in order to have added to the reference queue during the next GC cycle.
     *
     * @param reference the reference to link
     * @throws IllegalStateException if shutdown was requested an this queue
     * must not be used
     */
    private void link(ReleasableReference<T> reference) {
        if (down) {
            throw new IllegalStateException("Shutdown was requested. This queue must not be used anymore");
        }
        set.add(reference);
    }

    /**
     * Unlink the specified reference from this reference queue.
     * This removes the hard reference to the linked reference, thus the GC
     * will not add it to the reference queue during the next GC cycle.
     *
     * @param reference the reference to unlink
     */
    private void unlink(ReleasableReference<T> reference) {
        set.remove(reference);
    }

    /**
     * This class holds a reference to a {@link Runnable} that will be executed
     * the latest when its referent (the {@link T} instance) is garbage
     * collected. It is however strongly recommended to call the
     * {@link #release()} method explicitly due to
     * a performance impact and a large memory demand.
     *
     * @param <T> the referent type
     */
    static final class ReleasableReference<T> extends IndirectReference<Runnable> {

        private final ReferenceHoldingQueue<T> queue;

        /**
         * Create a new {@code ReleasableReference}.
         *
         * @param referent the referenced object
         * @param q the reference holding queue
         * @param r the release callback
         */
        ReleasableReference(T referent, ReferenceHoldingQueue<T> q, Runnable r) {
            super(referent, (ReferenceQueue<Object>) q, r);
            this.queue = q;
            queue.link(this);
        }

        /**
         * Indicate if {@link #release()} was invoked.
         *
         * @return {@code true} if released was invoked, {@code false} otherwise
         */
        boolean isReleased() {
            return otherRef.get() == null;
        }

        /**
         * Unlink this reference from the queue and invoke the associated release callback.
         */
        void release() {
            Runnable r = acquire();
            if (r != null) {
                queue.unlink(this);
                r.run();
            }
        }
    }

    static class IndirectReference<T> extends PhantomReference<Object> {
       protected final AtomicReference<T> otherRef = new AtomicReference<>();

       public IndirectReference(Object referent, ReferenceQueue<Object> q, T otherRef) {
          super(referent, q);
          this.otherRef.lazySet(otherRef);
       }

       public T acquire() {
          return otherRef.get() == null ? null : otherRef.getAndSet(null);
       }
    }
}
