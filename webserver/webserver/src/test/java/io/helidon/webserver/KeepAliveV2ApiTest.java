/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.common.LogConfig;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;

@Deprecated(since = "3.0.0", forRemoval = true)
public class KeepAliveV2ApiTest {
    private static WebServer server;
    private static WebClient webClient;

    @BeforeAll
    @SuppressWarnings({"deprecation", "removal"})
    static void setUp() {
        LogConfig.configureRuntime();
        server = WebServer.builder()
                .routing(Routing.builder()
                                 .register("/close", rules -> rules.any((req, res) -> {
                                     req.content().forEach(dataChunk -> {
                                         // consume only first from two chunks
                                         dataChunk.release();
                                         throw new RuntimeException("BOOM!");
                                     }).exceptionally(res::send);
                                 }))
                                 .register("/plain", rules -> rules.any((req, res) -> {
                                     req.content()
                                             .forEach(DataChunk::release)
                                             .onComplete(res::send)
                                             .ignoreElement();
                                 }))
                                 .build())
                .build();
        server.start().await();
        String serverUrl = "http://localhost:" + server.port();
        webClient = WebClient.builder()
                .baseUri(serverUrl)
                .keepAlive(true)
                .build();

    }

    @AfterAll
    static void afterAll() {
        server.shutdown();
    }

    @RepeatedTest(10)
    void closeWithKeepAliveUnconsumedRequest() {
        testCall(webClient, true, "/close", 500, HttpHeaderValues.CLOSE, true);
    }

    @RepeatedTest(10)
    void sendWithoutKeepAlive() {
        testCall(webClient, false, "/plain", 200, null, false);
    }

    @RepeatedTest(10)
    void sendWithKeepAlive() {
        testCall(webClient, true, "/plain", 200, HttpHeaderValues.KEEP_ALIVE, false);
    }

    private static void testCall(WebClient webClient,
                                 boolean keepAlive,
                                 String path,
                                 int expectedStatus,
                                 AsciiString expectedConnectionHeader,
                                 boolean ignoreConnectionClose) {
        WebClientResponse res = null;
        try {
            res = webClient
                    .put()
                    .keepAlive(keepAlive)
                    .path(path)
                    .submit(Multi.interval(10, TimeUnit.MILLISECONDS, Executors.newSingleThreadScheduledExecutor())
                                    .limit(2)
                                    .map(l -> "msg_"+ l)
                                    .map(String::getBytes)
                                    .map(ByteBuffer::wrap)
                                    .map(bb -> DataChunk.create(true, true, bb))
                    )
                    .await(Duration.ofMinutes(5));

            assertThat(res.status().code(), is(expectedStatus));
            if (expectedConnectionHeader != null) {
                assertThat(res.headers().toMap(),
                           hasEntry(HttpHeaderNames.CONNECTION.toString(), List.of(expectedConnectionHeader.toString())));
            } else {
                assertThat(res.headers().toMap(), not(hasKey(HttpHeaderNames.CONNECTION.toString())));
            }
            res.content().forEach(DataChunk::release);
        } catch (CompletionException e) {
            if (ignoreConnectionClose && e.getMessage().contains("Connection reset")) {
                // this is an expected (intermittent) result - due to a natural race (between us writing the request
                // data and server responding), we may either get a response
                // or the socket may be closed for writing (the reset comes from an attempt to write entity to a closed
                // socket)
                return;
            }
            throw e;
        } finally {
            Optional.ofNullable(res).ifPresent(WebClientResponse::close);
        }
    }
}