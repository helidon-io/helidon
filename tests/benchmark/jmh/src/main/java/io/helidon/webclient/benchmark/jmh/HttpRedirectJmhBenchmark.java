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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.OptionalLong;

import io.helidon.common.GenericType;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.InstanceWriter;
import io.helidon.http.media.MediaSupport;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

public class HttpRedirectJmhBenchmark {
    private static final int REQUESTS_PER_INVOCATION = 8;
    private static final String HOST = "127.0.0.1";
    private static final String REDIRECT_PATH = "/redirect";
    private static final String EARLY_REDIRECT_PATH = "/early-redirect";
    private static final String TARGET_PATH = "/target";
    private static final byte[] REQUEST_ENTITY = "request-entity".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONSE_ENTITY = "ok".getBytes(StandardCharsets.UTF_8);

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1PutOutputStreamRedirect(NetworkState state, Blackhole blackhole) {
        invokeRedirectOutputStream(state.http1Client, REDIRECT_PATH, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1PutOutputStreamEarlyRedirect(NetworkState state, Blackhole blackhole) {
        invokeRedirectOutputStream(state.http1Client, EARLY_REDIRECT_PATH, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1PutLegacyEntity(LegacyWriterState state, Blackhole blackhole) {
        invokeLegacyEntity(state.http1Client, TARGET_PATH, state.entity, false, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1PutLegacyEntityContinue(LegacyWriterState state, Blackhole blackhole) {
        invokeLegacyEntity(state.http1Client, TARGET_PATH, state.entity, true, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1PutLegacyEntityRedirect(LegacyWriterState state, Blackhole blackhole) {
        invokeLegacyEntity(state.http1Client, REDIRECT_PATH, state.entity, false, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1PutLegacyEntityEarlyRedirect(LegacyWriterState state, Blackhole blackhole) {
        invokeLegacyEntity(state.http1Client, EARLY_REDIRECT_PATH, state.entity, true, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2PutOutputStreamRedirect(NetworkState state, Blackhole blackhole) {
        invokeRedirectOutputStream(state.http2Client, REDIRECT_PATH, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1QueryEntityRedirect(NetworkState state, Blackhole blackhole) {
        invokeQueryRedirect(state.http1Client, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2QueryEntityRedirect(NetworkState state, Blackhole blackhole) {
        invokeQueryRedirect(state.http2Client, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http1QueryOutputStreamRedirect(NetworkState state, Blackhole blackhole) {
        invokeQueryRedirectOutputStream(state.http1Client, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(REQUESTS_PER_INVOCATION)
    public void http2QueryOutputStreamRedirect(NetworkState state, Blackhole blackhole) {
        invokeQueryRedirectOutputStream(state.http2Client, blackhole);
    }

    private static void configureRouting(HttpRouting.Builder routing) {
        routing.put(REDIRECT_PATH, HttpRedirectJmhBenchmark::redirect)
                .put(EARLY_REDIRECT_PATH, HttpRedirectJmhBenchmark::earlyRedirect)
                .put(TARGET_PATH, HttpRedirectJmhBenchmark::handle)
                .route(Method.QUERY, REDIRECT_PATH, HttpRedirectJmhBenchmark::queryRedirect)
                .route(Method.QUERY, TARGET_PATH, HttpRedirectJmhBenchmark::handle);
    }

    private static void redirect(ServerRequest request, ServerResponse response) {
        request.content().as(byte[].class);
        response.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, TARGET_PATH)
                .send();
    }

    private static void earlyRedirect(ServerRequest request, ServerResponse response) {
        response.status(Status.TEMPORARY_REDIRECT_307)
                .header(HeaderNames.LOCATION, TARGET_PATH)
                .send();
    }

    private static void queryRedirect(ServerRequest request, ServerResponse response) {
        request.content().as(byte[].class);
        response.status(Status.FOUND_302)
                .header(HeaderNames.LOCATION, TARGET_PATH)
                .send();
    }

    private static void handle(ServerRequest request, ServerResponse response) {
        request.content().as(byte[].class);
        response.send(RESPONSE_ENTITY);
    }

    private static void invokeRedirectOutputStream(HttpClient<?> client, String path, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            ClientRequest<?> request = client.method(Method.PUT)
                    .uri(path)
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

    private static void invokeLegacyEntity(HttpClient<?> client,
                                           String path,
                                           LegacyEntity entity,
                                           boolean expectContinue,
                                           Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (HttpClientResponse response = client.method(Method.PUT)
                    .uri(path)
                    .sendExpectContinue(expectContinue)
                    .followRedirects(true)
                    .submit(entity)) {
                consume(response, blackhole);
            }
        }
    }

    private static void invokeQueryRedirect(HttpClient<?> client, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            try (HttpClientResponse response = client.method(Method.QUERY)
                    .uri(REDIRECT_PATH)
                    .header(HeaderNames.CONTENT_TYPE, "application/octet-stream")
                    .followRedirects(true)
                    .submit(REQUEST_ENTITY)) {
                consume(response, blackhole);
            }
        }
    }

    private static void invokeQueryRedirectOutputStream(HttpClient<?> client, Blackhole blackhole) {
        for (int i = 0; i < REQUESTS_PER_INVOCATION; i++) {
            ClientRequest<?> request = client.method(Method.QUERY)
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

    public enum WriterType {
        LEGACY,
        INSTANCE_UNKNOWN,
        INSTANCE_KNOWN
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

    @State(Scope.Benchmark)
    public static class LegacyWriterState {
        @Param({"14", "65536"})
        public int legacyPayloadSize;

        @Param({"LEGACY"})
        public WriterType writerType;

        private Http1Client http1Client;
        private LegacyEntity entity;

        @Setup(Level.Trial)
        public void setup(NetworkState networkState) {
            var payload = new byte[legacyPayloadSize];
            Arrays.fill(payload, (byte) 'a');
            entity = new LegacyEntity(payload);
            try {
                http1Client = Http1Client.builder()
                        .baseUri("http://" + HOST + ":" + networkState.server.port())
                        .shareConnectionCache(false)
                        .servicesDiscoverServices(false)
                        .maxInMemoryEntity(0)
                        .addMediaSupport(new LegacyMediaSupport(writerType))
                        .build();
                warmUp(http1Client);
            } catch (RuntimeException e) {
                tearDown();
                throw e;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (http1Client != null) {
                http1Client.closeResource();
            }
        }
    }

    private record LegacyEntity(byte[] bytes) {
    }

    private static final class LegacyMediaSupport implements MediaSupport {
        private final WriterType writerType;

        private LegacyMediaSupport(WriterType writerType) {
            this.writerType = writerType;
        }

        @Override
        public String name() {
            return "legacy-redirect-benchmark";
        }

        @Override
        public String type() {
            return "legacy-redirect-benchmark";
        }

        @Override
        public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
            return type.rawType() == LegacyEntity.class
                    ? new WriterResponse<>(SupportLevel.SUPPORTED, () -> new LegacyWriter<>(writerType))
                    : WriterResponse.unsupported();
        }
    }

    private static final class LegacyWriter<T> implements EntityWriter<T> {
        private final WriterType writerType;

        private LegacyWriter(WriterType writerType) {
            this.writerType = writerType;
        }

        @Override
        public boolean supportsInstanceWriter() {
            return writerType != WriterType.LEGACY;
        }

        @Override
        public InstanceWriter instanceWriter(GenericType<T> type, T object, WritableHeaders<?> headers) {
            LegacyEntity entity = (LegacyEntity) object;
            headers.set(HeaderNames.CONTENT_TYPE, "application/octet-stream");
            return new InstanceWriter() {
                @Override
                public OptionalLong contentLength() {
                    return writerType == WriterType.INSTANCE_KNOWN
                            ? OptionalLong.of(entity.bytes().length)
                            : OptionalLong.empty();
                }

                @Override
                public boolean alwaysInMemory() {
                    return false;
                }

                @Override
                public void write(OutputStream output) {
                    try (output) {
                        output.write(entity.bytes());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                @Override
                public byte[] instanceBytes() {
                    return entity.bytes();
                }
            };
        }

        @Override
        public void write(GenericType<T> type,
                          T object,
                          OutputStream outputStream,
                          Headers requestHeaders,
                          WritableHeaders<?> responseHeaders) {
            throw new UnsupportedOperationException("Client writer only");
        }

        @Override
        public void write(GenericType<T> type, T object, OutputStream outputStream, WritableHeaders<?> headers) {
            LegacyEntity entity = (LegacyEntity) object;
            try (outputStream) {
                headers.set(HeaderNames.CONTENT_TYPE, "application/octet-stream");
                outputStream.write(entity.bytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
