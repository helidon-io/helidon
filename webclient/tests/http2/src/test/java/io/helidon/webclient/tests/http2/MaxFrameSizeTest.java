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

package io.helidon.webclient.tests.http2;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MaxFrameSizeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static MockHttp2Server mockHttp2Server;
    private static int serverPort;
    private static CompletableFuture<Void> SETTINGS_SENT;
    private static CompletableFuture<Void> SETTINGS_ACKED;
    private static CompletableFuture<Void> SEND_HEADERS;

    @BeforeEach
    void beforeAll() {
        LogConfig.configureRuntime();
        SETTINGS_SENT = new CompletableFuture<>();
        SETTINGS_ACKED = CompletableFuture.completedFuture(null);
        SEND_HEADERS = CompletableFuture.completedFuture(null);

        mockHttp2Server = MockHttp2Server.builder()
                .onHeaders((ctx, streamId, headers, payload, encoder) -> {
                    var mockServerCtx = mockHttp2Server.mockServerContext().stream(streamId);
                    mockServerCtx.setHeaders(headers);

                    switch (headers.path().toString()) {
                    case "/trigger-settings" -> {
                        Http2Headers h = new DefaultHttp2Headers()
                                .status(HttpResponseStatus.OK.codeAsText());

                        encoder.writeHeaders(ctx, streamId, h, 0, false, ctx.newPromise());

                        int testMaxFrameSize = Integer.parseInt(headers.get("test-max-frame-size").toString());
                        encoder.writeData(ctx,
                                          streamId,
                                          Unpooled.wrappedBuffer(("Settings size change triggered: " + testMaxFrameSize).getBytes()),
                                          0,
                                          true,
                                          ctx.newPromise());

                        encoder.writeSettings(ctx,
                                              Http2Settings.defaultSettings().maxFrameSize(testMaxFrameSize),
                                              ctx.newPromise());
                    }
                    case "/test-batch" -> {
                    }
                    case "/test-batch-interim-settings" -> {
                        var maxFrameSizeHeader = headers.get("test-max-frame-size");
                        int testMaxFrameSize = Integer.parseInt(maxFrameSizeHeader.toString());
                        encoder.writeSettings(ctx,
                                              Http2Settings.defaultSettings().maxFrameSize(testMaxFrameSize),
                                              ctx.newPromise());
                        SETTINGS_SENT.complete(null);
                    }
                    default -> {
                        Http2Headers h = new DefaultHttp2Headers()
                                .status(HttpResponseStatus.NOT_FOUND.codeAsText());
                        encoder.writeHeaders(ctx, streamId, h, 0, true, ctx.newPromise());
                    }
                    }

                })
                .onSettingsAck((ctx, encoder) -> {
                    SETTINGS_ACKED.complete(null);
                })
                .onData((ctx, streamId, data, padding, endOfStream, encoder) -> {
                    var mckCtx = mockHttp2Server.mockServerContext().stream(streamId);
                    List<Integer> frameSizeList = (List<Integer>) mckCtx
                            .ctx()
                            .computeIfAbsent("frame-size", s -> new CopyOnWriteArrayList<Integer>());

                    frameSizeList.add(data.readableBytes());

                    if (endOfStream) {
                        Http2Headers h = new DefaultHttp2Headers()
                                .status(HttpResponseStatus.OK.codeAsText());
                        encoder.writeHeaders(ctx, streamId, h, 0, false, ctx.newPromise());
                        var msg = frameSizeList.stream()
                                .filter(i -> i > 0)
                                .map(String::valueOf)
                                .toList()
                                .toString();
                        encoder.writeData(ctx,
                                          streamId,
                                          Unpooled.wrappedBuffer((msg).getBytes()),
                                          0,
                                          true,
                                          ctx.newPromise());

                    }
                    return 0;
                })
                .build();

        serverPort = mockHttp2Server.port();
    }

    @AfterEach
    void afterEach() {
        mockHttp2Server.shutdown();
    }

    @Test
    void maxFrameChangeMidConnection() throws InterruptedException {
        Http2Client
                client = Http2Client.builder()
                .shareConnectionCache(false)
                .connectTimeout(Duration.ofMinutes(10))
                .baseUri("http://localhost:" + serverPort)
                .build();

        // Check default MAX_FRAME_SIZE=16384 is set
        try (Http2ClientResponse res = client
                .method(Method.GET)
                .path("/test-batch")
                .header(HeaderValues.create("test-batch-size", String.valueOf(30_000)))
                .priorKnowledge(true)
                .submit("A".repeat(17_000))) {

            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.as(String.class), is("[16384, 616]"));
        }

        // Trigger server to change MAX_FRAME_SIZE=18_000
        try (Http2ClientResponse res = client
                .method(Method.GET)
                .path("/trigger-settings")
                .header(HeaderValues.create("test-max-frame-size", String.valueOf(18_000)))
                .priorKnowledge(true)
                .request()) {

            assertThat(res.status(), is(Status.OK_200));
        }

        // Test that the client honors MAX_FRAME_SIZE=18_000
        try (Http2ClientResponse res = client
                .method(Method.GET)
                .path("/test-batch")
                .header(HeaderValues.create("test-batch-size", String.valueOf(30_000)))
                .priorKnowledge(true)
                .submit("A".repeat(20_000))) {

            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.as(String.class), is("[18000, 2000]"));
        }
    }

    @Test
    void maxFrameChangeMidStream() throws InterruptedException {
        Http2Client
                client = Http2Client.builder()
                .shareConnectionCache(false)
                .connectTimeout(Duration.ofMinutes(10))
                .baseUri("http://localhost:" + serverPort)
                .build();

        //Check default MAX_FRAME_SIZE=16384 is set
        try (Http2ClientResponse res = client
                .method(Method.GET)
                .path("/test-batch")
                .header(HeaderValues.create("test-batch-size", String.valueOf(30_000)))
                .priorKnowledge(true)
                .submit("A".repeat(20_000))) {

            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.as(String.class), is("[16384, 3616]"));
        }

        SEND_HEADERS = new CompletableFuture<>();
        SETTINGS_ACKED = new CompletableFuture<>();

        // Test that the client honors MAX_FRAME_SIZE=18_000
        try (Http2ClientResponse res = client
                .method(Method.GET)
                .path("/test-batch-interim-settings")
                .header(HeaderValues.create("test-max-frame-size", String.valueOf(18_000)))
                .header(HeaderValues.create("test-batch-size", String.valueOf(30_000)))
                .priorKnowledge(true)
                .outputStream(out -> {
                    var future = new CompletableFuture<Void>();
                    SETTINGS_ACKED = future;
                    // The first data frame just forces headers to be sent
                    out.write("A".repeat(10).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    // Now wait till the new setting are sent from the server
                    try {
                        SETTINGS_SENT.get(TIMEOUT.getSeconds(), TimeUnit.SECONDS);
                        SETTINGS_ACKED.get(TIMEOUT.getSeconds(), TimeUnit.SECONDS);
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        throw new RuntimeException(e);
                    }
                    // Write more than MAX_FRAME_SIZE=18_000 to observe correct split
                    out.write("A".repeat(19_000).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    SEND_HEADERS.complete(null);
                    out.close();
                })) {

            assertThat(res.status(), is(Status.OK_200));
            assertThat(res.as(String.class), is("[10, 18000, 1000]"));
        }
    }
}
