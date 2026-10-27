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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
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
import io.helidon.common.configurable.AllowList;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.common.tls.Tls;
import io.helidon.http.HttpException;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.webserver.HttpTransportObserverSupport.ConnectionObservationContext;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ResourceLock("webserver-error-log-summary")
@ResourceLock("webserver-connection-handler-log")
class ConnectionHandlerTest {

    @Test
    void testHttp10Prologue() {
        DataReader reader = DataReader.create(() -> "GET / HTTP/1.0\r\n".getBytes(StandardCharsets.US_ASCII));
        assertThat(ConnectionHandler.isHttp10Connection(reader), is(true));
    }

    @Test
    void unexpectedThrowableAndFatalObserverFailureStillCleanUp() throws Exception {
        AssertionError failure = new AssertionError("unexpected failure");
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        ListenerContext listenerContext = mock(ListenerContext.class, CALLS_REAL_METHODS);
        when(listenerContext.config()).thenReturn(listenerConfig);
        ListenerConfig virtualHostConfig = mock(ListenerConfig.class);
        when(virtualHostConfig.sni()).thenReturn(SniConfig.create());
        when(virtualHostConfig.virtualHosts()).thenReturn(List.of());
        VirtualHostRegistry virtualHosts = VirtualHostRegistry.create("server", virtualHostConfig, mock(Tls.class));
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        SocketChannel socket = mock(SocketChannel.class);
        when(socket.getRemoteAddress()).thenThrow(failure);
        ConnectionObservation transportObservation = mock(ConnectionObservation.class);
        doThrow(new AssertionError("observer failure"))
                .when(transportObservation)
                .close(ConnectionOutcome.ERROR);
        AtomicReference<ConnectionHandler> removed = new AtomicReference<>();
        ConnectionHandler handler = new ConnectionHandler(listenerContext,
                                                          Optional.of(new TrustedProxyMatcher(AllowList.builder()
                                                                                                       .allowAll(true)
                                                                                                       .build())),
                                                          token,
                                                          mock(Limit.class),
                                                          ConnectionProviders.create(List.of()),
                                                          socket,
                                                          "server",
                                                          Router.empty(),
                                                          mock(Tls.class),
                                                          virtualHosts,
                                                          transportObservation,
                                                          removed::set);

        try (TestLogHandler logHandler = TestLogHandler.install()) {
            AssertionError observerFailure = assertThrows(AssertionError.class, handler::run);

            LogRecord record = logHandler.await();
            assertThat(record.getMessage(), containsString("Unexpected throwable while handling connection"));
            assertThat(record.getThrown(), sameInstance(failure));
            assertThat(observerFailure.getMessage(), is("observer failure"));
            verify(token).ignore();
            verify(transportObservation).close(ConnectionOutcome.ERROR);
            assertThat(removed.get(), sameInstance(handler));
        }
    }

    @Test
    void socketWriterFailureIsTrace() throws Exception {
        assertConnectionFailureLevel(new SocketWriterException(), Level.FINER, ConnectionOutcome.ERROR);
    }

    @Test
    void socketWriterTimeoutIsTraceAndRecordsTimeout() throws Exception {
        assertConnectionFailureLevel(new SocketWriterException(new SocketTimeoutException("test timeout")),
                                     Level.FINER,
                                     ConnectionOutcome.TIMEOUT);
    }

    @Test
    void connectionInterruptionIsTrace() throws Exception {
        assertConnectionFailureLevel(new InterruptedException("test interruption"), Level.FINER, ConnectionOutcome.ERROR);
    }

    @Test
    void unexpectedConnectionFailureRemainsWarning() throws Exception {
        assertConnectionFailureLevel(new IllegalStateException("unexpected"), Level.WARNING, ConnectionOutcome.ERROR);
    }

    @Test
    void repeatedInnerConnectionFailuresPreserveFirstDetailsAndCleanup() throws Exception {
        ErrorLogLimiter limiter = limiter(1, 10);
        Exception first = new IllegalStateException("first diagnostic");
        try (LogCapture details = new LogCapture(ConnectionHandler.class.getName());
             LogCapture summaries = new LogCapture(ErrorLogLimiter.class.getName())) {
            for (int i = 0; i < 4; i++) {
                runConnectionFailure(first, limiter);
            }
            assertThat(details.warnings(), hasSize(1));
            assertThat(details.warnings().getFirst().getThrown(), sameInstance(first));
            assertThat(details.warnings().getFirst().getMessage(), containsString("unexpected exception [error-id="));
            limiter.flush();
            assertThat(summaries.warnings(), hasSize(1));
            assertThat(summaries.warnings().getFirst().getMessage(), containsString(
                    "Suppressed 3 internal server error log entries (per-error limit: 3, overall limit: 0, "
                            + "identity limit: 0, identity work limit: 0)."));
        }
    }

    @Test
    void overallBudgetIsSharedByAllInnerInternalFailureSites() throws Exception {
        ErrorLogLimiter limiter = limiter(10, 2);
        List<Exception> failures = List.of(new IllegalStateException("software failure"),
                                          new UncheckedIOException(new IOException("unexpected I/O failure")),
                                          RequestException.builder().status(Status.INTERNAL_SERVER_ERROR_500).build(),
                                          new HttpException("escaped internal failure", Status.INTERNAL_SERVER_ERROR_500));
        try (LogCapture details = new LogCapture(ConnectionHandler.class.getName());
             LogCapture summaries = new LogCapture(ErrorLogLimiter.class.getName())) {
            for (Exception failure : failures) {
                runConnectionFailure(failure, limiter);
            }
            assertThat(details.warnings(), hasSize(2));
            assertThat(details.warnings().get(0).getThrown(), sameInstance(failures.get(0)));
            assertThat(details.warnings().get(1).getThrown(), sameInstance(failures.get(1)));
            limiter.flush();
            assertThat(summaries.warnings(), hasSize(1));
            assertThat(summaries.warnings().getFirst().getMessage(), containsString(
                    "Suppressed 2 internal server error log entries (per-error limit: 0, overall limit: 2, "
                            + "identity limit: 0, identity work limit: 0)."));
        }
    }

    @Test
    void expectedHttpAndTransportFailuresDoNotConsumeInternalErrorBudget() throws Exception {
        ErrorLogLimiter limiter = limiter(1, 1);
        List<Exception> expected = List.of(
                RequestException.builder().status(Status.BAD_REQUEST_400).build(),
                RequestException.builder().status(Status.NOT_FOUND_404).build(),
                RequestException.builder().status(Status.SERVICE_UNAVAILABLE_503).build(),
                new HttpException("bad request", Status.BAD_REQUEST_400),
                new HttpException("not found", Status.NOT_FOUND_404),
                new HttpException("unavailable", Status.SERVICE_UNAVAILABLE_503),
                new SocketWriterException(),
                new UncheckedIOException(new SocketException("peer disconnected")),
                new InterruptedException("connection interrupted"));
        Exception unexpected = new IllegalStateException("internal failure after expected failures");
        Exception ioTimeout = new UncheckedIOException(new SocketTimeoutException("expected I/O timeout"));
        Exception wrappedTimeout = new IllegalStateException("expected timeout", new SocketTimeoutException("timeout"));
        try (LogCapture details = new LogCapture(ConnectionHandler.class.getName());
             LogCapture summaries = new LogCapture(ErrorLogLimiter.class.getName())) {
            for (Exception failure : expected) {
                runConnectionFailure(failure, limiter);
            }
            runConnectionFailure(ioTimeout, limiter, ConnectionOutcome.TIMEOUT);
            runConnectionFailure(wrappedTimeout, limiter, ConnectionOutcome.TIMEOUT);
            runConnectionFailure(unexpected, limiter);
            runConnectionFailure(unexpected, limiter);
            assertThat(details.warnings(), hasSize(9));
            assertThat(details.warnings().get(6).getThrown(), sameInstance(ioTimeout));
            assertThat(details.warnings().get(7).getThrown(), sameInstance(wrappedTimeout));
            assertThat(details.warnings().getLast().getThrown(), sameInstance(unexpected));
            assertThat(details.records.stream().filter(it -> it.getThrown() != null && expected.contains(it.getThrown()))
                               .map(LogRecord::getThrown).toList(), hasSize(9));
            limiter.flush();
            assertThat(summaries.warnings(), hasSize(1));
            assertThat(summaries.warnings().getFirst().getMessage(), containsString(
                    "Suppressed 1 internal server error log entries (per-error limit: 0, overall limit: 1, "
                            + "identity limit: 0, identity work limit: 0)."));
        }
    }

    @Test
    void firstTransportOutcomeWins() throws Exception {
        AssertionError failure = new AssertionError("unexpected failure");
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(listenerConfig);
        ListenerConfig virtualHostConfig = mock(ListenerConfig.class);
        when(virtualHostConfig.sni()).thenReturn(SniConfig.create());
        when(virtualHostConfig.virtualHosts()).thenReturn(List.of());
        VirtualHostRegistry virtualHosts = VirtualHostRegistry.create("server", virtualHostConfig, mock(Tls.class));
        ConnectionObservation transportObservation = mock(ConnectionObservation.class);
        SocketChannel socket = mock(SocketChannel.class);
        when(socket.getRemoteAddress()).thenThrow(failure);
        ConnectionHandler handler = new ConnectionHandler(listenerContext,
                                                          Optional.of(new TrustedProxyMatcher(AllowList.builder()
                                                                                                       .allowAll(true)
                                                                                                       .build())),
                                                          mock(LimitAlgorithm.Token.class),
                                                          mock(Limit.class),
                                                          ConnectionProviders.create(List.of()),
                                                          socket,
                                                          "server",
                                                          Router.empty(),
                                                          mock(Tls.class),
                                                          virtualHosts,
                                                          transportObservation,
                                                          it -> { });

        handler.httpTransportOutcome(ConnectionOutcome.REMOTE_CLOSE);
        handler.httpTransportOutcome(ConnectionOutcome.ERROR);
        handler.run();

        verify(transportObservation).close(ConnectionOutcome.REMOTE_CLOSE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void abortSocketClosesChannelAndPreservesTransportOutcome(boolean useNio) throws Exception {
        ListenerConfig listenerConfig = ListenerConfig.builder()
                .useNio(useNio)
                .writeQueueLength(1)
                .build();
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(listenerConfig);
        Tls tls = Tls.builder().enabled(false).build();
        VirtualHostRegistry virtualHosts = VirtualHostRegistry.create("server", listenerConfig, tls);
        ConnectionObservation observation = mock(ConnectionObservation.class);
        CountDownLatch reading = new CountDownLatch(1);
        AtomicReference<ConnectionHandler> removed = new AtomicReference<>();

        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            try (SocketChannel client = SocketChannel.open(listener.getLocalAddress());
                 SocketChannel accepted = listener.accept()) {
                ConnectionHandler handler = new ConnectionHandler(listenerContext,
                                                                  Optional.empty(),
                                                                  mock(LimitAlgorithm.Token.class),
                                                                  mock(Limit.class),
                                                                  ConnectionProviders.create(List.of(
                                                                          new ReadingConnectionSelector(reading))),
                                                                  accepted,
                                                                  "server",
                                                                  Router.empty(),
                                                                  tls,
                                                                  virtualHosts,
                                                                  observation,
                                                                  removed::set);
                // A platform thread also exercises the legacy socket path without relying on interruption.
                Thread thread = Thread.ofPlatform().start(handler::run);
                try {
                    assertThat("connection reached its socket read", reading.await(5, TimeUnit.SECONDS), is(true));
                    assertThat(accepted.isOpen(), is(true));

                    handler.abortSocket();

                    assertThat("abort closes the physical channel", accepted.isOpen(), is(false));
                    thread.join(TimeUnit.SECONDS.toMillis(5));
                    assertThat("socket read and handler cleanup finish after abort", thread.isAlive(), is(false));
                    verify(observation).close(ConnectionOutcome.REMOTE_CLOSE);
                    assertThat(removed.get(), sameInstance(handler));
                } finally {
                    handler.abortSocket();
                    client.close();
                    thread.join(TimeUnit.SECONDS.toMillis(5));
                    assertThat("handler thread must be stopped", thread.isAlive(), is(false));
                }
            }
        }
    }

    private static void assertConnectionFailureLevel(Exception failure,
                                                     Level expectedLevel,
                                                     ConnectionOutcome expectedOutcome) throws Exception {
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        when(listenerConfig.useNio()).thenReturn(true);
        ListenerContext listenerContext = mock(ListenerContext.class, CALLS_REAL_METHODS);
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
        ConnectionObservation transportObservation = mock(ConnectionObservation.class);
        ConnectionHandler handler = new ConnectionHandler(listenerContext,
                                                          Optional.empty(),
                                                          mock(LimitAlgorithm.Token.class),
                                                          mock(Limit.class),
                                                          connectionProviders,
                                                          socket,
                                                          "server",
                                                          Router.empty(),
                                                          tls,
                                                          virtualHosts,
                                                          transportObservation,
                                                          _ -> { });

        try (TestLogHandler logHandler = TestLogHandler.install(failure)) {
            Thread thread = Thread.ofVirtual().start(handler::run);
            try {
                LogRecord record = logHandler.await();
                thread.join(TimeUnit.SECONDS.toMillis(5));

                assertThat(thread.isAlive(), is(false));
                assertThat(record.getThrown(), sameInstance(failure));
                assertThat(record.getLevel(), is(expectedLevel));
                verify(transportObservation).close(expectedOutcome);
            } finally {
                thread.interrupt();
                thread.join(TimeUnit.SECONDS.toMillis(5));
                assertThat("Test connection thread terminated", thread.isAlive(), is(false));
            }
        }
    }

    private static ErrorLogLimiter limiter(int perError, int overall) {
        return new ErrorLogLimiter(ErrorLogConfig.builder()
                                           .maxPerError(perError)
                                           .maxTotal(overall)
                                           .build(), 0);
    }

    private static void runConnectionFailure(Exception failure, ErrorLogLimiter limiter) throws Exception {
        runConnectionFailure(failure, limiter, ConnectionOutcome.ERROR);
    }

    private static void runConnectionFailure(Exception failure,
                                             ErrorLogLimiter limiter,
                                             ConnectionOutcome outcome) throws Exception {
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        when(listenerConfig.useNio()).thenReturn(true);
        ListenerContext listenerContext = mock(ListenerContext.class, CALLS_REAL_METHODS);
        when(listenerContext.config()).thenReturn(listenerConfig);
        doAnswer(invocation -> {
            limiter.log(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3), 0);
            return null;
        }).when(listenerContext).logError(any(System.Logger.class), any(System.Logger.Level.class),
                                         any(String.class), any(Throwable.class));
        SocketChannel socket = mock(SocketChannel.class, withSettings().useConstructor(SelectorProvider.provider()));
        when(socket.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        when(socket.getLocalAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 80));
        ListenerConfig virtualHostConfig = mock(ListenerConfig.class);
        when(virtualHostConfig.sni()).thenReturn(SniConfig.create());
        when(virtualHostConfig.virtualHosts()).thenReturn(List.of());
        Tls tls = mock(Tls.class);
        TestConnectionSelector selector = new TestConnectionSelector(failure);
        LimitAlgorithm.Token token = mock(LimitAlgorithm.Token.class);
        ConnectionObservation observation = mock(ConnectionObservation.class);
        AtomicReference<ConnectionHandler> removed = new AtomicReference<>();
        ConnectionHandler handler = new ConnectionHandler(listenerContext,
                                                          Optional.empty(),
                                                          token,
                                                          mock(Limit.class),
                                                          ConnectionProviders.create(List.of(selector)),
                                                          socket,
                                                          "server",
                                                          Router.empty(),
                                                          tls,
                                                          VirtualHostRegistry.create("server", virtualHostConfig, tls),
                                                          observation,
                                                          removed::set);
        Thread thread = Thread.ofVirtual().start(handler::run);
        try {
            thread.join(TimeUnit.SECONDS.toMillis(5));
            assertThat("Connection handling terminated", thread.isAlive(), is(false));
            verify(token).success();
            assertThat("Connection socket closed", socket.isOpen(), is(false));
            verify(observation).close(outcome);
            assertThat(removed.get(), sameInstance(handler));
        } finally {
            thread.interrupt();
            thread.join(TimeUnit.SECONDS.toMillis(5));
            assertThat("Test connection thread terminated", thread.isAlive(), is(false));
        }
    }

    private record ReadingConnectionSelector(CountDownLatch reading) implements ServerConnectionSelector {
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
                public void handle(Limit limit) {
                    if (!(ctx instanceof ConnectionObservationContext observedContext)) {
                        throw new IllegalStateException("Connection does not support transport observation");
                    }
                    observedContext.httpTransportOutcome(ConnectionOutcome.REMOTE_CLOSE);
                    reading.countDown();
                    ctx.dataReader().ensureAvailable();
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

    private static final class LogCapture extends Handler implements AutoCloseable {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();
        private final Logger logger;
        private final Level previousLevel;
        private final boolean previousUseParentHandlers;

        private LogCapture(String name) {
            logger = Logger.getLogger(name);
            previousLevel = logger.getLevel();
            previousUseParentHandlers = logger.getUseParentHandlers();
            setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
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

        private List<LogRecord> warnings() {
            return records.stream().filter(it -> it.getLevel() == Level.WARNING).toList();
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
