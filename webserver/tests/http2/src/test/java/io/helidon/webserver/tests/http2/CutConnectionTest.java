/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.http2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.http.Method;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http2.Http2Route;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CutConnectionTest {
    private static final AssertingHandler ASSERTING_HANDLER = new AssertingHandler();
    private static final Logger WEBSERVER_LOGGER = Logger.getLogger("io.helidon.webserver");
    private static final int TIME_OUT_SEC = 10;

    static {
        LogConfig.configureRuntime();
    }

    private final HttpClient client;

    public CutConnectionTest() {
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }


    private static void stream(ServerRequest req, ServerResponse res) throws InterruptedException, IOException {
        try (OutputStream os = res.outputStream()) {
            for (int i = 0; i < 1000; i++) {
                Thread.sleep(1);
                os.write("TEST".getBytes());
                os.flush();
            }
        }
    }

    @Test
    void testStringRoute() throws Exception {
        CompletableFuture<Void> receivedFirstChunk = new CompletableFuture<>();
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Level originalLevel = Level.INFO;
        try {
            originalLevel = WEBSERVER_LOGGER.getLevel();
            WEBSERVER_LOGGER.setLevel(Level.FINE);
            WEBSERVER_LOGGER.addHandler(ASSERTING_HANDLER);
            WebServer server = WebServer.builder()
                    .host("localhost")
                    .routing(r -> r.route(Http2Route.route(Method.GET, "/stream", CutConnectionTest::stream)))
                    .build();
            server.start();

            URI uri = new URI("http://localhost:" + server.port()).resolve("/stream");

            exec.submit(() -> {
                try {
                    HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder()
                            .timeout(Duration.ofSeconds(20))
                            .uri(uri)
                            .GET()
                            .build(), HttpResponse.BodyHandlers.ofInputStream());
                    try (InputStream is = response.body()) {
                        byte[] chunk;
                        for (int read = 0; read != -1; read = is.read(chunk)) {
                            receivedFirstChunk.complete(null);
                            chunk = new byte[4];
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    // ignored
                }
            });
            receivedFirstChunk.get(TIME_OUT_SEC, TimeUnit.SECONDS);
            exec.shutdownNow();
            assertThat(exec.awaitTermination(TIME_OUT_SEC, TimeUnit.SECONDS), is(true));
            server.stop();
            SocketClosedLog log = ASSERTING_HANDLER.socketClosedLog.get(TIME_OUT_SEC, TimeUnit.SECONDS);
            assertThat(log.record.getLevel(), is(Level.FINE));
        } finally {
            WEBSERVER_LOGGER.removeHandler(ASSERTING_HANDLER);
            WEBSERVER_LOGGER.setLevel(originalLevel);
        }
    }


    private record SocketClosedLog(LogRecord record, SocketException e) {

    }

    /**
     * DEBUG level logging for attempts to write to closed socket is expected:
     * <pre>{@code
     * 023.05.23 14:51:53 FINE io.helidon.webserver.http2.Http2Connection !thread!: Socket error on writer thread
     * java.io.UncheckedIOException: java.net.SocketException: Socket closed
     * 	at io.helidon.common.buffers.FixedBufferData.writeTo(FixedBufferData.java:74)
     * 	at io.helidon.common.buffers.CompositeArrayBufferData.writeTo(CompositeArrayBufferData.java:41)
     * 	at io.helidon.common.socket.PlainSocket.write(PlainSocket.java:127)
     *  ...
     * Caused by: java.net.SocketException: Socket closed
     * 	at java.base/sun.nio.ch.NioSocketImpl.ensureOpenAndConnected(NioSocketImpl.java:163)
     *  ...
     * 	at java.base/java.net.Socket$SocketOutputStream.write(Socket.java:1120)
     * 	at io.helidon.common.buffers.FixedBufferData.writeTo(FixedBufferData.java:71)
     * 	...
     * 	}</pre>
     */

    private static class AssertingHandler extends Handler {

        CompletableFuture<SocketClosedLog> socketClosedLog = new CompletableFuture<>();

        @Override
        public void publish(LogRecord record) {
            Throwable t = record.getThrown();
            if (t == null) return;
            while (t.getCause() != null) {
                t = t.getCause();
            }
            if (t instanceof SocketException e) {
                socketClosedLog.complete(new SocketClosedLog(record, e));
            }
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() throws SecurityException {

        }
    }
}
