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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private static Http1Connection createConnection(DataWriter dataWriter) {
        return createConnection(mock(DataReader.class), dataWriter, DirectHandlers.create(), Router.empty());
    }

    private static Http1Connection createConnection(DataReader dataReader,
                                                    DataWriter dataWriter,
                                                    DirectHandlers directHandlers,
                                                    Router router) {
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(ContentEncodingContext.create());
        when(listenerContext.config()).thenReturn(WebServer.builder().buildPrototype());
        when(listenerContext.directHandlers()).thenReturn(directHandlers);

        PeerInfo peerInfo = mock(PeerInfo.class);
        when(peerInfo.tlsCertificates()).thenReturn(Optional.empty());

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.dataWriter()).thenReturn(dataWriter);
        when(ctx.dataReader()).thenReturn(dataReader);
        when(ctx.router()).thenReturn(router);
        when(ctx.remotePeer()).thenReturn(peerInfo);
        when(ctx.localPeer()).thenReturn(peerInfo);

        return new Http1Connection(ctx,
                                   Http1Config.builder()
                                           .continueImmediately(true)
                                           .build(),
                                   Map.of());
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

    private static final class BlockingDataWriter implements DataWriter {
        private final CountDownLatch flushStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFlush = new CountDownLatch(1);
        private volatile boolean interrupted;

        @Override
        public void write(BufferData... buffers) {
        }

        @Override
        public void write(BufferData buffer) {
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
