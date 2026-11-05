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

package io.helidon.webserver.http1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.context.Context;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerResponseTrailers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.http.HttpSecurity;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class Http1ResponseTrailersJmhBenchmark {
    private static final String PAYLOAD_TEXT = "Hello, World!";
    private static final byte[] PAYLOAD = PAYLOAD_TEXT.getBytes(StandardCharsets.US_ASCII);
    private static final Header TRAILER_NAMES = HeaderValues.create(HeaderNames.TRAILER, "x-benchmark");
    private static final Header INITIAL_TRAILER = HeaderValues.create("x-benchmark", "initial");
    private static final Header COMPLETED_TRAILER = HeaderValues.create("x-benchmark", "complete");
    private static final Consumer<ServerResponseTrailers> BEFORE_TRAILERS = trailers -> trailers.set(COMPLETED_TRAILER);
    private static final Http1ConnectionListener SEND_LISTENER = new Http1ConnectionListener() { };

    private BenchmarkContext ctx;
    private Http1ServerRequest plainRequest;
    private Http1ServerRequest trailerRequest;
    private CountingWriter writer;

    @Setup
    public void setup() throws IOException {
        ctx = new BenchmarkContext();
        var security = HttpSecurity.create();
        var prologue = HttpPrologue.create("HTTP/1.1", "HTTP", "1.1", Method.GET, "/", true);
        var outcome = LimitAlgorithm.Outcome.immediateAcceptance("benchmark", "unlimited", new LimitAlgorithm.Token() {
            @Override
            public void dropped() {
            }

            @Override
            public void ignore() {
            }

            @Override
            public void success() {
            }
        });
        plainRequest = Http1ServerRequest.create(ctx, security, prologue, WritableHeaders.create(), 1, outcome);
        trailerRequest = Http1ServerRequest.create(ctx,
                                                   security,
                                                   prologue,
                                                   WritableHeaders.create().set(HeaderValues.TE_TRAILERS),
                                                   2,
                                                   outcome);

        var recording = new RecordingWriter();
        writer = recording;
        fixedSend();
        verifyWire(recording.take(), false, false);
        streamNoTrailers();
        verifyWire(recording.take(), true, false);
        streamWithTrailers();
        verifyWire(recording.take(), true, true);
        var accessed = (Http1ServerResponse) trailerAccess();
        if (!accessed.trailers().get(INITIAL_TRAILER.headerName()).get().equals("initial")) {
            throw new IllegalStateException("Trailer accessor did not preserve the supplied value");
        }
        writer = new CountingWriter();
    }

    @Benchmark
    public Object construct() {
        return newResponse(plainRequest);
    }

    @Benchmark
    public Object fixedSend() {
        var response = newResponse(plainRequest);
        response.send(PAYLOAD);
        return response;
    }

    @Benchmark
    public Object trailerAccess() {
        var response = newResponse(trailerRequest);
        response.trailers().set(INITIAL_TRAILER);
        return response;
    }

    @Benchmark
    public Object streamNoTrailers() throws IOException {
        var response = newResponse(plainRequest);
        writeStream(response);
        return response;
    }

    @Benchmark
    public Object streamWithTrailers() throws IOException {
        var response = newResponse(trailerRequest);
        response.header(TRAILER_NAMES);
        response.trailers().set(INITIAL_TRAILER);
        response.beforeTrailers(BEFORE_TRAILERS);
        response.streamResult("OK");
        writeStream(response);
        return response;
    }

    private static void writeStream(Http1ServerResponse response) throws IOException {
        try (OutputStream stream = response.outputStream()) {
            stream.write(PAYLOAD);
            // Force an actual chunked stream rather than the single-buffer content-length optimization.
            stream.flush();
        }
        response.commit();
    }

    private static void verifyWire(String wire, boolean chunked, boolean trailers) {
        int separator = wire.indexOf("\r\n\r\n");
        if (!wire.startsWith("HTTP/1.1 200 OK\r\n") || separator < 0) {
            throw new IllegalStateException("Invalid HTTP/1 response: " + wire);
        }
        String headers = wire.substring(0, separator).toLowerCase(Locale.ROOT);
        String body = wire.substring(separator + 4);
        if (!chunked) {
            if (!headers.contains("\r\ncontent-length: " + PAYLOAD.length)
                    || headers.contains("\r\ntransfer-encoding:")
                    || !body.equals(PAYLOAD_TEXT)) {
                throw new IllegalStateException("Invalid fixed-length response: " + wire);
            }
            return;
        }
        String prefix = Integer.toHexString(PAYLOAD.length) + "\r\n" + PAYLOAD_TEXT + "\r\n0\r\n";
        if (!headers.contains("\r\ntransfer-encoding: chunked")
                || headers.contains("\r\ncontent-length:")
                || !body.startsWith(prefix)) {
            throw new IllegalStateException("Invalid chunked response: " + wire);
        }
        String trailerBlock = body.substring(prefix.length());
        if (trailers) {
            if (!trailerBlock.endsWith("\r\n\r\n")
                    || !Set.of(trailerBlock.split("\r\n"))
                            .equals(Set.of("x-benchmark: complete", "stream-result: OK"))) {
                throw new IllegalStateException("Missing callback or incorrect trailers: " + wire);
            }
        } else if (!trailerBlock.equals("\r\n")) {
            throw new IllegalStateException("Unexpected response trailers: " + wire);
        }
    }

    private Http1ServerResponse newResponse(Http1ServerRequest request) {
        return new Http1ServerResponse(ctx, SEND_LISTENER, writer, request, true, true);
    }

    private static class CountingWriter implements DataWriter {
        private long checksum;

        @Override
        public void write(BufferData... buffers) {
            for (BufferData buffer : buffers) {
                write(buffer);
            }
        }

        @Override
        public void write(BufferData buffer) {
            while (buffer.available() > 0) {
                checksum += buffer.read();
            }
        }

        @Override
        public void writeNow(BufferData... buffers) {
            write(buffers);
        }

        @Override
        public void writeNow(BufferData buffer) {
            write(buffer);
        }
    }

    private static final class RecordingWriter extends CountingWriter {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public void write(BufferData buffer) {
            bytes.writeBytes(buffer.readBytes());
        }

        private String take() {
            String wire = bytes.toString(StandardCharsets.US_ASCII);
            bytes.reset();
            return wire;
        }
    }

    private static final class BenchmarkContext implements ConnectionContext, ListenerContext {
        private final Context context = Context.create();
        private final ListenerConfig config = WebServer.builder().buildPrototype();
        private final MediaContext mediaContext = MediaContext.builder()
                .mediaSupportsDiscoverServices(false)
                .build();
        private final ContentEncodingContext encodingContext = ContentEncodingContext.builder()
                .contentEncodingsDiscoverServices(false)
                .build();

        @Override
        public ListenerContext listenerContext() {
            return this;
        }

        @Override
        public Context context() {
            return context;
        }

        @Override
        public MediaContext mediaContext() {
            return mediaContext;
        }

        @Override
        public ContentEncodingContext contentEncodingContext() {
            return encodingContext;
        }

        @Override
        public ListenerConfig config() {
            return config;
        }

        @Override
        public String socketId() {
            return "benchmark-listener";
        }

        @Override
        public String childSocketId() {
            return "benchmark-connection";
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public ExecutorService executor() {
            throw new UnsupportedOperationException("Benchmark does not execute asynchronous tasks");
        }

        @Override
        public DataWriter dataWriter() {
            throw new UnsupportedOperationException("Benchmark supplies the response writer directly");
        }

        @Override
        public DataReader dataReader() {
            throw new UnsupportedOperationException("Benchmark does not read socket data");
        }

        @Override
        public Router router() {
            throw new UnsupportedOperationException("Benchmark does not route requests");
        }

        @Override
        public DirectHandlers directHandlers() {
            throw new UnsupportedOperationException("Benchmark does not handle connection failures");
        }

        @Override
        public PeerInfo remotePeer() {
            throw new UnsupportedOperationException("Benchmark does not access socket peers");
        }

        @Override
        public PeerInfo localPeer() {
            throw new UnsupportedOperationException("Benchmark does not access socket peers");
        }
    }
}
