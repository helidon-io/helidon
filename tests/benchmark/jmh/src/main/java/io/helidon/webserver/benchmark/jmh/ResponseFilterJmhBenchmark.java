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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import io.helidon.http.HeaderValues;
import io.helidon.http.sse.SseEvent;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.sse.SseSink;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class ResponseFilterJmhBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final String DIRECT_BEFORE_SEND = "/direct-before-send";
    private static final String DIRECT_ENTITY = "/direct-entity";
    private static final String DIRECT_NONE = "/direct-none";
    private static final String DIRECT_ENTITY_BEFORE_SEND = "/direct-entity-before-send";
    private static final String DIRECT_PUBLIC = "/direct-public";
    private static final String STREAM_ENTITY = "/stream-entity";
    private static final String STREAM_NONE = "/stream-none";
    private static final String STREAM_PUBLIC = "/stream-public";
    private static final String SSE = "/sse";
    private static final String SSE_SUSTAINED = "/sse-sustained";
    private static final int REQUESTS_PER_INVOCATION = 16;
    private static final int SSE_STREAMS_PER_INVOCATION = 4;
    private static final int SSE_EVENTS_PER_STREAM = 256;
    private static final byte[] RESPONSE_BYTES = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    private static final Runnable BEFORE_SEND = () -> { };
    private static final UnaryOperator<OutputStream> OUTPUT_FILTER = PassthroughOutputStream::new;

    private final AtomicReference<String> http2SseSocketId = new AtomicReference<>();
    private WebServer server;
    private HttpClient http1Client;
    private Http2Client http2Client;
    private HttpRequest http1DirectNone;
    private HttpRequest http1DirectEntity;
    private HttpRequest http1DirectPublic;
    private HttpRequest http1DirectBeforeSend;
    private HttpRequest http1DirectEntityBeforeSend;
    private HttpRequest http1StreamNone;
    private HttpRequest http1StreamEntity;
    private HttpRequest http1StreamPublic;
    private HttpRequest http1Sse;

    @Setup
    public void setup() {
        LogConfig.configureRuntime();
        Http2Config http2Config = Http2Config.builder().build();
        server = WebServer.builder()
                .host(HOST)
                .addProtocol(http2Config)
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(http2Config)
                                               .build())
                .routing(routing -> routing
                        .addFilter(ResponseFilterJmhBenchmark::configureFilter)
                        .get(DIRECT_NONE, ResponseFilterJmhBenchmark::sendDirect)
                        .get(DIRECT_ENTITY, ResponseFilterJmhBenchmark::sendDirect)
                        .get(DIRECT_PUBLIC, ResponseFilterJmhBenchmark::sendDirect)
                        .get(DIRECT_BEFORE_SEND, ResponseFilterJmhBenchmark::sendDirect)
                        .get(DIRECT_ENTITY_BEFORE_SEND, ResponseFilterJmhBenchmark::sendDirect)
                        .get(STREAM_NONE, ResponseFilterJmhBenchmark::sendStream)
                        .get(STREAM_ENTITY, ResponseFilterJmhBenchmark::sendStream)
                        .get(STREAM_PUBLIC, ResponseFilterJmhBenchmark::sendStream)
                        .get(SSE, ResponseFilterJmhBenchmark::sendSse)
                        .get(SSE_SUSTAINED, this::sendSustainedSse))
                .build()
                .start();

        String baseUri = "http://" + HOST + ":" + server.port();
        http1Client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .protocolConfig(http2 -> http2.priorKnowledge(true))
                .baseUri(baseUri)
                .build();
        http1DirectNone = request(baseUri, DIRECT_NONE);
        http1DirectEntity = request(baseUri, DIRECT_ENTITY);
        http1DirectPublic = request(baseUri, DIRECT_PUBLIC);
        http1DirectBeforeSend = request(baseUri, DIRECT_BEFORE_SEND);
        http1DirectEntityBeforeSend = request(baseUri, DIRECT_ENTITY_BEFORE_SEND);
        http1StreamNone = request(baseUri, STREAM_NONE);
        http1StreamEntity = request(baseUri, STREAM_ENTITY);
        http1StreamPublic = request(baseUri, STREAM_PUBLIC);
        http1Sse = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(baseUri + SSE))
                .header(HeaderValues.ACCEPT_EVENT_STREAM.name(), HeaderValues.ACCEPT_EVENT_STREAM.get())
                .build();
    }

    @TearDown
    public void tearDown() {
        http2Client.closeResource();
        server.stop();
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1DirectNoFilter(Blackhole blackhole) throws IOException, InterruptedException {
        http1(http1DirectNone, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1DirectEntityFilter(Blackhole blackhole) throws IOException, InterruptedException {
        http1(http1DirectEntity, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1DirectPublicFilter(Blackhole blackhole) throws IOException, InterruptedException {
        http1(http1DirectPublic, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1DirectBeforeSend(Blackhole blackhole) throws IOException, InterruptedException {
        http1(http1DirectBeforeSend, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1DirectEntityBeforeSend(Blackhole blackhole) throws IOException, InterruptedException {
        http1(http1DirectEntityBeforeSend, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1StreamNoFilter(Blackhole blackhole) throws IOException, InterruptedException {
        http1(http1StreamNone, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1StreamEntityFilter(Blackhole blackhole) throws IOException, InterruptedException {
        http1(http1StreamEntity, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1StreamPublicFilter(Blackhole blackhole) throws IOException, InterruptedException {
        http1(http1StreamPublic, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1SseSink(Blackhole blackhole) throws IOException, InterruptedException {
        http1(http1Sse, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2DirectNoFilter(Blackhole blackhole) {
        http2(DIRECT_NONE, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2DirectEntityFilter(Blackhole blackhole) {
        http2(DIRECT_ENTITY, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2DirectPublicFilter(Blackhole blackhole) {
        http2(DIRECT_PUBLIC, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2DirectBeforeSend(Blackhole blackhole) {
        http2(DIRECT_BEFORE_SEND, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2DirectEntityBeforeSend(Blackhole blackhole) {
        http2(DIRECT_ENTITY_BEFORE_SEND, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2StreamNoFilter(Blackhole blackhole) {
        http2(STREAM_NONE, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2StreamEntityFilter(Blackhole blackhole) {
        http2(STREAM_ENTITY, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2StreamPublicFilter(Blackhole blackhole) {
        http2(STREAM_PUBLIC, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2SseSink(Blackhole blackhole) {
        http2Sse(SSE, REQUESTS_PER_INVOCATION, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(SSE_STREAMS_PER_INVOCATION * SSE_EVENTS_PER_STREAM)
    public void http2SseSustained(Blackhole blackhole) {
        http2Sse(SSE_SUSTAINED, SSE_STREAMS_PER_INVOCATION, blackhole);
    }

    private static HttpRequest request(String baseUri, String path) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(baseUri + path))
                .build();
    }

    private static void configureFilter(FilterChain chain, RoutingRequest request, RoutingResponse response) {
        switch (request.path().path()) {
        case DIRECT_ENTITY, STREAM_ENTITY -> response.entityStreamFilter(OUTPUT_FILTER);
        case DIRECT_PUBLIC, STREAM_PUBLIC -> response.streamFilter(OUTPUT_FILTER);
        case DIRECT_BEFORE_SEND -> response.beforeSend(BEFORE_SEND);
        case DIRECT_ENTITY_BEFORE_SEND -> response.entityBeforeSend(BEFORE_SEND);
        default -> {
        }
        }
        chain.proceed();
    }

    private static void sendDirect(ServerRequest request, ServerResponse response) {
        response.send(RESPONSE_BYTES);
    }

    private static void sendStream(ServerRequest request, ServerResponse response) {
        try (OutputStream outputStream = response.outputStream()) {
            outputStream.write(RESPONSE_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sendSse(ServerRequest request, ServerResponse response) {
        sendSse(response, 1);
    }

    private static void sendSse(ServerResponse response, int eventCount) {
        try (SseSink sink = response.sink(SseSink.TYPE)) {
            SseEvent event = SseEvent.create(RESPONSE_BYTES);
            for (int i = 0; i < eventCount; i++) {
                sink.emit(event);
            }
        }
    }

    private void sendSustainedSse(ServerRequest request, ServerResponse response) {
        String socketId = request.socketId();
        String expectedSocketId = http2SseSocketId.get();
        if (expectedSocketId == null) {
            http2SseSocketId.compareAndSet(null, socketId);
            expectedSocketId = http2SseSocketId.get();
        }
        if (!Objects.equals(expectedSocketId, socketId)) {
            throw new IllegalStateException("HTTP/2 SSE benchmark expected one physical connection, but observed socket IDs "
                                                    + expectedSocketId + " and " + socketId);
        }
        sendSse(response, SSE_EVENTS_PER_STREAM);
    }

    private void http1(HttpRequest request, Blackhole blackhole) throws IOException, InterruptedException {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            HttpResponse<byte[]> response = http1Client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            blackhole.consume(response.statusCode());
            blackhole.consume(response.body());
        }
    }

    private void http2(String path, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (Http2ClientResponse response = http2Client.get(path).request()) {
                blackhole.consume(response.status());
                blackhole.consume(response.entity().as(byte[].class));
            }
        }
    }

    private void http2Sse(String path, int requestCount, Blackhole blackhole) {
        for (int i = 0; i < requestCount; i++) {
            try (Http2ClientResponse response = http2Client.get(path)
                    .header(HeaderValues.ACCEPT_EVENT_STREAM)
                    .request()) {
                int status = response.status().code();
                if (status != 200) {
                    throw new IllegalStateException("HTTP/2 SSE benchmark request failed with status " + status);
                }
                blackhole.consume(status);
                blackhole.consume(response.entity().as(byte[].class));
            }
        }
    }

    private static final class PassthroughOutputStream extends FilterOutputStream {
        private PassthroughOutputStream(OutputStream outputStream) {
            super(outputStream);
        }
    }
}
