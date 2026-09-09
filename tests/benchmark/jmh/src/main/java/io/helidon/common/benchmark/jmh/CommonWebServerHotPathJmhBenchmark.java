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

package io.helidon.common.benchmark.jmh;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.socket.NioSocket;
import io.helidon.common.socket.SocketWriter;
import io.helidon.common.uri.UriPath;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http1.Http1Headers;
import io.helidon.webserver.http1.Http1Prologue;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CommonWebServerHotPathJmhBenchmark {
    private static final String REQUEST_TEXT = "GET /json/25 HTTP/1.1\r\n"
            + "Host: localhost:8080\r\n"
            + "Accept: */*\r\n"
            + "\r\n";
    private static final byte[] REQUEST = REQUEST_TEXT.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PIPELINED_REQUESTS = REQUEST_TEXT.repeat(16).getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RESPONSE_HEADERS = ("HTTP/1.1 200 OK\r\n"
            + "Content-Type: text/plain\r\n"
            + "Content-Length: 13\r\n"
            + "\r\n").getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RESPONSE_PAYLOAD = "Hello, World!".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RESPONSE_PAYLOAD_1K = new byte[1024];

    @Benchmark
    public void dataReader(DataReaderState state, Blackhole blackhole) {
        readRequest(state.prologue, state.headers, blackhole);
    }

    @Benchmark
    public void dataReaderPipelined(DataReaderState state, Blackhole blackhole) {
        readRequest(state.pipelinedPrologue, state.pipelinedHeaders, blackhole);
    }

    @Benchmark
    public UriPath uriPathHelperAndNoParam(SimpleUriPathState state) {
        UriPath path = UriPath.create(state.path);
        path.path();
        return path;
    }

    @Benchmark
    public UriPath uriPathHelperAndNoParamValidated(SimpleUriPathState state) {
        UriPath path = UriPath.create(state.path);
        path.validate();
        path.path();
        return path;
    }

    @Benchmark
    public UriPath uriPathHelperAndNoParamFallback(FallbackUriPathState state) {
        UriPath path = UriPath.create(state.path);
        path.path();
        return path;
    }

    @Benchmark
    public UriPath uriPathHelperAndNoParamFallbackValidated(FallbackUriPathState state) {
        UriPath path = UriPath.create(state.path);
        path.validate();
        path.path();
        return path;
    }

    @Benchmark
    public BufferData growingBufferData(GrowingBufferState state) {
        BufferData result = BufferData.growing(256 + RESPONSE_PAYLOAD.length);
        result.write(RESPONSE_HEADERS);
        result.write(state.payload);
        state.payload.rewind();
        return result;
    }

    @Benchmark
    public int growingBufferDataReused(GrowingBufferState state) {
        BufferData result = state.reusable.clear();
        result.write(RESPONSE_HEADERS);
        result.write(state.payload);
        state.payload.rewind();
        return result.available();
    }

    @Benchmark
    public BufferData growingBufferData1K(GrowingBufferState state) {
        BufferData result = BufferData.growing(256 + RESPONSE_PAYLOAD_1K.length);
        result.write(RESPONSE_HEADERS);
        result.write(state.payload1K);
        state.payload1K.rewind();
        return result;
    }

    @Benchmark
    public int growingBufferDataReused1K(GrowingBufferState state) {
        BufferData result = state.reusable1K.clear();
        result.write(RESPONSE_HEADERS);
        result.write(state.payload1K);
        state.payload1K.rewind();
        return result.available();
    }

    @Benchmark
    public void socketWriterDirect(SocketState state) {
        state.writer.write(state.buffer);
        state.buffer.rewind();
    }

    @Benchmark
    public void socketWriter(SocketState state) {
        state.writer.writeNow(state.buffer);
        state.buffer.rewind();
    }

    @Benchmark
    public void nioSocket(SocketState state) {
        state.socket.write(state.buffer);
        state.buffer.rewind();
    }

    @Benchmark
    public void nioSocketComposite(SocketState state) {
        state.socket.write(state.compositeBuffer);
        state.compositeBuffer.rewind();
    }

    @Benchmark
    public byte[] nioSocketRead(SocketReadState state) {
        try {
            state.requestBuffer.rewind();
            while (state.requestBuffer.hasRemaining()) {
                state.writer.write(state.requestBuffer);
            }
            return state.socket.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void readRequest(Http1Prologue requestPrologue,
                                    Http1Headers requestHeaders,
                                    Blackhole blackhole) {
        HttpPrologue prologue = requestPrologue.readPrologue();
        WritableHeaders<?> headers = requestHeaders.readHeaders(prologue);
        blackhole.consume(prologue);
        blackhole.consume(headers.get(HeaderNames.HOST));
    }

    @State(Scope.Thread)
    public static class DataReaderState {
        private DataReader reader;
        private Http1Headers headers;
        private Http1Prologue prologue;
        private Http1Headers pipelinedHeaders;
        private Http1Prologue pipelinedPrologue;

        @Setup(Level.Trial)
        public void setup() {
            reader = DataReader.create(() -> REQUEST);
            headers = new Http1Headers(reader, 4096, false);
            prologue = new Http1Prologue(reader, 1024, false);
            DataReader pipelinedReader = DataReader.create(() -> PIPELINED_REQUESTS);
            pipelinedHeaders = new Http1Headers(pipelinedReader, 4096, false);
            pipelinedPrologue = new Http1Prologue(pipelinedReader, 1024, false);
        }
    }

    @State(Scope.Thread)
    public static class GrowingBufferState {
        private final BufferData payload = BufferData.create(RESPONSE_PAYLOAD);
        private final BufferData payload1K = BufferData.create(RESPONSE_PAYLOAD_1K);
        private final BufferData reusable = BufferData.growing(256 + RESPONSE_PAYLOAD.length);
        private final BufferData reusable1K = BufferData.growing(256 + RESPONSE_PAYLOAD_1K.length);
    }

    @State(Scope.Thread)
    public static class SimpleUriPathState {
        @Param("/json/25")
        private String path;
    }

    @State(Scope.Thread)
    public static class FallbackUriPathState {
        @Param({
                "/static/index.html",
                "/json/%32%35",
                "/json//25"
        })
        private String path;
    }

    @State(Scope.Thread)
    public static class SocketState {
        private final BufferData buffer = BufferData.create(RESPONSE_HEADERS);
        private final BufferData compositeBuffer = BufferData.create(BufferData.create(RESPONSE_HEADERS),
                                                                     BufferData.create(RESPONSE_PAYLOAD));

        private ServerSocketChannel listener;
        private SocketChannel reader;
        private NioSocket socket;
        private SocketWriter writer;
        private Thread drainThread;

        @Setup(Level.Trial)
        public void setup() {
            try {
                listener = ServerSocketChannel.open();
                listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                reader = SocketChannel.open(listener.getLocalAddress());
                socket = NioSocket.server(listener.accept(), "benchmark", "benchmark");
                writer = SocketWriter.create(socket);
                drainThread = Thread.ofPlatform()
                        .daemon()
                        .name("common-hot-path-jmh-drain")
                        .start(this::drain);
            } catch (IOException e) {
                closeResources();
                throw new UncheckedIOException(e);
            } catch (RuntimeException e) {
                closeResources();
                throw e;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() throws InterruptedException {
            closeResources();
            if (drainThread != null) {
                drainThread.join();
            }
        }

        private static void close(NioSocket socket) {
            if (socket == null) {
                return;
            }
            try {
                socket.close();
            } catch (RuntimeException _) {
                // best effort after a benchmark trial
            }
        }

        private static void close(AutoCloseable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (Exception _) {
                // best effort after a benchmark trial
            }
        }

        private void drain() {
            ByteBuffer drainBuffer = ByteBuffer.allocate(8 * 1024);
            try {
                while (reader.read(drainBuffer) >= 0) {
                    drainBuffer.clear();
                }
            } catch (IOException e) {
                if (reader.isOpen()) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        private void closeResources() {
            close(socket);
            close(reader);
            close(listener);
        }
    }

    @State(Scope.Thread)
    public static class SocketReadState {
        private final ByteBuffer requestBuffer = ByteBuffer.wrap(REQUEST);

        private ServerSocketChannel listener;
        private SocketChannel writer;
        private NioSocket socket;

        @Setup(Level.Trial)
        public void setup() {
            try {
                listener = ServerSocketChannel.open();
                listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                writer = SocketChannel.open(listener.getLocalAddress());
                socket = NioSocket.server(listener.accept(), "benchmark-read", "benchmark-read");
            } catch (IOException e) {
                closeResources();
                throw new UncheckedIOException(e);
            } catch (RuntimeException e) {
                closeResources();
                throw e;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            closeResources();
        }

        private static void close(NioSocket socket) {
            if (socket == null) {
                return;
            }
            try {
                socket.close();
            } catch (RuntimeException _) {
                // best effort after a benchmark trial
            }
        }

        private static void close(AutoCloseable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (Exception _) {
                // best effort after a benchmark trial
            }
        }

        private void closeResources() {
            close(socket);
            close(writer);
            close(listener);
        }
    }
}
