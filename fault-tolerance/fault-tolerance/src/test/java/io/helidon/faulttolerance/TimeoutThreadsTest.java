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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

/**
 * Check that all threads created by timeout instances are properly
 * terminated and not in {@link Thread.State#TIMED_WAITING} state.
 */
class TimeoutThreadTest {

    @Test
    void testTimeout() {
        // create executor with our thread factory
        TestThreadFactory threadFactory = new TestThreadFactory();
        ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory);

        // run 10 tasks with long timeouts that return immediately
        for (int i = 0; i < 10; i++) {
            String status = Timeout.builder()
                    .timeout(Duration.ofSeconds(60))        // long timeout
                    .currentThread(true)
                    .executor(executor)
                    .build()
                    .invoke(() -> "done");
            assertThat(status, is("done"));
        }

        // if timeout monitor threads stopped, no TIMED_WAITING states
        List<Thread> threads = threadFactory.threads();
        assertThat(threads.size(), is(10));
        for (Thread thread : threads) {
            assertThat(thread.getState(), is(not(Thread.State.TIMED_WAITING)));
        }
    }

    static class TestThreadFactory implements ThreadFactory {

        private final ThreadFactory delegate = Thread.ofVirtual().factory();
        private final List<Thread> threads = new CopyOnWriteArrayList<>();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = delegate.newThread(r);
            threads.add(t);
            return t;
        }

        List<Thread> threads() {
            return threads;
        }
    }
}
