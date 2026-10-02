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

package io.helidon.webserver.http1;

import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.BadRequestException;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.Method;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.HttpTransportObserverSupport.ConnectionObservationContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http1.spi.Http1RoutedUpgrader;
import io.helidon.webserver.http1.spi.Http1Upgrader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.LOCAL_CLOSE;
import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.REMOTE_CLOSE;
import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Initiator.REMOTE;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.REJECTED;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class Http1ConnectionTest {
    private static final byte[] CONNECTION_CLOSE_REQUEST = ("""
            GET / HTTP/1.1\r
            Host: localhost\r
            Connection: close\r
            \r
            """).getBytes(StandardCharsets.UTF_8);

    @Test
    void rejectedHeadUsesParsedRequestMethod() throws InterruptedException {
        String response = rejectedRequest(Method.HEAD_NAME,
                                          DirectHandler.TransportResponse.builder()
                                                  .status(Status.SERVICE_UNAVAILABLE_503)
                                                  .header(HeaderNames.CONTENT_LENGTH, "23")
                                                  .build());

        assertAll(
                () -> assertThat(response, containsString("HTTP/1.1 503 Service Unavailable\r\n")),
                () -> assertThat(response, containsString("Content-Length: 23\r\n")),
                () -> assertThat(response, endsWith("\r\n\r\n"))
        );
    }

    @Test
    void rejectedHeadDoesNotSendHandlerEntity() throws InterruptedException {
        String response = rejectedRequest(Method.HEAD_NAME,
                                          DirectHandler.TransportResponse.builder()
                                                  .status(Status.SERVICE_UNAVAILABLE_503)
                                                  .entity("error")
                                                  .build());

        assertAll(
                () -> assertThat(response, containsString("Content-Length: 5\r\n")),
                () -> assertThat(response, endsWith("\r\n\r\n"))
        );
    }

    @Test
    void rejectedGetStillSendsHandlerEntity() throws InterruptedException {
        String response = rejectedRequest(Method.GET_NAME,
                                          DirectHandler.TransportResponse.builder()
                                                  .status(Status.SERVICE_UNAVAILABLE_503)
                                                  .entity("error")
                                                  .build());

        assertAll(
                () -> assertThat(response, containsString("Content-Length: 5\r\n")),
                () -> assertThat(response, endsWith("\r\n\r\nerror"))
        );
    }

    @Test
    void directErrorResponseFlushesBeforeConnectionReturns() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        BlockingDataWriter writer = new BlockingDataWriter();
        DirectHandlers directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.OTHER,
                            (_, _, _, _, _) -> DirectHandler.TransportResponse.builder()
                                    .status(Status.SERVICE_UNAVAILABLE_503)
                                    .entity("error")
                                    .build())
                .build();
        Limit limit = mock(Limit.class);
        when(limit.tryAcquireOutcome(true)).thenReturn(LimitAlgorithm.Outcome.immediateRejection("test", "test"));
        Http1Connection connection = createConnection(DataReader.create(() -> CONNECTION_CLOSE_REQUEST),
                                                      writer,
                                                      directHandlers,
                                                      Router.empty());
        try {
            Future<?> connectionTask = executor.submit(() -> {
                connection.handle(limit);
                return null;
            });

            assertThat("Direct error response did not reach the final flush",
                       writer.flushStarted.await(10, TimeUnit.SECONDS),
                       is(true));
            assertThat("Direct error response was not queued before the final flush",
                       writer.writeCalled.getCount(),
                       is(0L));
            assertThat("Connection returned before the direct error response was flushed",
                       connectionTask.isDone(),
                       is(false));
            writer.releaseFlush.countDown();
            connectionTask.get(2, TimeUnit.SECONDS);
        } finally {
            writer.releaseFlush.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void forcedCloseInterruptSkipsDirectErrorFlush() {
        byte[] requestBytes = ("""
                POST / HTTP/1.1\r
                Host: localhost\r
                Connection: close\r
                Content-Length: 1\r
                \r
                """).getBytes(StandardCharsets.US_ASCII);
        AtomicReference<Http1Connection> connectionRef = new AtomicReference<>();
        BlockingDataWriter writer = new BlockingDataWriter();
        Router router = Router.builder()
                .addRouting(HttpRouting.builder()
                                    .post("/", (_, res) -> {
                                        connectionRef.get().close(true);
                                        res.send("done");
                                    }))
                .build();
        Http1Connection connection = createConnection(DataReader.create(() -> requestBytes),
                                                      writer,
                                                      DirectHandlers.create(),
                                                      router);
        connectionRef.set(connection);
        writer.releaseFlush.countDown();
        try {
            CloseConnectionException exception = assertThrows(CloseConnectionException.class,
                                                              () -> connection.handle(FixedLimit.create()));

            assertAll(
                    () -> assertThat(exception.getCause(), instanceOf(InterruptedException.class)),
                    () -> assertThat("Forced close interrupt was cleared",
                                     Thread.currentThread().isInterrupted(),
                                     is(true)),
                    () -> assertThat("Forced close reached the direct error response flush",
                                     writer.flushStarted.getCount(),
                                     is(1L))
            );
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void continueImmediatelyWrapsSocketWriterExceptionFromSmartWriter() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        SocketWriter writer = smartFailingWriter(executor);
        try {
            Http1Connection connection = createConnection(writer);

            ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                               connection::writeContinue);

            assertAll(
                    () -> assertThat(exception.getCause(), instanceOf(SocketWriterException.class)),
                    () -> assertThat(exception.getCause().getCause(), instanceOf(UncheckedIOException.class)),
                    () -> assertThat(exception.getCause().getCause().getCause(), instanceOf(SocketException.class))
            );
        } finally {
            writer.close();
            executor.shutdownNow();
        }
    }

    @Test
    void gracefulCloseDoesNotInterruptClosingResponseFlush() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        BlockingDataWriter writer = new BlockingDataWriter();
        Http1Connection connection = createConnection(DataReader.create(() -> CONNECTION_CLOSE_REQUEST),
                                                      writer,
                                                      DirectHandlers.create(),
                                                      Router.builder()
                                                              .addRouting(HttpRouting.builder()
                                                                                  .get("/", (_, res) -> res.send("done")))
                                                              .build());
        try {
            Future<?> connectionTask = executor.submit(() -> {
                connection.handle(FixedLimit.create());
                return null;
            });

            assertThat("Closing response did not reach the final flush",
                       writer.flushStarted.await(10, TimeUnit.SECONDS),
                       is(true));
            connection.close(false);
            writer.releaseFlush.countDown();
            connectionTask.get(2, TimeUnit.SECONDS);

            assertThat("Graceful close interrupted the final response flush", writer.interrupted, is(false));
        } finally {
            writer.releaseFlush.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void peerCloseWhileReadingHeadersClosesConnection() {
        AtomicReference<byte[]> input =
                new AtomicReference<>("GET / HTTP/1.1\r\nHost:".getBytes(StandardCharsets.UTF_8));
        Http1Connection connection = createConnection(DataReader.create(() -> input.getAndSet(null)),
                                                      mock(DataWriter.class),
                                                      DirectHandlers.create(),
                                                      Router.empty());

        CloseConnectionException exception = assertThrows(CloseConnectionException.class,
                                                          () -> connection.handle(FixedLimit.create()));

        assertThat(exception.getCause(), instanceOf(DataReader.InsufficientDataAvailableException.class));
    }

    @Test
    void unobservedKeepAliveRequestsCompleteWithoutRecordingOutcomes() throws InterruptedException {
        byte[] requests = ("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
                + "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        AtomicInteger handled = new AtomicInteger();
        ConnectionContext ctx = connectionContext(
                DataReader.create(() -> requests),
                mock(DataWriter.class),
                DirectHandlers.create(),
                Router.builder()
                        .addRouting(HttpRouting.builder().get("/", (_, res) -> {
                            handled.incrementAndGet();
                            res.send("done");
                        }))
                        .build(),
                ConnectionObservation.noop());
        var connection = new Http1Connection(ctx, Http1Config.create(), Map.of());

        connection.handle(FixedLimit.create());

        assertThat(handled.get(), is(2));
        verify((ConnectionObservationContext) ctx, never()).httpTransportOutcome(any());
    }

    @Test
    void unobservedReadTimeoutStillPropagates() {
        var failure = new ServerConnectionException("Read timed out", new SocketTimeoutException("timeout"));
        Http1Connection connection = createConnection(
                DataReader.create(() -> {
                    throw failure;
                }),
                mock(DataWriter.class),
                DirectHandlers.create(),
                Router.empty());

        ServerConnectionException thrown = assertThrows(ServerConnectionException.class,
                                                        () -> connection.handle(FixedLimit.create()));

        assertThat(thrown, is(failure));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void badRequestWithTimeoutCauseRetainsResponse(boolean observed) throws InterruptedException {
        DataWriter writer = mock(DataWriter.class);
        ConnectionObservation observation = observed ? mock(ConnectionObservation.class) : ConnectionObservation.noop();
        StreamObservation stream = mock(StreamObservation.class);
        if (observed) {
            when(observation.streamOpened(BIDIRECTIONAL, REMOTE)).thenReturn(stream);
        }
        ConnectionContext ctx = connectionContext(
                DataReader.create(() -> CONNECTION_CLOSE_REQUEST),
                writer,
                DirectHandlers.create(),
                Router.builder()
                        .addRouting(HttpRouting.builder().get("/", (_, _) -> {
                            throw new BadRequestException("Invalid request", new SocketTimeoutException("backend timeout"));
                        }))
                        .build(),
                observation);
        var connection = new Http1Connection(ctx, Http1Config.create(), Map.of());

        connection.handle(FixedLimit.create());

        ArgumentCaptor<BufferData> responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String response = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
        assertThat(response, containsString("HTTP/1.1 400 Bad Request\r\n"));
        verify(writer).flush();
        if (observed) {
            verify(stream).close(StreamOutcome.ERROR);
            verify((ConnectionObservationContext) ctx).httpTransportOutcome(ConnectionOutcome.ERROR);
            verify((ConnectionObservationContext) ctx, never()).httpTransportOutcome(ConnectionOutcome.TIMEOUT);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void requestRejectionWithTimeoutCauseRetainsResponse(boolean observed) throws InterruptedException {
        DataWriter writer = mock(DataWriter.class);
        ConnectionObservation observation = observed ? mock(ConnectionObservation.class) : ConnectionObservation.noop();
        StreamObservation stream = mock(StreamObservation.class);
        if (observed) {
            when(observation.streamOpened(BIDIRECTIONAL, REMOTE)).thenReturn(stream);
        }
        ConnectionContext ctx = connectionContext(DataReader.create(() -> CONNECTION_CLOSE_REQUEST),
                                                  writer,
                                                  DirectHandlers.create(),
                                                  Router.empty(),
                                                  observation);
        var connection = new Http1Connection(ctx, Http1Config.create(), Map.of());
        Limit limit = mock(Limit.class);
        when(limit.tryAcquireOutcome(true)).thenThrow(RequestException.builder()
                                                             .type(DirectHandler.EventType.OTHER)
                                                             .status(Status.SERVICE_UNAVAILABLE_503)
                                                             .cause(new SocketTimeoutException("admission timeout"))
                                                             .build());

        connection.handle(limit);

        ArgumentCaptor<BufferData> responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String response = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
        assertThat(response, containsString("HTTP/1.1 503 Service Unavailable\r\n"));
        verify(writer).flush();
        if (observed) {
            verify(stream).close(REJECTED);
            verify((ConnectionObservationContext) ctx).httpTransportOutcome(LOCAL_CLOSE);
            verify((ConnectionObservationContext) ctx, never()).httpTransportOutcome(ConnectionOutcome.TIMEOUT);
        }
    }

    @ParameterizedTest(name = "observed={0}, flushTimeout={1}")
    @CsvSource({"true, false", "false, false", "true, true", "false, true"})
    void errorResponseTimeoutPropagatesWithConnectionTimeout(boolean observed, boolean flushTimeout) {
        var timeout = new UncheckedIOException(new SocketTimeoutException("response write timed out"));
        DataWriter writer = mock(DataWriter.class);
        if (flushTimeout) {
            doThrow(timeout).when(writer).flush();
        } else {
            doThrow(timeout).when(writer).write(any(BufferData.class));
        }
        ConnectionObservation observation = observed ? mock(ConnectionObservation.class) : ConnectionObservation.noop();
        StreamObservation stream = mock(StreamObservation.class);
        if (observed) {
            when(observation.streamOpened(BIDIRECTIONAL, REMOTE)).thenReturn(stream);
        }
        ConnectionContext ctx = connectionContext(
                DataReader.create(() -> CONNECTION_CLOSE_REQUEST),
                writer,
                DirectHandlers.create(),
                Router.builder()
                        .addRouting(HttpRouting.builder().get("/", (_, _) -> {
                            throw new BadRequestException("Invalid request");
                        }))
                        .build(),
                observation);
        var connection = new Http1Connection(ctx, Http1Config.create(), Map.of());

        CloseConnectionException thrown = assertThrows(CloseConnectionException.class,
                                                       () -> connection.handle(FixedLimit.create()));

        assertThat("The original write timeout must propagate as the close cause", thrown.getCause(), sameInstance(timeout));
        ArgumentCaptor<BufferData> responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        String response = new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
        assertThat(response, containsString("HTTP/1.1 400 Bad Request\r\n"));
        if (flushTimeout) {
            verify(writer).flush();
        } else {
            verify(writer, never()).flush();
        }
        verifyNoMoreInteractions(writer);
        if (observed) {
            verify(stream).close(StreamOutcome.ERROR);
            verify((ConnectionObservationContext) ctx).httpTransportOutcome(ConnectionOutcome.TIMEOUT);
            verify((ConnectionObservationContext) ctx, never()).httpTransportOutcome(ConnectionOutcome.ERROR);
        } else {
            verifyZeroInteractions(stream);
        }
    }

    @Test
    void errorResponseHandlerTimeoutRemainsConnectionError() {
        var failure = new IllegalStateException("Cannot create error response", new SocketTimeoutException("backend timeout"));
        DataWriter writer = mock(DataWriter.class);
        DirectHandlers directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.OTHER, (_, _, _, _, _) -> {
                    throw failure;
                })
                .build();
        ConnectionObservation observation = mock(ConnectionObservation.class);
        StreamObservation stream = mock(StreamObservation.class);
        when(observation.streamOpened(BIDIRECTIONAL, REMOTE)).thenReturn(stream);
        ConnectionContext ctx = connectionContext(DataReader.create(() -> CONNECTION_CLOSE_REQUEST),
                                                  writer,
                                                  directHandlers,
                                                  Router.empty(),
                                                  observation);
        var connection = new Http1Connection(ctx, Http1Config.create(), Map.of());
        Limit limit = mock(Limit.class);
        when(limit.tryAcquireOutcome(true)).thenReturn(LimitAlgorithm.Outcome.immediateRejection("test", "test"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> connection.handle(limit));

        assertThat(thrown, sameInstance(failure));
        verifyZeroInteractions(writer);
        verify(stream).close(StreamOutcome.ERROR);
        verify((ConnectionObservationContext) ctx).httpTransportOutcome(ConnectionOutcome.ERROR);
        verify((ConnectionObservationContext) ctx, never()).httpTransportOutcome(ConnectionOutcome.TIMEOUT);
    }

    @Test
    void requestCloseCompletesStreamAndRecordsRemoteClose() throws InterruptedException {
        ObservedConnection observed = observedConnection(
                DataReader.create(() -> CONNECTION_CLOSE_REQUEST),
                mock(DataWriter.class),
                Router.builder()
                        .addRouting(HttpRouting.builder().get("/", (_, res) -> res.send("done")))
                        .build());

        observed.connection().handle(FixedLimit.create());

        verify(observed.connectionObservation()).protocolSelected(PROTOCOL_HTTP_1_1);
        verify(observed.connectionObservation()).streamOpened(BIDIRECTIONAL, REMOTE);
        verify(observed.streamObservation()).close(COMPLETED);
        verify(observed.context()).httpTransportOutcome(REMOTE_CLOSE);
    }

    @Test
    void orderlyEofCompletesStreamAndRecordsRemoteClose() throws InterruptedException {
        AtomicBoolean supplied = new AtomicBoolean();
        byte[] request = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        ObservedConnection observed = observedConnection(
                DataReader.create(() -> supplied.compareAndSet(false, true) ? request : null),
                mock(DataWriter.class),
                Router.builder()
                        .addRouting(HttpRouting.builder().get("/", (_, res) -> res.send("done")))
                        .build());

        assertThrows(CloseConnectionException.class, () -> observed.connection().handle(FixedLimit.create()));

        verify(observed.streamObservation()).close(COMPLETED);
        verify(observed.context()).httpTransportOutcome(REMOTE_CLOSE);
    }

    @Test
    void responseCloseCompletesStreamAndRecordsLocalClose() throws InterruptedException {
        byte[] request = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        ObservedConnection observed = observedConnection(
                DataReader.create(() -> request),
                mock(DataWriter.class),
                Router.builder()
                        .addRouting(HttpRouting.builder()
                                            .get("/", (_, res) -> res.header(HeaderValues.CONNECTION_CLOSE).send("done")))
                        .build());

        observed.connection().handle(FixedLimit.create());

        verify(observed.streamObservation()).close(COMPLETED);
        verify(observed.context()).httpTransportOutcome(LOCAL_CLOSE);
    }

    @Test
    void rejectedRequestRecordsRejectedStreamAndLocalClose() throws InterruptedException {
        byte[] request = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        ObservedConnection observed = observedConnection(DataReader.create(() -> request),
                                                         mock(DataWriter.class),
                                                         Router.empty());
        Limit limit = mock(Limit.class);
        when(limit.tryAcquireOutcome(true)).thenReturn(LimitAlgorithm.Outcome.immediateRejection("test", "test"));

        observed.connection().handle(limit);

        verify(observed.streamObservation()).close(REJECTED);
        verify(observed.context()).httpTransportOutcome(LOCAL_CLOSE);
    }

    @Test
    void declinedUpgradeThenRejectedRequestRecordsRejectedStreamAndLocalClose() throws InterruptedException {
        byte[] request = "GET / HTTP/1.1\r\nHost: localhost\r\nUpgrade: test\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII);
        Http1Upgrader upgrader = mock(Http1Upgrader.class);
        when(upgrader.upgrade(any(), any(), any())).thenReturn(null);
        ObservedConnection observed = observedConnection(DataReader.create(() -> request),
                                                         mock(DataWriter.class),
                                                         Router.empty(),
                                                         Map.of("test", upgrader));
        Limit limit = mock(Limit.class);
        when(limit.tryAcquireOutcome(true)).thenReturn(LimitAlgorithm.Outcome.immediateRejection("test", "test"));

        observed.connection().handle(limit);

        verify(observed.streamObservation()).close(REJECTED);
        verify(observed.context()).httpTransportOutcome(LOCAL_CLOSE);
    }

    @Test
    void internalErrorBeforeRoutingRecordsErrorStreamAndConnection() throws InterruptedException {
        byte[] request = "GET / HTTP/1.1\r\nHost: localhost\r\nUpgrade: test\r\n\r\n"
                .getBytes(StandardCharsets.US_ASCII);
        Http1RoutedUpgrader upgrader = mock(Http1RoutedUpgrader.class);
        doThrow(new IllegalStateException("upgrade failed"))
                .when(upgrader)
                .routedUpgrade(any(), any(), any());

        ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
        StreamObservation streamObservation = mock(StreamObservation.class);
        when(connectionObservation.streamOpened(BIDIRECTIONAL, REMOTE)).thenReturn(streamObservation);
        ConnectionContext ctx = connectionContext(DataReader.create(() -> request),
                                                  mock(DataWriter.class),
                                                  DirectHandlers.create(),
                                                  Router.empty(),
                                                  connectionObservation);
        Http1Connection connection = new Http1Connection(ctx, Http1Config.create(), Map.of("test", upgrader));

        connection.handle(FixedLimit.create());

        verify(streamObservation).close(io.helidon.http.HttpTransportObserver.StreamOutcome.ERROR);
        verify((ConnectionObservationContext) ctx).httpTransportOutcome(
                io.helidon.http.HttpTransportObserver.ConnectionOutcome.ERROR);
    }

    @Test
    void timeoutBeforeApplicationProcessingCancelsStreamAndRecordsTimeout() {
        UncheckedIOException timeout = new UncheckedIOException(new SocketTimeoutException("read timed out"));
        AtomicBoolean prologueSupplied = new AtomicBoolean();
        DataReader reader = DataReader.create(() -> {
            if (prologueSupplied.compareAndSet(false, true)) {
                return "GET / HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII);
            }
            throw timeout;
        });
        DataWriter writer = mock(DataWriter.class);
        ObservedConnection observed = observedConnection(reader, writer, Router.empty());

        assertThrows(UncheckedIOException.class, () -> observed.connection().handle(FixedLimit.create()));

        verify(observed.streamObservation()).close(
                io.helidon.http.HttpTransportObserver.StreamOutcome.CANCELLED);
        verify(observed.context()).httpTransportOutcome(
                io.helidon.http.HttpTransportObserver.ConnectionOutcome.TIMEOUT);
        verifyZeroInteractions(writer);
    }

    @Test
    void timeoutDuringApplicationProcessingErrorsStreamAndRecordsTimeout() {
        UncheckedIOException timeout = new UncheckedIOException(new SocketTimeoutException("read timed out"));
        HttpRouting routing = mock(HttpRouting.class);
        when(routing.security()).thenReturn(HttpSecurity.create());
        doThrow(timeout)
                .when(routing)
                .route(any(), any(), any());
        Router router = mock(Router.class);
        doReturn(routing)
                .when(router)
                .routing(eq(HttpRouting.class), any(HttpRouting.class));
        DataWriter writer = mock(DataWriter.class);
        ObservedConnection observed = observedConnection(
                DataReader.create(() -> "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
                        .getBytes(StandardCharsets.US_ASCII)),
                writer,
                router);

        assertThrows(UncheckedIOException.class, () -> observed.connection().handle(FixedLimit.create()));

        verify(observed.streamObservation()).close(io.helidon.http.HttpTransportObserver.StreamOutcome.ERROR);
        verify(observed.context()).httpTransportOutcome(
                io.helidon.http.HttpTransportObserver.ConnectionOutcome.TIMEOUT);
        verifyZeroInteractions(writer);
    }

    @Test
    void routedFailureRecordsErrorStreamAndConnection() throws InterruptedException {
        byte[] request = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        HttpRouting routing = mock(HttpRouting.class);
        when(routing.security()).thenReturn(HttpSecurity.create());
        doThrow(new IllegalStateException("routing failed"))
                .when(routing)
                .route(any(), any(), any());
        Router router = mock(Router.class);
        doReturn(routing)
                .when(router)
                .routing(eq(HttpRouting.class), any(HttpRouting.class));
        ObservedConnection observed = observedConnection(
                DataReader.create(() -> request),
                mock(DataWriter.class),
                router);

        observed.connection().handle(FixedLimit.create());

        verify(observed.streamObservation()).close(io.helidon.http.HttpTransportObserver.StreamOutcome.ERROR);
        verify(observed.context()).httpTransportOutcome(
                io.helidon.http.HttpTransportObserver.ConnectionOutcome.ERROR);
    }

    @Test
    void closingResponseFlushFailureRecordsErrorStreamAndConnection() {
        DataWriter writer = mock(DataWriter.class);
        doThrow(new IllegalStateException("flush failed")).when(writer).flush();
        ObservedConnection observed = observedConnection(
                DataReader.create(() -> CONNECTION_CLOSE_REQUEST),
                writer,
                Router.builder()
                        .addRouting(HttpRouting.builder().get("/", (_, res) -> res.send("done")))
                        .build());

        assertThrows(CloseConnectionException.class, () -> observed.connection().handle(FixedLimit.create()));

        verify(observed.streamObservation()).close(io.helidon.http.HttpTransportObserver.StreamOutcome.ERROR);
        verify(observed.context()).httpTransportOutcome(
                io.helidon.http.HttpTransportObserver.ConnectionOutcome.ERROR);
    }

    private static Http1Connection createConnection(DataWriter dataWriter) {
        return createConnection(mock(DataReader.class), dataWriter, DirectHandlers.create(), Router.empty());
    }

    private static Http1Connection createConnection(DataReader dataReader,
                                                    DataWriter dataWriter,
                                                    DirectHandlers directHandlers,
                                                    Router router) {
        ConnectionContext ctx = connectionContext(dataReader,
                                                  dataWriter,
                                                  directHandlers,
                                                  router,
                                                  ConnectionObservation.noop());
        return new Http1Connection(ctx,
                                   Http1Config.builder()
                                           .continueImmediately(true)
                                           .build(),
                                   Map.of());
    }

    private static ObservedConnection observedConnection(DataReader dataReader,
                                                         DataWriter dataWriter,
                                                         Router router) {
        return observedConnection(dataReader, dataWriter, router, Map.of());
    }

    private static ObservedConnection observedConnection(DataReader dataReader,
                                                         DataWriter dataWriter,
                                                         Router router,
                                                         Map<String, Http1Upgrader> upgradeProviderMap) {
        ConnectionObservation connectionObservation = mock(ConnectionObservation.class);
        StreamObservation streamObservation = mock(StreamObservation.class);
        when(connectionObservation.streamOpened(BIDIRECTIONAL, REMOTE)).thenReturn(streamObservation);
        ConnectionContext ctx = connectionContext(dataReader,
                                                  dataWriter,
                                                  DirectHandlers.create(),
                                                  router,
                                                  connectionObservation);
        Http1Connection connection = new Http1Connection(ctx, Http1Config.create(), upgradeProviderMap);
        return new ObservedConnection(connection,
                                      (ConnectionObservationContext) ctx,
                                      connectionObservation,
                                      streamObservation);
    }

    private static ConnectionContext connectionContext(DataReader dataReader,
                                                       DataWriter dataWriter,
                                                       DirectHandlers directHandlers,
                                                       Router router,
                                                       ConnectionObservation connectionObservation) {
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(ContentEncodingContext.create());
        when(listenerContext.config()).thenReturn(WebServer.builder().buildPrototype());
        when(listenerContext.directHandlers()).thenReturn(directHandlers);

        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());

        ConnectionContext ctx = mock(ConnectionContext.class,
                                     withSettings().extraInterfaces(ConnectionObservationContext.class));
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.dataWriter()).thenReturn(dataWriter);
        when(ctx.dataReader()).thenReturn(dataReader);
        when(ctx.router()).thenReturn(router);
        when(ctx.remotePeer()).thenReturn(peerInfo);
        when(ctx.localPeer()).thenReturn(peerInfo);
        when(((ConnectionObservationContext) ctx).httpTransportObservation()).thenReturn(connectionObservation);

        return ctx;
    }

    private static String rejectedRequest(String method,
                                          DirectHandler.TransportResponse directResponse) throws InterruptedException {
        byte[] requestBytes = (method + " / HTTP/1.1\r\nHost: localhost\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        DataReader reader = DataReader.create(() -> requestBytes);
        DataWriter writer = mock(DataWriter.class);
        DirectHandlers directHandlers = DirectHandlers.builder()
                .addHandler(DirectHandler.EventType.OTHER, (_, _, _, _, _) -> directResponse)
                .build();
        Limit limit = mock(Limit.class);
        when(limit.tryAcquireOutcome(true)).thenReturn(LimitAlgorithm.Outcome.immediateRejection("test", "test"));

        createConnection(reader, writer, directHandlers, Router.empty()).handle(limit);

        ArgumentCaptor<BufferData> responseBuffer = ArgumentCaptor.forClass(BufferData.class);
        verify(writer).write(responseBuffer.capture());
        return new String(responseBuffer.getValue().readBytes(), StandardCharsets.ISO_8859_1);
    }

    private static SocketWriter smartFailingWriter(ExecutorService executor) {
        HelidonSocket socket = mock(HelidonSocket.class);
        when(socket.socketId()).thenReturn("test");
        when(socket.childSocketId()).thenReturn("child");
        doThrow(new UncheckedIOException(new SocketException("Broken pipe")))
                .when(socket)
                .write(any(BufferData.class));
        return SocketWriter.create(executor, socket, 2, true);
    }

    private record ObservedConnection(Http1Connection connection,
                                      ConnectionObservationContext context,
                                      ConnectionObservation connectionObservation,
                                      StreamObservation streamObservation) {
    }

    private static final class BlockingDataWriter implements DataWriter {
        private final CountDownLatch writeCalled = new CountDownLatch(1);
        private final CountDownLatch flushStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFlush = new CountDownLatch(1);
        private volatile boolean interrupted;

        @Override
        public void write(BufferData... buffers) {
        }

        @Override
        public void write(BufferData buffer) {
            writeCalled.countDown();
        }

        @Override
        public void writeNow(BufferData... buffers) {
        }

        @Override
        public void writeNow(BufferData buffer) {
        }

        @Override
        public void flush() {
            flushStarted.countDown();
            try {
                releaseFlush.await();
            } catch (InterruptedException e) {
                interrupted = true;
                Thread.currentThread().interrupt();
            }
        }
    }
}
