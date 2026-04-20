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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.PeerInfo;
import io.helidon.grpc.core.WeightedBag;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
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

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcProtocolHandlerTest {

    private static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.create("grpc-accept-encoding");

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
    void testCloseSuppressesTrailerWriteDisconnect() {
        ServerCall<String, String> serverCall = createServerCall(closeFailingWriter());
        serverCall.sendHeaders(new Metadata());

        assertDoesNotThrow(() -> serverCall.close(Status.OK, new Metadata()));
        assertThat(serverCall.isCancelled(), is(true));
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
        ServerMethodDefinition<String, String> definition =
                ServerMethodDefinition.create(stringMethodDescriptor(), new ServerCallHandler<>() {
                    @Override
                    public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
                        return listener;
                    }
                });
        return GrpcRouteHandler.methodDefinition(definition, null, WeightedBag.create());
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

    private static class UnimplementedGrpcConnectionContext implements ConnectionContext {
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
