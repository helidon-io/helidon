/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import io.netty.util.internal.ConcurrentSet;

/**
 * The ReferenceHoldingQueue is an enhanced reference queue that allows a post morten execution
 * such as a releasing of memory that would otherwise cause a memory leak.
 *
 * @see ReferenceHoldingQueue.ReleasableReference
 */
class ReferenceHoldingQueue<T> extends ReferenceQueue<T> {

    private static final Logger LOGGER = Logger.getLogger(ReferenceHoldingQueue.class.getName());

    private final Set<ReleasableReference<T>> set = new ConcurrentSet<>();
    private volatile boolean down = false;

    /**
     * Release all the associated {@link ReleasableReference} objects that were
     * added into this reference queue when their associated objects were
     * garbage collected.
     *
     * @return whether all the associated objects with this reference holding queue
     * were released and there is nothing else to release left
     */
    boolean release() {
        while (true) {
            Reference poll = poll();
            if (poll == null) {
                break;
            }

            ByteBufRequestChunk.OneTimeLoggerHolder.logOnce();

            if (poll instanceof ReleasableReference) {
                ((ReleasableReference) poll).release();
            } else {
                LOGGER.warning(() -> "Unexpected type detected: " + poll.getClass());
            }
        }

        return set.isEmpty();
    }

    /**
     * Shutdown this reference queue. Once shut down, all the managed references are forcibly cleared
     * and no more items are accepted to be managed by this queue.
     */
    void shutdown() {
        down = true;
        for (ReleasableReference<T> reference : set) {
            reference.release();
        }
    }

    /**
     * Links a reference with this reference queue so that there is at least one hard
     * reference to the linked reference. If not linked, this queue wouldn't be able
     * to release the GCed object that is referenced by this reference.
     *
     * @param reference the reference to link
     * @throws IllegalStateException if shutdown was requested an this queue must not be used
     */
    private void link(ReleasableReference<T> reference) {
        if (down) {
            throw new IllegalStateException("Shutdown was requested. This queue must not be used anymore");
        }
        set.add(reference);
    }

    /**
     * Unlinks the reference in order to clear all the residue objects from the memory.
     *
     * @param reference the reference to unlink
     */
    private void unlink(Reference<T> reference) {
        set.remove(reference);
    }

    /**
     * This class holds a reference to a {@link Runnable} that will be executed the latest when its
     * referent (the {@link T} instance) is garbage collected. It is however
     * strongly recommended to call the {@link #release()} method explicitly due to a performance impact
     * and a large memory demand.
     *
     * @param <T> the referent type
     */
    static class ReleasableReference<T> extends PhantomReference<T> {

        private final AtomicBoolean released = new AtomicBoolean(false);
        private final ReferenceHoldingQueue<T> queue;
        private final Runnable r;

        ReleasableReference(T referent, ReferenceHoldingQueue<T> q, Runnable r) {
            super(referent, q);

            this.queue = q;
            this.r = r;

            queue.link(this);
        }

        boolean isReleased() {
            return released.get();
        }

        void release() {
            if (!released.getAndSet(true)) {
                queue.unlink(this);
                r.run();
            }
        }
    }
}
