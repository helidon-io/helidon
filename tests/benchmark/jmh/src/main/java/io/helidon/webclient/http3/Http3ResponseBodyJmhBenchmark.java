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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.http3.Http3MessageReader;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.quic.QuicClientRuntime;

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

/**
 * Established HTTP/3 GET exchanges with complete response materialization or streaming consumption.
 *
 * <p>Client and server run in the same JVM; process CPU and allocation include both endpoints and payload validation.
 * The same harness must be used for both normal production implementations. A successful prior-knowledge HTTP/3
 * response also passes the production connection's negotiated {@code h3} ALPN check.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
public class Http3ResponseBodyJmhBenchmark {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Complete response body size, including the empty-response control.
     */
    @Param({"0", "1024", "131072"})
    public int payloadSize;

    /**
     * Unchanged default scheduling or a caller-owned asynchronous two-worker QUIC runtime pool.
     */
    @Param({"DEFAULT", "FJP2"})
    public RuntimeExecutorMode runtimeExecutorMode;

    /**
     * Verified source-manifest identity supplied by the evidence runner, or the finite-smoke classification.
     */
    @Param({"functional-smoke"})
    public String sourceIdentity;

    private byte[] expectedBody;
    private Http3BenchmarkEnvironment environment;
    private Http3Client client;
    private ForkJoinPool runtimePool;

    /**
     * Creates one standalone server/client and verifies GET, materialization, POST and Expect paths before timing.
     *
     * @throws Exception if setup or finite qualification fails
     */
    @Setup
    public void setUp() throws Exception {
        if (payloadSize != 0 && payloadSize != 1024 && payloadSize != 131072) {
            throw new IllegalArgumentException("Response body size must be 0, 1024 or 131072");
        }
        if (runtimeExecutorMode == null) {
            throw new IllegalArgumentException("Runtime executor mode is required");
        }
        if (sourceIdentity == null || sourceIdentity.isBlank()) {
            throw new IllegalArgumentException("Response benchmark source identity is required");
        }
        requireSingleClassOrigin(Http3ClientResponseImpl.class);
        requireSingleClassOrigin(Http3RequestStream.class);
        requireSingleClassOrigin(Http3MessageReader.class);
        requireSingleClassOrigin(Http3StreamSupport.class);
        requireSingleClassOrigin(QuicClientRuntime.class);
        expectedBody = new byte[payloadSize];
        for (int index = 0; index < payloadSize; index++) {
            expectedBody[index] = expectedByte(index);
        }
        try {
            // This array is initialized once and never written by application code after publication.
            byte[] body = expectedBody;
            environment = Http3BenchmarkEnvironment.create(routing -> routing
                    .get("/body", (_, response) -> {
                        response.header(HeaderValues.create(HeaderNames.CONTENT_LENGTH, body.length));
                        response.send(body);
                    })
                    .post("/echo", (request, response) -> {
                        boolean expect = request.headers().contains(HeaderValues.EXPECT_100);
                        response.header("benchmark-expect", Boolean.toString(expect));
                        response.send(request.content().as(byte[].class));
                    }));
            var protocol = Http3ClientProtocolConfig.builder()
                    .priorKnowledge(true)
                    .initialResponseTimeout(TIMEOUT)
                    .handshakeTimeout(TIMEOUT)
                    .streamOpenTimeout(TIMEOUT);
            if (runtimeExecutorMode == RuntimeExecutorMode.FJP2) {
                // The benchmark owns this pool; the production QUIC runtime borrows it.
                // Standard factory/compensation replace the experimental probe's instrumented worker factory.
                runtimePool = new ForkJoinPool(2, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
                protocol.runtimeExecutor(runtimePool);
            }
            client = environment.client(environment.baseUri(environment.port()), TIMEOUT, builder -> builder
                    .shareConnectionCache(false)
                    .connectionCacheSize(1)
                    .servicesDiscoverServices(false)
                    .readTimeout(TIMEOUT)
                    .protocolConfig(protocol.build()));
            materializedGet();
            streamedGet();
            verifyPost(false);
            verifyPost(true);
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
     * Measures the actual production response byte-array conversion, including full content verification and close.
     * The zero-byte control consumes the empty response stream because conversion without an entity is unsupported.
     *
     * @return checksum of every response byte
     */
    @Benchmark
    public int materializedGet() {
        try (Http3ClientResponse response = client.get("/body").request()) {
            requireResponse(response);
            if (payloadSize == 0) {
                var entity = response.entity();
                if (entity.hasEntity()) {
                    throw new IllegalStateException("Expected no entity for the zero-byte response control");
                }
                try (var input = entity.inputStream()) {
                    return verifyBody(input.readAllBytes());
                }
            }
            return verifyBody(response.as(byte[].class));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Measures complete streaming GET consumption with invocation-local drain storage.
     *
     * @return checksum of every response byte
     */
    @Benchmark
    public int streamedGet() {
        try (Http3ClientResponse response = client.get("/body").request();
             var input = response.inputStream()) {
            requireResponse(response);
            byte[] buffer = new byte[8192];
            int consumed = 0;
            int checksum = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    throw new IllegalStateException("Streaming response made no progress");
                }
                for (int index = 0; index < read; index++) {
                    if (consumed >= payloadSize || buffer[index] != expectedByte(consumed)) {
                        throw new IllegalStateException("Streaming response content mismatch at " + consumed);
                    }
                    checksum += buffer[index] & 0xFF;
                    consumed++;
                }
            }
            if (consumed != payloadSize) {
                throw new IllegalStateException("Streaming response length mismatch: " + consumed);
            }
            return checksum;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Awaits scoped client cleanup before shutting down the borrowed pool and then the server.
     *
     * @throws Exception if cleanup or bounded pool termination fails
     */
    @TearDown
    public void tearDown() throws Exception {
        Throwable failure = null;
        boolean interrupted = false;
        if (client != null) {
            try {
                client.closeResourceAsync().toCompletableFuture().get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (Throwable cleanupFailure) {
                failure = cleanupFailure;
                interrupted = cleanupFailure instanceof InterruptedException;
            }
            client = null;
        }
        if (runtimePool != null) {
            runtimePool.shutdown();
            try {
                if (!runtimePool.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                    runtimePool.shutdownNow();
                    if (!runtimePool.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                        throw new IllegalStateException("HTTP/3 benchmark runtime pool did not terminate");
                    }
                }
            } catch (Throwable cleanupFailure) {
                runtimePool.shutdownNow();
                interrupted |= cleanupFailure instanceof InterruptedException;
                failure = collectFailure(failure, cleanupFailure);
                try {
                    if (!runtimePool.awaitTermination(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                        failure = collectFailure(failure,
                                new IllegalStateException("HTTP/3 benchmark runtime pool did not terminate"));
                    }
                } catch (InterruptedException terminationFailure) {
                    interrupted = true;
                    failure = collectFailure(failure, terminationFailure);
                }
            }
            runtimePool = null;
        }
        if (environment != null) {
            try {
                environment.close();
            } catch (Throwable cleanupFailure) {
                failure = collectFailure(failure, cleanupFailure);
            }
            environment = null;
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure != null) {
            throw new IllegalStateException("HTTP/3 response benchmark cleanup failed", failure);
        }
    }

    private static byte expectedByte(int index) {
        return (byte) (index * 31 + 7);
    }

    private static void requireResponse(Http3ClientResponse response) {
        if (response.getClass() != Http3ClientResponseImpl.class
                || !Http3Client.PROTOCOL_ID.equals(response.protocolId()) || !Status.OK_200.equals(response.status())) {
            throw new IllegalStateException(
                    "Expected the normal successful prior-knowledge HTTP/3 response implementation");
        }
    }

    private static void requireSingleClassOrigin(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        var resources = type.getClassLoader().getResources(resource);
        int count = 0;
        while (resources.hasMoreElements()) {
            resources.nextElement();
            count++;
        }
        if (count != 1 || type.getProtectionDomain().getCodeSource() == null) {
            throw new IllegalStateException("Expected one normal runtime class origin for " + type.getName());
        }
    }

    private static Throwable collectFailure(Throwable current, Throwable addition) {
        if (current == null) {
            return addition;
        }
        if (current != addition) {
            current.addSuppressed(addition);
        }
        return current;
    }

    private int verifyBody(byte[] bytes) {
        if (bytes.length != payloadSize) {
            throw new IllegalStateException("Materialized response length mismatch: " + bytes.length);
        }
        int checksum = 0;
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] != expectedByte(index)) {
                throw new IllegalStateException("Materialized response content mismatch at " + index);
            }
            checksum += bytes[index] & 0xFF;
        }
        return checksum;
    }

    private void verifyPost(boolean expect) {
        byte[] body = new byte[] {1, 2, 3, 4};
        var request = client.post("/echo").sendExpectContinue(expect);
        try (Http3ClientResponse response = request.submit(body)) {
            requireResponse(response);
            String observed = response.headers().first(HeaderNames.create("benchmark-expect")).orElseThrow();
            if (!Arrays.equals(response.as(byte[].class), body) || !Boolean.toString(expect).equals(observed)) {
                throw new IllegalStateException("POST/Expect body verification failed: " + expect);
            }
        }
    }

    /**
     * Runtime scheduling choices; neither choice changes reader or request-producer executors.
     */
    public enum RuntimeExecutorMode {
        /**
         * Leave the production runtime executor option absent.
         */
        DEFAULT,
        /**
         * Borrow the benchmark-owned two-worker asynchronous fork/join pool.
         */
        FJP2
    }
}
