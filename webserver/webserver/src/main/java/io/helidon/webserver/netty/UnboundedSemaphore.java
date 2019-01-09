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

import java.util.concurrent.atomic.AtomicLong;

/**
 * The UnboundedSemaphore is designed to fit Reactive Streams use-case; that is to be able to allow
 * an unbounded number of acquires once a cumulative release count reaches {@link Long#MAX_VALUE}.
 * For details, refer to {@link #release(long)} and {@link #tryAcquire()} methods where specifics of
 * this semaphore are explained in detail.
 *
 * @see <a href="https://github.com/reactive-streams/reactive-streams-jvm#3.17">Reactive Streams 3.17</a>
 */
class UnboundedSemaphore {

    // a synchronization primitive used to implement a semaphore logic
    private final AtomicLong atomicLong = new AtomicLong();

    /**
     * Releases {@code n} permits. If the cumulative value of the current total permits
     * reaches {@link Long#MAX_VALUE} this semaphore becomes unbounded and any consecutive
     * acquire call succeeds. Furthermore, any consecutive release call doesn't change the
     * new unbounded nature of this semaphore.
     *
     * @param n the number of permits to release
     * @return the new count of currently available permits
     */
    long release(long n) {
        return atomicLong.updateAndGet(original -> {
            long r = original + n;
            // HD 2-12 Overflow iff both arguments have the opposite sign of the result; inspired by Math.addExact(long, long)
            if (r == Long.MAX_VALUE || ((original ^ r) & (n ^ r)) < 0) {
                // unbounded reached
                return Long.MAX_VALUE;
            } else {
                return original + n;
            }
        });
    }

    /**
     * In a non-blocking manner, try to acquire a single permit.
     *
     * @return original number of permits in this semaphore; if {@code 0} is returned,
     * the requester didn't obtain a permit. In case a {@link Long#MAX_VALUE} is returned,
     * the requester is informed that this semaphore is unbounded and that any further
     * acquire will be always successful.
     */
    long tryAcquire() {
        return atomicLong.getAndUpdate(original -> {
            if (original == Long.MAX_VALUE) {
                // unbounded
                return original;
            } else {
                if (original == 0) {
                    return 0;
                } else {
                    return original - 1;
                }
            }
        });
    }

    /**
     * The current number of available permits.
     *
     * @return the number of currently available permits.
     */
    long availablePermits() {
        return atomicLong.get();
    }
}
