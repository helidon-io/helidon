/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;

import static io.helidon.http.http2.Http2Flag.DataFlags;
import static io.helidon.http.http2.Http2Flag.END_OF_HEADERS;
import static io.helidon.http.http2.Http2Flag.END_OF_STREAM;
import static io.helidon.http.http2.Http2Flag.HeaderFlags;
import static java.lang.System.Logger.Level.ERROR;

class GrpcProtocolHandler<REQ, RES> implements Http2SubProtocolSelector.SubProtocolHandler {
    private static final System.Logger LOGGER = System.getLogger(GrpcProtocolHandler.class.getName());
    private static final Header GRPC_CONTENT_TYPE = HeaderValues.createCached(HeaderNames.CONTENT_TYPE, "application/grpc");
    private static final Header GRPC_ENCODING_IDENTITY = HeaderValues.createCached("grpc-encoding", "identity");
    private static final DataFlags DATA_FLAGS_ZERO = DataFlags.create(0);

    private final HttpPrologue prologue;
    private final Http2Headers headers;
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private final Http2Settings serverSettings;
    private final Http2Settings clientSettings;
    private final Grpc<REQ, RES> route;

    private final StreamFlowControl flowControl;
    private Http2StreamState currentStreamState;
    private ServerCall.Listener<REQ> listener;

    private BufferData entityBytes;
    private final AtomicInteger numMessages = new AtomicInteger();
    private final LinkedBlockingQueue<REQ> listenerQueue = new LinkedBlockingQueue<>();

    GrpcProtocolHandler(HttpPrologue prologue,
                        Http2Headers headers,
                        Http2StreamWriter streamWriter,
                        int streamId,
                        Http2Settings serverSettings,
                        Http2Settings clientSettings,
                        StreamFlowControl flowControl,
                        Http2StreamState currentStreamState,
                        Grpc<REQ, RES> route) {
        this.prologue = prologue;
        this.headers = headers;
        this.streamWriter = streamWriter;
        this.streamId = streamId;
        this.serverSettings = serverSettings;
        this.clientSettings = clientSettings;
        this.flowControl = flowControl;
        this.currentStreamState = currentStreamState;
        this.route = route;
    }

    @Override
    public void init() {
        try {
            ServerCall<REQ, RES> serverCall = createServerCall();
            ServerCallHandler<REQ, RES> callHandler = route.callHandler();
            listener = callHandler.startCall(serverCall, GrpcHeadersUtil.toMetadata(headers));
            listener.onReady();
        } catch (Throwable e) {
            LOGGER.log(ERROR, "Failed to initialize grpc protocol handler", e);
            throw e;
        }
    }

    private void addNumMessages(int n) {
        numMessages.getAndAdd(n);
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
                // start of new chunk?
                if (entityBytes == null) {
                    // fixme compression support
                    boolean isCompressed = (data.read() == 1);
                    long length = data.readUnsignedInt32();
                    entityBytes = BufferData.create((int) length);
                }

                // append data to current chunk
                entityBytes.write(data);

                // is chunk complete?
                if (entityBytes.capacity() == 0) {
                    byte[] bytes = new byte[entityBytes.available()];
                    entityBytes.read(bytes);
                    REQ request = route.method().parseRequest(new ByteArrayInputStream(bytes));
                    listenerQueue.add(request);
                    flushQueue();
                    entityBytes = null;
                }
            }

            // if EOS then half close
            if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
                listener.onHalfClose();
                currentStreamState = Http2StreamState.HALF_CLOSED_LOCAL;
            }
        } catch (Exception e) {
            LOGGER.log(ERROR, "Failed to process grpc request: " + data.debugDataHex(true), e);
        }
    }

    private void flushQueue() {
        if (listener != null) {
            while (!listenerQueue.isEmpty() && numMessages.getAndDecrement() > 0) {
                listener.onMessage(listenerQueue.poll());
            }
        }
    }

    private ServerCall<REQ, RES> createServerCall() {
        return new ServerCall<>() {
            @Override
            public void request(int numMessages) {
                addNumMessages(numMessages);
                flushQueue();
            }

            @Override
            public void sendHeaders(Metadata headers) {
                // prepare response haaders
                WritableHeaders<?> writable = WritableHeaders.create();
                GrpcHeadersUtil.updateHeaders(writable, headers);
                writable.set(GRPC_CONTENT_TYPE);
                writable.set(GRPC_ENCODING_IDENTITY);

                // write headers frame
                Http2Headers http2Headers = Http2Headers.create(writable);
                http2Headers.status(io.helidon.http.Status.OK_200);
                streamWriter.writeHeaders(http2Headers,
                                          streamId,
                                          HeaderFlags.create(END_OF_HEADERS),
                                          flowControl.outbound());
            }

            @Override
            public void sendMessage(RES message) {
                try (InputStream inputStream = route.method().streamResponse(message)) {
                    // prepare buffer for writing
                    byte[] bytes = inputStream.readAllBytes();
                    BufferData bufferData = BufferData.create(5 + bytes.length);
                    bufferData.write(0);
                    bufferData.writeUnsignedInt32(bytes.length);
                    bufferData.write(bytes);

                    // create data frame, EOS sent in close with trailers
                    Http2FrameHeader header = Http2FrameHeader.create(bufferData.available(),
                            Http2FrameTypes.DATA,
                            DATA_FLAGS_ZERO,
                            streamId);

                    // write data frame
                    streamWriter.writeData(new Http2FrameData(header, bufferData), flowControl.outbound());
                } catch (Exception e) {
                    LOGGER.log(ERROR, "Failed to respond to grpc request: " + route.method(), e);
                }
            }

            @Override
            public void close(Status status, Metadata trailers) {
                // prepare trailers
                WritableHeaders<?> writable = WritableHeaders.create();
                GrpcHeadersUtil.updateHeaders(writable, trailers);
                writable.set(HeaderValues.create(GrpcStatus.STATUS_NAME, status.getCode().value()));
                String description = status.getDescription();
                if (description != null) {
                    writable.set(HeaderValues.create(GrpcStatus.MESSAGE_NAME, description));
                }

                // write headers frame with trailers and EOS
                Http2Headers http2Headers = Http2Headers.create(writable);
                streamWriter.writeHeaders(http2Headers,
                                          streamId,
                                          HeaderFlags.create(END_OF_HEADERS | END_OF_STREAM),
                                          flowControl.outbound());
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
}
