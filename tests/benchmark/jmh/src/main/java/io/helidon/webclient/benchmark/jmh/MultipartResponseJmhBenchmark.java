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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.http.media.multipart.MultiPartSupport;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

/**
 * Sequential loopback exchanges that exercise response creation and multipart ownership on both HTTP protocols.
 * Each operation closes the outer response as well as the multipart owner so older implementations complete the
 * same resource lifecycle. GC profiling includes the server's allocations. Use exact method includes.
 * Decorated scenarios wrap the service response stream. Fallback scenarios use the HTTP/2 client against an
 * HTTP/1-only server and verify that the exchanges use HTTP/1.1.
 */
@State(Scope.Thread)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MultipartResponseJmhBenchmark {
    private static final int PART_BYTES = 4096;
    private static final String VALUE = "A".repeat(PART_BYTES);
    private static final String CONTENT_TYPE = "multipart/form-data; boundary=benchmark-boundary";
    private static final HeaderName CONNECTION_ID = HeaderNames.create("X-Benchmark-Connection");
    private static final byte[] MULTIPART = ("--benchmark-boundary\r\n"
            + "Content-Disposition: form-data; name=payload\r\n\r\n"
            + VALUE + "\r\n--benchmark-boundary--\r\n").getBytes(StandardCharsets.US_ASCII);
    private static final WebClientService DECORATOR = (chain, request) -> {
        var response = chain.proceed(request);
        return WebClientServiceResponse.builder(response)
                .inputStream(new FilterInputStream(response.inputStream().orElseThrow()) { })
                .build();
    };

    @Param({"http1", "http2", "http1-decorated", "http2-decorated", "fallback", "fallback-decorated"})
    private String protocol;

    private WebServer server;
    private HttpClient<?> client;
    private String protocolId;
    private String latestConnection;

    @Setup
    public void setup() throws IOException {
        MediaContext mediaContext = MediaContext.builder()
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(MultiPartSupport.create(Config.empty()))
                .build();
        boolean http2 = protocol.startsWith("http2");
        boolean fallback = protocol.startsWith("fallback");
        boolean decorated = protocol.endsWith("-decorated");
        var serverBuilder = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .connectionOptions(options -> options.tcpNoDelay(true))
                .routing(rules -> rules
                        .get("/text", (request, response) -> {
                            response.headers().set(CONNECTION_ID, request.socketId());
                            response.send(VALUE);
                        })
                        .get("/parts", (request, response) -> {
                            response.headers().set(CONNECTION_ID, request.socketId());
                            response.headers().set(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
                            response.send(MULTIPART);
                        }));
        if (http2) {
            serverBuilder.addProtocol(Http2Config.create());
        } else {
            serverBuilder.protocolsDiscoverServices(false)
                    .addConnectionSelector(Http1ConnectionSelector.builder().config(Http1Config.create()).build());
        }
        server = serverBuilder.build().start();
        try {
            String baseUri = "http://127.0.0.1:" + server.port();
            List<WebClientService> services = decorated ? List.of(DECORATOR) : List.of();
            client = http2 || fallback
                    ? Http2Client.builder()
                        .baseUri(baseUri)
                        .mediaContext(mediaContext)
                        .servicesDiscoverServices(false)
                        .services(services)
                        .shareConnectionCache(false)
                        .socketOptions(options -> options.tcpNoDelay(true))
                        .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(http2).build())
                        .readTimeout(Duration.ofSeconds(10))
                        .build()
                    : Http1Client.builder()
                        .baseUri(baseUri)
                        .mediaContext(mediaContext)
                        .servicesDiscoverServices(false)
                        .services(services)
                        .shareConnectionCache(false)
                        .socketOptions(options -> options.tcpNoDelay(true))
                        .readTimeout(Duration.ofSeconds(10))
                        .build();
            protocolId = http2 ? Http2Client.PROTOCOL_ID : Http1Client.PROTOCOL_ID;
            if (ordinaryResponse() != PART_BYTES) {
                throw new IllegalStateException("Benchmark smoke did not consume the expected response");
            }
            String connection = latestConnection;
            if (fullMultipart() != PART_BYTES || !connection.equals(latestConnection)) {
                throw new IllegalStateException("Full multipart consumption did not reuse the physical connection");
            }
            if (partialMultipart() != 'A' || ordinaryResponse() != PART_BYTES) {
                throw new IllegalStateException("Partial multipart cleanup did not permit another exchange");
            }
            if (http2 && !connection.equals(latestConnection)) {
                throw new IllegalStateException("HTTP/2 partial cleanup replaced the physical connection");
            }
            try (HttpClientResponse response = request("/parts"); MultiPart parts = response.as(MultiPart.class)) {
                String value = parts.next().as(String.class);
                if (!VALUE.equals(value) || parts.hasNext()) {
                    throw new IllegalStateException("Multipart benchmark payload differs from its fixture");
                }
            }
        } catch (IOException | RuntimeException | Error failure) {
            try {
                closeResources();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @TearDown
    public void tearDown() {
        closeResources();
    }

    @Benchmark
    public int ordinaryResponse() {
        try (HttpClientResponse response = request("/text")) {
            return response.as(String.class).length();
        }
    }

    @Benchmark
    public long fullMultipart() throws IOException {
        try (HttpClientResponse response = request("/parts"); MultiPart parts = response.as(MultiPart.class)) {
            long count = 0;
            while (parts.hasNext()) {
                count += parts.next().inputStream().transferTo(OutputStream.nullOutputStream());
            }
            return count;
        }
    }

    @Benchmark
    public int partialMultipart() throws IOException {
        try (HttpClientResponse response = request("/parts"); MultiPart parts = response.as(MultiPart.class)) {
            return parts.next().inputStream().read();
        }
    }

    private HttpClientResponse request(String path) {
        HttpClientResponse response = client.get(path).request();
        if (response.status().code() != 200 || !protocolId.equals(response.protocolId())) {
            response.close();
            throw new IllegalStateException("Expected status 200 over " + protocolId
                                                    + ", received " + response.status() + " over " + response.protocolId());
        }
        latestConnection = response.headers().get(CONNECTION_ID).get();
        return response;
    }

    private void closeResources() {
        try {
            if (client != null) {
                client.closeResource();
            }
        } finally {
            server.stop();
        }
    }
}
