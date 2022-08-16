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
 */

package io.helidon.nima.tests.benchmark.jmh;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.common.LogConfig;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http1.Http1Route;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class HttpJmhTest {
    private static final HeaderValue CONTENT_TYPE = HeaderValue.createCached(Header.CONTENT_TYPE,
                                                                             "text/plain; charset=UTF-8");
    private static final HeaderValue CONTENT_LENGTH = HeaderValue.createCached(Header.CONTENT_LENGTH, "13");
    private static final HeaderValue SERVER = HeaderValue.createCached(Header.SERVER, "Nima");
    private static final byte[] RESPONSE_BYTES = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    private WebServer server;
    private int serverPort;
    private HttpClient http1Client;
    private HttpClient http2Client;

    @Setup
    public void setup() {
        LogConfig.configureRuntime();

        server = WebServer.builder()
                .defaultSocket(socket -> socket
                        .connectionOptions(builder -> builder
                                .readTimeout(Duration.ZERO)
                                .connectTimeout(Duration.ZERO)
                                .socketSendBufferSize(64000)
                                .socketReceiveBufferSize(64000))
                        .writeQueueLength(4000)
                        .host("127.0.0.1")
                        .backlog(8192))
                .routing(router -> router.route(Http1Route.route(Http.Method.GET, "/plaintext", new PlaintextHandler())))
                .build()
                .start();

        serverPort = server.port();

        http2Client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        http1Client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @TearDown
    public void tearDown() {
        server.stop();
    }

    @Benchmark
    public void http1(Blackhole bh) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + serverPort + "/plaintext"))
                .build();
        HttpResponse<byte[]> response = http1Client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        bh.consume(response);
    }

    @Benchmark
    public void http2(Blackhole bh) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://localhost:" + serverPort + "/plaintext"))
                .build();
        HttpResponse<byte[]> response = http2Client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        bh.consume(response);
    }

    private static class PlaintextHandler implements Handler {
        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.header(CONTENT_LENGTH);
            res.header(CONTENT_TYPE);
            res.header(SERVER);
            res.send(RESPONSE_BYTES);
        }
    }
}
