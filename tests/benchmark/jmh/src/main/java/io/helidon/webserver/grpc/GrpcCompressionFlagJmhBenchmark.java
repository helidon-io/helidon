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

package io.helidon.webserver.grpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;

import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerMethodDefinition;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Measures valid message framing through the server's actual gRPC DATA handler.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class GrpcCompressionFlagJmhBenchmark {
    @Param({"false", "true"})
    private boolean compressed;
    @Param({"32", "1024"})
    private int payloadSize;
    @Param({"0", "2"})
    private int headerSplit;

    private GrpcProtocolHandler<byte[], byte[]> handler;
    private byte[] payload;
    private byte[] frame;
    private byte[] received;
    private Http2FrameHeader completeHeader;
    private Http2FrameHeader prefixHeader;
    private Http2FrameHeader suffixHeader;

    @Setup
    public void setup() throws IOException {
        payload = new byte[payloadSize];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31);
        }
        byte[] wirePayload = payload;
        if (compressed) {
            var output = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(output)) {
                gzip.write(payload);
            }
            wirePayload = output.toByteArray();
        }
        var framed = BufferData.create(5 + wirePayload.length);
        framed.write(compressed ? 1 : 0);
        framed.writeUnsignedInt32(wirePayload.length);
        framed.write(wirePayload);
        frame = new byte[framed.available()];
        framed.read(frame);
        completeHeader = dataHeader(frame.length);
        prefixHeader = dataHeader(headerSplit);
        suffixHeader = dataHeader(frame.length - headerSplit);
        var marshaller = new ByteArrayMarshaller();
        var method = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                .setFullMethodName("benchmark.Framing/Message")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
        var definition = ServerMethodDefinition.create(method, (call, _) -> {
            call.request(Integer.MAX_VALUE);
            return new ServerCall.Listener<byte[]>() {
                @Override
                public void onMessage(byte[] message) {
                    received = message;
                }
            };
        });
        // This route never reads transport attributes or schedules transport work.
        handler = new GrpcProtocolHandler<>(null,
                                            Http2Headers.create(WritableHeaders.create()
                                                                        .add(HeaderNames.create("grpc-encoding"), "gzip")),
                                            new UnusedWriter(),
                                            1,
                                            null,
                                            Http2StreamState.OPEN,
                                            GrpcRouteHandler.methodDefinition(definition, null, WeightedBag.create()),
                                            GrpcConfig.builder().enableMetrics(false).build(),
                                            null);
        handler.init();
        if (!Arrays.equals(message(), payload)) {
            throw new IllegalStateException("The actual gRPC handler did not deliver the complete benchmark payload");
        }
    }

    @Benchmark
    public byte[] message() {
        received = null;
        var data = BufferData.create(frame);
        if (headerSplit == 0) {
            handler.data(completeHeader, data);
        } else {
            var first = BufferData.create(headerSplit);
            first.write(data, headerSplit);
            handler.data(prefixHeader, first);
            handler.data(suffixHeader, data);
        }
        if (received == null) {
            throw new IllegalStateException("The gRPC handler did not deliver a message");
        }
        return received;
    }

    @TearDown
    public void tearDown() {
        handler.close();
    }

    private static Http2FrameHeader dataHeader(int length) {
        return Http2FrameHeader.create(length, Http2FrameTypes.DATA, Http2Flag.DataFlags.create(0), 1);
    }

    private static final class ByteArrayMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final class UnusedWriter implements Http2StreamWriter {
        @Override
        public void write(Http2FrameData frame) {
            throw new IllegalStateException("Unexpected transport write");
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound outbound) {
            throw new IllegalStateException("Unexpected DATA write");
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                FlowControl.Outbound outbound) {
            throw new IllegalStateException("Unexpected response headers");
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame, FlowControl.Outbound outbound) {
            throw new IllegalStateException("Unexpected response headers and DATA");
        }
    }
}
