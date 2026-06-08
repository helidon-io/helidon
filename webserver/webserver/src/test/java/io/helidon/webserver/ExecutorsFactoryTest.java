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

package io.helidon.webserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class ExecutorsFactoryTest {

    @Test
    void loomServerExecutorLogsUncaughtException() throws Exception {
        assertExecutorLogsUncaughtException(ExecutorsFactory.newLoomServerVirtualThreadPerTaskExecutor());
    }

    @Test
    void sharedExecutorLogsUncaughtException() throws Exception {
        assertExecutorLogsUncaughtException(ExecutorsFactory.newServerListenerSharedExecutor());
    }

    private static void assertExecutorLogsUncaughtException(ExecutorService executor) throws Exception {
        IllegalStateException failure = new IllegalStateException("test failure");

        try (TestLogHandler handler = TestLogHandler.install(ExecutorsFactory.class);
                executor) {
            executor.execute(() -> {
                throw failure;
            });

            LogRecord record = handler.await();

            assertThat(record.getMessage(), containsString("Uncaught exception in WebServer executor thread"));
            assertThat(record.getThrown(), sameInstance(failure));
        }
    }

    private static final class TestLogHandler extends Handler implements AutoCloseable {
        private final Logger logger;
        private final Level previousLevel;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<LogRecord> record = new AtomicReference<>();

        private TestLogHandler(Logger logger) {
            this.logger = logger;
            this.previousLevel = logger.getLevel();
            setLevel(Level.ALL);
        }

        static TestLogHandler install(Class<?> clazz) {
            Logger logger = Logger.getLogger(clazz.getName());
            TestLogHandler handler = new TestLogHandler(logger);
            logger.setLevel(Level.ALL);
            logger.addHandler(handler);
            return handler;
        }

        @Override
        public void publish(LogRecord record) {
            if (this.record.compareAndSet(null, record)) {
                latch.countDown();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            logger.setLevel(previousLevel);
        }

        private LogRecord await() throws InterruptedException {
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
            return record.get();
        }
    }
}
