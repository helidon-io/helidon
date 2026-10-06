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

package io.helidon.webserver.tests.websocket;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.websocket.WsConfig;
import io.helidon.webserver.websocket.WsRouting;
import io.helidon.websocket.WsCloseCodes;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertAll;

class WebSocketFrameRejectionTest {
    private static final String CLOSE_REASON = "Payload too large";
    private static final int FRAME_LENGTH = 17;

    @Test
    void directWriterRejectsOversizedFrames() throws Exception {
        verifyRejection(0, false);
    }

    @Test
    void asyncWriterRejectsOversizedFrames() throws Exception {
        verifyRejection(2, false);
    }

    @Test
    void smartAsyncWriterRejectsOversizedFrames() throws Exception {
        verifyRejection(2, true);
    }

    private static void verifyRejection(int writeQueueLength, boolean smartAsyncWrites) throws Exception {
        for (int opcode : new int[] {1, 2}) {
            for (boolean headerOnly : new boolean[] {true, false}) {
                RecordingListener listener = new RecordingListener();
                WebServer server = WebServer.builder()
                        .port(0)
                        .writeQueueLength(writeQueueLength)
                        .smartAsyncWrites(smartAsyncWrites)
                        .addProtocol(WsConfig.builder().maxFrameLength(FRAME_LENGTH - 1).build())
                        .addRouting(WsRouting.builder().endpoint("/reject", listener))
                        .build()
                        .start();
                try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                    socket.setSoTimeout(10000);
                    InputStream input = socket.getInputStream();
                    socket.getOutputStream().write("""
                            GET /reject HTTP/1.1\r
                            Host: localhost\r
                            Upgrade: websocket\r
                            Connection: Upgrade\r
                            Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r
                            Sec-WebSocket-Version: 13\r
                            \r
                            """.getBytes(StandardCharsets.US_ASCII));
                    assertThat(readUpgrade(input), startsWith("HTTP/1.1 101 "));
                    WsSession opened = listener.opened.get(10, TimeUnit.SECONDS);
                    assertThat("The opened session owns a resource", listener.resources.containsKey(opened), is(true));

                    // A header alone proves rejection does not wait for the oversized payload.
                    // The complete frame stays small so this test does not depend on a large TCP write succeeding.
                    byte[] frame = new byte[6 + (headerOnly ? 0 : FRAME_LENGTH)];
                    frame[0] = (byte) (0x80 | opcode);
                    frame[1] = (byte) (0x80 | FRAME_LENGTH);
                    socket.getOutputStream().write(frame);

                    assertThat("Server sends a final close frame before closing the socket", input.read(), is(0x88));
                    int length = input.read();
                    assertThat("Server close is unmasked and has the complete reason", length,
                               is(2 + CLOSE_REASON.length()));
                    byte[] payload = input.readNBytes(length);
                    assertThat("Complete close payload", payload.length, is(length));
                    int status = ((payload[0] & 0xff) << 8) | (payload[1] & 0xff);
                    String reason = new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
                    assertThat(status, is(WsCloseCodes.TOO_BIG));
                    assertThat(reason, is(CLOSE_REASON));
                    assertThat("No second frame follows the rejection close", input.read(), is(-1));

                    CloseInfo close = listener.closed.get(10, TimeUnit.SECONDS);
                    assertAll(
                            () -> assertThat(close.session(), sameInstance(opened)),
                            () -> assertThat(close.status(), is(WsCloseCodes.TOO_BIG)),
                            () -> assertThat(close.reason(), is(CLOSE_REASON)),
                            () -> assertThat("The session's resource was released", close.resource(),
                                             sameInstance(listener.resource)),
                            () -> assertThat(listener.resources.size(), is(0)),
                            () -> assertThat(listener.closeCalls.get(), is(1)),
                            () -> assertThat(listener.messages.get(), is(0)),
                            () -> assertThat(listener.errors.get(), is(0)));
                } finally {
                    server.stop();
                }
            }
        }
    }

    private static String readUpgrade(InputStream input) throws Exception {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        int matched = 0;
        byte[] terminator = {'\r', '\n', '\r', '\n'};
        while (matched < terminator.length) {
            int value = input.read();
            assertThat("Upgrade response ends before EOF", value, not(-1));
            response.write(value);
            assertThat("Bounded upgrade response", response.size(), lessThan(4096));
            matched = value == terminator[matched] ? matched + 1 : (value == '\r' ? 1 : 0);
        }
        return response.toString(StandardCharsets.US_ASCII);
    }

    private record CloseInfo(WsSession session, int status, String reason, Object resource) {
    }

    private static class RecordingListener implements WsListener {
        private final Object resource = new Object();
        private final Map<WsSession, Object> resources = new ConcurrentHashMap<>();
        private final CompletableFuture<WsSession> opened = new CompletableFuture<>();
        private final CompletableFuture<CloseInfo> closed = new CompletableFuture<>();
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final AtomicInteger messages = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();

        @Override
        public void onOpen(WsSession session) {
            resources.put(session, resource);
            opened.complete(session);
        }

        @Override
        public void onMessage(WsSession session, String text, boolean last) {
            messages.incrementAndGet();
        }

        @Override
        public void onMessage(WsSession session, BufferData buffer, boolean last) {
            messages.incrementAndGet();
        }

        @Override
        public void onClose(WsSession session, int status, String reason) {
            closeCalls.incrementAndGet();
            closed.complete(new CloseInfo(session, status, reason, resources.remove(session)));
        }

        @Override
        public void onError(WsSession session, Throwable throwable) {
            errors.incrementAndGet();
        }
    }
}
