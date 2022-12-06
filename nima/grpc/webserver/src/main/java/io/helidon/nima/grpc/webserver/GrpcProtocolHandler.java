/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.grpc.webserver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.http2.FlowControl;
import io.helidon.nima.http2.Http2Flag;
import io.helidon.nima.http2.Http2FrameData;
import io.helidon.nima.http2.Http2FrameHeader;
import io.helidon.nima.http2.Http2FrameTypes;
import io.helidon.nima.http2.Http2Headers;
import io.helidon.nima.http2.Http2RstStream;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.Http2StreamState;
import io.helidon.nima.http2.Http2StreamWriter;
import io.helidon.nima.http2.Http2WindowUpdate;
import io.helidon.nima.http2.webserver.spi.Http2SubProtocolProvider;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;

class GrpcProtocolHandler<REQ, RES> implements Http2SubProtocolProvider.SubProtocolHandler {
    private static final System.Logger LOGGER = System.getLogger(GrpcProtocolHandler.class.getName());
    private static final HeaderValue GRPC_CONTENT_TYPE = Header.createCached(Header.CONTENT_TYPE, "application/grpc");
    private static final HeaderValue GRPC_ENCODING_IDENTITY = Header.createCached("grpc-encoding", "identity");

    private final HttpPrologue prologue;
    private final Http2Headers headers;
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private final Http2Settings serverSettings;
    private final Http2Settings clientSettings;
    private final Grpc<REQ, RES> route;

    private Http2StreamState currentStreamState;
    private ServerCall.Listener<REQ> listener;
    private ServerCall<REQ, RES> serverCall;

    GrpcProtocolHandler(HttpPrologue prologue,
                        Http2Headers headers,
                        Http2StreamWriter streamWriter,
                        int streamId,
                        Http2Settings serverSettings,
                        Http2Settings clientSettings,
                        Http2StreamState currentStreamState,
                        Grpc<REQ, RES> route) {

        this.prologue = prologue;
        this.headers = headers;
        this.streamWriter = streamWriter;
        this.streamId = streamId;
        this.serverSettings = serverSettings;
        this.clientSettings = clientSettings;
        this.currentStreamState = currentStreamState;
        this.route = route;
    }

    @Override
    public void init() {

        try {
            serverCall = createServerCall();
            ServerCallHandler<REQ, RES> callHandler = route.callHandler();
            listener = callHandler.startCall(serverCall, toMetadata(headers));
            listener.onReady();
        } catch (Throwable e) {
            LOGGER.log(System.Logger.Level.ERROR, "Failed to initialize grpc protocol handler", e);
            throw e;
        }

    }

    @Override
    public Http2StreamState streamState() {
        return currentStreamState;
    }

    @Override
    public void rstStream(Http2RstStream rstStream) {
        listener.onComplete();
    }

    @Override
    public void windowUpdate(Http2WindowUpdate update) {

    }

    @Override
    public void data(Http2FrameHeader header, BufferData data) {
        try {
            while (data.available() > 0) {
                // todo compression support
                boolean isCompressed = (data.read() == 1);
                long length = data.readUnsignedInt32(); // if the result is > than data length, wait for next data?
                byte[] bytes = new byte[(int) length];
                data.read(bytes);
                listener.onMessage(route.method().parseRequest(new ByteArrayInputStream(bytes)));
            }
            if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
                listener.onHalfClose();
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "Failed to process grpc request: " + data.debugDataHex(true),
                       e);
        }
    }

    private ServerCall<REQ, RES> createServerCall() {
        return new ServerCall<>() {
            @Override
            public void request(int numMessages) {
                //System.out.println("request: " + numMessages);
            }

            @Override
            public void sendHeaders(Metadata headers) {
                // todo ignoring headers, just sending required response headers
                WritableHeaders<?> writable = WritableHeaders.create();
                writable.set(GRPC_CONTENT_TYPE);
                writable.set(GRPC_ENCODING_IDENTITY);

                Http2Headers http2Headers = Http2Headers.create(writable);
                http2Headers.status(Http.Status.OK_200);
                streamWriter.writeHeaders(http2Headers,
                                          streamId,
                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                                          FlowControl.NOOP);
            }

            @Override
            public void sendMessage(RES message) {
                BufferData bufferData = BufferData.growing(1024);

                try (InputStream inputStream = route.method().streamResponse(message)) {
                    byte[] bytes = inputStream.readAllBytes();
                    bufferData.write(0);
                    bufferData.writeUnsignedInt32(bytes.length);
                    bufferData.write(bytes);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // todo flags based on method type
                // end flag should be sent when last message is sent (or just rst stream if we cannot determine this)

                Http2FrameHeader header = Http2FrameHeader.create(bufferData.available(),
                                                                  Http2FrameTypes.DATA,
                                                                  Http2Flag.DataFlags.create(0),
                                                                  streamId);

                streamWriter.write(new Http2FrameData(header, bufferData), FlowControl.NOOP);
            }

            @Override
            public void close(Status status, Metadata trailers) {
                // todo ignoring trailers
                WritableHeaders<?> writable = WritableHeaders.create();
                writable.set(GrpcStatus.OK);
                Http2Headers http2Headers = Http2Headers.create(writable);
                streamWriter.writeHeaders(http2Headers,
                                          streamId,
                                          Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS | Http2Flag.END_OF_STREAM),
                                          FlowControl.NOOP);
                currentStreamState = Http2StreamState.HALF_CLOSED_LOCAL;
            }

            @Override
            public boolean isCancelled() {
                return currentStreamState == Http2StreamState.CLOSED;
            }

            @Override
            public MethodDescriptor<REQ, RES> getMethodDescriptor() {
                return route.method();
            }
        };
    }

    private Metadata toMetadata(Http2Headers headers) {
        return null;
    }

    private static final class BufferDataInputStream extends InputStream {
        private final BufferData data;

        private BufferDataInputStream(BufferData data) {
            this.data = data;
        }

        @Override
        public int read() throws IOException {
            if (data.available() > 0) {
                return data.read();
            } else {
                return -1;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (data.available() > 0) {
                return data.read(b, off, len);
            } else {
                return -1;
            }
        }

        @Override
        public void close() throws IOException {
            data.skip(data.available());
        }
    }
}
