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

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerMethodDefinition;
import io.grpc.Status;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Measures compression negotiation and delivery of one valid request message on a new gRPC stream.
 * Transport I/O, routing lookup, and payload preparation are outside the measured operation.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class GrpcCompressionNegotiationJmhBenchmark {
    private static final GrpcConfig CONFIG = GrpcConfig.builder().enableMetrics(false).build();
    private static final Http2StreamWriter WRITER = new TerminalWriter();

    @Param({"acceptGzip", "acceptIdentity", "encodingGzip"})
    private String negotiation;

    @Param({"32", "1024"})
    private int payloadSize;

    private Http2Headers headers;
    private GrpcRouteHandler<byte[], byte[]> route;
    private byte[] frame;
    private byte[] received;
    private Http2FrameHeader dataHeader;

    /**
     * Prepare valid wire data and verify complete payload delivery and the negotiated response encoding.
     *
     * @throws IOException if fixture compression fails
     */
    @Setup
    public void setup() throws IOException {
        var payload = new byte[payloadSize];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31);
        }
        boolean compressed = negotiation.equals("encodingGzip");
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
        dataHeader = Http2FrameHeader.create(frame.length, Http2FrameTypes.DATA, Http2Flag.DataFlags.create(1), 1);
        headers = Http2Headers.create(switch (negotiation) {
            case "acceptGzip" -> WritableHeaders.create().add(HeaderNames.create("grpc-accept-encoding"), "gzip");
            case "acceptIdentity" -> WritableHeaders.create().add(HeaderNames.create("grpc-accept-encoding"), "identity");
            case "encodingGzip" -> WritableHeaders.create().add(HeaderNames.create("grpc-encoding"), "gzip");
            default -> throw new IllegalArgumentException("Unexpected negotiation: " + negotiation);
        });
        var marshaller = new ByteArrayMarshaller();
        var method = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                .setFullMethodName("benchmark.Negotiation/Message")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
        var definition = ServerMethodDefinition.create(method, (call, _) -> {
            call.request(1);
            return new ServerCall.Listener<byte[]>() {
                @Override
                public void onMessage(byte[] message) {
                    received = message;
                }

                @Override
                public void onHalfClose() {
                    call.close(Status.OK, new Metadata());
                }
            };
        });
        route = GrpcRouteHandler.methodDefinition(definition, null, WeightedBag.create());
        if (!Arrays.equals(negotiateAndDecode(), payload)) {
            throw new IllegalStateException("Negotiated gRPC handler did not deliver the complete benchmark payload");
        }
    }

    /**
     * Initialize compression on a fresh stream and deliver one request message.
     *
     * @return the decoded request payload
     */
    @Benchmark
    public byte[] negotiateAndDecode() {
        received = null;
        // This route never reads transport attributes or schedules transport work.
        var handler = new GrpcProtocolHandler<>(null, headers, WRITER, 1, null, Http2StreamState.OPEN,
                                                route, CONFIG, null);
        try {
            handler.init();
            if (handler.identityCompressor() != negotiation.equals("acceptIdentity")) {
                throw new IllegalStateException("Unexpected negotiated response encoding for " + negotiation);
            }
            handler.data(dataHeader, BufferData.create(frame));
            if (received == null) {
                throw new IllegalStateException("The gRPC handler did not deliver a message");
            }
            if (handler.streamState() != Http2StreamState.CLOSED) {
                throw new IllegalStateException("The gRPC stream did not complete");
            }
            return received;
        } finally {
            handler.close();
        }
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

    private static final class TerminalWriter implements Http2StreamWriter {
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
            if (!flags.endOfStream()
                    || headers.httpHeaders().get(HeaderNames.create("grpc-status")).asInt().get() != 0) {
                throw new IllegalStateException("Expected successful terminal gRPC trailers");
            }
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers, int streamId, Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame, FlowControl.Outbound outbound) {
            throw new IllegalStateException("Unexpected response headers and DATA");
        }
    }
}
