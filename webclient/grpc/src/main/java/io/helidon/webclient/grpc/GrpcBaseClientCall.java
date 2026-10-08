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

package io.helidon.webclient.grpc;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.UnixDomainSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.common.tls.Tls;
import io.helidon.grpc.core.GrpcHeadersUtil;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2StreamState;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectedSocketChannelInfo;
import io.helidon.webclient.api.ConnectedSocketInfo;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.ConnectionListener;
import io.helidon.webclient.api.DefaultDnsResolver;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.SniConfig;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.UnixDomainSocketClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.api.WebClientCookieManager;
import io.helidon.webclient.api.WebClientProtocolResponse;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientConnection;
import io.helidon.webclient.http2.Http2ClientImpl;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.http2.Http2StreamConfig;
import io.helidon.webclient.http2.StreamTimeoutException;
import io.helidon.webclient.spi.Protocol;
import io.helidon.webclient.spi.ProtocolConfig;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.InternalStatus;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import static io.grpc.Contexts.statusFromCancelled;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Base class for gRPC client calls.
 */
abstract class GrpcBaseClientCall<ReqT, ResT> extends ClientCall<ReqT, ResT> {
    static final Metadata EMPTY_METADATA = new Metadata();
    static final Header GRPC_ACCEPT_ENCODING = HeaderValues.createCached(HeaderNames.ACCEPT_ENCODING, "gzip");
    static final Header GRPC_CONTENT_TYPE = HeaderValues.createCached(HeaderNames.CONTENT_TYPE, "application/grpc");
    static final HeaderName STATUS_NAME = HeaderNames.createFromLowercase("grpc-status");

    static final BufferData PING_FRAME = BufferData.create("PING");
    static final BufferData EMPTY_BUFFER_DATA = BufferData.empty();
    static final int DATA_PREFIX_LENGTH = 5;

    private static final System.Logger LOGGER = System.getLogger(GrpcBaseClientCall.class.getName());
    private static final Tls CLEARTEXT_TLS = Tls.builder()
            .enabled(false)
            .build();

    private static final HeaderName TIMEOUT_NAME = HeaderNames.create("grpc-timeout");
    private static final ScheduledThreadPoolExecutor DEADLINE_SCHEDULER = deadlineScheduler();
    private static final ScopedValue<GrpcBaseClientCall<?, ?>> CONNECTING_CALL = ScopedValue.newInstance();

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
    private final GrpcDeframer deframer;
    private final Context context;
    private final Deadline deadline;
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final ReentrantLock listenerLock = new ReentrantLock();
    private final Context.CancellationListener cancellationListener =
            cancelled -> close(statusFromCancelled(cancelled));

    private final MethodDescriptor.Marshaller<ReqT> requestMarshaller;
    private final MethodDescriptor.Marshaller<ResT> responseMarshaller;

    private volatile ClientConnection transportConnection;
    private volatile Http2ClientConnection connection;
    private volatile GrpcClientStream clientStream;
    private volatile Listener<ResT> responseListener;
    private volatile HelidonSocket socket;
    private volatile MethodMetrics methodMetrics;
    private volatile long startMillis;
    private volatile Status closeStatus;
    private ScheduledFuture<?> deadlineTask;
    private volatile Closeable rawTransport;
    private volatile boolean initialHeadersWritten;

    private Metadata closeMetadata;
    private boolean closeNotified;
    private volatile boolean closeComplete;

    private AtomicLong bytesSent;
    private AtomicLong bytesRcvd;
    private BufferData unreadData;

    GrpcBaseClientCall(GrpcChannel grpcChannel, MethodDescriptor<ReqT, ResT> methodDescriptor, CallOptions callOptions) {
        this.grpcClient = (GrpcClientImpl) grpcChannel.grpcClient();
        this.grpcConfig = grpcClient.clientConfig();
        this.grpcChannel = grpcChannel;
        this.methodDescriptor = methodDescriptor;
        this.callOptions = callOptions;
        this.context = Context.current();
        Deadline contextDeadline = context.getDeadline();
        Deadline callDeadline = callOptions.getDeadline();
        this.deadline = contextDeadline == null ? callDeadline
                : callDeadline == null ? contextDeadline : contextDeadline.minimum(callDeadline);
        this.requestMarshaller = methodDescriptor.getRequestMarshaller();
        this.responseMarshaller = methodDescriptor.getResponseMarshaller();
        this.initBufferSize = grpcClient.prototype().protocolConfig().initBufferSize();
        this.pollWaitTime = grpcClient.prototype().protocolConfig().pollWaitTime();
        this.abortPollTimeExpired = grpcClient.prototype().protocolConfig().abortPollTimeExpired();
        this.heartbeatPeriod = grpcClient.prototype().protocolConfig().heartbeatPeriod();
        this.clientUriSupplier = grpcClient.prototype().clientUriSupplier().orElse(null);
        Integer maxInboundMessageSize = callOptions.getMaxInboundMessageSize();
        this.deframer = new GrpcDeframer(initBufferSize,
                                         maxInboundMessageSize == null ? Integer.MAX_VALUE : maxInboundMessageSize);
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
        // Listed in the gRPC-over-HTTP/2 Call-Definition as a non-optional field.
        // Used to detect incompatible proxies: intermediaries that do not support HTTP/2 trailers
        // may silently strip grpc-status and trailing metadata. RFC 7540 §8.1.2.2 permits TE in
        // HTTP/2 headers only with the value "trailers".
        // See: https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md
        headers.set(HeaderValues.TE_TRAILERS);
        return headers;
    }

    static WebClient connectionWebClient(WebClient webClient) {
        ConnectionListener original = webClient.prototype().connectionListener();
        return new CallWebClient(webClient, WebClient.builder()
                .from(webClient.prototype())
                .executor(webClient.executor())
                .connectionListener(new ConnectionListener() {
                    @Override
                    public void socketConnected(ConnectedSocketInfo socketInfo) throws IOException {
                        GrpcBaseClientCall<?, ?> call = CONNECTING_CALL.get();
                        call.registerRawTransport(socketInfo.socket());
                        if (!call.isClosed()) {
                            original.socketConnected(socketInfo);
                        }
                        call.ensureTransportActive();
                    }

                    @Override
                    public void socketChannelConnected(ConnectedSocketChannelInfo socketInfo) throws IOException {
                        GrpcBaseClientCall<?, ?> call = CONNECTING_CALL.get();
                        call.registerRawTransport(socketInfo.socketChannel());
                        if (!call.isClosed()) {
                            original.socketChannelConnected(socketInfo);
                        }
                        call.ensureTransportActive();
                    }
                })
                .buildPrototype());
    }

    @Override
    public void start(Listener<ResT> responseListener, Metadata metadata) {
        LOGGER.log(DEBUG, "start called");

        // init metrics
        if (grpcConfig.enableMetrics()) {
            initMetrics();
            bytesSent = new AtomicLong(0L);
            bytesRcvd = new AtomicLong(0L);
            startMillis = System.currentTimeMillis();
            methodMetrics.callStarted.increment();
        }

        lifecycleLock.lock();
        try {
            if (this.responseListener != null) {
                throw new IllegalStateException("Call already started");
            }
            this.responseListener = Objects.requireNonNull(responseListener);
        } finally {
            lifecycleLock.unlock();
        }
        if (isClosed()) {
            notifyClose();
            return;
        }

        context.addListener(cancellationListener, Runnable::run);
        if (isClosed()) {
            // Cancellation can race with listener registration.
            context.removeListener(cancellationListener);
            return;
        }
        if (deadline != null && deadline.isExpired()) {
            close(Status.DEADLINE_EXCEEDED.withDescription("Call deadline exceeded"));
            return;
        }

        lifecycleLock.lock();
        try {
            if (isClosed()) {
                return;
            }
            if (deadline != null) {
                scheduleDeadline();
            }
        } finally {
            lifecycleLock.unlock();
        }
        context.run(() -> startTransport(metadata));
    }

    @Override
    public void cancel(String message, Throwable cause) {
        close(Status.CANCELLED.withDescription(message).withCause(cause));
    }

    abstract void startStreamingThreads();

    abstract void closeStreamingThreads();

    protected final boolean isClosed() {
        return closeStatus != null;
    }

    protected final void onMessage(ResT response) {
        listenerLock.lock();
        try {
            if (!isClosed()) {
                context.run(() -> responseListener.onMessage(response));
            }
        } finally {
            listenerLock.unlock();
            notifyClose();
        }
    }

    protected final void onReady() {
        listenerLock.lock();
        try {
            if (!isClosed()) {
                context.run(responseListener::onReady);
            }
        } finally {
            listenerLock.unlock();
            notifyClose();
        }
    }

    protected final void close(Status status) {
        close(status, EMPTY_METADATA);
    }

    protected final void closeResponse(Status status) {
        closeResponse(status, EMPTY_METADATA);
    }

    final void closeResponse(Status status, Metadata metadata) {
        if (status.getCode() == Status.Code.CANCELLED && deadline != null && deadline.isExpired()) {
            status = Status.DEADLINE_EXCEEDED.withCause(status.getCause());
        }
        close(status, metadata);
    }

    protected final void close(Status status, Metadata metadata) {
        lifecycleLock.lock();
        try {
            if (isClosed()) {
                return;
            }
            closeStatus = status;
            closeMetadata = metadata;
            if (deadlineTask != null) {
                deadlineTask.cancel(false);
            }
        } finally {
            lifecycleLock.unlock();
        }

        context.removeListener(cancellationListener);
        boolean abortTransport = !status.isOk();
        if (abortTransport) {
            // Each call owns its connection. Abort the raw socket before cleanup can wait for
            // an HTTP/2 writer blocked by the peer, including a TLS write or a stream reset.
            closeTransport();
        }
        try {
            closeStreamingThreads();
            GrpcClientStream stream = clientStream;
            if (stream != null) {
                try {
                    // HTTP/2 marks the stream open before writing its initial HEADERS. Until that
                    // write completes, cancel by closing the dedicated connection instead of resetting
                    // a stream the peer has not seen yet (or whose id has not been assigned).
                    if (initialHeadersWritten && !abortTransport) {
                        stream.cancel();
                    }
                } finally {
                    stream.close();
                }
            }
        } catch (Throwable t) {
            LOGGER.log(DEBUG, "Failed to close gRPC stream", t);
        } finally {
            try {
                Http2ClientConnection currentConnection = connection;
                if (currentConnection != null) {
                    currentConnection.close();
                }
                if (enableMetrics() && bytesSent != null && bytesRcvd != null && status.isOk()) {
                    methodMetrics.callDuration().record(Duration.ofMillis(System.currentTimeMillis() - startMillis));
                    methodMetrics.recvMessageSize().record(bytesRcvd.get());
                    methodMetrics.sentMessageSize().record(bytesSent.get());
                }
            } catch (Throwable t) {
                LOGGER.log(DEBUG, "Failed to close gRPC connection", t);
            } finally {
                // A failed graceful shutdown, including an interrupted GOAWAY write, must not
                // leave the dedicated raw connection open after the terminal callback.
                closeTransport();
                closeComplete = true;
                notifyClose();
            }
        }
    }

    /**
     * Read a single gRPC frame, possibly assembled from multiple HTTP/2 frames.
     *
     * @return data for gRPC frame or {@code null}
     */
    protected BufferData readGrpcFrame() {
        BufferData data = unreadData;
        unreadData = null;
        while (true) {
            if (data == null || data.available() == 0) {
                Http2FrameData frameData;
                try {
                    frameData = clientStream().readOne(pollWaitTime());
                } catch (StreamTimeoutException e) {
                    if (deframer.hasPartialFrame() && responseEnded()) {
                        endOfStream();
                    }
                    handleStreamTimeout(e);
                    if (!deframer.hasPartialFrame()) {
                        return null;
                    }
                    continue;
                }
                if (frameData == null) {
                    if (!deframer.hasPartialFrame()) {
                        return null;
                    }
                    if (responseEnded()) {
                        endOfStream();
                    }
                    continue;
                }
                data = frameData.data();
            }

            BufferData frame = deframer.deframe(data);
            if (frame != null) {
                if (deframer.hasRemainder()) {
                    unreadData = data;
                }
                return frame;
            }
            if (deframer.hasPartialFrame() && responseEnded()) {
                endOfStream();
            }
            data = null;
        }
    }

    /**
     * Unary blocking calls that use stubs provide their own executor which needs
     * to be used at least once to unblock the calling thread and complete the
     * gRPC invocation. This method submits an empty task for that purpose. There
     * may be a better way to achieve this.
     */
    void unblockUnaryExecutor() {
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

    GrpcClientImpl grpcClient() {
        return grpcClient;
    }

    ClientConnection clientConnection(ClientUri clientUri, String authority) {
        WebClient webClient = grpcClient.webClient();
        if (deadline != null || context != Context.ROOT) {
            webClient = grpcClient.connectionWebClient();
        }
        GrpcClientConfig clientConfig = grpcClient.prototype();
        SniConfig sni = clientConfig.sni().orElse(null);
        Tls tls = "http".equalsIgnoreCase(clientUri.scheme()) ? CLEARTEXT_TLS : clientConfig.tls();

        if (clientConfig.baseAddress().isPresent()
            && clientConfig.baseAddress().get() instanceof UnixDomainSocketAddress udsAddress) {
            ConnectionKey connectionKey;
            if (sni == null) {
                connectionKey = ConnectionKey.createUnixDomainSocket(clientUri,
                                                                     tls,
                                                                     clientConfig.dnsResolver(),
                                                                     clientConfig.dnsAddressLookup(),
                                                                     udsAddress);
            } else {
                connectionKey = ConnectionKey.createUnixDomainSocket(clientUri,
                                                                     sni,
                                                                     tls,
                                                                     clientConfig.dnsResolver(),
                                                                     clientConfig.dnsAddressLookup(),
                                                                     udsAddress,
                                                                     authorityHeaders(authority));
            }
            return UnixDomainSocketClientConnection.create(
                webClient,
                connectionKey,
                List.of(Http2Client.PROTOCOL_ID),
                udsAddress,
                connection -> false,
                connection -> {}).connect();
        }

        ConnectionKey connectionKey;
        if (sni == null) {
            connectionKey = ConnectionKey.create(
                    clientUri,
                    tls,
                    DefaultDnsResolver.create(),
                    DnsAddressLookup.defaultLookup(),
                    Proxy.noProxy());
        } else {
            connectionKey = ConnectionKey.create(
                    clientUri,
                    sni,
                    tls,
                    DefaultDnsResolver.create(),
                    DnsAddressLookup.defaultLookup(),
                    Proxy.noProxy(),
                    authorityHeaders(authority));
        }
        return TcpClientConnection.create(webClient,
                                          connectionKey,
                                          List.of(Http2Client.PROTOCOL_ID),
                                          connection -> false,
                                          connection -> {
                                          }).connect();
    }

    boolean hasUnreadData() {
        return unreadData != null && unreadData.available() > 0;
    }

    boolean isRemoteOpen() {
        return clientStream().streamState() != Http2StreamState.HALF_CLOSED_REMOTE
                && clientStream().streamState() != Http2StreamState.CLOSED;
    }

    ResT toResponse(BufferData bufferData) {
        bufferData.read();                  // compression
        long grpcLength = bufferData.readUnsignedInt32();     // length prefixed
        if (grpcLength > bufferData.available()) {
            throw new IllegalStateException("Incomplete gRPC message data");
        }
        return responseMarshaller.parse(new MessageInputStream(bufferData, (int) grpcLength));
    }

    byte[] serializeMessage(ReqT message) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(initBufferSize);
        try (InputStream is = requestMarshaller().stream(message)) {
            is.transferTo(baos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }

    Duration heartbeatPeriod() {
        return heartbeatPeriod;
    }

    boolean abortPollTimeExpired() {
        return abortPollTimeExpired;
    }

    Duration pollWaitTime() {
        return pollWaitTime;
    }

    Http2ClientConnection connection() {
        return connection;
    }

    MethodDescriptor.Marshaller<ReqT> requestMarshaller() {
        return requestMarshaller;
    }

    GrpcClientStream clientStream() {
        return clientStream;
    }

    Listener<ResT> responseListener() {
        return responseListener;
    }

    HelidonSocket socket() {
        return socket;
    }

    MethodMetrics methodMetrics() {
        return methodMetrics;
    }

    long startMillis() {
        return startMillis;
    }

    boolean enableMetrics() {
        return grpcConfig.enableMetrics();
    }

    AtomicLong bytesSent() {
        return bytesSent;
    }

    AtomicLong bytesRcvd() {
        return bytesRcvd;
    }

    void handleStreamTimeout(StreamTimeoutException e) {
        if (abortPollTimeExpired()) {
            socket().log(LOGGER, ERROR, "[Reading thread] HTTP/2 stream timeout, aborting");
            throw e;
        }
        socket().log(LOGGER, TRACE, "[Reading thread] HTTP/2 stream timeout, retrying");
    }

    void initMetrics() {
        String baseUri = grpcChannel.baseUri().toString();
        String methodName = methodDescriptor.getFullMethodName();

        methodMetrics = grpcChannel.methodMetrics().computeIfAbsent(baseUri + methodName, uri -> {
            MetricsFactory metricsFactory = grpcChannel.metricsFactory();
            MeterRegistry meterRegistry = grpcChannel.meterRegistry();

            Tag okTag = metricsFactory.tagCreate("grpc.status", "OK");
            Tag grpcMethod = metricsFactory.tagCreate("grpc.method", methodName);
            Tag grpcTarget = metricsFactory.tagCreate("grpc.target", baseUri);

            Counter callStarted = meterRegistry.getOrCreate(
                    metricsFactory.counterBuilder("grpc.client.attempt.started")
                            .tags(List.of(grpcMethod, grpcTarget))
                            .origin(GrpcClient.class.getName()));

            Timer callDuration = meterRegistry.getOrCreate(
                    metricsFactory.timerBuilder("grpc.client.attempt.duration")
                            .baseUnit(Timer.BaseUnits.MILLISECONDS)
                            .tags(List.of(grpcMethod, grpcTarget, okTag))
                            .origin(GrpcClient.class.getName()));

            DistributionSummary sentMessageSize = meterRegistry.getOrCreate(
                    metricsFactory.distributionSummaryBuilder(
                                    "grpc.client.attempt.sent_total_compressed_message_size",
                                    metricsFactory.distributionStatisticsConfigBuilder())
                            .tags(List.of(grpcMethod, grpcTarget, okTag))
                            .origin(GrpcClient.class.getName()));

            DistributionSummary recvMessageSize = meterRegistry.getOrCreate(
                    metricsFactory.distributionSummaryBuilder(
                                    "grpc.client.attempt.rcvd_total_compressed_message_size",
                                    metricsFactory.distributionStatisticsConfigBuilder())
                            .tags(List.of(grpcMethod, grpcTarget, okTag))
                            .origin(GrpcClient.class.getName()));

            return new MethodMetrics(callStarted, callDuration, sentMessageSize, recvMessageSize);
        });
    }

    private static ClientRequestHeaders authorityHeaders(String authority) {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(HeaderValues.create(HeaderNames.HOST, authority));
        return ClientRequestHeaders.create(headers);
    }

    private static ScheduledThreadPoolExecutor deadlineScheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(1,
                Thread.ofPlatform().daemon().name("grpc-client-deadline").factory());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    private String authority(ClientUri clientUri) {
        String authority = callOptions.getAuthority();
        if (authority != null) {
            return authority;
        }
        return clientUri.authority();
    }

    private Http2Settings http2Settings(Http2ClientProtocolConfig config) {
        Http2Settings.Builder b = Http2Settings.builder();
        if (config.maxHeaderListSize() > 0) {
            b.add(Http2Setting.MAX_HEADER_LIST_SIZE, config.maxHeaderListSize());
        }
        return b.add(Http2Setting.INITIAL_WINDOW_SIZE, (long) config.initialWindowSize())
                .add(Http2Setting.MAX_FRAME_SIZE, (long) config.maxFrameSize())
                .add(Http2Setting.ENABLE_PUSH, false)
                .build();
    }

    private void startTransport(Metadata metadata) {
        try {
            if (!isClosed()) {
                initializeTransport(metadata);
            }
        } catch (Throwable t) {
            close(Status.fromThrowable(t));
        }
    }

    private void initializeTransport(Metadata metadata) {

        // obtain HTTP2 connection
        ClientUri clientUri = nextClientUri();
        String authority = authority(clientUri);
        ClientConnection clientConnection = deadline == null && context == Context.ROOT
                ? clientConnection(clientUri, authority)
                : ScopedValue.where(CONNECTING_CALL, this).call(() -> clientConnection(clientUri, authority));
        lifecycleLock.lock();
        try {
            if (!isClosed()) {
                transportConnection = clientConnection;
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (transportConnection != clientConnection) {
            clientConnection.closeResource();
            return;
        }
        socket = clientConnection.helidonSocket();
        Http2ClientImpl http2Client = (Http2ClientImpl) grpcClient.http2Client();
        Http2ClientConnection newConnection;
        try {
            newConnection = Http2ClientConnection.create(http2Client, clientConnection, true);
        } catch (Throwable t) {
            clientConnection.closeResource();
            throw t;
        }
        lifecycleLock.lock();
        try {
            if (!isClosed()) {
                connection = newConnection;
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (connection != newConnection) {
            newConnection.close();
            return;
        }

        // note that settings from connection may not be initialized at this time
        // given that Http2ClientConnection.create() above runs asynchronously
        Http2Settings http2Settings = http2Settings(grpcClient.http2Client()
                                                            .prototype()
                                                            .protocolConfig());

        // create HTTP2 stream from connection
        GrpcClientStream newStream = new GrpcClientStream(
                connection,
                http2Settings,
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
                        GrpcClientConfig config = grpcClient.prototype();
                        return config.readTimeout().orElse(config.protocolConfig().pollWaitTime());
                    }
                },
                http2Client.prototype(),
                connection.streamIdSequence(),
                http2Client);
        lifecycleLock.lock();
        try {
            if (!isClosed()) {
                clientStream = newStream;
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (clientStream != newStream) {
            newStream.close();
            return;
        }

        // send HEADERS frame
        WritableHeaders<?> headers = setupHeaders(metadata, authority, methodDescriptor.getFullMethodName());
        headers.remove(TIMEOUT_NAME);
        if (deadline != null) {
            long remaining = deadline.timeRemaining(TimeUnit.NANOSECONDS);
            if (remaining <= 0) {
                close(Status.DEADLINE_EXCEEDED.withDescription("Call deadline exceeded"));
                return;
            }
            headers.set(TIMEOUT_NAME, GrpcHeadersUtil.encodeTimeout(remaining));
        }
        if (isClosed()) {
            return;
        }
        clientStream.writeHeaders(Http2Headers.create(headers), false);
        initialHeadersWritten = true;
        if (isClosed()) {
            // A concurrent close always closes the registered stream and connection, including
            // when it wins after the HEADERS write but before initialHeadersWritten is published.
            return;
        }

        // Start workers only after the stream has its request headers and stream id.
        try {
            startStreamingThreads();
        } finally {
            if (isClosed()) {
                closeStreamingThreads();
                newStream.close();
            }
        }
    }

    private void closeTransport() {
        try {
            Closeable currentRawTransport = rawTransport;
            if (currentRawTransport != null) {
                currentRawTransport.close();
            }
        } catch (Throwable t) {
            LOGGER.log(DEBUG, "Failed to close gRPC raw transport", t);
        }
        try {
            ClientConnection currentTransport = transportConnection;
            if (currentTransport != null) {
                currentTransport.closeResource();
            }
        } catch (Throwable t) {
            LOGGER.log(DEBUG, "Failed to close gRPC transport", t);
        }
    }

    private void notifyClose() {
        if (!closeComplete || responseListener == null) {
            return;
        }
        // Cancellation must not wait for an application callback. That callback delivers the
        // terminal notification on return, including when it cancels the call itself.
        if (listenerLock.isHeldByCurrentThread() || !listenerLock.tryLock()) {
            return;
        }
        try {
            if (!closeNotified) {
                closeNotified = true;
                // Transport cancellation may have interrupted the reader delivering this callback.
                boolean interrupted = Thread.interrupted();
                try {
                    context.run(() -> responseListener.onClose(closeStatus, closeMetadata));
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    unblockUnaryExecutor();
                }
            }
        } finally {
            listenerLock.unlock();
        }
    }

    private void registerRawTransport(Closeable transport) throws IOException {
        lifecycleLock.lock();
        try {
            if (!isClosed()) {
                rawTransport = transport;
                return;
            }
        } finally {
            lifecycleLock.unlock();
        }
        transport.close();
        throw new IOException("gRPC call closed during connection setup");
    }

    private void ensureTransportActive() throws IOException {
        if (isClosed()) {
            throw new IOException("gRPC call closed during connection setup");
        }
    }

    private void scheduleDeadline() {
        deadlineTask = DEADLINE_SCHEDULER.schedule(this::expireDeadline,
                                                  Math.max(0, deadline.timeRemaining(TimeUnit.NANOSECONDS)),
                                                  TimeUnit.NANOSECONDS);
    }

    private void expireDeadline() {
        lifecycleLock.lock();
        try {
            if (isClosed()) {
                return;
            }
            // A custom deadline ticker can advance more slowly than the scheduler's clock.
            if (!deadline.isExpired()) {
                scheduleDeadline();
                return;
            }
        } finally {
            lifecycleLock.unlock();
        }
        close(Status.DEADLINE_EXCEEDED.withDescription("Call deadline exceeded"));
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

    private boolean responseEnded() {
        return !isRemoteOpen() || clientStream().trailers().isDone();
    }

    private void endOfStream() {
        var trailersFuture = clientStream().trailers();
        if (trailersFuture.isDone() && !trailersFuture.isCompletedExceptionally()) {
            Metadata trailers;
            try {
                trailers = GrpcHeadersUtil.toMetadata(trailersFuture.join());
            } catch (RuntimeException e) {
                StatusRuntimeException failure = Status.INTERNAL
                        .withDescription("Invalid gRPC response trailers")
                        .withCause(e)
                        .asRuntimeException();
                deframer.endOfStream(failure);
                return;
            }
            Status status = trailers.get(InternalStatus.CODE_KEY);
            if (status != null && !status.isOk()) {
                String description = trailers.get(InternalStatus.MESSAGE_KEY);
                trailers.discardAll(InternalStatus.CODE_KEY);
                trailers.discardAll(InternalStatus.MESSAGE_KEY);
                Status actualStatus = description == null ? status : status.withDescription(description);
                StatusRuntimeException failure = actualStatus.asRuntimeException(trailers);
                deframer.endOfStream(failure);
                return;
            }
        }
        deframer.endOfStream();
    }

    record MethodMetrics(Counter callStarted,
                                   Timer callDuration,
                                   DistributionSummary sentMessageSize,
                                   DistributionSummary recvMessageSize) { }

    // Expose the call's connection listener without constructing another set of protocol clients.
    private static final class CallWebClient implements WebClient {
        private final WebClient delegate;
        private final WebClientConfig config;

        private CallWebClient(WebClient delegate, WebClientConfig config) {
            this.delegate = delegate;
            this.config = config;
        }

        @Override
        public HttpClientRequest method(Method method) {
            return delegate.method(method);
        }

        @Override
        public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol, C protocolConfig) {
            return delegate.client(protocol, protocolConfig);
        }

        @Override
        public <T, C extends ProtocolConfig> T client(Protocol<T, C> protocol) {
            return delegate.client(protocol);
        }

        @Override
        public List<String> tcpProtocolIds() {
            return delegate.tcpProtocolIds();
        }

        @Override
        public void responseReceived(WebClientProtocolResponse response) {
            delegate.responseReceived(response);
        }

        @Override
        public ExecutorService executor() {
            return delegate.executor();
        }

        @Override
        public WebClientCookieManager cookieManager() {
            return delegate.cookieManager();
        }

        @Override
        public WebClientConfig prototype() {
            return config;
        }

        @Override
        public void releaseResource() {
            delegate.releaseResource();
        }

        @Override
        public void closeResource() {
            delegate.closeResource();
        }
    }

    private static final class MessageInputStream extends InputStream {
        private final BufferData data;
        private int remaining;

        private MessageInputStream(BufferData data, int remaining) {
            this.data = data;
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return data.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                return -1;
            }
            int read = data.read(bytes, offset, Math.min(remaining, length));
            remaining -= read;
            return read;
        }

        @Override
        public int available() {
            return remaining;
        }
    }
}
