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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;

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
 * Real HTTP/1 response consumption against an unchanged JDK socket responder.
 * The responder does not use Helidon WebServer or the entity implementations under comparison.
 * Each thread owns a client, responder and destination buffer. The scenario is
 * {@code decodedBytes:callerBytes:access:transport:framing:encoding:connection}, where access is
 * {@code bulk}, {@code entity}, {@code scalar}, {@code mixed}, {@code bytes} or {@code string}; transport is
 * {@code plain} or {@code tls}; framing is {@code fixed} or {@code chunked}; encoding is {@code identity} or
 * {@code gzip}; and connection is {@code reuse} or {@code new}. A single scenario parameter prevents an accidental
 * Cartesian product. Pass an explicit scenario list when running a campaign.
 *
 * <p>Setup verifies every decoded byte through the selected access path, HTTP/1 status/protocol and physical
 * connection behavior before timing.
 * Measured exchanges consume the complete body and check its length and first/last byte. This keeps a full payload
 * checksum out of the bandwidth measurement. Gzip bodies are compressed once during setup. TLS uses the existing
 * {@code client.p12} and {@code server.p12} test fixtures, so {@code target/test-classes} must be on the classpath.
 *
 * <p>Use an exact include for {@code Http1ResponseReadJmhBenchmark.exchange}. Results are time per complete exchange;
 * derive delivered bandwidth from decodedBytes. The unchanged raw responder shares the fork, so GC profiling includes
 * its allocations and JDK TLS costs, not only client allocation. This is a client-path comparison, not an HttpArena
 * server score. Small socket-read call counts and fragmented-producer correctness require separate untimed checks.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@State(Scope.Thread)
public class Http1ResponseReadJmhBenchmark {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final HeaderName CONNECTION_ID = HeaderNames.create("X-Benchmark-Connection");

    @Param({"64:64:bulk:plain:fixed:identity:reuse",
            "8192:8192:bulk:plain:fixed:identity:reuse",
            "1048576:65536:bulk:plain:fixed:identity:reuse"})
    public String scenario;

    private Scenario selected;
    private byte[] body;
    private byte[] destination;
    private long expectedSignature;
    private RawResponder responder;
    private Http1Client client;
    private String originalConnection;
    private String latestConnection;

    static void submitResponseHandler(Socket socket, ExecutorService handlers, Set<Socket> sockets, Runnable handler)
            throws IOException {
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            sockets.add(socket);
            handlers.submit(handler);
        } catch (IOException | RuntimeException | Error failure) {
            // The acceptor owns the socket until the handler has been submitted successfully.
            try {
                socket.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            } finally {
                sockets.remove(socket);
            }
            throw failure;
        }
    }

    @Setup
    public void setup() throws IOException {
        selected = Scenario.parse(scenario);
        body = body(selected.bodyBytes());
        destination = new byte[selected.callerBytes()];
        expectedSignature = signature(body.length, body[0] & 0xff, body[body.length - 1] & 0xff);
        byte[] wireBody = selected.gzip() ? gzip(body) : body;
        try {
            responder = new RawResponder(wireBody, selected);
            client = Http1Client.builder()
                    .baseUri((selected.tls() ? "https" : "http") + "://localhost:" + responder.port())
                    .tls(selected.tls() ? clientTls() : Tls.builder().enabled(false).build())
                    .proxy(Proxy.noProxy())
                    .socketOptions(options -> options.tcpNoDelay(true))
                    .shareConnectionCache(false)
                    .servicesDiscoverServices(false)
                    .contentEncoding(ContentEncodingContext.builder().addContentEncoding(GzipDecoder.INSTANCE).build())
                    .connectTimeout(TIMEOUT)
                    .readTimeout(TIMEOUT)
                    .build();
            validateBody();
            originalConnection = latestConnection;
            if (exchange() != expectedSignature) {
                throw new IllegalStateException("Unexpected response signature during setup");
            }
            if (selected.reuse() && !originalConnection.equals(latestConnection)) {
                throw new IllegalStateException("Setup did not reuse the original physical connection");
            }
            if (!selected.reuse() && originalConnection.equals(latestConnection)) {
                throw new IllegalStateException("New-connection scenario reused a physical connection");
            }
            responder.checkHealthy();
        } catch (IOException | RuntimeException | Error failure) {
            try {
                closeResources();
            } catch (IOException | RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @TearDown
    public void tearDown() throws IOException {
        try {
            if (selected.reuse() && !originalConnection.equals(latestConnection)) {
                throw new IllegalStateException("Measured exchanges replaced the original physical connection");
            }
            responder.checkHealthy();
            validateBody();
        } finally {
            closeResources();
        }
    }

    @Benchmark
    public long exchange() throws IOException {
        try (Http1ClientResponse response = request()) {
            long actual = switch (selected.access()) {
                case BYTES -> {
                    byte[] value = response.as(byte[].class);
                    yield signature(value.length, value[0] & 0xff, value[value.length - 1] & 0xff);
                }
                case STRING -> {
                    String value = response.as(String.class);
                    yield signature(value.length(), value.charAt(0), value.charAt(value.length() - 1));
                }
                case BULK, ENTITY, SCALAR, MIXED -> {
                    InputStream stream = selected.access() == Access.ENTITY
                            ? response.entity().inputStream()
                            : response.inputStream();
                    try (stream) {
                        yield consume(stream);
                    }
                }
            };
            if (actual != expectedSignature) {
                throw new IllegalStateException("Incorrect decoded length or body sentinel for " + scenario
                                                        + ": expected=" + expectedSignature + ", actual=" + actual);
            }
            return actual;
        }
    }

    private static byte[] body(int length) {
        byte[] result = new byte[length];
        int random = 0x12345678;
        for (int i = 0; i < result.length; i++) {
            random ^= random << 13;
            random ^= random >>> 17;
            random ^= random << 5;
            result[i] = (byte) (' ' + (random & 63));
        }
        return result;
    }

    private static byte[] gzip(byte[] value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value);
        }
        return bytes.toByteArray();
    }

    private static long signature(long count, int first, int last) {
        return count << 16 | first << 8 | last;
    }

    private static Tls serverTls() {
        Keys keys = Keys.builder()
                .keystore(store -> store.keystore(Resource.create("server.p12")).passphrase("password"))
                .build();
        return Tls.builder().privateKey(keys).privateKeyCertChain(keys).build();
    }

    private static Tls clientTls() {
        return Tls.builder()
                .trust(trust -> trust.keystore(store -> store.passphrase("password")
                        .trustStore(true).keystore(Resource.create("client.p12"))))
                .build();
    }

    private Http1ClientResponse request() {
        Http1ClientResponse response = client.get("/body").keepAlive(selected.reuse()).request();
        if (response.status().code() != 200 || !Http1Client.PROTOCOL_ID.equals(response.protocolId())) {
            response.close();
            throw new IllegalStateException("Expected HTTP/1 status 200");
        }
        latestConnection = response.headers().get(CONNECTION_ID).values();
        if (selected.reuse() && originalConnection != null && !originalConnection.equals(latestConnection)) {
            response.close();
            throw new IllegalStateException("Response was received on a replacement physical connection");
        }
        return response;
    }

    private void validateBody() throws IOException {
        try (Http1ClientResponse response = request()) {
            if (selected.access() == Access.STRING) {
                String actual = response.as(String.class);
                String expected = new String(body, StandardCharsets.US_ASCII);
                if (!actual.equals(expected)) {
                    throw new IllegalStateException("Full decoded String differs for " + scenario + ": expected "
                                                            + expected.length() + " characters, received "
                                                            + actual.length());
                }
                return;
            }
            byte[] actual;
            if (selected.access() == Access.BYTES) {
                actual = response.as(byte[].class);
            } else {
                InputStream stream = selected.access() == Access.ENTITY
                        ? response.entity().inputStream()
                        : response.inputStream();
                try (stream) {
                    actual = readUntimedBody(stream);
                }
            }
            if (!Arrays.equals(actual, body)) {
                throw new IllegalStateException("Full decoded body differs for " + scenario + ": expected "
                                                        + body.length + " bytes, received " + actual.length);
            }
        }
    }

    private byte[] readUntimedBody(InputStream stream) throws IOException {
        byte[] actual = new byte[body.length];
        int count = 0;
        while (true) {
            if (selected.access() == Access.SCALAR || selected.access() == Access.MIXED) {
                int value = stream.read();
                if (value == -1) {
                    break;
                }
                if (count == actual.length) {
                    throw new IllegalStateException("Decoded body exceeds expected length for " + scenario);
                }
                actual[count++] = (byte) value;
                if (selected.access() == Access.SCALAR) {
                    continue;
                }
            }
            int read = stream.read(destination, 0, destination.length);
            if (read == -1) {
                break;
            }
            if (read == 0) {
                throw new IllegalStateException("Positive-length blocking response read made no progress");
            }
            if (read > actual.length - count) {
                throw new IllegalStateException("Decoded body exceeds expected length for " + scenario);
            }
            System.arraycopy(destination, 0, actual, count, read);
            count += read;
        }
        if (count != actual.length) {
            throw new IllegalStateException("Incomplete decoded body for " + scenario + ": expected "
                                                    + actual.length + " bytes, received " + count);
        }
        return actual;
    }

    private long consume(InputStream stream) throws IOException {
        long count = 0;
        int first = -1;
        int last = -1;
        while (true) {
            if (selected.access() == Access.SCALAR || selected.access() == Access.MIXED) {
                int value = stream.read();
                if (value == -1) {
                    break;
                }
                if (first == -1) {
                    first = value;
                }
                last = value;
                count++;
                if (selected.access() == Access.SCALAR) {
                    continue;
                }
            }
            int read = stream.read(destination, 0, destination.length);
            if (read == -1) {
                break;
            }
            if (read == 0) {
                throw new IllegalStateException("Positive-length blocking response read made no progress");
            }
            if (first == -1) {
                first = destination[0] & 0xff;
            }
            last = destination[read - 1] & 0xff;
            count += read;
        }
        return signature(count, first, last);
    }

    private void closeResources() throws IOException {
        try {
            if (client != null) {
                try {
                    client.closeResourceAsync().toCompletableFuture().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while closing benchmark client", e);
                } catch (ExecutionException | TimeoutException e) {
                    throw new IllegalStateException("Benchmark client did not close", e);
                }
            }
        } finally {
            if (responder != null) {
                responder.close();
            }
        }
    }

    private enum Access {
        BULK, ENTITY, SCALAR, MIXED, BYTES, STRING
    }

    private static final class GzipDecoder implements ContentEncoding {
        private static final GzipDecoder INSTANCE = new GzipDecoder();

        private GzipDecoder() {
        }

        @Override
        public String name() {
            return "gzip";
        }

        @Override
        public String type() {
            return "gzip";
        }

        @Override
        public Set<String> ids() {
            return Set.of("gzip");
        }

        @Override
        public boolean supportsEncoding() {
            return false;
        }

        @Override
        public boolean supportsDecoding() {
            return true;
        }

        @Override
        public ContentDecoder decoder() {
            return source -> {
                try {
                    return new GZIPInputStream(source);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }

        @Override
        public ContentEncoder encoder() {
            return ContentEncoder.NO_OP;
        }
    }

    private record Scenario(int bodyBytes, int callerBytes, Access access, boolean tls,
                            boolean chunked, boolean gzip, boolean reuse) {
        private static Scenario parse(String value) {
            String[] values = value.split(":", -1);
            if (values.length != 7) {
                throw new IllegalArgumentException("Expected body:buffer:access:transport:framing:encoding:connection: "
                                                           + value);
            }
            int bodyBytes = Integer.parseInt(values[0]);
            int callerBytes = Integer.parseInt(values[1]);
            if (bodyBytes < 1 || bodyBytes > 128 * 1024 * 1024 || callerBytes < 1 || callerBytes > 1024 * 1024) {
                throw new IllegalArgumentException("Invalid body or caller-buffer length: " + value);
            }
            Access access = switch (values[2]) {
                case "bulk" -> Access.BULK;
                case "entity" -> Access.ENTITY;
                case "scalar" -> Access.SCALAR;
                case "mixed" -> Access.MIXED;
                case "bytes" -> Access.BYTES;
                case "string" -> Access.STRING;
                default -> throw new IllegalArgumentException("Unknown access: " + values[2]);
            };
            return new Scenario(bodyBytes, callerBytes, access,
                                choice(values[3], "tls", "plain"),
                                choice(values[4], "chunked", "fixed"),
                                choice(values[5], "gzip", "identity"),
                                choice(values[6], "reuse", "new"));
        }

        private static boolean choice(String actual, String positive, String negative) {
            if (!actual.equals(positive) && !actual.equals(negative)) {
                throw new IllegalArgumentException("Expected " + positive + " or " + negative + ": " + actual);
            }
            return actual.equals(positive);
        }
    }

    private static final class RawResponder implements AutoCloseable {
        private static final byte[] CRLF = {'\r', '\n'};
        private static final byte[] FINAL_CHUNK = {'0', '\r', '\n', '\r', '\n'};
        private static final int CHUNK_BYTES = 8192;

        private final byte[] wireBody;
        private final Scenario scenario;
        private final ServerSocket listener;
        private final ExecutorService handlers = Executors.newVirtualThreadPerTaskExecutor();
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final AtomicLong connections = new AtomicLong();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final Thread acceptor;
        private volatile boolean stopping;

        private RawResponder(byte[] wireBody, Scenario scenario) throws IOException {
            this.wireBody = wireBody;
            this.scenario = scenario;
            listener = scenario.tls() ? serverTls().createServerSocket() : new ServerSocket();
            listener.bind(new InetSocketAddress("localhost", 0));
            acceptor = Thread.ofPlatform().daemon(true).name("http1-response-benchmark-accept").start(this::accept);
        }

        @Override
        public void close() throws IOException {
            stopping = true;
            IOException closeFailure = null;
            try {
                try {
                    listener.close();
                } catch (IOException e) {
                    closeFailure = e;
                }
                for (Socket socket : sockets) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        if (closeFailure == null) {
                            closeFailure = e;
                        } else {
                            closeFailure.addSuppressed(e);
                        }
                    }
                }
            } finally {
                handlers.shutdownNow();
                try {
                    acceptor.join(TIMEOUT.toMillis());
                    if (acceptor.isAlive() || !handlers.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                        throw new IllegalStateException("Raw responder did not stop");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while closing raw responder", e);
                }
            }
            if (closeFailure != null) {
                throw closeFailure;
            }
        }

        private static boolean readRequest(InputStream input) throws IOException {
            int matched = 0;
            for (int count = 0; count < 32 * 1024; count++) {
                int value = input.read();
                if (value == -1) {
                    if (count == 0) {
                        return false;
                    }
                    throw new IOException("Truncated benchmark request headers");
                }
                int expected = matched % 2 == 0 ? '\r' : '\n';
                matched = value == expected ? matched + 1 : value == '\r' ? 1 : 0;
                if (matched == 4) {
                    return true;
                }
            }
            throw new IOException("Benchmark request headers exceed 32 KiB");
        }

        private int port() {
            return listener.getLocalPort();
        }

        private void checkHealthy() {
            if (failure.get() != null) {
                throw new IllegalStateException("Raw benchmark responder failed", failure.get());
            }
        }

        private void accept() {
            try {
                while (!stopping) {
                    Socket socket = listener.accept();
                    long connection = connections.incrementAndGet();
                    submitResponseHandler(socket, handlers, sockets, () -> respond(socket, connection));
                }
            } catch (IOException | RuntimeException e) {
                if (!stopping) {
                    failure.compareAndSet(null, e);
                }
            }
        }

        private void respond(Socket socket, long connection) {
            try (socket; InputStream input = new BufferedInputStream(socket.getInputStream())) {
                OutputStream output = socket.getOutputStream();
                byte[] headers = ("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=us-ascii\r\n"
                        + "X-Benchmark-Connection: " + connection + "\r\n"
                        + (scenario.chunked() ? "Transfer-Encoding: chunked\r\n"
                                : "Content-Length: " + wireBody.length + "\r\n")
                        + (scenario.gzip() ? "Content-Encoding: gzip\r\n" : "")
                        + "Connection: " + (scenario.reuse() ? "keep-alive" : "close") + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII);
                while (readRequest(input)) {
                    output.write(headers);
                    if (scenario.chunked()) {
                        for (int offset = 0; offset < wireBody.length; offset += CHUNK_BYTES) {
                            int size = Math.min(CHUNK_BYTES, wireBody.length - offset);
                            output.write(Integer.toHexString(size).getBytes(StandardCharsets.US_ASCII));
                            output.write(CRLF);
                            output.write(wireBody, offset, size);
                            output.write(CRLF);
                        }
                        output.write(FINAL_CHUNK);
                    } else {
                        output.write(wireBody);
                    }
                    output.flush();
                    if (!scenario.reuse()) {
                        break;
                    }
                }
            } catch (IOException | RuntimeException e) {
                if (!stopping) {
                    failure.compareAndSet(null, e);
                }
            } finally {
                sockets.remove(socket);
            }
        }
    }
}
