/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.SniMatchType;

import io.grpc.Drainable;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcProtocolHandlerTest {

    private static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.create("grpc-accept-encoding");
    private static final int GRPC_PREFIX_LENGTH = 5;

    @Test
    @SuppressWarnings("unchecked")
    void testIdentityCompressorFlag() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "identity");
        GrpcProtocolHandler handler = new GrpcProtocolHandler(new UnimplementedGrpcConnectionContext(),
                                                              Http2Headers.create(headers),
                                                              null,
                                                              1,
                                                              null,
                                                              Http2StreamState.OPEN,
                                                              null,
                                                              GrpcConfig.create());
        handler.initCompression(null, headers);
        assertThat(handler.identityCompressor(), is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGzipCompressor() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "gzip");
        GrpcProtocolHandler handler = new GrpcProtocolHandler(new UnimplementedGrpcConnectionContext(),
                                                              Http2Headers.create(headers),
                                                              null,
                                                              1,
                                                              null,
                                                              Http2StreamState.OPEN,
                                                              null,
                                                              GrpcConfig.create());
        handler.initCompression(null, headers);
        assertThat(handler.identityCompressor(), is(false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testIgnoreGzipCompressor() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "gzip");
        GrpcProtocolHandler handler = new GrpcProtocolHandler(new UnimplementedGrpcConnectionContext(),
                                                              Http2Headers.create(headers),
                                                              null,
                                                              1,
                                                              null,
                                                              Http2StreamState.OPEN,
                                                              null,
                                                              GrpcConfig.builder()
                                                                      .enableCompression(false)
                                                                      .build());
        handler.initCompression(null, headers);
        assertThat(handler.identityCompressor(), is(true));
    }

    @Test
    void testFromHalfCloseLocalTransition() {
        Http2StreamState next = GrpcProtocolHandler.nextStreamState(
                Http2StreamState.HALF_CLOSED_LOCAL, Http2StreamState.HALF_CLOSED_REMOTE);
        assertThat(next, is(Http2StreamState.CLOSED));
    }

    @Test
    void testFromHalfCloseRemoteTransition() {
        Http2StreamState next = GrpcProtocolHandler.nextStreamState(
                Http2StreamState.HALF_CLOSED_REMOTE, Http2StreamState.HALF_CLOSED_LOCAL);
        assertThat(next, is(Http2StreamState.CLOSED));
    }

    @Test
    void testSendHeadersWrapsUncheckedIOException() {
        ServerCall<String, String> serverCall = createServerCall(headersFailingWriter());

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> serverCall.sendHeaders(new Metadata()));

        assertThat(exception.getCause(), instanceOf(UncheckedIOException.class));
    }

    @Test
    void testSendMessageWrapsUncheckedIOException() {
        ServerCall<String, String> serverCall = createServerCall(dataFailingWriter());

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> serverCall.sendMessage("hello"));

        assertThat(exception.getCause(), instanceOf(UncheckedIOException.class));
    }

    @Test
    void testCancelledPeerDoesNotLeakGrpcCancellationStacktrace() {
        ServerCall.Listener<String> listener = new ServerCall.Listener<>() {
            @Override
            public void onHalfClose() {
                throw Status.CANCELLED.asRuntimeException();
            }
        };

        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                                Http2Headers.create(WritableHeaders.create()),
                                                                                noOpWriter(),
                                                                                1,
                                                                                null,
                                                                                Http2StreamState.OPEN,
                                                                                route(listener),
                                                                                GrpcConfig.create());
        handler.init();
        handler.rstStream(new Http2RstStream(io.helidon.http.http2.Http2ErrorCode.CANCEL));

        Http2FrameHeader header = Http2FrameHeader.create(0,
                                                          Http2FrameTypes.DATA,
                                                          Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                          1);

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> handler.data(header, BufferData.empty()));

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(RuntimeException.class)),
                () -> assertThat(Status.fromThrowable(exception.getCause()).getCode(), is(Status.Code.CANCELLED))
        );
    }

    @Test
    void bufferDataInputStreamReturnsMinusOneAtEof() throws IOException {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        BufferData bufferData = BufferData.create(content);
        try (var stream = new GrpcProtocolHandler.BufferDataInputStream(bufferData)) {
            // drain the stream
            for (int i = 0; i < content.length; i++) {
                assertThat("byte " + i, stream.read(), is(content[i] & 0xFF));
            }

            // InputStream contract: all three read overloads must return -1 at EOF
            assertAll(
                    () -> assertThat("read()", stream.read(), is(-1)),
                    () -> assertThat("read(byte[])", stream.read(new byte[8]), is(-1)),
                    () -> assertThat("read(byte[],off,len)", stream.read(new byte[8], 0, 8), is(-1))
            );
        }
    }

    @Test
    void bufferDataInputStreamReadAllBytes() throws IOException {
        byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
        BufferData bufferData = BufferData.create(content);
        try (var stream = new GrpcProtocolHandler.BufferDataInputStream(bufferData)) {
            // readAllBytes() internally loops on read(byte[],off,len) until -1.
            // Before the fix, this hung forever because the stream returned 0 instead of -1.
            byte[] result = stream.readAllBytes();

            assertThat(result, is(content));
        }
    }

    @Test
    void defaultMaxReadBufferSizeMatchesGrpcEcosystemStandard() {
        // Matches GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE in grpc-java:
        // https://github.com/grpc/grpc-java/blob/v1.73.0/core/src/main/java/io/grpc/internal/GrpcUtil.java#L212
        assertThat(GrpcConfig.create().maxReadBufferSize(), is(4 * 1024 * 1024));
    }

    @Test
    @SuppressWarnings("unchecked")
    void oversizedMessageThrowsResourceExhausted() {
        // grpc-java throws RESOURCE_EXHAUSTED (not a plain Java exception) for oversized messages.
        // See: MessageDeframer.processHeader() in
        // https://github.com/grpc/grpc-java/blob/v1.73.0/core/src/main/java/io/grpc/internal/MessageDeframer.java#L388-L393
        int limit = 100 * 1024;
        GrpcProtocolHandler<?, ?> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                       Http2Headers.create(WritableHeaders.create()),
                                                                       null, 1, null,
                                                                       Http2StreamState.OPEN, null,
                                                                       GrpcConfig.builder().maxReadBufferSize(limit).build());
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                                                  () -> handler.allocateReadBuffer(limit + 1));
        assertThat(ex.getStatus().getCode(), is(Status.Code.RESOURCE_EXHAUSTED));
    }

    @Test
    @SuppressWarnings("unchecked")
    void oversizedMessageBelowInitialBufferThrowsResourceExhausted() {
        // A limit below the initial 16 KB read buffer must still be enforced. The gRPC length
        // prefix carries the full message size, so the limit can be checked before any buffer
        // is grown.
        int limit = 8 * 1024;
        GrpcProtocolHandler<?, ?> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                       Http2Headers.create(WritableHeaders.create()),
                                                                       null, 1, null,
                                                                       Http2StreamState.OPEN, null,
                                                                       GrpcConfig.builder().maxReadBufferSize(limit).build());
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                                                  () -> handler.allocateReadBuffer(limit + 1));
        assertThat(ex.getStatus().getCode(), is(Status.Code.RESOURCE_EXHAUSTED));
    }

    @Test
    @SuppressWarnings("unchecked")
    void oversizedMessageExceedingIntRangeThrowsResourceExhausted() {
        // The gRPC length prefix is an unsigned 32-bit value (up to 4_294_967_295), which does
        // not fit in a signed int. The limit check must run on the full value, before any cast,
        // so a declared length above Integer.MAX_VALUE cannot wrap negative and slip past it.
        GrpcProtocolHandler<?, ?> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                       Http2Headers.create(WritableHeaders.create()),
                                                                       null, 1, null,
                                                                       Http2StreamState.OPEN, null,
                                                                       GrpcConfig.create());
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                                                  () -> handler.allocateReadBuffer(3_000_000_000L));
        assertThat(ex.getStatus().getCode(), is(Status.Code.RESOURCE_EXHAUSTED));
    }

    @Test
    void testCloseSuppressesTrailerWriteDisconnect() {
        ServerCall<String, String> serverCall = createServerCall(closeFailingWriter());
        serverCall.sendHeaders(new Metadata());

        assertDoesNotThrow(() -> serverCall.close(Status.OK, new Metadata()));
        assertThat(serverCall.isCancelled(), is(true));
    }

    @Test
    void exposesSniHostsInGrpcContext() {
        AtomicReference<GrpcConnectionContext> grpcConnectionContext = new AtomicReference<>();
        ServerCallHandler<String, String> callHandler = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
                grpcConnectionContext.set(ServerContextKeys.CONNECTION_CONTEXT.get(io.grpc.Context.current()));
                return new ServerCall.Listener<>() {
                };
            }
        };
        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(
                new UnimplementedGrpcConnectionContext(sniContext("api.example.com", "*.example.com")),
                Http2Headers.create(WritableHeaders.create()),
                noOpWriter(),
                1,
                null,
                Http2StreamState.OPEN,
                route(callHandler),
                GrpcConfig.create());

        handler.init();

        assertThat(grpcConnectionContext.get().sniRequestedHost(), is(Optional.of("api.example.com")));
        assertThat(grpcConnectionContext.get().sniMatchedHost(), is(Optional.of("*.example.com")));
    }

    private static ServerCall<String, String> createServerCall(Http2StreamWriter streamWriter) {
        GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(new UnimplementedGrpcConnectionContext(),
                                                                                Http2Headers.create(WritableHeaders.create()),
                                                                                streamWriter,
                                                                                1,
                                                                                null,
                                                                                Http2StreamState.OPEN,
                                                                                route(new ServerCall.Listener<>() {
                                                                                }),
                                                                                GrpcConfig.create());
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(GRPC_ACCEPT_ENCODING, "identity");
        handler.initCompression(null, headers);
        return handler.createServerCall();
    }

    private static GrpcRouteHandler<String, String> route(ServerCall.Listener<String> listener) {
        return route(new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
                return listener;
            }
        });
    }

    private static GrpcRouteHandler<String, String> route(ServerCallHandler<String, String> callHandler) {
        ServerMethodDefinition<String, String> definition =
                ServerMethodDefinition.create(stringMethodDescriptor(), callHandler);
        return GrpcRouteHandler.methodDefinition(definition, null, WeightedBag.create());
    }

    private static SniContext sniContext(String presentedHost, String matchedHost) {
        return new SniContext() {
            @Override
            public Optional<String> presentedHost() {
                return Optional.of(presentedHost);
            }

            @Override
            public Optional<String> matchedHost() {
                return Optional.of(matchedHost);
            }

            @Override
            public SniMatchType matchType() {
                return SniMatchType.WILDCARD;
            }

            @Override
            public AuthorityCheck checkAuthority(String authority) {
                return AuthorityCheck.ALLOWED;
            }
        };
    }

    private static MethodDescriptor<String, String> stringMethodDescriptor() {
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
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.Test/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private static Http2StreamWriter headersFailingWriter() {
        return new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                throw new UncheckedIOException(new IOException("Broken pipe"));
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    Http2FrameData dataFrame,
                                    FlowControl.Outbound flowControl) {
                throw new UnsupportedOperationException("Unused");
            }
        };
    }

    private static Http2StreamWriter dataFailingWriter() {
        return new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
                throw new UncheckedIOException(new IOException("Broken pipe"));
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                return 0;
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    Http2FrameData dataFrame,
                                    FlowControl.Outbound flowControl) {
                throw new UnsupportedOperationException("Unused");
            }
        };
    }

    private static Http2StreamWriter closeFailingWriter() {
        AtomicInteger headerWrites = new AtomicInteger();
        return new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                if (headerWrites.incrementAndGet() == 1) {
                    return 0;
                }
                throw new UncheckedIOException(new IOException("Broken pipe"));
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    Http2FrameData dataFrame,
                                    FlowControl.Outbound flowControl) {
                throw new UnsupportedOperationException("Unused");
            }
        };
    }

    private static Http2StreamWriter noOpWriter() {
        return new Http2StreamWriter() {
            @Override
            public void write(Http2FrameData frame) {
            }

            @Override
            public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    FlowControl.Outbound flowControl) {
                return 0;
            }

            @Override
            public int writeHeaders(Http2Headers headers,
                                    int streamId,
                                    Http2Flag.HeaderFlags flags,
                                    Http2FrameData dataFrame,
                                    FlowControl.Outbound flowControl) {
                return 0;
            }
        };
    }

    /**
     * When message processing throws a {@link StatusRuntimeException} (e.g. {@code RESOURCE_EXHAUSTED}
     * for an oversized message), the call is closed with that status, but the client may still be
     * sending request data. The handler must keep consuming those frames: if its stream state left
     * the set accepted by {@code Http2ServerStream.DATA_RECEIVABLE_STATES}, the stream's read loop
     * would stop while the connection thread keeps queueing DATA frames into the stream's bounded
     * queue, eventually blocking the connection thread.
     */
    @Nested
    class EarlyCloseOnStatusException {

        private static final Metadata.Key<String> DEBUG_KEY =
                Metadata.Key.of("x-test-debug", Metadata.ASCII_STRING_MARSHALLER);

        // allocateReadBuffer() checks the limit only for messages larger than its initial
        // 16 KB buffer, so the limit must be at least that for the rejection to trigger
        private static final int LIMIT = 16 * 1024;

        private final RecordingWriter writer = new RecordingWriter();
        private final RecordingListener listener = new RecordingListener();
        private final AtomicReference<ServerCall<String, String>> callRef = new AtomicReference<>();

        @Test
        void oversizedMessageKeepsConsumingInboundData() {
            GrpcProtocolHandler<String, String> handler = handler(listener);

            handler.data(dataHeader(false), messagePrefix(LIMIT + 1));

            assertAll(
                    () -> assertThat(trailerStatus(), is(Status.Code.RESOURCE_EXHAUSTED.value())),
                    () -> assertThat("read loop must keep running to drain the request",
                                     handler.streamState(), is(Http2StreamState.OPEN)),
                    () -> assertThat(listener.completes, is(1))
            );

            // remaining request data is discarded without reaching the listener
            handler.data(dataHeader(false), message("hi"));
            assertAll(
                    () -> assertThat(listener.messages, is(List.of())),
                    () -> assertThat(writer.headersWritten, hasSize(1)),
                    () -> assertThat(handler.streamState(), is(Http2StreamState.OPEN))
            );

            // end of stream completes the drain and lets the stream close
            handler.data(dataHeader(true), BufferData.empty());
            assertAll(
                    () -> assertThat(handler.streamState(), is(Http2StreamState.CLOSED)),
                    () -> assertThat(listener.halfCloses, is(0))
            );
        }

        @Test
        void oversizedMessageOnLastFrameClosesStream() {
            GrpcProtocolHandler<String, String> handler = handler(listener);

            handler.data(dataHeader(true), messagePrefix(LIMIT + 1));

            assertAll(
                    () -> assertThat(trailerStatus(), is(Status.Code.RESOURCE_EXHAUSTED.value())),
                    () -> assertThat("no more inbound data, nothing left to drain",
                                     handler.streamState(), is(Http2StreamState.CLOSED))
            );
        }

        @Test
        void statusExceptionTrailersArePropagated() {
            Metadata trailers = new Metadata();
            trailers.put(DEBUG_KEY, "details");
            GrpcProtocolHandler<String, String> handler = handler(new RecordingListener() {
                @Override
                public void onMessage(String message) {
                    throw Status.FAILED_PRECONDITION.asRuntimeException(trailers);
                }
            });

            handler.data(dataHeader(false), message("hi"));

            Headers written = writer.headersWritten.getFirst().httpHeaders();
            assertAll(
                    () -> assertThat(trailerStatus(), is(Status.Code.FAILED_PRECONDITION.value())),
                    () -> assertThat(written.get(HeaderNames.create("x-test-debug")).get(), is("details"))
            );
        }

        @Test
        void rstStreamAfterCloseDoesNotCancelListener() {
            GrpcProtocolHandler<String, String> handler = handler(listener);
            callRef.get().close(Status.OK, new Metadata());

            // a late RST_STREAM, e.g. the client cancelling the remainder of a rejected request,
            // must not surface as a cancellation after the call has already completed
            handler.rstStream(new Http2RstStream(io.helidon.http.http2.Http2ErrorCode.CANCEL));

            assertAll(
                    () -> assertThat(listener.cancels, is(0)),
                    () -> assertThat(listener.completes, is(1))
            );
        }

        private GrpcProtocolHandler<String, String> handler(ServerCall.Listener<String> callListener) {
            GrpcProtocolHandler<String, String> handler = new GrpcProtocolHandler<>(
                    new UnimplementedGrpcConnectionContext(),
                    Http2Headers.create(WritableHeaders.create()),
                    writer,
                    1,
                    null,
                    Http2StreamState.OPEN,
                    route(new ServerCallHandler<>() {
                        @Override
                        public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
                            callRef.set(call);
                            call.request(2);
                            return callListener;
                        }
                    }),
                    GrpcConfig.builder().maxReadBufferSize(LIMIT).build());
            handler.init();
            return handler;
        }

        private int trailerStatus() {
            return writer.headersWritten.getFirst().httpHeaders().get(GrpcStatus.STATUS_NAME).get(int.class);
        }

        private Http2FrameHeader dataHeader(boolean endOfStream) {
            return Http2FrameHeader.create(0,
                                           Http2FrameTypes.DATA,
                                           Http2Flag.DataFlags.create(endOfStream ? Http2Flag.END_OF_STREAM : 0),
                                           1);
        }

        /**
         * A gRPC length-prefixed frame header declaring {@code length} bytes, without the payload.
         * The declared size alone is enough for the server to reject the message.
         */
        private BufferData messagePrefix(int length) {
            BufferData buffer = BufferData.create(GRPC_PREFIX_LENGTH);
            buffer.write(0);
            buffer.writeUnsignedInt32(length);
            return buffer;
        }

        private BufferData message(String content) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            BufferData buffer = BufferData.create(GRPC_PREFIX_LENGTH + bytes.length);
            buffer.write(0);
            buffer.writeUnsignedInt32(bytes.length);
            buffer.write(bytes);
            return buffer;
        }
    }

    private static class RecordingListener extends ServerCall.Listener<String> {
        private final List<String> messages = new ArrayList<>();
        private int halfCloses;
        private int cancels;
        private int completes;

        @Override
        public void onMessage(String message) {
            messages.add(message);
        }

        @Override
        public void onHalfClose() {
            halfCloses++;
        }

        @Override
        public void onCancel() {
            cancels++;
        }

        @Override
        public void onComplete() {
            completes++;
        }
    }

    private static final class RecordingWriter implements Http2StreamWriter {
        private final List<Http2Headers> headersWritten = new ArrayList<>();

        @Override
        public void write(Http2FrameData frame) {
        }

        @Override
        public void writeData(Http2FrameData frame, FlowControl.Outbound flowControl) {
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                FlowControl.Outbound flowControl) {
            headersWritten.add(headers);
            return 0;
        }

        @Override
        public int writeHeaders(Http2Headers headers,
                                int streamId,
                                Http2Flag.HeaderFlags flags,
                                Http2FrameData dataFrame,
                                FlowControl.Outbound flowControl) {
            headersWritten.add(headers);
            return 0;
        }
    }

    @Nested
    class BufferDataInputStreamTest {

        @Test
        void drainsFullBuffer() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var stream = stream(content)) {
                int count = stream.drainTo(out);
                assertThat(count, is(content.length));
                assertThat(out.toByteArray(), is(content));
            }
        }

        @Test
        void drainsRemainingBytesAfterPartialRead() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var stream = stream(content)) {
                stream.read(); // consume 'g'
                stream.read(); // consume 'r'
                int count = stream.drainTo(out);
                assertThat(count, is(content.length - 2));
                assertThat(out.toByteArray(), is(Arrays.copyOfRange(content, 2, content.length)));
            }
        }

        @Test
        void drainsNothingFromEmptyBuffer() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var stream = stream(new byte[0])) {
                int count = stream.drainTo(out);
                assertThat(count, is(0));
                assertThat(out.toByteArray().length, is(0));
            }
        }

        @Test
        void readAllBytesReturnsRemainingAfterPartialRead() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            try (var stream = stream(content)) {
                stream.read(); // consume 'g'
                stream.read(); // consume 'r'
                assertThat(stream.readAllBytes(), is(Arrays.copyOfRange(content, 2, content.length)));
            }
        }

        @Test
        void readAllBytesOnExhaustedBufferReturnsEmptyArray() throws IOException {
            try (var stream = stream(new byte[0])) {
                assertThat(stream.readAllBytes().length, is(0));
            }
        }

        @Test
        void skipAdvancesPosition() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            try (var stream = stream(content)) {
                long skipped = stream.skip(4);
                assertThat(skipped, is(4L));
                assertThat(stream.readAllBytes(), is(Arrays.copyOfRange(content, 4, content.length)));
            }
        }

        @Test
        void skipClampsToAvailable() throws IOException {
            byte[] content = "hi".getBytes(StandardCharsets.UTF_8);
            try (var stream = stream(content)) {
                long skipped = stream.skip(100);
                assertThat(skipped, is((long) content.length));
                assertThat(stream.available(), is(0));
            }
        }

        @Test
        void skipZeroDoesNothing() throws IOException {
            byte[] content = "grpc payload".getBytes(StandardCharsets.UTF_8);
            try (var stream = stream(content)) {
                long skipped = stream.skip(0);
                assertThat(skipped, is(0L));
                assertThat(stream.available(), is(content.length));
            }
        }

        private GrpcProtocolHandler.BufferDataInputStream stream(String content) {
            return stream(content.getBytes(StandardCharsets.UTF_8));
        }

        private GrpcProtocolHandler.BufferDataInputStream stream(byte[] content) {
            return new GrpcProtocolHandler.BufferDataInputStream(BufferData.create(content));
        }
    }

    private static class UnimplementedGrpcConnectionContext implements ConnectionContext {
        private final SniContext sniContext;

        private UnimplementedGrpcConnectionContext() {
            this(null);
        }

        private UnimplementedGrpcConnectionContext(SniContext sniContext) {
            this.sniContext = sniContext;
        }

        @Override
        public Optional<SniContext> sniContext() {
            return Optional.ofNullable(sniContext);
        }

        @Override
        public ListenerContext listenerContext() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public ExecutorService executor() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public DataWriter dataWriter() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public DataReader dataReader() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public Router router() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public PeerInfo remotePeer() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public PeerInfo localPeer() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public boolean isSecure() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public String socketId() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public String childSocketId() {
            throw new UnsupportedOperationException("Should not be called");
        }
    }
}
