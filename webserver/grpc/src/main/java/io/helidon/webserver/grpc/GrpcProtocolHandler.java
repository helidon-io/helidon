/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
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
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.webserver.http2.spi.Http2SubProtocolSelector;

import io.grpc.Codec;
import io.grpc.Compressor;
import io.grpc.CompressorRegistry;
import io.grpc.Decompressor;
import io.grpc.DecompressorRegistry;
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

    private static final Tag OK_TAG = Tag.create("grpc.status", "OK");
    private record MethodMetrics(Counter callStarted,
                                 Timer callDuration,
                                 DistributionSummary sentMessageSize,
                                 DistributionSummary recvMessageSize) { }
    private static final LazyValue<Map<String, MethodMetrics>> METHOD_METRICS = LazyValue.create(ConcurrentHashMap::new);

    private final Http2Headers headers;
    private final Http2StreamWriter streamWriter;
    private final int streamId;
    private final GrpcRouteHandler<REQ, RES> route;
    private final AtomicInteger numMessages = new AtomicInteger();
    private final LinkedBlockingQueue<REQ> listenerQueue = new LinkedBlockingQueue<>();
    private final StreamFlowControl flowControl;
    private final GrpcConfig grpcConfig;

    private Http2StreamState currentStreamState;
    private ServerCall.Listener<REQ> listener;
    private BufferData entityBytes;
    private Compressor compressor;
    private Decompressor decompressor;
    private boolean isIdentityCompressor;
    private long bytesReceived;
    private MethodMetrics methodMetrics;
    private long startMillis;

    GrpcProtocolHandler(HttpPrologue prologue,
                        Http2Headers headers,
                        Http2StreamWriter streamWriter,
                        int streamId,
                        Http2Settings serverSettings,
                        Http2Settings clientSettings,
                        StreamFlowControl flowControl,
                        Http2StreamState currentStreamState,
                        GrpcRouteHandler<REQ, RES> route,
                        GrpcConfig grpcConfig) {
        this.headers = headers;
        this.streamWriter = streamWriter;
        this.streamId = streamId;
        this.flowControl = flowControl;
        this.currentStreamState = currentStreamState;
        this.route = route;
        this.grpcConfig = grpcConfig;
    }

    @Override
    public void init() {
        try {
            ServerCall<REQ, RES> serverCall = createServerCall();
            Headers httpHeaders = headers.httpHeaders();

            // setup compression
            initCompression(serverCall, httpHeaders);

            // init metrics
            if (grpcConfig.enableMetrics()) {
                initMetrics();
                startMillis = System.currentTimeMillis();
                methodMetrics.callStarted.increment();
            }

            // initiate server call
            ServerCallHandler<REQ, RES> callHandler = route.callHandler();
            listener = callHandler.startCall(serverCall, GrpcHeadersUtil.toMetadata(headers));
            listener.onReady();
            bytesReceived = 0L;
        } catch (Throwable e) {
            LOGGER.log(ERROR, "Failed to initialize grpc protocol handler", e);
            throw e;
        }
    }

    @Override
    public Http2StreamState streamState() {
        return currentStreamState;
    }

    @Override
    public void rstStream(Http2RstStream rstStream) {
        listener.onCancel();
    }

    @Override
    public void windowUpdate(Http2WindowUpdate update) {
    }

    @Override
    public void data(Http2FrameHeader header, BufferData data) {
        try {
            boolean isCompressed = false;

            while (data.available() > 0) {
                // start of new chunk?
                if (entityBytes == null) {
                    isCompressed = (data.read() == 1);
                    long length = data.readUnsignedInt32();
                    entityBytes = BufferData.create((int) length);
                }

                // append data to current chunk
                entityBytes.write(data);

                // is chunk complete?
                if (entityBytes.capacity() == 0) {
                    // fail if compressed and no decompressor
                    if (isCompressed && decompressor == null) {
                        throw new IllegalStateException("Unable to codec for compressed data");
                    }

                    // read and possibly decompress data
                    int readLength = entityBytes.available();
                    bytesReceived += readLength;
                    byte[] bytes = new byte[readLength];
                    entityBytes.read(bytes);
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                    REQ request = route.method().parseRequest(isCompressed ? decompressor.decompress(bais) : bais);
                    listenerQueue.add(request);
                    flushQueue();
                    entityBytes = null;
                }
            }

            // if EOS then half close remote
            if (header.flags(Http2FrameTypes.DATA).endOfStream()) {
                listener.onHalfClose();
                currentStreamState = Http2StreamState.HALF_CLOSED_REMOTE;

                // update metrics
                if (grpcConfig.enableMetrics()) {
                    methodMetrics.recvMessageSize.record(bytesReceived);
                }
            }
        } catch (Exception e) {
            listener.onCancel();
            LOGGER.log(ERROR, "Failed to process grpc request: " + data.debugDataHex(true), e);
        }
    }

    void initCompression(ServerCall<REQ, RES> serverCall, Headers httpHeaders) {
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
                currentStreamState = Http2StreamState.CLOSED;       // stops processing
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

        // special handling for identity compressor
        isIdentityCompressor = (compressor instanceof Codec.Identity);
    }

    boolean isIdentityCompressor() {
        return isIdentityCompressor;
    }

    private void addNumMessages(int n) {
        numMessages.getAndAdd(n);
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

            private long bytesSent;
            private boolean headersSent;

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

                // set encoding header based on negotiation
                if (compressor == null) {
                    writable.set(GRPC_ENCODING_IDENTITY);
                } else {
                    writable.set(HeaderValues.createCached(GRPC_ENCODING, compressor.getMessageEncoding()));
                }

                // write headers frame
                Http2Headers http2Headers = Http2Headers.create(writable);
                http2Headers.status(io.helidon.http.Status.OK_200);
                streamWriter.writeHeaders(http2Headers,
                                          streamId,
                                          HeaderFlags.create(END_OF_HEADERS),
                                          flowControl.outbound());
                headersSent = true;
            }

            @Override
            public void sendMessage(RES message) {
                try (InputStream inputStream = route.method().streamResponse(message)) {
                    // prepare buffer for writing
                    BufferData bufferData;
                    if (compressor == null || isIdentityCompressor) {
                        byte[] bytes = inputStream.readAllBytes();
                        bufferData = BufferData.create(5 + bytes.length);
                        bufferData.write(0);        // off for identity compressor
                        bufferData.writeUnsignedInt32(bytes.length);
                        bufferData.write(bytes);
                    } else {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        try (OutputStream os = compressor.compress(baos)) {
                            inputStream.transferTo(os);
                        }
                        byte[] bytes = baos.toByteArray();
                        bufferData = BufferData.create(5 + bytes.length);
                        bufferData.write(1);
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
                    streamWriter.writeData(new Http2FrameData(header, bufferData), flowControl.outbound());
                    bytesSent += writeLength;
                } catch (Exception e) {
                    listener.onCancel();
                    LOGGER.log(ERROR, "Failed to respond to grpc request: " + route.method(), e);
                }
            }

            @Override
            public void close(Status status, Metadata trailers) {
                // headers are required even if call is cancelled
                if (!headersSent) {
                    sendHeaders(new Metadata());
                }

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

                // update metrics
                if (status.isOk() && grpcConfig.enableMetrics()) {
                    methodMetrics.sentMessageSize.record(bytesSent);
                    methodMetrics.callDuration.record(
                            Duration.ofMillis(System.currentTimeMillis() - startMillis));
                }
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

    /**
     * Initializes gRPC server metrics for the method being invoked. Note that
     * duration and size metrics are currently recorded only for successful calls,
     * using the {@link #OK_TAG}. If a call fails, it will only increment the number
     * of started calls, but not record any of the other metrics.
     */
    private void initMetrics() {
        String methodName = route.method().getFullMethodName();
        methodMetrics = METHOD_METRICS.get().computeIfAbsent(methodName, name -> {
            MeterRegistry meterRegistry = Metrics.globalRegistry();
            Tag grpcMethod = Tag.create("grpc.method", name);

            Counter.Builder callStartedBuilder = Counter.builder("grpc.server.call.started")
                    .scope(VENDOR)
                    .tags(List.of(grpcMethod));
            Counter callStarted = meterRegistry.getOrCreate(callStartedBuilder);

            Timer.Builder callDurationOkBuilder = Timer.builder("grpc.server.call.duration")
                    .scope(VENDOR)
                    .baseUnit(Timer.BaseUnits.MILLISECONDS)
                    .tags(List.of(grpcMethod, OK_TAG));
            Timer callDuration = meterRegistry.getOrCreate(callDurationOkBuilder);

            DistributionSummary.Builder sendMessageSizeBuilder = DistributionSummary.builder(
                            "grpc.server.call.sent_total_compressed_message_size")
                    .scope(VENDOR)
                    .tags(List.of(grpcMethod, OK_TAG));
            DistributionSummary sentMessageSize = meterRegistry.getOrCreate(sendMessageSizeBuilder);

            DistributionSummary.Builder recvMessageSizeBuilder = DistributionSummary.builder(
                            "grpc.server.call.rcvd_total_compressed_message_size")
                    .scope(VENDOR)
                    .tags(List.of(grpcMethod, OK_TAG));
            DistributionSummary recvMessageSize = meterRegistry.getOrCreate(recvMessageSizeBuilder);

            return new MethodMetrics(callStarted, callDuration, sentMessageSize, recvMessageSize);
        });
    }
}
