/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.webclient.grpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.webclient.http2.Http2ClientImpl;
import io.helidon.webclient.http2.Http2StreamConfig;
import io.helidon.webclient.http2.LockingStreamIdSequence;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcBaseClientCallTest {

    @Test
    void testHeadersAndMetadata() {
        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "sugar");
        WritableHeaders<?> headers = GrpcBaseClientCall.setupHeaders(metadata, "localhost", "foo");
        assertThat(headers.size(), greaterThan(2));
        assertThat(headers.get(Http2Headers.AUTHORITY_NAME).get(), is("localhost"));
        assertThat(headers.get(Http2Headers.METHOD_NAME).get(), is("POST"));
        assertThat(headers.get(Http2Headers.PATH_NAME).get(), is("/foo"));
        assertThat(headers.get(Http2Headers.SCHEME_NAME).get(), is("http"));
        assertThat(headers.get(HeaderNames.COOKIE).get(), is("sugar"));
        assertThat(headers.get(HeaderNames.TE).get(), is("trailers"));
    }

    @Test
    void responseParserDoesNotConsumeNextCoalescedMessage() {
        MethodDescriptor<String, String> descriptor = stringDescriptor();
        var call = newCall(descriptor);
        BufferData data = BufferData.create(grpcData("one"), grpcData("two"));

        assertThat(call.toResponse(data), is("one"));
        assertThat(call.toResponse(data), is("two"));
    }

    @Test
    void deframerPreservesSplitPrefixAndBody() {
        byte[] frame = grpcData("fragmented").readBytes();
        var deframer = new GrpcDeframer(16, Integer.MAX_VALUE);

        assertThat(deframer.deframe(BufferData.create(Arrays.copyOfRange(frame, 0, 2))), nullValue());
        assertThat(deframer.deframe(BufferData.create(Arrays.copyOfRange(frame, 2, 7))), nullValue());
        BufferData result = deframer.deframe(BufferData.create(Arrays.copyOfRange(frame, 7, frame.length)));

        assertThat(newCall(stringDescriptor()).toResponse(result), is("fragmented"));
        assertThat(deframer.hasRemainder(), is(false));
    }

    @Test
    void deframerSeparatesCoalescedMessages() {
        BufferData data = BufferData.create(grpcData("one"), grpcData("two"));
        var deframer = new GrpcDeframer(16, Integer.MAX_VALUE);
        var call = newCall(stringDescriptor());

        BufferData first = deframer.deframe(data);
        assertThat(call.toResponse(first), is("one"));
        assertThat(deframer.hasRemainder(), is(true));

        BufferData second = deframer.deframe(data);
        assertThat(call.toResponse(second), is("two"));
        assertThat(deframer.hasRemainder(), is(false));
    }

    @Test
    void deframerRejectsIncompleteFrameAtEndOfStream() {
        var deframer = new GrpcDeframer(16, Integer.MAX_VALUE);
        assertThat(deframer.deframe(BufferData.create(new byte[] {0, 0})), nullValue());

        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class, deframer::endOfStream);

        assertThat(failure.getStatus().getCode(), is(Status.Code.INTERNAL));
        assertThat(failure.getStatus().getDescription(), is("Incomplete gRPC message data"));
        assertThat(deframer.hasPartialFrame(), is(false));
        assertThat(deframer.hasRemainder(), is(false));
    }

    @Test
    void clientCallRejectsIncompleteFrameAfterRemoteClose() {
        byte[] message = grpcData("one").readBytes();
        BufferData partialPrefix = BufferData.create(Arrays.copyOfRange(message, 0, 2));
        GrpcClient client = grpcClient();
        var stream = new ScriptedGrpcClientStream((GrpcClientImpl) client, dataFrame(partialPrefix));
        var call = new GrpcUnaryClientCall<String, String>((GrpcChannel) client.channel(),
                                                           stringDescriptor(),
                                                           CallOptions.DEFAULT) {
            @Override
            GrpcClientStream clientStream() {
                return stream;
            }
        };

        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class, call::readGrpcFrame);

        assertThat(failure.getStatus().getCode(), is(Status.Code.INTERNAL));
        assertThat(failure.getStatus().getDescription(), is("Incomplete gRPC message data"));
        assertThat(stream.readCount(), is(2));
        assertThat(call.readGrpcFrame(), nullValue());
        assertThat(stream.readCount(), is(3));
    }

    @Test
    void clientCallRejectsIncompleteDataEndingStream() {
        byte[] message = grpcData("one").readBytes();
        BufferData partialPrefix = BufferData.create(Arrays.copyOfRange(message, 0, 2));
        GrpcClient client = grpcClient();
        var stream = new ScriptedGrpcClientStream((GrpcClientImpl) client, dataFrame(partialPrefix, true));
        var call = new GrpcUnaryClientCall<String, String>((GrpcChannel) client.channel(),
                                                           stringDescriptor(),
                                                           CallOptions.DEFAULT) {
            @Override
            GrpcClientStream clientStream() {
                return stream;
            }
        };

        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class, call::readGrpcFrame);

        assertThat(failure.getStatus().getCode(), is(Status.Code.INTERNAL));
        assertThat(failure.getStatus().getDescription(), is("Incomplete gRPC message data"));
        assertThat(stream.readCount(), is(1));
        assertThat(call.readGrpcFrame(), nullValue());
        assertThat(stream.readCount(), is(2));
    }

    @Test
    void clientCallPreservesNonOkTrailersForIncompleteFrame() {
        byte[] message = grpcData("one").readBytes();
        BufferData partialPrefix = BufferData.create(Arrays.copyOfRange(message, 0, 2));
        WritableHeaders<?> trailers = WritableHeaders.create();
        trailers.set(HeaderValues.create(GrpcBaseClientCall.STATUS_NAME,
                                         String.valueOf(Status.Code.RESOURCE_EXHAUSTED.value())));
        trailers.set(HeaderValues.create(HeaderNames.create("test-trailer"), "test-value"));
        GrpcClient client = grpcClient();
        var stream = new ScriptedGrpcClientStream((GrpcClientImpl) client,
                                                  dataFrame(partialPrefix),
                                                  trailers);
        var call = new GrpcUnaryClientCall<String, String>((GrpcChannel) client.channel(),
                                                           stringDescriptor(),
                                                           CallOptions.DEFAULT) {
            @Override
            GrpcClientStream clientStream() {
                return stream;
            }
        };

        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class, call::readGrpcFrame);

        Metadata.Key<String> trailerKey = Metadata.Key.of("test-trailer", Metadata.ASCII_STRING_MARSHALLER);
        assertThat(failure.getStatus().getCode(), is(Status.Code.RESOURCE_EXHAUSTED));
        assertThat(failure.getTrailers().get(trailerKey), is("test-value"));
        assertThat(call.readGrpcFrame(), nullValue());
    }

    @Test
    void clientCallClearsPartialFrameAfterMalformedBinaryTrailer() {
        byte[] message = grpcData("one").readBytes();
        BufferData partialPrefix = BufferData.create(Arrays.copyOfRange(message, 0, 2));
        WritableHeaders<?> trailers = WritableHeaders.create();
        trailers.set(HeaderValues.create(GrpcBaseClientCall.STATUS_NAME,
                                         String.valueOf(Status.Code.RESOURCE_EXHAUSTED.value())));
        trailers.set(HeaderValues.create(HeaderNames.create("test-bin"), "!!!"));
        GrpcClient client = grpcClient();
        var stream = new ScriptedGrpcClientStream((GrpcClientImpl) client,
                                                  dataFrame(partialPrefix),
                                                  trailers);
        var call = new GrpcUnaryClientCall<String, String>((GrpcChannel) client.channel(),
                                                           stringDescriptor(),
                                                           CallOptions.DEFAULT) {
            @Override
            GrpcClientStream clientStream() {
                return stream;
            }
        };

        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class, call::readGrpcFrame);

        assertThat(failure.getStatus().getCode(), is(Status.Code.INTERNAL));
        assertThat(failure.getCause(), instanceOf(IllegalArgumentException.class));
        assertThat(call.readGrpcFrame(), nullValue());
    }

    @Test
    void clientCallRejectsMessageLargerThanConfiguredInboundLimit() {
        BufferData prefix = BufferData.create(GrpcBaseClientCall.DATA_PREFIX_LENGTH);
        prefix.write(0);
        prefix.writeUnsignedInt32(4);
        GrpcClient client = grpcClient();
        var stream = new ScriptedGrpcClientStream((GrpcClientImpl) client, dataFrame(prefix));
        var call = new GrpcUnaryClientCall<String, String>((GrpcChannel) client.channel(),
                                                           stringDescriptor(),
                                                           CallOptions.DEFAULT.withMaxInboundMessageSize(3)) {
            @Override
            GrpcClientStream clientStream() {
                return stream;
            }
        };

        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class, call::readGrpcFrame);

        assertThat(failure.getStatus().getCode(), is(Status.Code.RESOURCE_EXHAUSTED));
        assertThat(failure.getStatus().getDescription(), is("gRPC message exceeds maximum size 3: 4"));
        assertThat(stream.readCount(), is(1));
    }

    @Test
    void blockingUnaryCallReportsIncompleteResponseAsStatusRuntimeException() {
        StatusRuntimeException failure = blockingUnaryFailure(new byte[] {0, 0});

        assertThat(failure.getStatus().getCode(), is(Status.Code.INTERNAL));
        assertThat(failure.getStatus().getDescription(), is("Incomplete gRPC message data"));
    }

    @Test
    void blockingUnaryCallRejectsAdditionalResponseInTerminalData() {
        byte[] response = BufferData.create(grpcData("one"), grpcData("two")).readBytes();

        StatusRuntimeException failure = blockingUnaryFailure(response);

        assertThat(failure.getStatus().getCode(), is(Status.Code.INTERNAL));
    }

    @Test
    void blockingUnaryCallRejectsTruncatedRemainderInTerminalData() {
        byte[] secondFrame = grpcData("two").readBytes();
        byte[] response = BufferData.create(grpcData("one"),
                                            BufferData.create(Arrays.copyOfRange(secondFrame, 0, 2)))
                .readBytes();

        StatusRuntimeException failure = blockingUnaryFailure(response);

        assertThat(failure.getStatus().getCode(), is(Status.Code.INTERNAL));
        assertThat(failure.getStatus().getDescription(), is("Incomplete gRPC message data"));
    }

    @Test
    void serverStreamingCallPreservesMaxInboundStatus() {
        WebServer server = responseServer(grpcPrefix(4));
        try {
            GrpcClient client = grpcClient(server);
            Iterator<String> responses = ClientCalls.blockingServerStreamingCall(
                    client.channel(),
                    stringDescriptor(MethodDescriptor.MethodType.SERVER_STREAMING),
                    CallOptions.DEFAULT.withMaxInboundMessageSize(3),
                    "request");

            StatusRuntimeException failure = assertThrows(StatusRuntimeException.class, responses::hasNext);

            assertThat(failure.getStatus().getCode(), is(Status.Code.RESOURCE_EXHAUSTED));
        } finally {
            server.stop();
        }
    }

    @Test
    void unaryCallClosesOnMaxInboundStatus() throws InterruptedException {
        WebServer server = responseServer(grpcPrefix(4));
        try {
            GrpcClient client = grpcClient(server);
            @SuppressWarnings("unchecked")
            GrpcUnaryClientCall<String, String> call = (GrpcUnaryClientCall<String, String>) client.channel()
                    .newCall(stringDescriptor(), CallOptions.DEFAULT.withMaxInboundMessageSize(3));
            AtomicReference<Status> closeStatus = new AtomicReference<>();
            AtomicInteger closeCount = new AtomicInteger();
            CountDownLatch closed = new CountDownLatch(1);
            call.start(new ClientCall.Listener<>() {
                @Override
                public void onClose(Status status, Metadata trailers) {
                    closeStatus.set(status);
                    closeCount.incrementAndGet();
                    closed.countDown();
                }
            }, new Metadata());
            call.request(1);

            call.sendMessage("request");
            call.halfClose();

            assertThat(closed.await(10, TimeUnit.SECONDS), is(true));
            assertThat(closeStatus.get().getCode(), is(Status.Code.RESOURCE_EXHAUSTED));
            assertThat(closeCount.get(), is(1));
            assertThat(call.clientStream().streamState(), is(Http2StreamState.CLOSED));
        } finally {
            server.stop();
        }
    }

    @Test
    void unaryCallClosesOnMalformedBinaryTrailer() throws InterruptedException {
        WebServer server = WebServer.builder()
                .tls(tls -> tls.enabled(false))
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(Http2Config.create())
                                               .build())
                .addRouting(HttpRouting.builder()
                                    .post("/test.Test/Call", (req, res) -> {
                                        HeaderName binaryTrailer = HeaderNames.create("test-bin");
                                        res.header(HeaderValues.create(HeaderNames.TRAILER,
                                                                       "grpc-status, " + binaryTrailer));
                                        res.trailers().set(HeaderValues.create(GrpcBaseClientCall.STATUS_NAME,
                                                                               String.valueOf(
                                                                                       Status.Code.RESOURCE_EXHAUSTED
                                                                                               .value())));
                                        res.trailers().set(HeaderValues.create(binaryTrailer, "!!!"));
                                        res.send(new byte[] {0, 0});
                                    }))
                .build()
                .start();
        try {
            GrpcClient client = grpcClient(server);
            @SuppressWarnings("unchecked")
            GrpcUnaryClientCall<String, String> call = (GrpcUnaryClientCall<String, String>) client.channel()
                    .newCall(stringDescriptor(), CallOptions.DEFAULT);
            AtomicReference<Status> closeStatus = new AtomicReference<>();
            AtomicInteger closeCount = new AtomicInteger();
            CountDownLatch closed = new CountDownLatch(1);
            call.start(new ClientCall.Listener<>() {
                @Override
                public void onClose(Status status, Metadata trailers) {
                    closeStatus.set(status);
                    closeCount.incrementAndGet();
                    closed.countDown();
                }
            }, new Metadata());
            call.request(1);

            call.sendMessage("request");
            call.halfClose();

            assertThat(closed.await(10, TimeUnit.SECONDS), is(true));
            assertThat(closeStatus.get().getCode(), is(Status.Code.INTERNAL));
            assertThat(closeCount.get(), is(1));
            assertThat(call.clientStream().streamState(), is(Http2StreamState.CLOSED));
        } finally {
            server.stop();
        }
    }

    private static StatusRuntimeException blockingUnaryFailure(byte[] response) {
        WebServer server = responseServer(response);
        try {
            GrpcClient client = grpcClient(server);
            return assertThrows(StatusRuntimeException.class,
                                () -> ClientCalls.blockingUnaryCall(client.channel(),
                                                                   stringDescriptor(),
                                                                   CallOptions.DEFAULT,
                                                                   "request"));
        } finally {
            server.stop();
        }
    }

    private static WebServer responseServer(byte[] response) {
        return WebServer.builder()
                .tls(tls -> tls.enabled(false))
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(Http2Config.create())
                                               .build())
                .addRouting(HttpRouting.builder()
                                    .post("/test.Test/Call", (req, res) -> res.send(response)))
                .build()
                .start();
    }

    private static GrpcClient grpcClient(WebServer server) {
        return GrpcClient.builder()
                .baseUri("http://localhost:" + server.port())
                .tls(tls -> tls.enabled(false))
                .build();
    }

    private static GrpcBaseClientCall<String, String> newCall(MethodDescriptor<String, String> descriptor) {
        GrpcClient client = grpcClient();
        return new GrpcUnaryClientCall<>((GrpcChannel) client.channel(), descriptor, CallOptions.DEFAULT);
    }

    private static GrpcClient grpcClient() {
        return GrpcClient.builder()
                .baseUri("http://localhost:1")
                .tls(tls -> tls.enabled(false))
                .build();
    }

    private static MethodDescriptor<String, String> stringDescriptor() {
        return stringDescriptor(MethodDescriptor.MethodType.UNARY);
    }

    private static MethodDescriptor<String, String> stringDescriptor(MethodDescriptor.MethodType methodType) {
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                try {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        return MethodDescriptor.<String, String>newBuilder()
                .setType(methodType)
                .setFullMethodName("test.Test/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private static BufferData grpcData(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        BufferData data = BufferData.create(5 + bytes.length);
        data.write(0);
        data.writeUnsignedInt32(bytes.length);
        data.write(bytes);
        return data;
    }

    private static byte[] grpcPrefix(int messageLength) {
        BufferData data = BufferData.create(GrpcBaseClientCall.DATA_PREFIX_LENGTH);
        data.write(0);
        data.writeUnsignedInt32(messageLength);
        return data.readBytes();
    }

    private static Http2FrameData dataFrame(BufferData data) {
        return dataFrame(data, false);
    }

    private static Http2FrameData dataFrame(BufferData data, boolean endOfStream) {
        int flags = endOfStream ? Http2Flag.END_OF_STREAM : 0;
        return new Http2FrameData(Http2FrameHeader.create(data.available(),
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(flags),
                                                          1),
                                  data);
    }

    private static final class ScriptedGrpcClientStream extends GrpcClientStream {
        private final Http2FrameData data;
        private final CompletableFuture<Headers> trailers = new CompletableFuture<>();
        private final AtomicInteger readCount = new AtomicInteger();
        private Http2StreamState streamState = Http2StreamState.OPEN;

        private ScriptedGrpcClientStream(GrpcClientImpl client, Http2FrameData data) {
            this((Http2ClientImpl) client.http2Client(), data);
        }

        private ScriptedGrpcClientStream(GrpcClientImpl client, Http2FrameData data, Headers trailers) {
            this((Http2ClientImpl) client.http2Client(), data);
            this.trailers.complete(trailers);
        }

        private ScriptedGrpcClientStream(Http2ClientImpl http2Client, Http2FrameData data) {
            super(null,
                  Http2Settings.create(),
                  null,
                  new Http2StreamConfig() {
                      @Override
                      public boolean priorKnowledge() {
                          return true;
                      }

                      @Override
                      public int priority() {
                          return 0;
                      }

                      @Override
                      public Duration readTimeout() {
                          return Duration.ofSeconds(1);
                      }
                  },
                  http2Client.prototype(),
                  LockingStreamIdSequence.create(),
                  http2Client);
            this.data = data;
        }

        @Override
        public Http2FrameData readOne(Duration pollTimeout) {
            return switch (readCount.incrementAndGet()) {
            case 1 -> {
                if (data.header().flags(Http2FrameTypes.DATA).endOfStream()) {
                    streamState = Http2StreamState.HALF_CLOSED_REMOTE;
                }
                yield data;
            }
            case 2, 3 -> {
                streamState = Http2StreamState.HALF_CLOSED_REMOTE;
                yield null;
            }
            default -> throw new AssertionError("Unexpected extra stream read");
            };
        }

        @Override
        public Http2StreamState streamState() {
            return streamState;
        }

        @Override
        public CompletableFuture<Headers> trailers() {
            return trailers;
        }

        private int readCount() {
            return readCount.get();
        }
    }

}
