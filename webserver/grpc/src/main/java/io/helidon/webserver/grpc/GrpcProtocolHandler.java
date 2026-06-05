/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.grpc.core.GrpcHeadersUtil;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.http.http2.Http2StreamWriter;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.http.http2.StreamFlowControl;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;
import io.helidon.webserver.CloseConnectionException;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;

import io.grpc.Attributes;
import io.grpc.Codec;
import io.grpc.Compressor;
import io.grpc.CompressorRegistry;
import io.grpc.Decompressor;
import io.grpc.DecompressorRegistry;
import io.grpc.Drainable;
import io.grpc.Grpc;
import io.grpc.KnownLength;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;

import static io.helidon.http.HeaderNames.CONTENT_TYPE;
import static io.helidon.http.http2.Http2Flag.DataFlags;
import static io.helidon.http.http2.Http2Flag.END_OF_HEADERS;
import static io.helidon.http.http2.Http2Flag.END_OF_STREAM;
import static io.helidon.http.http2.Http2Flag.HeaderFlags;
import static io.helidon.http.http2.Http2StreamState.CLOSED;
import static io.helidon.http.http2.Http2StreamState.HALF_CLOSED_LOCAL;
import static io.helidon.metrics.api.Meter.Scope.VENDOR;
import static java.lang.System.Logger.Level.ERROR;

class GrpcProtocolHandler<REQ, RES> implements Http2SubProtocolSelector.SubProtocolHandler {
    private static final System.Logger LOGGER = System.getLogger(GrpcProtocolHandler.class.getName());

    private static final HeaderName GRPC_ENCODING = HeaderNames.create("grpc-encoding");
    private static final HeaderName GRPC_ACCEPT_ENCODING = HeaderNames.create("grpc-accept-encoding");
    private static final Header GRPC_CONTENT_TYPE = HeaderValues.createCached(CONTENT_TYPE, "application/grpc");
    private static final Header GRPC_ENCODING_IDENTITY = HeaderValues.createCached(GRPC_ENCODING, "identity");

    private static final DataFlags DATA_FLAGS_ZERO = DataFlags.create(0);

    private static final DecompressorRegistry DECOMPRESSOR_REGISTRY = DecompressorRegistry.getDefaultInstance();
    private static final CompressorRegistry COMPRESSOR_REGISTRY = CompressorRegistry.getDefaultInstance();

    private record MethodMetrics(Counter callStarted,
                                 Timer callDuration,
                                 DistributionSummary sentMessageSize,
                                 DistributionSummary recvMessageSize) { }

    private enum ListenerTerminal {
        CANCEL,
        COMPLETE
    }

    private static final LazyValue<Map<String, MethodMetrics>> METHOD_METRICS = LazyValue.create(ConcurrentHashMap::new);

    private static final int GRPC_HEADER_SIZE = 5;
    private static final int INITIAL_BUFFER_SIZE = 16 * 1024;

    private final ConnectionContext connectionContext;
    private final Http2Headers headers;
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private final GrpcRouteHandler<REQ, RES> route;
    private final ReentrantLock inboundLock = new ReentrantLock();
    private final Condition inboundChanged = inboundLock.newCondition();
    private final StreamFlowControl flowControl;
    private final GrpcConfig grpcConfig;

    private volatile ServerCall<REQ, RES> serverCall;
    private volatile ServerCall.Listener<REQ> listener;
    private BufferData entityBytes;
    private BufferData readBufferData = BufferData.create(INITIAL_BUFFER_SIZE);
    private BufferData unreadBufferData;
    private long entityBytesLeft;
    private Compressor compressor;
    private Decompressor decompressor;
    private boolean identityCompressor;
    private long bytesReceived;
    private MethodMetrics methodMetrics;
    private long startMillis;
    private int numMessages;
    private boolean readyPending;
    private boolean halfClosePending;
    private boolean inboundDraining;
    private boolean listenerTerminated;
    private boolean streamCloseDelivered;
    private ListenerTerminal terminalPending;
    private Runnable streamCloseListener;
    private REQ pendingRequest;
    private long messagesQueued;
    private long messagesDelivered;

    private volatile boolean callCancelled;
    private final AtomicReference<Http2StreamState> currentStreamState = new AtomicReference<>();

    GrpcProtocolHandler(ConnectionContext connectionContext,
                        Http2Headers headers,
                        Http2StreamWriter streamWriter,
                        int streamId,
                        StreamFlowControl flowControl,
                        Http2StreamState currentStreamState,
                        GrpcRouteHandler<REQ, RES> route,
                        GrpcConfig grpcConfig) {
        this.connectionContext = connectionContext;
        this.headers = headers;
        this.streamWriter = streamWriter;
        this.streamId = streamId;
        this.flowControl = flowControl;
        this.currentStreamState.set(currentStreamState);
        this.route = route;
        this.grpcConfig = grpcConfig;
    }

    @Override
    public void init() {
        try {
            ServerCall<REQ, RES> serverCall = createServerCall();
            this.serverCall = serverCall;
            Headers httpHeaders = headers.httpHeaders();

            // setup compression
            initCompression(serverCall, httpHeaders);

            // init metrics
            if (grpcConfig.enableMetrics()) {
                initMetrics();
                startMillis = System.currentTimeMillis();
                methodMetrics.callStarted.increment();
            }

            // Include the GrpcConnectionContext in the gRPC Context so that the gRPC customer
            // handler can access the peer info and proxy protocol data.
            var grpcContextImpl = new GrpcConnectionContextImpl(connectionContext);
            io.grpc.Context.current()
                .withValue(ServerContextKeys.CONNECTION_CONTEXT, grpcContextImpl)
                .run(() -> {
                    // initiate server call
                    ServerCallHandler<REQ, RES> callHandler = route.callHandler();
                    listener = callHandler.startCall(serverCall, GrpcHeadersUtil.toMetadata(headers));
                    inboundLock.lock();
                    try {
                        if (!listenerTerminated && terminalPending == null) {
                            readyPending = true;
                        }
                    } finally {
                        inboundLock.unlock();
                    }
                    flushQueue();
                    bytesReceived = 0L;
                });
        } catch (CloseConnectionException e) {
            throw e;
        } catch (Throwable e) {
            if (isPeerCancellation(e)) {
                throw new ServerConnectionException("gRPC call cancelled by remote peer", e);
            }
            LOGGER.log(ERROR, "Failed to initialize grpc protocol handler", e);
            throw e;
        }
    }

    @Override
    public Http2StreamState streamState() {
        return currentStreamState.get();
    }

    @Override
    public void onStreamClosed(Runnable listener) {
        boolean deliver;
        inboundLock.lock();
        try {
            streamCloseListener = Objects.requireNonNull(listener);
            deliver = currentStreamState.get() == CLOSED && !streamCloseDelivered;
            if (deliver) {
                streamCloseDelivered = true;
            }
        } finally {
            inboundLock.unlock();
        }
        if (deliver) {
            listener.run();
        }
    }

    private void updateStreamState(Http2StreamState next) {
        Http2StreamState state = currentStreamState.updateAndGet(current -> nextStreamState(current, next));
        Runnable listener = null;
        if (state == CLOSED) {
            inboundLock.lock();
            try {
                if (!streamCloseDelivered && streamCloseListener != null) {
                    streamCloseDelivered = true;
                    listener = streamCloseListener;
                }
            } finally {
                inboundLock.unlock();
            }
        }
        if (listener != null) {
            listener.run();
        }
    }

    /**
     * An RST stream frame was received, or the HTTP/2 connection closed before initialization completed.
     * Proper synchronization is required because this may run on the connection or stream thread.
     *
     * @param rstStream RST stream frame
     */
    @Override
    public void rstStream(Http2RstStream rstStream) {
        callCancelled = (rstStream.errorCode() == Http2ErrorCode.CANCEL);
        updateStreamState(Http2StreamState.CLOSED);
        scheduleTerminal(ListenerTerminal.CANCEL);
    }

    @Override
    public void windowUpdate(Http2WindowUpdate update) {
    }

    /**
     * Data received from HTTP/2 layer. Data may contain a partial gRPC request
     * or more than one request, making logic a bit more difficult.
     *
     * @param header frame header
     * @param data   frame data
     */
    @Override
    public void data(Http2FrameHeader header, BufferData data) {
        try {
            Http2StreamState state = currentStreamState.get();
            if (state == Http2StreamState.CLOSED) {
                return;
            }
            if (state == Http2StreamState.HALF_CLOSED_LOCAL) {
                if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
                    updateStreamState(Http2StreamState.HALF_CLOSED_REMOTE);
                }
                return;
            }

            boolean isCompressed = false;

            // check for any unread data received before
            BufferData newData;
            if (unreadBufferData != null) {
                newData = BufferData.create(unreadBufferData, data);
                unreadBufferData = null;
            } else {
                newData = data;
            }

            // process 0 or more requests from data
            while (newData.available() > 0) {
                // start of new request?
                if (entityBytes == null) {
                    if (newData.available() >= GRPC_HEADER_SIZE) {
                        isCompressed = (newData.read() == 1);
                        entityBytesLeft = newData.readUnsignedInt32();
                        entityBytes = allocateReadBuffer((int) entityBytesLeft);
                    } else {
                        unreadBufferData = newData;
                        return;     // need more for gRPC header
                    }
                }

                // append data to current entity
                int writableNow = (int) Math.min(entityBytesLeft, newData.available());
                entityBytes.write(newData, writableNow);
                entityBytesLeft -= writableNow;

                // is the entity complete?
                if (entityBytesLeft == 0) {
                    // fail if compressed and no decompressor
                    if (isCompressed && decompressor == null) {
                        throw new IllegalStateException("Unable to codec for compressed data");
                    }

                    // read and possibly decompress data
                    bytesReceived += entityBytes.available();
                    InputStream is = new BufferDataInputStream(entityBytes);
                    REQ request = route.method().parseRequest(isCompressed ? decompressor.decompress(is) : is);
                    long messageSequence = 0;
                    inboundLock.lock();
                    try {
                        if (!listenerTerminated && terminalPending == null) {
                            if (pendingRequest != null) {
                                throw new IllegalStateException("Previous gRPC request is still pending");
                            }
                            pendingRequest = request;
                            messageSequence = ++messagesQueued;
                        }
                    } finally {
                        inboundLock.unlock();
                    }
                    flushQueue();

                    if (messageSequence == 0) {
                        entityBytes = null;
                        break;
                    }

                    inboundLock.lock();
                    try {
                        while (messagesDelivered < messageSequence
                                && !listenerTerminated
                                && terminalPending == null) {
                            inboundChanged.await();
                        }
                        if (messagesDelivered < messageSequence) {
                            entityBytes = null;
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ServerConnectionException("Interrupted while waiting for gRPC message demand", e);
                    } finally {
                        inboundLock.unlock();
                    }
                    entityBytes = null;
                }
            }

            // if EOS then half close remote
            if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
                inboundLock.lock();
                try {
                    if (!listenerTerminated && terminalPending == null) {
                        halfClosePending = true;
                    }
                } finally {
                    inboundLock.unlock();
                }
                flushQueue();
                updateStreamState(Http2StreamState.HALF_CLOSED_REMOTE);
                // update metrics
                if (grpcConfig.enableMetrics()) {
                    methodMetrics.recvMessageSize.record(bytesReceived);
                }
            }
        } catch (CloseConnectionException e) {
            throw e;
        } catch (Exception e) {
            if (isPeerCancellation(e)) {
                throw new ServerConnectionException("gRPC call cancelled by remote peer", e);
            }
            closeOnException(e, header);
            LOGGER.log(ERROR, "Failed to process grpc request, data bytes: " + data.available(), e);
        }
    }

    BufferData allocateReadBuffer(int length) {
        readBufferData.reset();
        int capacity = readBufferData.capacity();
        if (length > capacity) {
            if (length > grpcConfig.maxReadBufferSize()) {
                throw new IllegalStateException("gRPC message size exceeds max read buffer size");
            }
            readBufferData = BufferData.create(length);
        }
        return readBufferData;
    }

    void initCompression(ServerCall<REQ, RES> serverCall, Headers httpHeaders) {
        if (grpcConfig.enableCompression()) {
            // check for encoding and respond using same algorithm
            if (httpHeaders.contains(GRPC_ENCODING)) {
                Header grpcEncoding = httpHeaders.get(GRPC_ENCODING);
                String encoding = grpcEncoding.asString().get();
                decompressor = DECOMPRESSOR_REGISTRY.lookupDecompressor(encoding);
                compressor = COMPRESSOR_REGISTRY.lookupCompressor(encoding);

                // report encoding not supported
                if (decompressor == null || compressor == null) {
                    Metadata metadata = new Metadata();
                    Set<String> encodings = DECOMPRESSOR_REGISTRY.getAdvertisedMessageEncodings();
                    metadata.put(Metadata.Key.of(GRPC_ACCEPT_ENCODING.defaultCase(), Metadata.ASCII_STRING_MARSHALLER),
                                 String.join(",", encodings));
                    serverCall.close(Status.UNIMPLEMENTED, metadata);
                    updateStreamState(Http2StreamState.CLOSED);  // stops processing
                    return;
                }
            } else if (httpHeaders.contains(GRPC_ACCEPT_ENCODING)) {
                Header acceptEncoding = httpHeaders.get(GRPC_ACCEPT_ENCODING);

                // check for matching encoding
                for (String encoding : acceptEncoding.allValues()) {
                    compressor = COMPRESSOR_REGISTRY.lookupCompressor(encoding);
                    if (compressor != null) {
                        decompressor = DECOMPRESSOR_REGISTRY.lookupDecompressor(encoding);
                        if (decompressor != null) {
                            break;      // found match
                        }
                        compressor = null;
                    }
                }
            }
        }

        // special handling for identity compressor
        identityCompressor = (compressor == null || compressor instanceof Codec.Identity);
    }

    boolean identityCompressor() {
        return identityCompressor;
    }

    private boolean isPeerCancellation(Throwable throwable) {
        return callCancelled && Status.fromThrowable(throwable).getCode() == Status.Code.CANCELLED;
    }

    private void closeOnException(Throwable throwable, Http2FrameHeader header) {
        if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
            updateStreamState(Http2StreamState.HALF_CLOSED_REMOTE);
        }

        ServerCall<REQ, RES> call = serverCall;
        if (call != null && currentStreamState.get() != CLOSED) {
            Metadata trailers = Status.trailersFromThrowable(throwable);
            call.close(Status.fromThrowable(throwable), trailers == null ? new Metadata() : trailers);
        }
    }

    private void writeHeaders(Http2Headers http2Headers, HeaderFlags flags) {
        try {
            streamWriter.writeHeaders(http2Headers, streamId, flags, outboundFlowControl());
        } catch (UncheckedIOException e) {
            throw new ServerConnectionException("Failed to write grpc response headers", e);
        }
    }

    private void writeData(Http2FrameData frameData) {
        streamWriter.writeData(frameData, outboundFlowControl());
    }

    private FlowControl.Outbound outboundFlowControl() {
        return flowControl == null ? FlowControl.Outbound.NOOP : flowControl.outbound();
    }

    private void addNumMessages(int n) {
        inboundLock.lock();
        try {
            if (!listenerTerminated && terminalPending == null) {
                numMessages += n;
            }
        } finally {
            inboundLock.unlock();
        }
    }

    private void flushQueue() {
        inboundLock.lock();
        try {
            if (inboundDraining || listener == null || listenerTerminated) {
                return;
            }
            inboundDraining = true;
        } finally {
            inboundLock.unlock();
        }

        boolean finished = false;
        try {
            while (true) {
                REQ request = null;
                boolean ready = false;
                boolean halfClose = false;
                ListenerTerminal terminal = null;
                inboundLock.lock();
                try {
                    if (terminalPending != null) {
                        terminal = terminalPending;
                        terminalPending = null;
                        listenerTerminated = true;
                        readyPending = false;
                        pendingRequest = null;
                        halfClosePending = false;
                        numMessages = 0;
                        inboundChanged.signalAll();
                    } else if (readyPending) {
                        readyPending = false;
                        ready = true;
                    } else if (pendingRequest != null && numMessages > 0) {
                        numMessages--;
                        request = pendingRequest;
                        pendingRequest = null;
                    } else if (pendingRequest == null && halfClosePending) {
                        halfClosePending = false;
                        halfClose = true;
                    } else {
                        inboundDraining = false;
                        finished = true;
                        return;
                    }
                } finally {
                    inboundLock.unlock();
                }
                if (terminal == ListenerTerminal.CANCEL) {
                    listener.onCancel();
                    return;
                } else if (terminal == ListenerTerminal.COMPLETE) {
                    listener.onComplete();
                    return;
                } else if (ready) {
                    listener.onReady();
                } else if (request != null) {
                    try {
                        listener.onMessage(request);
                    } finally {
                        inboundLock.lock();
                        try {
                            messagesDelivered++;
                            inboundChanged.signalAll();
                        } finally {
                            inboundLock.unlock();
                        }
                    }
                } else if (halfClose) {
                    listener.onHalfClose();
                }
            }
        } finally {
            if (!finished) {
                inboundLock.lock();
                try {
                    inboundDraining = false;
                } finally {
                    inboundLock.unlock();
                }
            }
        }
    }

    private void scheduleTerminal(ListenerTerminal terminal) {
        inboundLock.lock();
        try {
            if (!listenerTerminated
                    && (terminalPending == null || terminal == ListenerTerminal.CANCEL)) {
                terminalPending = terminal;
                readyPending = false;
                pendingRequest = null;
                halfClosePending = false;
                numMessages = 0;
                inboundChanged.signalAll();
            }
        } finally {
            inboundLock.unlock();
        }
        flushQueue();
    }

    /**
     * Ensures that if moving to a HALF_CLOSE state we can reach the CLOSED state
     * if already on the other HALF_CLOSE state. Reaching CLOSED state is necessary
     * for {@code io.helidon.webserver.http2.Http2ConnectionStreams} to remove
     * streams from its map.
     *
     * @param desiredStreamState desired new state
     * @return actual next state
     */
    static Http2StreamState nextStreamState(Http2StreamState currentStreamState,
                                            Http2StreamState desiredStreamState) {
        if (currentStreamState == Http2StreamState.CLOSED) {
            return Http2StreamState.CLOSED;
        }
        return switch (desiredStreamState) {
            case HALF_CLOSED_LOCAL -> currentStreamState == Http2StreamState.HALF_CLOSED_REMOTE
                    ? Http2StreamState.CLOSED
                    : Http2StreamState.HALF_CLOSED_LOCAL;
            case HALF_CLOSED_REMOTE -> currentStreamState == Http2StreamState.HALF_CLOSED_LOCAL
                    ? Http2StreamState.CLOSED
                    : Http2StreamState.HALF_CLOSED_REMOTE;
            default -> desiredStreamState;
        };
    }

    ServerCall<REQ, RES> createServerCall() {
        return new ServerCall<REQ, RES>() {
            private long bytesSent;
            private boolean headersSent;
            private BufferData writeBufferData = BufferData.growing(INITIAL_BUFFER_SIZE);
            @Override
            public void request(int numMessages) {
                addNumMessages(numMessages);
                flushQueue();
            }

            @Override
            public void sendHeaders(Metadata headers) {
                // prepare response headers
                WritableHeaders<?> writable = WritableHeaders.create();
                GrpcHeadersUtil.updateHeaders(writable, headers);
                writable.set(GRPC_CONTENT_TYPE);

                // set encoding header based on negotiation
                if (compressor == null) {
                    writable.set(GRPC_ENCODING_IDENTITY);
                } else {
                    writable.set(HeaderValues.createCached(GRPC_ENCODING, compressor.getMessageEncoding()));
                }

                // write headers frame
                Http2Headers http2Headers = Http2Headers.create(writable);
                http2Headers.status(io.helidon.http.Status.OK_200);
                writeHeaders(http2Headers, HeaderFlags.create(END_OF_HEADERS));
                headersSent = true;
            }

            @Override
            public void sendMessage(RES message) {
                try (InputStream inputStream = route.method().streamResponse(message)) {
                    // prepare buffer for writing
                    BufferData bufferData;
                    if (identityCompressor && inputStream instanceof KnownLength knownLength) {
                        int bytesLength = knownLength.available();
                        bufferData = allocateWriteBuffer(GRPC_HEADER_SIZE + bytesLength);
                        bufferData.write(0);        // 0 for identity compressor
                        bufferData.writeUnsignedInt32(bytesLength);
                        bufferData.readFrom(inputStream);
                    } else {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        if (identityCompressor) {
                            inputStream.transferTo(baos);
                        } else {
                            try (OutputStream os = compressor.compress(baos)) {
                                inputStream.transferTo(os);
                            }
                        }
                        byte[] bytes = baos.toByteArray();
                        bufferData = allocateWriteBuffer(GRPC_HEADER_SIZE + bytes.length);
                        bufferData.write(identityCompressor ? 0 : 1);
                        bufferData.writeUnsignedInt32(bytes.length);
                        bufferData.write(bytes);
                    }

                    // create data frame, EOS sent in close with trailers
                    int writeLength = bufferData.available();
                    Http2FrameHeader header = Http2FrameHeader.create(writeLength,
                                                                      Http2FrameTypes.DATA,
                                                                      DATA_FLAGS_ZERO,
                                                                      streamId);

                    // write data frame
                    writeData(new Http2FrameData(header, bufferData));
                    bytesSent += writeLength;
                } catch (UncheckedIOException e) {
                    throw new ServerConnectionException("Failed to write grpc response data", e);
                } catch (IOException e) {
                    callCancelled = true;
                    scheduleTerminal(ListenerTerminal.CANCEL);
                    LOGGER.log(ERROR, "Failed to respond to grpc request: " + route.method(), e);
                }
            }

            @Override
            public void close(Status status, Metadata trailers) {
                // Security interceptors can reject the call before startCall returns a listener.
                Http2StreamState closeState = listener == null ? CLOSED : HALF_CLOSED_LOCAL;
                WritableHeaders<?> writable = WritableHeaders.create();
                if (!headersSent) {
                    writable.set(GRPC_CONTENT_TYPE);
                }
                GrpcHeadersUtil.updateHeaders(writable, trailers);
                int statusValue = callCancelled ? Status.CANCELLED.getCode().value() : status.getCode().value();
                writable.set(HeaderValues.create(GrpcStatus.STATUS_NAME, statusValue));
                String description = status.getDescription();
                if (description != null) {
                    writable.set(HeaderValues.create(GrpcStatus.MESSAGE_NAME, description));
                }

                // write headers frame with trailers and EOS
                Http2Headers http2Headers = Http2Headers.create(writable);
                if (!headersSent) {
                    http2Headers.status(io.helidon.http.Status.OK_200);
                }
                try {
                    writeHeaders(http2Headers, HeaderFlags.create(END_OF_HEADERS | END_OF_STREAM));
                } catch (CloseConnectionException e) {
                    callCancelled = true;
                    updateStreamState(closeState);
                    scheduleTerminal(ListenerTerminal.CANCEL);
                    return;
                }
                updateStreamState(closeState);

                // inform listener of completion
                if (!callCancelled) {
                    scheduleTerminal(ListenerTerminal.COMPLETE);
                }

                // update metrics
                if (status.isOk() && grpcConfig.enableMetrics()) {
                    methodMetrics.sentMessageSize.record(bytesSent);
                    methodMetrics.callDuration.record(
                            Duration.ofMillis(System.currentTimeMillis() - startMillis));
                }
            }

            @Override
            public boolean isCancelled() {
                return callCancelled || currentStreamState.get() == Http2StreamState.CLOSED;
            }

            @Override
            public Attributes getAttributes() {
                // gRPC security reads peer addresses from the standard gRPC transport attributes.
                return Attributes.newBuilder()
                        .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, connectionContext.remotePeer().address())
                        .set(Grpc.TRANSPORT_ATTR_LOCAL_ADDR, connectionContext.localPeer().address()).build();
            }

            @Override
            public MethodDescriptor<REQ, RES> getMethodDescriptor() {
                return route.method();
            }

            private BufferData allocateWriteBuffer(int length) {
                writeBufferData.reset();
                int capacity = writeBufferData.capacity();
                if (length > capacity) {
                    writeBufferData = BufferData.create(length);
                }
                return writeBufferData;
            }
        };
    }

    /**
     * Initializes gRPC server metrics for the method being invoked. Note that
     * duration and size metrics are currently recorded only for successful calls,
     * using the ok tag. If a call fails, it will only increment the number
     * of started calls, but not record any of the other metrics.
     */
    private void initMetrics() {
        String methodName = route.method().getFullMethodName();
        methodMetrics = METHOD_METRICS.get().computeIfAbsent(methodName, name -> {
            MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
            MeterRegistry meterRegistry = metricsFactory.globalRegistry();

            Tag okTag = metricsFactory.tagCreate("grpc.status", "OK");
            Tag grpcMethod = metricsFactory.tagCreate("grpc.method", name);

            Counter.Builder callStartedBuilder = metricsFactory.counterBuilder("grpc.server.call.started")
                    .scope(VENDOR)
                    .tags(List.of(grpcMethod));
            Counter callStarted = meterRegistry.getOrCreate(callStartedBuilder);

            Timer.Builder callDurationOkBuilder = metricsFactory.timerBuilder("grpc.server.call.duration")
                    .scope(VENDOR)
                    .baseUnit(Timer.BaseUnits.MILLISECONDS)
                    .tags(List.of(grpcMethod, okTag));
            Timer callDuration = meterRegistry.getOrCreate(callDurationOkBuilder);

            DistributionSummary.Builder sendMessageSizeBuilder = metricsFactory.distributionSummaryBuilder(
                            "grpc.server.call.sent_total_compressed_message_size",
                            metricsFactory.distributionStatisticsConfigBuilder())
                    .scope(VENDOR)
                    .tags(List.of(grpcMethod, okTag));
            DistributionSummary sentMessageSize = meterRegistry.getOrCreate(sendMessageSizeBuilder);

            DistributionSummary.Builder recvMessageSizeBuilder = metricsFactory.distributionSummaryBuilder(
                            "grpc.server.call.rcvd_total_compressed_message_size",
                            metricsFactory.distributionStatisticsConfigBuilder())
                    .scope(VENDOR)
                    .tags(List.of(grpcMethod, okTag));
            DistributionSummary recvMessageSize = meterRegistry.getOrCreate(recvMessageSizeBuilder);

            return new MethodMetrics(callStarted, callDuration, sentMessageSize, recvMessageSize);
        });
    }

    /**
     * An input stream that can return its length. gRPC parsers can use this extra
     * knowledge for optimizations. It can also copy a byte array directly on a
     * single read.
     */
    static class BufferDataInputStream extends InputStream implements KnownLength, Drainable {
        private final BufferData bufferData;

        BufferDataInputStream(BufferData bufferData) {
            this.bufferData = bufferData;
        }

        @Override
        public int read() {
            // BufferData.read() throws when exhausted; InputStream requires -1 at EOF
            return bufferData.available() > 0 ? bufferData.read() : -1;
        }

        @Override
        public int read(byte[] b) {
            Objects.requireNonNull(b);
            if (b.length == 0) {
                return 0;
            }
            // BufferData.read(byte[]) returns 0 when exhausted; InputStream requires -1 at EOF
            return bufferData.available() == 0 ? -1 : bufferData.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, Objects.requireNonNull(b).length);
            if (len == 0) {
                return 0;
            }
            // BufferData.read(byte[],int,int) returns 0 when exhausted; InputStream requires -1 at EOF
            return bufferData.available() == 0 ? -1 : bufferData.read(b, off, len);
        }

        @Override
        public int available() {
            return bufferData.available();
        }

        @Override
        public int drainTo(OutputStream target) {
            int count = bufferData.available();
            bufferData.writeTo(target);
            return count;
        }

        @Override
        public long skip(long n) {
            // advance readPosition directly; avoids the default read()-in-a-loop
            if (n <= 0) {
                return 0;
            }
            int toSkip = (int) Math.min(bufferData.available(), n);
            bufferData.skip(toSkip);
            return toSkip;
        }

        @Override
        public byte[] readAllBytes() {
            // single exact-size allocation; avoids the default growing-array loop
            return bufferData.readBytes();
        }
    }
}
