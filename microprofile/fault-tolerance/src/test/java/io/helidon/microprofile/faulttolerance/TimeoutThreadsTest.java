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
package io.helidon.microprofile.faulttolerance;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import io.helidon.faulttolerance.FaultTolerance;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

/**
 * Check that all threads created by timeout instances are properly
 * terminated and not in {@link Thread.State#TIMED_WAITING} state.
 */
@HelidonTest
@AddBean(TimeoutThreadsTest.MyBean.class)
class TimeoutThreadsTest {

    private final static TestThreadFactory THREAD_FACTORY = new TestThreadFactory();

    @Inject
    private MyBean bean;

    @BeforeAll
    static void init() {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(THREAD_FACTORY);
        FaultTolerance.executor(() -> executor);
    }

    @Test
    void testTimeout() {
        for (int i = 0; i < 10; i++) {
            String status = bean.timeout();
            assertThat(status, is("done"));
        }
    }

    @AfterAll
    static void close() {
        List<Thread> threads = THREAD_FACTORY.threads();
        assertThat(threads.size(), is(10));
        for (Thread thread : threads) {
            assertThat(thread.getState(), is(not(Thread.State.TIMED_WAITING)));
        }
    }

    static class MyBean {

        @Timeout(value = 60, unit = ChronoUnit.SECONDS)     // long timeout
        public String timeout() {
            return "done";
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
