/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.tls.Tls;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionHandlerTest {

    @Test
    void testHttp10Prologue() {
        DataReader reader = DataReader.create(() -> "GET / HTTP/1.0\r\n".getBytes(StandardCharsets.US_ASCII));
        assertThat(ConnectionHandler.isHttp10Connection(reader), is(true));
    }

    @Test
    void logsUnexpectedThrowableFromConnectionHandling() throws Exception {
        AssertionError failure = new AssertionError("unexpected failure");
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        when(listenerConfig.enableProxyProtocol()).thenThrow(failure);
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(listenerConfig);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        ConnectionHandler handler = new ConnectionHandler(listenerContext,
                                                          token,
                                                          mock(Limit.class),
                                                          ConnectionProviders.create(List.of()),
                                                          mock(SocketChannel.class),
                                                          "server",
                                                          Router.empty(),
                                                          mock(Tls.class),
                                                          it -> { });

        try (TestLogHandler logHandler = TestLogHandler.install()) {
            handler.run();

            LogRecord record = logHandler.await();
            assertThat(record.getMessage(), containsString("Unexpected throwable while handling connection"));
            assertThat(record.getThrown(), sameInstance(failure));
            verify(token).ignore();
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

        static TestLogHandler install() {
            Logger logger = Logger.getLogger(ConnectionHandler.class.getName());
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
