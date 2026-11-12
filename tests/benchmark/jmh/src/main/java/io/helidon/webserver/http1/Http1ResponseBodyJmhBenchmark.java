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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.context.Context;
import io.helidon.common.socket.PeerInfo;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Fixed-length response construction and buffer consumption without sockets or TLS.
 * The queued configuration exercises response ownership decisions; consumption is synchronous and does not measure scheduling.
 * Compare the same benchmark against baseline and candidate server artifacts.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class Http1ResponseBodyJmhBenchmark {
    private static final int OFFSET = 17;
    private static final Http1ConnectionListener SEND_LISTENER = new Http1ConnectionListener() { };

    @Param({"512", "1024", "10240", "262144"})
    public int payloadSize;

    @Param({"directNio", "queuedNio", "blocking"})
    public String transportConfig;

    private BenchmarkContext ctx;
    private Http1ServerRequest request;
    private ConsumingWriter writer;
    private byte[] payload;
    private byte[] slice;

    @Setup
    public void setup() {
        boolean nio = switch (transportConfig) {
            case "directNio", "queuedNio" -> true;
            case "blocking" -> false;
            default -> throw new IllegalArgumentException("Unknown transport configuration: " + transportConfig);
        };
        ctx = new BenchmarkContext(nio, transportConfig.equals("queuedNio") ? 16 : 0);
        payload = new byte[payloadSize];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ('a' + i % 26);
        }
        slice = new byte[payloadSize + 2 * OFFSET];
        Arrays.fill(slice, (byte) '!');
        System.arraycopy(payload, 0, slice, OFFSET, payload.length);
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
        request = Http1ServerRequest.create(ctx,
                                            HttpSecurity.create(),
                                            HttpPrologue.create("HTTP/1.1", "HTTP", "1.1", Method.GET, "/", true),
                                            WritableHeaders.create(),
                                            1,
                                            outcome);
        var sink = new CountingOutputStream();
        sink.recording = new ByteArrayOutputStream();
        writer = new ConsumingWriter(nio, sink);
        sendArray();
        verifyWire(sink);
        sendSlice();
        verifyWire(sink);
        sink.recording = null;
    }

    @Benchmark
    public long sendArray() {
        var response = new Http1ServerResponse(ctx, SEND_LISTENER, writer, request, true, true);
        response.send(payload);
        return writer.sink.checksum;
    }

    @Benchmark
    public long sendSlice() {
        var response = new Http1ServerResponse(ctx, SEND_LISTENER, writer, request, true, true);
        response.send(slice, OFFSET, payloadSize);
        return writer.sink.checksum;
    }

    private void verifyWire(CountingOutputStream sink) {
        byte[] wire = sink.recording.toByteArray();
        String text = new String(wire, StandardCharsets.US_ASCII);
        int separator = text.indexOf("\r\n\r\n");
        if (!text.startsWith("HTTP/1.1 200 OK\r\n") || separator < 0) {
            throw new IllegalStateException("Invalid HTTP/1 response headers");
        }
        String headers = text.substring(0, separator).toLowerCase(Locale.ROOT);
        if (!headers.contains("\r\ncontent-length: " + payloadSize)
                || headers.contains("\r\ntransfer-encoding:")
                || !Arrays.equals(wire, separator + 4, wire.length, payload, 0, payload.length)
                || sink.length != wire.length) {
            throw new IllegalStateException("Incorrect response length or entity for " + transportConfig);
        }
        sink.recording.reset();
        sink.length = 0;
        sink.checksum = 0;
    }

    private static final class CountingOutputStream extends OutputStream {
        private ByteArrayOutputStream recording;
        private long length;
        private long checksum;

        @Override
        public void write(int value) {
            length++;
            checksum += value & 0xff;
            if (recording != null) {
                recording.write(value);
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int count) {
            length += count;
            if (count > 0) {
                checksum += count + (bytes[offset] & 0xff) + (bytes[offset + count - 1] & 0xff);
            }
            if (recording != null) {
                recording.write(bytes, offset, count);
            }
        }
    }

    private static final class ConsumingWriter implements DataWriter {
        private final boolean nio;
        private final CountingOutputStream sink;
        private final ByteBuffer staging = ByteBuffer.allocate(8192);

        private ConsumingWriter(boolean nio, CountingOutputStream sink) {
            this.nio = nio;
            this.sink = sink;
        }

        @Override
        public void write(BufferData... buffers) {
            for (BufferData buffer : buffers) {
                write(buffer);
            }
        }

        @Override
        public void write(BufferData buffer) {
            if (nio) {
                while (buffer.available() > 0) {
                    staging.clear();
                    int written = buffer.writeTo(staging, buffer.available());
                    if (written <= 0) {
                        throw new IllegalStateException("Response buffer made no progress");
                    }
                    sink.write(staging.array(), 0, written);
                }
            } else {
                buffer.writeTo(sink);
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

    private static final class BenchmarkContext implements ConnectionContext, ListenerContext {
        private final Context context = Context.create();
        private final ListenerConfig config;
        private final MediaContext mediaContext = MediaContext.builder()
                .mediaSupportsDiscoverServices(false)
                .build();
        private final ContentEncodingContext encodingContext = ContentEncodingContext.builder()
                .contentEncodingsDiscoverServices(false)
                .build();

        private BenchmarkContext(boolean nio, int writeQueueLength) {
            config = WebServer.builder().useNio(nio).writeQueueLength(writeQueueLength).buildPrototype();
        }

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
