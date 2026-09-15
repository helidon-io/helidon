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

package io.helidon.webclient.http3;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerResponse;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Measures complete established HTTP/3 exchanges produced from bounded small-write shapes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class Http3SmallWriteJmhBenchmark {
    static final int APPLICATION_BUFFER_SIZE = 16 * 1024;
    private static final int MAX_BODY_SIZE = 128 * 1024;
    private static final byte[] BODY = new byte[MAX_BODY_SIZE];
    private static final HeaderName BYTE_COUNT = HeaderNames.create("benchmark-byte-count");

    private Http3BenchmarkEnvironment environment;
    private Http3Client client;

    /**
     * Starts one standalone HTTP/3 server and one prior-knowledge client, then establishes the connection.
     *
     * @throws Exception if setup fails
     */
    @Setup
    public void setUp() throws Exception {
        byte[] serverDrainBuffer = new byte[8192];
        try {
            environment = Http3BenchmarkEnvironment.create(routing -> routing
                    .get("/ready", (_, response) -> response.send())
                    .post("/upload", (request, response) -> {
                        try (var inputStream = request.content().inputStream()) {
                            long byteCount = 0;
                            int read;
                            while ((read = inputStream.read(serverDrainBuffer)) != -1) {
                                byteCount += read;
                            }
                            response.header(BYTE_COUNT, Long.toString(byteCount));
                            response.send();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .get("/download/buffered/{shape}", (request, response) -> streamDownload(
                            response,
                            Shape.valueOf(request.path().pathParameters().get("shape")),
                            false))
                    .get("/download/dispatch-barrier/{shape}", (request, response) -> streamDownload(
                            response,
                            Shape.valueOf(request.path().pathParameters().get("shape")),
                            true)));
            client = environment.client(environment.baseUri(environment.port()), Duration.ofSeconds(10));
            try (Http3ClientResponse response = client.get("/ready").request()) {
                requireHttp3Success(response);
            }
        } catch (Exception | Error failure) {
            try {
                tearDown();
            } catch (Exception | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    /**
     * Closes the sole client and server used by the benchmark trial.
     *
     * @throws Exception if cleanup fails
     */
    @TearDown
    public void tearDown() throws Exception {
        Throwable failure = null;
        if (client != null) {
            try {
                client.closeResource();
            } catch (Throwable cleanupFailure) {
                failure = cleanupFailure;
            }
        }
        if (environment != null) {
            try {
                environment.close();
            } catch (Throwable cleanupFailure) {
                if (failure == null) {
                    failure = cleanupFailure;
                } else if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
        client = null;
        environment = null;
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure != null) {
            throw new IllegalStateException("HTTP/3 small-write benchmark cleanup failed", failure);
        }
    }

    /**
     * Measures application writes coalesced by one fixed-size application buffer.
     *
     * @param state bounded write shape
     * @param blackhole result sink
     */
    @Benchmark
    public void bufferedUpload(SmallWriteState state, Blackhole blackhole) {
        upload(state, false, blackhole);
    }

    /**
     * Measures eager DATA-frame production by flushing each upload chunk. The flushes below the client dispatch
     * window do not form synchronous dispatch barriers.
     *
     * @param state bounded write shape
     * @param blackhole result sink
     */
    @Benchmark
    public void eagerFrameUpload(SmallWriteState state, Blackhole blackhole) {
        upload(state, true, blackhole);
    }

    /**
     * Measures server writes coalesced by one fixed-size application buffer.
     *
     * @param state bounded write shape
     * @param blackhole result sink
     */
    @Benchmark
    public void bufferedDownload(SmallWriteState state, Blackhole blackhole) {
        download(state.bufferedDownloadPath, state, blackhole);
    }

    /**
     * Measures the synchronous server dispatch barrier caused by flushing every response chunk.
     *
     * @param state bounded write shape
     * @param blackhole result sink
     */
    @Benchmark
    public void dispatchBarrierDownload(SmallWriteState state, Blackhole blackhole) {
        download(state.dispatchBarrierDownloadPath, state, blackhole);
    }

    private void upload(SmallWriteState state, boolean flushEachChunk, Blackhole blackhole) {
        Shape shape = state.shape;
        try (Http3ClientResponse response = client.post("/upload").outputStream(helidonOutputStream -> {
            try (var outputStream = new BufferedOutputStream(helidonOutputStream, APPLICATION_BUFFER_SIZE)) {
                for (int offset = 0; offset < shape.bodySize; offset += shape.chunkSize) {
                    int length = Math.min(shape.chunkSize, shape.bodySize - offset);
                    outputStream.write(BODY, offset, length);
                    if (flushEachChunk) {
                        outputStream.flush();
                    }
                }
            }
        })) {
            requireHttp3Success(response);
            String byteCount = response.headers().first(BYTE_COUNT)
                    .orElseThrow(() -> new IllegalStateException("Missing benchmark byte-count response header"));
            if (!shape.bodySizeText.equals(byteCount)) {
                throw new IllegalStateException("HTTP/3 small-write upload completed with " + byteCount
                                                        + " bytes instead of " + shape.bodySize);
            }
            blackhole.consume(response.status());
            blackhole.consume(response.protocolId());
            blackhole.consume(byteCount);
        }
    }

    private void download(String path, SmallWriteState state, Blackhole blackhole) {
        try (Http3ClientResponse response = client.get(path).request();
             var inputStream = response.inputStream()) {
            requireHttp3Success(response);
            long byteCount = 0;
            int read;
            while ((read = inputStream.read(state.readBuffer)) != -1) {
                byteCount += read;
            }
            if (byteCount != state.shape.bodySize) {
                throw new IllegalStateException("HTTP/3 small-write download completed with " + byteCount
                                                        + " bytes instead of " + state.shape.bodySize);
            }
            blackhole.consume(response.status());
            blackhole.consume(response.protocolId());
            blackhole.consume(byteCount);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void requireHttp3Success(Http3ClientResponse response) {
        if (!Status.OK_200.equals(response.status()) || !Http3Client.PROTOCOL_ID.equals(response.protocolId())) {
            throw new IllegalStateException("Expected successful HTTP/3 exchange, observed "
                                                    + response.status() + " over " + response.protocolId());
        }
    }

    private static void streamDownload(ServerResponse response, Shape shape, boolean dispatchBarrier) {
        try (var outputStream = new BufferedOutputStream(response.outputStream(), APPLICATION_BUFFER_SIZE)) {
            for (int offset = 0; offset < shape.bodySize; offset += shape.chunkSize) {
                int length = Math.min(shape.chunkSize, shape.bodySize - offset);
                outputStream.write(BODY, offset, length);
                if (dispatchBarrier) {
                    outputStream.flush();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Coupled body and chunk sizes keep the benchmark matrix bounded while covering short and sustained exchanges.
     */
    public enum Shape {
        /**
         * Eight KiB body written one byte at a time.
         */
        SHORT_8K_1(8 * 1024, 1),
        /**
         * Eight KiB body written 16 bytes at a time.
         */
        SHORT_8K_16(8 * 1024, 16),
        /**
         * Eight KiB body written 256 bytes at a time.
         */
        SHORT_8K_256(8 * 1024, 256),
        /**
         * Eight KiB body written in MTU-adjacent 1200-byte chunks.
         */
        SHORT_8K_1200(8 * 1024, 1200),
        /**
         * Eight KiB body written 4096 bytes at a time.
         */
        SHORT_8K_4096(8 * 1024, 4096),
        /**
         * Sustained 128 KiB body written in MTU-adjacent 1200-byte chunks.
         */
        SUSTAINED_128K_1200(128 * 1024, 1200),
        /**
         * Sustained 128 KiB body written 4096 bytes at a time.
         */
        SUSTAINED_128K_4096(128 * 1024, 4096);

        private final int bodySize;
        private final int chunkSize;
        private final String bodySizeText;

        Shape(int bodySize, int chunkSize) {
            this.bodySize = bodySize;
            this.chunkSize = chunkSize;
            this.bodySizeText = Integer.toString(bodySize);
        }

        int bodySize() {
            return bodySize;
        }

        int chunkSize() {
            return chunkSize;
        }
    }

    /**
     * Coupled small-write shape and reusable client-side drain storage.
     */
    @State(Scope.Benchmark)
    public static class SmallWriteState {
        /**
         * Bounded short or sustained write shape.
         */
        @Param({
                "SHORT_8K_1",
                "SHORT_8K_16",
                "SHORT_8K_256",
                "SHORT_8K_1200",
                "SHORT_8K_4096",
                "SUSTAINED_128K_1200",
                "SUSTAINED_128K_4096"
        })
        public Shape shape;

        /**
         * SHA-256 of the verified source manifest, or the functional-smoke classification.
         */
        @Param({"functional-smoke"})
        public String sourceIdentity;

        private final byte[] readBuffer = new byte[8192];
        private String bufferedDownloadPath;
        private String dispatchBarrierDownloadPath;

        /**
         * Prepares fixed request paths outside measured invocations.
         */
        @Setup
        public void setUp() {
            if (sourceIdentity.isBlank()) {
                throw new IllegalArgumentException("HTTP/3 small-write source identity is blank");
            }
            bufferedDownloadPath = "/download/buffered/" + shape.name();
            dispatchBarrierDownloadPath = "/download/dispatch-barrier/" + shape.name();
        }
    }
}
