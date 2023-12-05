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

package io.helidon.webclient.tests.http2;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class ShutDownTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static MockHttp2Server mockHttp2Server;
    private static int serverPort;
    private static ConcurrentMap<Integer, CompletableFuture<Void>> goAwayReceived = new ConcurrentHashMap<>();

    @BeforeAll
    static void beforeAll() throws InterruptedException {
        LogConfig.configureRuntime();
        mockHttp2Server = MockHttp2Server.builder()
                .onGoAway((ctx, streamId, headers, payload, encoder) -> {
                    int remotePort = ((InetSocketAddress) ctx.channel().remoteAddress()).getPort();
                    goAwayReceived.computeIfAbsent(remotePort, i -> new CompletableFuture<>()).complete(null);
                })
                .onHeaders((ctx, streamId, headers, unused, encoder) -> {
                    Http2Headers h = new DefaultHttp2Headers()
                            .status(HttpResponseStatus.OK.codeAsText());

                    encoder.writeHeaders(ctx, streamId, h, 0, false, ctx.newPromise());

                    int remotePort = ((InetSocketAddress) ctx.channel().remoteAddress()).getPort();

                    encoder.writeData(ctx,
                                      streamId,
                                      Unpooled.wrappedBuffer(String.valueOf(remotePort).getBytes()),
                                      0,
                                      true,
                                      ctx.newPromise());
                })
                .build();
        serverPort = mockHttp2Server.port();
    }

    @AfterAll
    static void afterAll() {
        mockHttp2Server.shutdown();
    }

    @Test
    void clientConnectionCacheHttp2() {
        Http2Client
                http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true))
                .connectTimeout(Duration.ofMinutes(10))
                .baseUri("http://localhost:" + serverPort)
                .build();

        int clientPort;
        try (var res = http2Client.get().request()) {
            clientPort = Integer.parseInt(res.entity().as(String.class));
        }

        http2Client.closeResource();

        try {
            goAwayReceived.computeIfAbsent(clientPort, i -> new CompletableFuture<>())
                    .get(TIMEOUT.getSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("GOAWAY not received from the client with port " + clientPort + "!", e);
        }

        IllegalStateException e = assertThrows(IllegalStateException.class,
                                               () -> {
                                                   try (var res = http2Client.get().request()) {
                                                       res.entity().as(String.class);
                                                   }
                                               });
        assertThat(e.getMessage(), is("Connection cache is closed"));
    }

    @Test
    void globalConnectionCacheHttp2() {
        Http2Client
                http2Client = Http2Client.builder()
                .shareConnectionCache(true)
                .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true))
                .connectTimeout(Duration.ofMinutes(10))
                .baseUri("http://localhost:" + serverPort)
                .build();

        String clientPort;
        try (var res = http2Client.get().request()) {
            clientPort = res.entity().as(String.class);
        }

        // should be noop, not testing the shutdown hook
        http2Client.closeResource();

        try (var res = http2Client.get().request()) {
            assertThat(res.entity().as(String.class), is(clientPort));
        }
    }
}
