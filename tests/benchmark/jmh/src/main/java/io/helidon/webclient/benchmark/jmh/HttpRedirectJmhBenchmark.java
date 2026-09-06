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

package io.helidon.webclient.benchmark.jmh;

import java.nio.charset.StandardCharsets;

import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.api.ClientRequest;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

public class HttpRedirectJmhBenchmark {
    private static final int REQUESTS_PER_INVOCATION = 8;
    private static final String HOST = "127.0.0.1";
    private static final String REDIRECT_PATH = "/redirect";
    private static final String TARGET_PATH = "/target";
    private static final byte[] REQUEST_ENTITY = "request-entity".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONSE_ENTITY = "ok".getBytes(StandardCharsets.UTF_8);

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1PutOutputStreamRedirect(NetworkState state, Blackhole blackhole) {
        invokeRedirectOutputStream(state.http1Client, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2PutOutputStreamRedirect(NetworkState state, Blackhole blackhole) {
        invokeRedirectOutputStream(state.http2Client, blackhole);
    }

    private static void configureRouting(HttpRouting.Builder routing) {
        routing.put(REDIRECT_PATH, HttpRedirectJmhBenchmark::redirect)
                .put(TARGET_PATH, HttpRedirectJmhBenchmark::handle);
    }

    private static void redirect(ServerRequest request, ServerResponse response) {
        request.content().as(byte[].class);
        response.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, TARGET_PATH)
                .send();
    }

    private static void handle(ServerRequest request, ServerResponse response) {
        request.content().as(byte[].class);
        response.send(RESPONSE_ENTITY);
    }

    private static void invokeRedirectOutputStream(HttpClient<?> client, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            ClientRequest<?> request = client.method(Method.PUT)
                    .uri(REDIRECT_PATH)
                    .header(HeaderNames.CONTENT_TYPE, "application/octet-stream")
                    .followRedirects(true);
            try (HttpClientResponse response = request.outputStream(output -> {
                output.write(REQUEST_ENTITY);
                output.close();
            })) {
                consume(response, blackhole);
            }
        }
    }

    private static void consume(HttpClientResponse response, Blackhole blackhole) {
        response.entity().consume();
        int status = response.status().code();
        if (status != Status.OK_200.code()) {
            throw new IllegalStateException("Unexpected benchmark response: " + response.status());
        }
        blackhole.consume(status);
    }

    private static void warmUp(HttpClient<?> client) {
        try (HttpClientResponse response = client.method(Method.PUT)
                .uri(TARGET_PATH)
                .header(HeaderNames.CONTENT_TYPE, "application/octet-stream")
                .submit(REQUEST_ENTITY)) {
            response.entity().consume();
            if (response.status().code() != Status.OK_200.code()) {
                throw new IllegalStateException("Could not warm benchmark connection: " + response.status());
            }
        }
    }

    @State(Scope.Benchmark)
    public static class NetworkState {
        private WebServer server;
        private Http1Client http1Client;
        private Http2Client http2Client;

        @Setup(Level.Trial)
        public void setup() {
            LogConfig.configureRuntime();
            Http2Config http2Config = Http2Config.builder().build();
            try {
                server = WebServer.builder()
                        .host(HOST)
                        .addProtocol(http2Config)
                        .addConnectionSelector(Http2ConnectionSelector.builder()
                                                       .http2Config(http2Config)
                                                       .build())
                        .routing(HttpRedirectJmhBenchmark::configureRouting)
                        .build()
                        .start();

                String baseUri = "http://" + HOST + ":" + server.port();
                http1Client = Http1Client.builder()
                        .baseUri(baseUri)
                        .shareConnectionCache(false)
                        .servicesDiscoverServices(false)
                        .build();
                http2Client = Http2Client.builder()
                        .baseUri(baseUri)
                        .shareConnectionCache(false)
                        .servicesDiscoverServices(false)
                        .protocolConfig(config -> config.priorKnowledge(true))
                        .build();

                warmUp(http1Client);
                warmUp(http2Client);
            } catch (RuntimeException e) {
                tearDown();
                throw e;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (http2Client != null) {
                http2Client.closeResource();
            }
            if (http1Client != null) {
                http1Client.closeResource();
            }
            if (server != null) {
                server.stop();
            }
        }
    }
}
