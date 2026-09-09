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

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.common.tls.Tls;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;

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
        ListenerConfig virtualHostConfig = mock(ListenerConfig.class);
        when(virtualHostConfig.sni()).thenReturn(SniConfig.create());
        when(virtualHostConfig.virtualHosts()).thenReturn(List.of());
        VirtualHostRegistry virtualHosts = VirtualHostRegistry.create("server", virtualHostConfig, mock(Tls.class));
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        ConnectionHandler handler = new ConnectionHandler(listenerContext,
                                                          token,
                                                          mock(Limit.class),
                                                          ConnectionProviders.create(List.of()),
                                                          mock(SocketChannel.class),
                                                          "server",
                                                          Router.empty(),
                                                          mock(Tls.class),
                                                          virtualHosts,
                                                          it -> { });

        try (TestLogHandler logHandler = TestLogHandler.install()) {
            handler.run();

            LogRecord record = logHandler.await();
            assertThat(record.getMessage(), containsString("Unexpected throwable while handling connection"));
            assertThat(record.getThrown(), sameInstance(failure));
            verify(token).ignore();
        }
    }

    @Test
    void socketWriterFailureIsTrace() throws Exception {
        assertConnectionFailureLevel(new SocketWriterException(), Level.FINER);
    }

    @Test
    void connectionInterruptionIsTrace() throws Exception {
        assertConnectionFailureLevel(new InterruptedException("test interruption"), Level.FINER);
    }

    @Test
    void unexpectedConnectionFailureRemainsWarning() throws Exception {
        assertConnectionFailureLevel(new IllegalStateException("unexpected"), Level.WARNING);
    }

    private static void assertConnectionFailureLevel(Exception failure, Level expectedLevel) throws Exception {
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        when(listenerConfig.useNio()).thenReturn(true);
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(listenerConfig);
        SocketChannel socket = mock(SocketChannel.class);
        when(socket.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        when(socket.getLocalAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 80));
        ListenerConfig virtualHostConfig = mock(ListenerConfig.class);
        when(virtualHostConfig.sni()).thenReturn(SniConfig.create());
        when(virtualHostConfig.virtualHosts()).thenReturn(List.of());
        Tls tls = mock(Tls.class);
        VirtualHostRegistry virtualHosts = VirtualHostRegistry.create("server", virtualHostConfig, tls);
        ConnectionProviders connectionProviders =
                ConnectionProviders.create(List.of(new TestConnectionSelector(failure)));
        ConnectionHandler handler = new ConnectionHandler(listenerContext,
                                                          mock(LimitAlgorithm.Token.class),
                                                          mock(Limit.class),
                                                          connectionProviders,
                                                          socket,
                                                          "server",
                                                          Router.empty(),
                                                          tls,
                                                          virtualHosts,
                                                          _ -> { });

        try (TestLogHandler logHandler = TestLogHandler.install(failure)) {
            Thread thread = Thread.ofVirtual().start(handler::run);
            LogRecord record = logHandler.await();
            thread.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(thread.isAlive(), is(false));
            assertThat(record.getThrown(), sameInstance(failure));
            assertThat(record.getLevel(), is(expectedLevel));
        }
    }

    private record TestConnectionSelector(Exception failure) implements ServerConnectionSelector {
        @Override
        public int bytesToIdentifyConnection() {
            return 0;
        }

        @Override
        public Support supports(BufferData data) {
            return Support.SUPPORTED;
        }

        @Override
        public Set<String> supportedApplicationProtocols() {
            return Set.of();
        }

        @Override
        public ServerConnection connection(ConnectionContext ctx) {
            return new ServerConnection() {
                @Override
                public void handle(Limit limit) throws InterruptedException {
                    if (failure instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw (InterruptedException) failure;
                }

                @Override
                public Duration idleTime() {
                    return Duration.ZERO;
                }

                @Override
                public void close(boolean interrupt) {
                }
            };
        }
    }

    private static final class TestLogHandler extends Handler implements AutoCloseable {
        private final Logger logger;
        private final Level previousLevel;
        private final boolean previousUseParentHandlers;
        private final Throwable failure;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<LogRecord> record = new AtomicReference<>();

        private TestLogHandler(Logger logger, Throwable failure) {
            this.logger = logger;
            this.previousLevel = logger.getLevel();
            this.previousUseParentHandlers = logger.getUseParentHandlers();
            this.failure = failure;
            setLevel(Level.ALL);
        }

        static TestLogHandler install() {
            return install(null);
        }

        static TestLogHandler install(Throwable failure) {
            Logger logger = Logger.getLogger(ConnectionHandler.class.getName());
            TestLogHandler handler = new TestLogHandler(logger, failure);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
            logger.addHandler(handler);
            return handler;
        }

        @Override
        public void publish(LogRecord record) {
            if ((failure == null || record.getThrown() == failure)
                    && this.record.compareAndSet(null, record)) {
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
            logger.setUseParentHandlers(previousUseParentHandlers);
        }

        private LogRecord await() throws InterruptedException {
            assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
            return record.get();
        }
    }
}
