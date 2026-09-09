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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http1ConnectionTest {
    private static final byte[] CONNECTION_CLOSE_REQUEST = ("""
            GET / HTTP/1.1\r
            Host: localhost\r
            Connection: close\r
            \r
            """).getBytes(StandardCharsets.UTF_8);

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
        Http1Connection connection = createConnection(writer,
                                                      DataReader.create(() -> CONNECTION_CLOSE_REQUEST),
                                                      Router.builder()
                                                              .addRouting(HttpRouting.builder()
                                                                                  .get("/", (req, res) -> res.send("done")))
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
        return createConnection(dataWriter, mock(DataReader.class), Router.empty());
    }

    private static Http1Connection createConnection(DataWriter dataWriter, DataReader dataReader, Router router) {
        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(mock(ContentEncodingContext.class));
        when(listenerContext.config()).thenReturn(WebServer.builder().buildPrototype());
        when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());

        PeerInfo peerInfo = mock(PeerInfo.class);

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
