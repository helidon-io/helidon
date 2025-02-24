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

package io.helidon.faulttolerance;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.TRACE;
import static org.junit.jupiter.api.Assertions.fail;

class BulkheadBaseTest {

    protected static final long WAIT_TIMEOUT_MILLIS = 5000;
    protected static final System.Logger LOGGER = System.getLogger(BulkheadBaseTest.class.getName());

    /**
     * A task to submit to a bulkhead. Can be checked for startup and manually
     * unblocked for completion.
     */
    protected static class Task {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch blocked = new CountDownLatch(1);

        private final int index;
        private CompletableFuture<?> future;

        Task(int index) {
            this.index = index;
        }

        int run() {
            LOGGER.log(TRACE, "Task " + index + " running on thread " + Thread.currentThread().getName());

            started.countDown();
            try {
                blocked.await();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return index;
        }

        boolean isStarted() {
            return started.getCount() == 0;
        }

        boolean waitUntilStarted(long millis) throws InterruptedException {
            return started.await(millis, TimeUnit.MILLISECONDS);
        }

        boolean isBlocked() {
            return blocked.getCount() == 1;
        }

        void unblock() {
            blocked.countDown();
        }

        void future(CompletableFuture<?> future) {
            this.future = future;
        }

        CompletableFuture<?> future() {
            return future;
        }
    }

    protected static void assertEventually(Supplier<Boolean> predicate, long millis) throws InterruptedException {
        long start = System.currentTimeMillis();
        do {
            if (predicate.get()) {
                return;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() - start <= millis);
        fail("Predicate failed after " + millis + " milliseconds");
    }
}
