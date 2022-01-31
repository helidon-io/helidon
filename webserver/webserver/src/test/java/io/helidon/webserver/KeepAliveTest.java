/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.webserver;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.helidon.common.LogConfig;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

public class KeepAliveTest {
    private static WebServer server;
    private static WebClient webClient;
    private static HttpClient httpClient;
    private static URI uri;

    @BeforeAll
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

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        uri = URI.create(serverUrl);
    }

    @AfterAll
    static void afterAll() {
        server.shutdown();
    }

    @RepeatedTest(1000)
    void closeWithKeepAliveUnconsumedRequest() {
        testCall(webClient, true, "/close", 500, HttpHeaderValues.CLOSE);
    }

    void closeWithKeepAliveUnconsumedRequestHttpClient() {
        testCallJdk("/close", 500, HttpHeaderValues.CLOSE);
    }

    @RepeatedTest(100)
    void sendWithoutKeepAlive() {
        testCall(webClient, false, "/plain", 200, null);
    }

    @RepeatedTest(100)
    void sendWithKeepAlive() {
        testCall(webClient, true, "/plain", 200, HttpHeaderValues.KEEP_ALIVE);
    }

    private static void testCallJdk(String path,
                                    int expectedStatus,
                                    AsciiString expectedConnectionHeader) {
        try {
            HttpResponse<byte[]> res = httpClient.send(HttpRequest.newBuilder()
                                                               .uri(uri.resolve(path))
                                                               .PUT(HttpRequest.BodyPublishers.fromPublisher(Multi.just("first",
                                                                                                                        "second")
                                                                                                                     .map(String::getBytes)
                                                                                                                     .map(ByteBuffer::wrap)))
                                                               .build(),
                                                       HttpResponse.BodyHandlers.ofByteArray());

            assertThat(res.statusCode(), is(expectedStatus));
            if (expectedConnectionHeader == null) {
                assertThat(res.headers().firstValue(HttpHeaderNames.CONNECTION.toString()), is(Optional.empty()));
            } else {
                assertThat(res.headers().firstValue(HttpHeaderNames.CONNECTION.toString()),
                           is(Optional.of(expectedConnectionHeader.toString())));
            }
            byte[] bytes = res.body();
        } catch (AssertionError e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void testCall(WebClient webClient,
                                 boolean keepAlive,
                                 String path,
                                 int expectedStatus,
                                 AsciiString expectedConnectionHeader) {
        WebClientResponse res = null;
        try {
            res = webClient
                    .put()
                    .keepAlive(keepAlive)
                    .path(path)
                    .submit(Multi.just("first", "second")
                                    .map(String::getBytes)
                                    .map(ByteBuffer::wrap)
                                    .map(bb -> DataChunk.create(true, true, bb))
                    )
                    .await(Duration.ofMinutes(5));

            assertThat(res.status().code(), is(expectedStatus));
            if (expectedConnectionHeader != null) {
                assertThat(res.headers().toMap(), hasEntry(HttpHeaderNames.CONNECTION.toString(), List.of(expectedConnectionHeader.toString())));
            } else {
                assertThat(res.headers().toMap(), not(hasKey(HttpHeaderNames.CONNECTION.toString())));
            }
            res.content().forEach(DataChunk::release);
        } finally {
            Optional.ofNullable(res).ifPresent(WebClientResponse::close);
        }
    }
}