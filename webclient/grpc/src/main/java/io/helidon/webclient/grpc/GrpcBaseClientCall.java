/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.LazyValue;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.CompositeBufferData;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.grpc.core.GrpcHeadersUtil;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DefaultDnsResolver;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2ClientConnection;
import io.helidon.webclient.http2.Http2ClientImpl;
import io.helidon.webclient.http2.Http2StreamConfig;
import io.helidon.webclient.http2.StreamTimeoutException;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import static io.helidon.metrics.api.Meter.Scope.VENDOR;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;

/**
 * Base class for gRPC client calls.
 */
abstract class GrpcBaseClientCall<ReqT, ResT> extends ClientCall<ReqT, ResT> {
    private static final System.Logger LOGGER = System.getLogger(GrpcBaseClientCall.class.getName());

    protected static final Metadata EMPTY_METADATA = new Metadata();
    protected static final Header GRPC_ACCEPT_ENCODING = HeaderValues.create(HeaderNames.ACCEPT_ENCODING, "gzip");
    protected static final Header GRPC_CONTENT_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE, "application/grpc");

    protected static final BufferData PING_FRAME = BufferData.create("PING");
    protected static final BufferData EMPTY_BUFFER_DATA = BufferData.empty();
    protected static final int DATA_PREFIX_LENGTH = 5;

    protected static final Tag OK_TAG = Tag.create("grpc.status", "OK");
    protected record MethodMetrics(Counter callStarted,
                                   Timer callDuration,
                                   DistributionSummary sentMessageSize,
                                   DistributionSummary recvMessageSize) { }
    private static final LazyValue<Map<String, MethodMetrics>> METHOD_METRICS = LazyValue.create(ConcurrentHashMap::new);

    private final GrpcClientImpl grpcClient;
    private final GrpcChannel grpcChannel;
    private final MethodDescriptor<ReqT, ResT> methodDescriptor;
    private final CallOptions callOptions;
    private final int initBufferSize;
    private final Duration pollWaitTime;
    private final boolean abortPollTimeExpired;
    private final Duration heartbeatPeriod;
    private final ClientUriSupplier clientUriSupplier;
    private final GrpcClientConfig grpcConfig;

    private final MethodDescriptor.Marshaller<ReqT> requestMarshaller;
    private final MethodDescriptor.Marshaller<ResT> responseMarshaller;

    private volatile Http2ClientConnection connection;
    private volatile GrpcClientStream clientStream;
    private volatile Listener<ResT> responseListener;
    private volatile HelidonSocket socket;
    private volatile MethodMetrics methodMetrics;
    private volatile long startMillis;

    private AtomicLong bytesSent;
    private AtomicLong bytesRcvd;

    GrpcBaseClientCall(GrpcChannel grpcChannel, MethodDescriptor<ReqT, ResT> methodDescriptor, CallOptions callOptions) {
        this.grpcClient = (GrpcClientImpl) grpcChannel.grpcClient();
        this.grpcConfig = grpcClient.clientConfig();
        this.grpcChannel = grpcChannel;
        this.methodDescriptor = methodDescriptor;
        this.callOptions = callOptions;
        this.requestMarshaller = methodDescriptor.getRequestMarshaller();
        this.responseMarshaller = methodDescriptor.getResponseMarshaller();
        this.initBufferSize = grpcClient.prototype().protocolConfig().initBufferSize();
        this.pollWaitTime = grpcClient.prototype().protocolConfig().pollWaitTime();
        this.abortPollTimeExpired = grpcClient.prototype().protocolConfig().abortPollTimeExpired();
        this.heartbeatPeriod = grpcClient.prototype().protocolConfig().heartbeatPeriod();
        this.clientUriSupplier = grpcClient.prototype().clientUriSupplier().orElse(null);
    }

    @Override
    public void start(Listener<ResT> responseListener, Metadata metadata) {
        LOGGER.log(DEBUG, "start called");

        this.responseListener = responseListener;

        // init metrics
        if (grpcConfig.enableMetrics()) {
            initMetrics();
            bytesSent = new AtomicLong(0L);
            bytesRcvd = new AtomicLong(0L);
            startMillis = System.currentTimeMillis();
            methodMetrics.callStarted.increment();
        }

        // obtain HTTP2 connection
        ClientUri clientUri = nextClientUri();
        ClientConnection clientConnection = clientConnection(clientUri);
        socket = clientConnection.helidonSocket();
        connection = Http2ClientConnection.create((Http2ClientImpl) grpcClient.http2Client(),
                                                  clientConnection, true);

        // create HTTP2 stream from connection
        clientStream = new GrpcClientStream(
                connection,
                Http2Settings.create(),                 // Http2Settings
                socket,                                 // SocketContext
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
                        return grpcClient.prototype().readTimeout().orElse(
                                grpcClient.prototype().protocolConfig().pollWaitTime());
                    }
                },
                null,       // Http2ClientConfig
                connection.streamIdSequence());

        // start streaming threads
        startStreamingThreads();

        // send HEADERS frame
        WritableHeaders<?> headers = setupHeaders(metadata, clientUri.authority(), methodDescriptor.getFullMethodName());
        clientStream.writeHeaders(Http2Headers.create(headers), false);
    }

    static WritableHeaders<?> setupHeaders(Metadata metadata, String authority, String methodName) {
        WritableHeaders<?> headers = WritableHeaders.create();
        GrpcHeadersUtil.updateHeaders(headers, metadata);
        headers.set(Http2Headers.AUTHORITY_NAME, authority);
        headers.set(Http2Headers.METHOD_NAME, "POST");
        headers.set(Http2Headers.PATH_NAME, "/" + methodName);
        headers.set(Http2Headers.SCHEME_NAME, "http");
        headers.set(GRPC_CONTENT_TYPE);
        headers.set(GRPC_ACCEPT_ENCODING);
        return headers;
    }

    abstract void startStreamingThreads();

    /**
     * Read a single gRPC frame, possibly assembled from multiple HTTP/2 frames.
     *
     * @return data for gRPC frame or {@code null}
     */
    protected BufferData readGrpcFrame() {
        // attempt to read HTTP/2 frame
        Http2FrameData frameData;
        try {
            frameData = clientStream.readOne(pollWaitTime());
        } catch (StreamTimeoutException e) {
            handleStreamTimeout(e);
            return null;
        }
        if (frameData == null) {
            return null;
        }

        // read more HTTP/2 frames if long gRPC frame
        BufferData bufferData = frameData.data();
        bufferData.read();                                      // skip compression
        long grpcLength = bufferData.readUnsignedInt32();       // length prefixed
        grpcLength -= bufferData.available();

        if (grpcLength > 0) {
            // collect frames in composite buffer
            CompositeBufferData compositeBuffer = BufferData.createComposite(bufferData);
            do {
                try {
                    frameData = clientStream.readOne(pollWaitTime());
                } catch (StreamTimeoutException e) {
                    handleStreamTimeout(e);
                    continue;
                }
                if (frameData == null) {
                    continue;
                }

                bufferData = frameData.data();
                compositeBuffer.add(bufferData);
                grpcLength -= bufferData.available();
            } while (grpcLength > 0);

            // switch to composite buffer
            bufferData = compositeBuffer;
        }

        // rewind and return
        bufferData.rewind();
        return bufferData;
    }

    /**
     * Unary blocking calls that use stubs provide their own executor which needs
     * to be used at least once to unblock the calling thread and complete the
     * gRPC invocation. This method submits an empty task for that purpose. There
     * may be a better way to achieve this.
     */
    protected void unblockUnaryExecutor() {
        Executor executor = callOptions.getExecutor();
        if (executor != null) {
            try {
                executor.execute(() -> {
                });
            } catch (Throwable t) {
                // ignored
            }
        }
    }

    protected GrpcClientImpl grpcClient() {
        return grpcClient;
    }

    protected ClientConnection clientConnection(ClientUri clientUri) {
        WebClient webClient = grpcClient.webClient();
        GrpcClientConfig clientConfig = grpcClient.prototype();
        ConnectionKey connectionKey = new ConnectionKey(
                clientUri.scheme(),
                clientUri.host(),
                clientUri.port(),
                clientConfig.readTimeout().orElse(Duration.ZERO),
                clientConfig.tls(),
                DefaultDnsResolver.create(),
                DnsAddressLookup.defaultLookup(),
                Proxy.noProxy());
        return TcpClientConnection.create(webClient,
                                          connectionKey,
                                          Collections.emptyList(),
                                          connection -> false,
                                          connection -> {
                                          }).connect();
    }

    protected boolean isRemoteOpen() {
        return clientStream.streamState() != Http2StreamState.HALF_CLOSED_REMOTE
                && clientStream.streamState() != Http2StreamState.CLOSED;
    }

    protected ResT toResponse(BufferData bufferData) {
        bufferData.read();                  // compression
        bufferData.readUnsignedInt32();     // length prefixed
        return responseMarshaller.parse(new InputStream() {
            @Override
            public int read() {
                return bufferData.available() > 0 ? bufferData.read() : -1;
            }
        });
    }

    protected byte[] serializeMessage(ReqT message) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(initBufferSize);
        try (InputStream is = requestMarshaller().stream(message)) {
            is.transferTo(baos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }

    protected Duration heartbeatPeriod() {
        return heartbeatPeriod;
    }

    protected boolean abortPollTimeExpired() {
        return abortPollTimeExpired;
    }

    protected Duration pollWaitTime() {
        return pollWaitTime;
    }

    protected Http2ClientConnection connection() {
        return connection;
    }

    protected MethodDescriptor.Marshaller<ReqT> requestMarshaller() {
        return requestMarshaller;
    }

    protected GrpcClientStream clientStream() {
        return clientStream;
    }

    protected Listener<ResT> responseListener() {
        return responseListener;
    }

    protected HelidonSocket socket() {
        return socket;
    }

    protected MethodMetrics methodMetrics() {
        return methodMetrics;
    }

    protected long startMillis() {
        return startMillis;
    }

    protected boolean enableMetrics() {
        return grpcConfig.enableMetrics();
    }

    protected AtomicLong bytesSent() {
        return bytesSent;
    }

    protected AtomicLong bytesRcvd() {
        return bytesRcvd;
    }

    /**
     * Retrieves the next URI either from the supplier or directly from config. If
     * a supplier is provided, it will take precedence.
     *
     * @return the next {@link ClientUri}
     * @throws java.util.NoSuchElementException if supplier has been exhausted
     */
    private ClientUri nextClientUri() {
        return clientUriSupplier == null ? grpcClient.prototype().baseUri().orElseThrow()
                : clientUriSupplier.next();
    }

    protected void handleStreamTimeout(StreamTimeoutException e) {
        if (abortPollTimeExpired()) {
            socket().log(LOGGER, ERROR, "[Reading thread] HTTP/2 stream timeout, aborting");
            throw e;
        }
        socket().log(LOGGER, ERROR, "[Reading thread] HTTP/2 stream timeout, retrying");
    }

    protected void initMetrics() {
        String baseUri = grpcChannel.baseUri().toString();
        String methodName = methodDescriptor.getFullMethodName();

        methodMetrics = METHOD_METRICS.get().computeIfAbsent(baseUri + methodName, uri -> {
            MeterRegistry meterRegistry = Metrics.globalRegistry();
            Tag grpcMethod = Tag.create("grpc.method", methodName);
            Tag grpcTarget = Tag.create("grpc.target", baseUri);

            Counter.Builder callStartedBuilder = Counter.builder("grpc.client.attempt.started")
                    .scope(VENDOR)
                    .tags(List.of(grpcMethod, grpcTarget));
            Counter callStarted = meterRegistry.getOrCreate(callStartedBuilder);

            Timer.Builder callDurationOkBuilder = Timer.builder("grpc.client.attempt.duration")
                    .scope(VENDOR)
                    .baseUnit(Timer.BaseUnits.MILLISECONDS)
                    .tags(List.of(grpcMethod, grpcTarget, OK_TAG));
            Timer callDuration = meterRegistry.getOrCreate(callDurationOkBuilder);

            DistributionSummary.Builder sendMessageSizeBuilder = DistributionSummary.builder(
                            "grpc.client.attempt.sent_total_compressed_message_size")
                    .scope(VENDOR)
                    .tags(List.of(grpcMethod, grpcTarget, OK_TAG));
            DistributionSummary sentMessageSize = meterRegistry.getOrCreate(sendMessageSizeBuilder);

            DistributionSummary.Builder recvMessageSizeBuilder = DistributionSummary.builder(
                            "grpc.client.attempt.rcvd_total_compressed_message_size")
                    .scope(VENDOR)
                    .tags(List.of(grpcMethod, grpcTarget, OK_TAG));
            DistributionSummary recvMessageSize = meterRegistry.getOrCreate(recvMessageSizeBuilder);

            return new MethodMetrics(callStarted, callDuration, sentMessageSize, recvMessageSize);
        });
    }
}
