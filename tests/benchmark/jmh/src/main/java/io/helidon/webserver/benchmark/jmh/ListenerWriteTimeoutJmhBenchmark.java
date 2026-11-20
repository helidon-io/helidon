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

package io.helidon.webserver.benchmark.jmh;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.WebServer;

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
 * Real HTTP/1 keep-alive exchanges through listener socket writes with and without a write deadline.
 * Streaming responses exercise the direct, asynchronous, and smart writer configurations.
 * Measurements include client work and body validation and report end-to-end exchange cost.
 * Compare the absent configuration against the baseline using the same workload and JDK.
 * Run the enabled configuration only against artifacts implementing the listener write-timeout option.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@State(Scope.Benchmark)
public class ListenerWriteTimeoutJmhBenchmark {
    private static final Duration EXCHANGE_TIMEOUT = Duration.ofSeconds(10);
    private static final String CONNECTION_HEADER = "X-Benchmark-Connection";

    @Param({"absent", "enabled"})
    public String timeoutMode;

    @Param({"direct", "async", "smart"})
    public String writerMode;

    @Param({"1024", "65536"})
    public int payloadSize;

    private WebServer server;
    private HttpClient client;
    private HttpRequest request;
    private byte[] payload;
    private String connectionId;

    @Setup
    public void setup() throws IOException, InterruptedException {
        payload = new byte[payloadSize];
        Arrays.fill(payload, (byte) 'a');
        boolean queued = switch (writerMode) {
            case "direct" -> false;
            case "async", "smart" -> true;
            default -> throw new IllegalArgumentException("Unknown writer mode: " + writerMode);
        };
        var builder = WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .featuresDiscoverServices(false)
                .mediaContext(MediaContext.builder().mediaSupportsDiscoverServices(false).build())
                .contentEncoding(ContentEncodingContext.builder().contentEncodingsDiscoverServices(false).build())
                .writeQueueLength(queued ? 16 : 0)
                .smartAsyncWrites(writerMode.equals("smart"))
                .writeBufferSize(0)
                .connectionOptions(options -> options.tcpNoDelay(true))
                .routing(routing -> routing.get("/benchmark", (req, res) -> {
                    res.header(CONNECTION_HEADER, req.socketId());
                    try (OutputStream output = res.outputStream()) {
                        for (int offset = 0; offset < payload.length; offset += 1024) {
                            output.write(payload, offset, Math.min(1024, payload.length - offset));
                        }
                    }
                }));
        switch (timeoutMode) {
            case "enabled" -> builder.config(Config.just(ConfigSources.create(Map.of("write-timeout", "PT30S"))));
            case "absent" -> {
            }
            default -> throw new IllegalArgumentException("Unknown timeout mode: " + timeoutMode);
        }
        try {
            server = builder.build().start();
            client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(EXCHANGE_TIMEOUT)
                    .build();
            request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + "/benchmark"))
                    .timeout(EXCHANGE_TIMEOUT)
                    .build();
            HttpResponse<byte[]> response = exchange();
            connectionId = response.headers().firstValue(CONNECTION_HEADER).orElseThrow();
            keepAliveExchange();
        } catch (IOException | InterruptedException | RuntimeException | Error failure) {
            try {
                closeResources();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Benchmark
    public byte[] keepAliveExchange() throws IOException, InterruptedException {
        HttpResponse<byte[]> response = exchange();
        if (!connectionId.equals(response.headers().firstValue(CONNECTION_HEADER).orElseThrow())) {
            throw new IllegalStateException("Benchmark did not retain its original connection");
        }
        return response.body();
    }

    @TearDown
    public void tearDown() {
        closeResources();
    }

    private HttpResponse<byte[]> exchange() throws IOException, InterruptedException {
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.version() != HttpClient.Version.HTTP_1_1
                || !Arrays.equals(payload, response.body())) {
            throw new IllegalStateException("Invalid HTTP/1 benchmark response");
        }
        return response;
    }

    private void closeResources() {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }
}
