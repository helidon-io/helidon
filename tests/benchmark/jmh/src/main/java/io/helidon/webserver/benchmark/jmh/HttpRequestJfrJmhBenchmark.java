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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
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
 * Single-client real HTTP exchanges, including client work and full response consumption.
 * The fixture deliberately has no tracing observer. Copy this identical source to both comparison revisions.
 * Recording settings use event names so the overlay also runs on a revision without the request event.
 * Active recordings remain in memory; these measurements do not establish disk recording throughput.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@State(Scope.Benchmark)
public class HttpRequestJfrJmhBenchmark {
    static final String REQUEST_EVENT = "io.helidon.HttpServerRequest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String BODY = "Hello, World!";
    private static final List<String> OTHER_EVENTS = List.of("io.helidon.RequestTraceContext");

    /** Transport, with clear-text HTTP/2 prior knowledge. */
    @Param({"HTTP1", "HTTP2"})
    public String protocol;

    /** No recording, recording with request events disabled, or recording with request events enabled. */
    @Param({"OFF", "DISABLED", "ENABLED"})
    public String recordingMode;

    private WebServer server;
    private HttpClient<?> client;
    private Recording recording;
    private String socketId;

    @Setup
    public void setup() {
        setup(() -> { });
    }

    void setup(Runnable work) {
        setup(work, _ -> { }, Map.of(), List.of(REQUEST_EVENT));
    }

    void setup(Runnable work,
               Consumer<WebServerConfig.Builder> configureServer,
               Map<String, String> headers,
               List<String> enabledEvents) {
        if (!List.of("OFF", "DISABLED", "ENABLED").contains(recordingMode)) {
            throw new IllegalArgumentException("Unknown recording mode: " + recordingMode);
        }
        if (!List.of("HTTP1", "HTTP2").contains(protocol)) {
            throw new IllegalArgumentException("Unknown protocol: " + protocol);
        }
        if (FlightRecorder.getFlightRecorder().getRecordings().stream()
                .anyMatch(existing -> existing.getState() == RecordingState.RUNNING)) {
            throw new IllegalStateException("Benchmark requires exclusive control of active JFR recordings");
        }
        try {
            var builder = WebServer.builder()
                    .host("127.0.0.1").port(-1)
                    .connectionOptions(options -> options.tcpNoDelay(true).readTimeout(TIMEOUT))
                    .featuresDiscoverServices(false).protocolsDiscoverServices(false)
                    .routing(routing -> routing
                            .get("/benchmark", (request, response) -> {
                                String expected = "HTTP1".equals(protocol) ? "1.1" : "2.0";
                                if (!expected.equals(request.prologue().protocolVersion())) {
                                    throw new IllegalStateException("Unexpected protocol: " + request.prologue());
                                }
                                work.run();
                                response.send(BODY);
                            })
                            .get("/connection", (request, response) -> response.send(request.socketId())));
            if ("HTTP1".equals(protocol)) {
                builder.addConnectionSelector(Http1ConnectionSelector.builder().config(Http1Config.create()).build());
            } else {
                builder.addConnectionSelector(Http2ConnectionSelector.builder()
                                                      .http2Config(Http2Config.create()).build());
            }
            configureServer.accept(builder);
            server = builder.build();
            server.start();
            String uri = "http://127.0.0.1:" + server.port();
            client = "HTTP1".equals(protocol)
                    ? Http1Client.builder().baseUri(uri).proxy(Proxy.noProxy())
                            .defaultHeadersMap(headers)
                            .shareConnectionCache(false).servicesDiscoverServices(false)
                            .socketOptions(options -> options.tcpNoDelay(true))
                            .connectTimeout(TIMEOUT).readTimeout(TIMEOUT).build()
                    : Http2Client.builder().baseUri(uri).proxy(Proxy.noProxy())
                            .defaultHeadersMap(headers)
                            .shareConnectionCache(false).servicesDiscoverServices(false)
                            .socketOptions(options -> options.tcpNoDelay(true))
                            .protocolConfig(config -> config.priorKnowledge(true))
                            .connectTimeout(TIMEOUT).readTimeout(TIMEOUT).build();
            socketId = exchange("/connection");
            keepAliveExchange();
            if (!"OFF".equals(recordingMode)) {
                recording = new Recording();
                recording.setToDisk(false);
                // New Recording has no JDK configuration: no unrelated JDK samplers are enabled.
                FlightRecorder.getFlightRecorder().getEventTypes().stream()
                        .filter(type -> type.getName().startsWith("io.helidon."))
                        .forEach(type -> recording.disable(type.getName()));
                OTHER_EVENTS.forEach(recording::disable);
                if ("ENABLED".equals(recordingMode)) {
                    enabledEvents.forEach(name -> recording.enable(name)
                            .withThreshold(Duration.ZERO).withoutStackTrace());
                } else {
                    recording.disable(REQUEST_EVENT);
                }
                recording.start();
            }
        } catch (RuntimeException | Error failure) {
            try {
                tearDown();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Benchmark
    public String keepAliveExchange() {
        String body = exchange("/benchmark");
        if (!BODY.equals(body)) {
            throw new IllegalStateException("Unexpected response body: " + body);
        }
        return body;
    }

    @TearDown
    public void tearDown() {
        try {
            if (client != null && socketId != null && !socketId.equals(exchange("/connection"))) {
                throw new IllegalStateException("Benchmark did not retain the original connection");
            }
        } finally {
            try {
                if (recording != null) {
                    recording.close();
                    recording = null;
                }
            } finally {
                try {
                    if (client != null) {
                        client.closeResource();
                        client = null;
                    }
                } finally {
                    if (server != null) {
                        server.stop();
                        server = null;
                    }
                }
            }
        }
    }

    Recording recording() {
        if (recording == null) {
            throw new IllegalStateException("No recording in mode " + recordingMode);
        }
        return recording;
    }

    void dump(Path destination) throws IOException {
        // Stop producers before reading the recording; consuming the response can precede event commit.
        socketId = null;
        server.stop();
        server = null;
        // In-memory recording data is available for a snapshot while running, but not after stop().
        try {
            recording().dump(destination);
        } finally {
            recording().stop();
        }
    }

    private String exchange(String path) {
        try (var response = client.get(path).request()) {
            String body = response.as(String.class);
            if (response.status().code() != 200) {
                throw new IllegalStateException("Expected status 200, received " + response.status());
            }
            return body;
        }
    }
}
