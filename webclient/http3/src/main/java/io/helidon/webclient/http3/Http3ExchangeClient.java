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

package io.helidon.webclient.http3;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.context.Context;
import io.helidon.common.tls.Tls;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HttpLogConfig;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.HandshakeOutcome;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.LogFormatter;
import io.helidon.http.Method;
import io.helidon.http.http3.Http3ControlStreamListener;
import io.helidon.http.http3.Http3ControlStreamSupport;
import io.helidon.http.http3.Http3ErrorCode;
import io.helidon.http.http3.Http3FrameListener;
import io.helidon.http.http3.Http3GoAway;
import io.helidon.http.http3.Http3MessageReader;
import io.helidon.http.http3.Http3PeerCriticalStreams;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.http.http3.Http3ProtocolException;
import io.helidon.http.http3.Http3QpackContext;
import io.helidon.http.http3.Http3Settings;
import io.helidon.http.http3.Http3StreamObservation;
import io.helidon.http.http3.Http3StreamSupport;
import io.helidon.http.http3.Http3StreamType;
import io.helidon.quic.QuicClientConnection;
import io.helidon.quic.QuicClientInitialTokenCache;
import io.helidon.quic.QuicClientRuntime;
import io.helidon.quic.QuicClientTlsSessionCache;
import io.helidon.quic.QuicCloseCommand;
import io.helidon.quic.QuicConfig;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicRemoteStreamRegistration;
import io.helidon.quic.QuicStreamLimitException;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicBidiStreamReservation;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicStreamException;
import io.helidon.quic.stream.QuicStreamWriter;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.ResolvedClientTarget;

import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Initiator.LOCAL;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;
import static io.helidon.webclient.http3.Http3RequestFailureSupport.AttemptDisposition.NOT_PROCESSED;
import static io.helidon.webclient.http3.Http3RequestFailureSupport.addSuppressed;
import static io.helidon.webclient.http3.Http3RequestFailureSupport.collectCleanupFailure;

final class Http3ExchangeClient implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(Http3ExchangeClient.class.getName());

    private final Http3ConnectionCache connectionCache;
    private final ConnectionConfig connectionConfig;
    private final Executor requestExecutor;
    private final boolean validateRequestHeaders;
    private final boolean validateResponseHeaders;

    private Http3ExchangeClient(Builder builder) {
        Objects.requireNonNull(builder.connectionCache, "connectionCache");
        Objects.requireNonNull(builder.cacheKey, "cacheKey");
        Objects.requireNonNull(builder.selection, "selection");
        Objects.requireNonNull(builder.receiveFrameListener, "receiveFrameListener");
        Objects.requireNonNull(builder.sendFrameListener, "sendFrameListener");
        Objects.requireNonNull(builder.requestExecutor, "requestExecutor");
        Objects.requireNonNull(builder.transportObserver, "transportObserver");
        this.connectionCache = builder.connectionCache;
        this.requestExecutor = builder.requestExecutor;
        this.validateRequestHeaders = builder.validateRequestHeaders;
        this.validateResponseHeaders = builder.validateResponseHeaders;
        SSLParameters tlsParameters = builder.cacheKey.connectionKey().tls().sslParameters();
        builder.cacheKey.connectionKey().applyServerNames(tlsParameters);
        this.connectionConfig = new ConnectionConfig(
                builder.cacheKey,
                builder.connectionCacheSize,
                builder.executor,
                Optional.ofNullable(tlsParameters.getServerNames()).map(List::copyOf),
                builder.receiveFrameListener,
                builder.sendFrameListener,
                builder.selection,
                builder.transportObserver);
    }

    Http3StreamedResponse send(URI uri,
                               Method method,
                               ClientRequestHeaders headers,
                               Http3RequestBody requestBody,
                               RequestOptions options) {
        return sendAsync(new RequestData(uri,
                                         method,
                                         headers,
                                         requestBody,
                                         options.readTimeout(),
                                         options.continueTimeout(),
                                         options.sendExpectContinue(),
                                         options.retried(),
                                         validateRequestHeaders,
                                         validateResponseHeaders,
                                         options.context(),
                                         options.requestSent())).join();
    }

    private CompletableFuture<Http3StreamedResponse> sendAsync(RequestData request) {
        CompletableFuture<Http3StreamedResponse> result = new CompletableFuture<>();
        AtomicReference<Http3RequestStream> activeStream = new AtomicReference<>();
        AtomicReference<CompletableFuture<RequestStream>> pendingOpen = new AtomicReference<>();
        result.whenComplete((_, _) -> {
            if (result.isCancelled()) {
                CompletableFuture<RequestStream> open = pendingOpen.get();
                if (open != null) {
                    open.cancel(true);
                }
                Http3RequestStream requestStream = activeStream.get();
                if (requestStream != null) {
                    requestStream.cancel();
                }
            }
        });
        CompletableFuture<RequestStream> requestStreamFuture;
        try {
            requestStreamFuture = connectionCache.requestStream(connectionConfig,
                                                                 request,
                                                                 connectionConfig.transportExecutor());
        } catch (RuntimeException e) {
            result.completeExceptionally(Http3RequestFailureSupport.attemptFailure(e, NOT_PROCESSED, false));
            return result;
        }
        pendingOpen.set(requestStreamFuture);
        if (result.isCancelled()) {
            requestStreamFuture.cancel(true);
        }
        requestStreamFuture.whenComplete((opened, openThrowable) -> {
            pendingOpen.compareAndSet(requestStreamFuture, null);
            if (openThrowable != null) {
                Throwable cause = unwrap(openThrowable);
                if (cause instanceof RequestStreamOpenException streamOpenException) {
                    boolean sessionReusable = streamOpenException.sessionReusable();
                    completeFailedAttempt(result,
                                          streamOpenException.session(),
                                          request,
                                          Http3RequestFailureSupport.attemptFailure(streamOpenException.getCause(),
                                                                                    NOT_PROCESSED,
                                                                                    sessionReusable));
                } else {
                    result.completeExceptionally(Http3RequestFailureSupport.attemptFailure(cause,
                                                                                           NOT_PROCESSED,
                                                                                           false));
                }
                return;
            }
            ConnectionSession session = opened.session();
            Http3RequestStream requestStream = opened.requestStream();
            activeStream.set(requestStream);
            if (result.isCancelled()) {
                requestStream.cancel();
                return;
            }
            AtomicBoolean requestCompletionClaimed = new AtomicBoolean();
            BiConsumer<Http3StreamedResponse, Throwable> requestCompletion = (response, requestThrowable) -> {
                if (result.isDone() || !requestCompletionClaimed.compareAndSet(false, true)) {
                    return;
                }
                if (requestThrowable == null) {
                    result.complete(response);
                } else {
                    Throwable requestFailure = unwrap(requestThrowable);
                    if (!(requestFailure
                            instanceof Http3RequestFailureSupport.RequestAttemptException attemptFailure)) {
                        result.completeExceptionally(requestFailure);
                        return;
                    }
                    completeFailedAttempt(result, session, request, attemptFailure);
                }
            };
            requestStream.execute(requestExecutor, requestCompletion);
        });
        return result;
    }

    private void completeFailedAttempt(CompletableFuture<Http3StreamedResponse> result,
                                       ConnectionSession session,
                                       RequestData request,
                                       Http3RequestFailureSupport.RequestAttemptException failure) {
        try {
            Throwable cause = failure.getCause();
            if (failure.sessionReusable()) {
                session.logFailureAction(request.method().text(), request.uri(), cause, "keep");
            } else if (session.isRetired() || Http3RequestFailureSupport.isRetryableRequestFailure(cause)) {
                session.logFailureAction(request.method().text(), request.uri(), cause, "retire");
                session.retire("request-failure");
            } else {
                session.logFailureAction(request.method().text(), request.uri(), cause, "evict");
                connectionCache.remove(connectionConfig.cacheKey(), session);
            }
        } catch (RuntimeException | Error cleanupFailure) {
            addSuppressed(failure, cleanupFailure);
        } finally {
            result.completeExceptionally(failure);
        }
    }

    @Override
    public void close() {
    }

    static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException completionException && completionException.getCause() != null
                ? completionException.getCause()
                : throwable;
    }

    private static String requestTarget(String method, URI uri) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(uri, "uri");
        String rawPath = Objects.requireNonNullElse(uri.getRawPath(), "");
        return LogFormatter.escape(method) + " " + LogFormatter.escape(LogFormatter.pathOnly(rawPath));
    }

    static ClientSettings clientSettings(Http3ClientProtocolConfig protocolConfig) {
        Objects.requireNonNull(protocolConfig, "protocolConfig");
        Http3Settings localSettings = Http3Settings.createConfigured(protocolConfig.maxFieldSectionSize(),
                                                                     protocolConfig.qpackMaxTableCapacity(),
                                                                     protocolConfig.qpackBlockedStreams());
        Duration initialResponseTimeout = protocolConfig.initialResponseTimeout();
        Http3ClientConfigSupport.validateTimeouts(initialResponseTimeout,
                                                  protocolConfig.handshakeTimeout(),
                                                  protocolConfig.streamOpenTimeout());
        QuicConfig quicConfig = protocolConfig.quic().orElseGet(QuicConfig::create);
        Http3ClientConfigSupport.validateQuic(quicConfig);
        Duration idleTimeout = quicConfig.idleTimeout();
        return new ClientSettings(idleTimeout.toMillis(),
                                  localSettings,
                                  protocolConfig.maxHeadersSize(),
                                  initialResponseTimeout,
                                  quicConfig,
                                  protocolConfig.log());
    }

    static LongFunction<String> applicationErrors() {
        return Http3Protocol::applicationErrorToString;
    }

    record ClientSettings(long idleTimeoutMillis,
                          Http3Settings localSettings,
                          int maxHeadersSize,
                          Duration initialResponseTimeout,
                          QuicConfig quicConfig,
                          HttpLogConfig logConfig) {
    }

    record RequestOptions(Duration readTimeout,
                          Duration continueTimeout,
                          boolean sendExpectContinue,
                          boolean retried,
                          Context context,
                          Runnable requestSent) {
        RequestOptions {
            Objects.requireNonNull(readTimeout, "readTimeout");
            Objects.requireNonNull(continueTimeout, "continueTimeout");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(requestSent, "requestSent");
        }
    }

    record RequestData(URI uri,
                       Method method,
                       ClientRequestHeaders headers,
                       Http3RequestBody requestBody,
                       Duration readTimeout,
                       Duration continueTimeout,
                       boolean sendExpectContinue,
                       boolean retried,
                       boolean validateRequestHeaders,
                       boolean validateResponseHeaders,
                       Context context,
                       Runnable requestSent) {
        RequestData {
            Objects.requireNonNull(uri, "uri");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(headers, "headers");
            Objects.requireNonNull(requestBody, "requestBody");
            Objects.requireNonNull(readTimeout, "readTimeout");
            Objects.requireNonNull(continueTimeout, "continueTimeout");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(requestSent, "requestSent");
        }
    }

    record RequestStream(ConnectionSession session, Http3RequestStream requestStream) {
        RequestStream {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(requestStream, "requestStream");
        }
    }

    static final class RequestStreamOpenException extends IllegalStateException {
        private final ConnectionSession session;

        RequestStreamOpenException(ConnectionSession session, Throwable cause) {
            super("HTTP/3 request stream could not be opened", Objects.requireNonNull(cause, "cause"));
            this.session = Objects.requireNonNull(session, "session");
        }

        ConnectionSession session() {
            return session;
        }

        boolean sessionReusable() {
            return getCause() instanceof QuicStreamLimitException && session.isUsable();
        }
    }

    static final class Builder {
        private Http3ConnectionCache connectionCache;
        private Http3ConnectionCache.CacheKey cacheKey;
        private int connectionCacheSize = -1;
        private Http3Discovery.Selection selection;
        private Executor executor;
        private Executor requestExecutor;
        private Http3FrameListener receiveFrameListener;
        private Http3FrameListener sendFrameListener;
        private HttpTransportObserver transportObserver = HttpTransportObserver.noop();
        private boolean validateRequestHeaders = true;
        private boolean validateResponseHeaders = true;

        Builder connectionCache(Http3ConnectionCache connectionCache) {
            this.connectionCache = connectionCache;
            return this;
        }

        Builder cacheKey(Http3ConnectionCache.CacheKey cacheKey) {
            this.cacheKey = cacheKey;
            return this;
        }

        Builder connectionCacheSize(int connectionCacheSize) {
            this.connectionCacheSize = connectionCacheSize;
            return this;
        }

        Builder selection(Http3Discovery.Selection selection) {
            this.selection = selection;
            return this;
        }

        Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        Builder requestExecutor(Executor requestExecutor) {
            this.requestExecutor = requestExecutor;
            return this;
        }

        Builder receiveFrameListener(Http3FrameListener receiveFrameListener) {
            this.receiveFrameListener = receiveFrameListener;
            return this;
        }

        Builder sendFrameListener(Http3FrameListener sendFrameListener) {
            this.sendFrameListener = sendFrameListener;
            return this;
        }

        Builder transportObserver(HttpTransportObserver transportObserver) {
            this.transportObserver = transportObserver;
            return this;
        }

        Builder validateRequestHeaders(boolean validateRequestHeaders) {
            this.validateRequestHeaders = validateRequestHeaders;
            return this;
        }

        Builder validateResponseHeaders(boolean validateResponseHeaders) {
            this.validateResponseHeaders = validateResponseHeaders;
            return this;
        }

        Http3ExchangeClient build() {
            return new Http3ExchangeClient(this);
        }
    }

    record ConnectionConfig(Http3ConnectionCache.CacheKey cacheKey,
                            int connectionCacheSize,
                            Executor transportExecutor,
                            Optional<List<SNIServerName>> serverNames,
                            Http3FrameListener receiveFrameListener,
                            Http3FrameListener sendFrameListener,
                            Http3Discovery.Selection selection,
                            HttpTransportObserver transportObserver) {
        ConnectionConfig {
            Http3ConnectionCache.validateConnectionCacheSize(connectionCacheSize);
        }

        Tls tls() {
            return cacheKey.connectionKey().tls();
        }

        Http3Discovery.Target target() {
            return cacheKey.target();
        }

        Http3Settings localSettings() {
            return cacheKey.localSettings();
        }

        int maxHeadersSize() {
            return cacheKey.maxHeadersSize();
        }

        QuicConfig quicConfig() {
            return cacheKey.quicConfig();
        }

        boolean sendErrorDetails() {
            return cacheKey.sendErrorDetails();
        }

        Duration initialResponseTimeout() {
            return cacheKey.initialResponseTimeout();
        }

        Duration handshakeTimeout() {
            return cacheKey.handshakeTimeout();
        }

        Duration streamOpenTimeout() {
            return cacheKey.streamOpenTimeout();
        }

        private QuicClientRuntime createRuntime(QuicClientTlsSessionCache tlsSessionCache,
                                                QuicClientInitialTokenCache initialTokenCache,
                                                QuicClientObserver observer,
                                                ResolvedClientTarget resolvedTarget) {
            QuicClientRuntime.Builder runtimeBuilder = QuicClientRuntime.builder()
                    .executor(transportExecutor())
                    .tls(tls())
                    .tlsSessionCache(tlsSessionCache)
                    .initialTokenCache(initialTokenCache)
                    .applicationErrors(applicationErrors())
                    .quicConfig(quicConfig())
                    .initialResponseTimeout(initialResponseTimeout())
                    .observer(observer);
            return runtimeBuilder.build();
        }

        private QuicClientConnection createConnection(QuicClientRuntime quicRuntime,
                                                      ResolvedClientTarget resolvedTarget) {
            ConnectionKey connectionKey = cacheKey.connectionKey();
            InetSocketAddress peer = resolvedTarget.peerAddress();
            Optional<List<SNIServerName>> effectiveServerNames = serverNames();
            if (effectiveServerNames.isEmpty()) {
                return quicRuntime.createConnection(peer,
                                                    connectionKey.tlsPeerHost(),
                                                    connectionKey.tlsPeerPort(),
                                                    new String[] {Http3Protocol.ALPN});
            }
            return quicRuntime.createConnection(peer,
                                                connectionKey.tlsPeerHost(),
                                                connectionKey.tlsPeerPort(),
                                                new String[] {Http3Protocol.ALPN},
                                                effectiveServerNames.get());
        }

    }

    static final class ConnectionSession implements Http3ControlStreamListener {
        private final Runnable onClose;
        private final QuicClientRuntime quicRuntime;
        private final QuicClientConnection connection;
        private final ResolvedClientTarget resolvedTarget;
        private final QuicClientObserver clientObserver;
        private final ConnectionObservation connectionObservation;
        private final Duration streamOpenTimeout;
        private final Http3Settings localSettings;
        private final int maxHeadersSize;
        private final Http3FrameListener receiveFrameListener;
        private final Http3FrameListener sendFrameListener;
        private final boolean sendErrorDetails;
        private final Http3PeerCriticalStreams peerCriticalStreams = Http3PeerCriticalStreams.create();
        private final List<PrimedUniStream> localCriticalStreams = new CopyOnWriteArrayList<>();
        private final ConcurrentHashMap<Long, Http3RequestStream> activeRequestStreams = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Long, Http3StreamObservation> observations = new ConcurrentHashMap<>();
        private final AtomicBoolean registrationClosed = new AtomicBoolean();
        private final AtomicReference<Lifecycle> lifecycle = new AtomicReference<>(Lifecycle.OPEN);
        private final AtomicInteger activeRequests = new AtomicInteger();
        private final ReentrantLock lifecycleLock = new ReentrantLock();
        private final CompletableFuture<Void> transportCleanupComplete = new CompletableFuture<>();
        private final CompletableFuture<Void> cleanupComplete = new CompletableFuture<>();
        private final CompletableFuture<Void> terminated = new CompletableFuture<>();
        private final AtomicReference<NormalCloseState> normalCloseState =
                new AtomicReference<>(NormalCloseState.AVAILABLE);
        private final AtomicReference<Http3GoAway> peerGoAway = new AtomicReference<>();
        private volatile Http3Settings peerSettings;
        private volatile PrimedUniStream localControlStream;
        private volatile Http3QpackContext qpackContext;
        private volatile CompletableFuture<ConnectionSession> ready = new CompletableFuture<>();
        private final AtomicReference<QuicRemoteStreamRegistration> remoteStreamRegistration = new AtomicReference<>();

        private ConnectionSession(Runnable onClose,
                                  QuicClientRuntime quicRuntime,
                                  QuicClientConnection connection,
                                  ResolvedClientTarget resolvedTarget,
                                  QuicClientObserver clientObserver,
                                  ConnectionObservation connectionObservation,
                                  Duration streamOpenTimeout,
                                  Http3Settings localSettings,
                                  int maxHeadersSize,
                                  Http3FrameListener receiveFrameListener,
                                  Http3FrameListener sendFrameListener,
                                  boolean sendErrorDetails) {
            this.onClose = onClose;
            this.quicRuntime = quicRuntime;
            this.connection = connection;
            this.resolvedTarget = Objects.requireNonNull(resolvedTarget, "resolvedTarget");
            this.clientObserver = Objects.requireNonNull(clientObserver, "clientObserver");
            this.connectionObservation = Objects.requireNonNull(connectionObservation, "connectionObservation");
            this.streamOpenTimeout = streamOpenTimeout;
            this.localSettings = localSettings;
            this.maxHeadersSize = maxHeadersSize;
            this.receiveFrameListener = receiveFrameListener;
            this.sendFrameListener = sendFrameListener;
            this.sendErrorDetails = sendErrorDetails;
            this.qpackContext = Http3QpackContext.create(localSettings.qpackMaxTableCapacity(),
                                                         localSettings.qpackBlockedStreams(),
                                                         maxHeadersSize,
                                                         this::fail);
            connection.whenTerminated().whenComplete(this::connectionTerminated);
        }

        private void connectionTerminated(QuicTermination termination, Throwable throwable) {
            if (throwable == null) {
                transportCleanupComplete.complete(null);
            } else {
                transportCleanupComplete.completeExceptionally(throwable);
            }
            if (termination != null) {
                QuicClientObserver.TerminationOutcomes outcomes =
                        QuicClientObserver.classifyTermination(termination, null);
                closeWithCause(termination.closeCause(), outcomes.openStreamOutcome());
            } else if (throwable != null) {
                closeWithCause(throwable, StreamOutcome.ERROR);
            } else {
                closeNormally();
            }
        }

        static ConnectionSession create(ConnectionConfig config,
                                        QuicClientTlsSessionCache tlsSessionCache,
                                        QuicClientInitialTokenCache initialTokenCache,
                                        Runnable onClose) {
            if (!config.cacheKey().endpointKey().proxyRoute().supportsDatagrams()) {
                throw new IllegalStateException("HTTP/3 requires a direct UDP route");
            }
            ResolvedClientTarget resolvedTarget;
            try {
                Http3Discovery.Target selectedTarget = config.target();
                resolvedTarget = config.cacheKey()
                        .connectionTarget()
                        .resolve(selectedTarget.peerHost(),
                                 selectedTarget.peerPort(),
                                 config.selection().networkGeneration());
            } catch (RuntimeException e) {
                throw Http3RequestFailureSupport.endpointUnavailable(e);
            }
            QuicClientObserver observer = new QuicClientObserver(config.transportObserver());
            QuicClientRuntime quicRuntime = config.createRuntime(tlsSessionCache,
                                                                 initialTokenCache,
                                                                 observer,
                                                                 resolvedTarget);
            QuicClientConnection connection;
            try {
                connection = config.createConnection(quicRuntime, resolvedTarget);
            } catch (RuntimeException e) {
                try {
                    quicRuntime.close();
                } catch (RuntimeException closeFailure) {
                    e.addSuppressed(closeFailure);
                }
                throw e;
            }
            Http3Settings localSettings = config.localSettings();
            ConnectionSession session = new ConnectionSession(onClose,
                                                              quicRuntime,
                                                              connection,
                                                              resolvedTarget,
                                                              observer,
                                                              observer.observation(connection),
                                                              config.streamOpenTimeout(),
                                                              localSettings,
                                                              config.maxHeadersSize(),
                                                              config.receiveFrameListener(),
                                                              config.sendFrameListener(),
                                                              config.sendErrorDetails());

            try {
                QuicRemoteStreamRegistration registration = connection.addRemoteStreamListener(session::handleRemoteStream);
                session.remoteStreamRegistration.set(registration);
                if (session.registrationClosed.get()
                        && session.remoteStreamRegistration.compareAndSet(registration, null)) {
                    registration.close();
                }
                session.initialize(config);
                return session;
            } catch (RuntimeException | Error failure) {
                try {
                    session.closeWithCause(failure, StreamOutcome.ERROR);
                } catch (RuntimeException | Error cleanupFailure) {
                    if (failure != cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                throw failure;
            }
        }

        CompletableFuture<ConnectionSession> ready() {
            return ready;
        }

        CompletionStage<Void> whenTerminated() {
            return terminated.minimalCompletionStage();
        }

        boolean isUsable() {
            return lifecycle.get() == Lifecycle.OPEN
                    && connection.isOpen()
                    && connection.termination().isEmpty();
        }

        QuicConnection connection() {
            return connection;
        }

        ResolvedClientTarget resolvedTarget() {
            return resolvedTarget;
        }

        boolean isRetired() {
            return lifecycle.get() != Lifecycle.OPEN;
        }

        boolean closeWouldBlockCurrentThread() {
            for (Http3RequestStream requestStream : activeRequestStreams.values()) {
                if (requestStream.runsOnCurrentThread()) {
                    return true;
                }
            }
            return false;
        }

        Http3QpackContext qpackContext() {
            return qpackContext;
        }

        boolean rejectsStream(long streamId) {
            return Http3RequestFailureSupport.goAwayRejectsStream(peerGoAway.get(), streamId);
        }

        CompletableFuture<Http3RequestStream> openRequestStream(RequestData request,
                                                                Executor responseExecutor,
                                                                Duration streamCreditTimeout) {
            Objects.requireNonNull(responseExecutor, "responseExecutor");
            Objects.requireNonNull(streamCreditTimeout, "streamCreditTimeout");
            if (lifecycle.get() != Lifecycle.OPEN) {
                return CompletableFuture.failedFuture(requestOpenFailure());
            }

            return connection.openNewLocalBidiStream(streamCreditTimeout)
                    .thenApply(stream -> createRequestStream(request, responseExecutor, stream));
        }

        CompletableFuture<QuicBidiStreamReservation> reserveRequestStream() {
            if (lifecycle.get() != Lifecycle.OPEN) {
                return CompletableFuture.failedFuture(requestOpenFailure());
            }
            return connection.reserveNewLocalBidiStream();
        }

        Http3RequestStream openReservedRequestStream(RequestData request,
                                                     Executor responseExecutor,
                                                     QuicBidiStreamReservation reservation) {
            Objects.requireNonNull(responseExecutor, "responseExecutor");
            Objects.requireNonNull(reservation, "reservation");
            try (reservation) {
                if (lifecycle.get() != Lifecycle.OPEN) {
                    throw new CompletionException(requestOpenFailure());
                }
                return createRequestStream(request, responseExecutor, reservation.open());
            }
        }

        private Http3RequestStream createRequestStream(RequestData request,
                                                       Executor responseExecutor,
                                                       QuicBidiStream stream) {
            StreamObservation streamObservation = connectionObservation.streamOpened(BIDIRECTIONAL, LOCAL);
            if (lifecycle.get() != Lifecycle.OPEN || rejectsStream(stream.streamId())) {
                logRequestOpenRejected(stream.streamId());
                try {
                    abortStream(stream);
                } finally {
                    streamObservation.close(StreamOutcome.REJECTED);
                }
                throw new CompletionException(requestOpenFailure());
            }
            Http3MessageReader reader;
            try {
                reader = Http3MessageReader.response(
                        stream,
                        qpackContext,
                        connection,
                        request.method(),
                        maxHeadersSize,
                        Http3MessageReader.ResponseOptions.create(request.validateResponseHeaders(),
                                                                  request.readTimeout(),
                                                                  receiveFrameListener));
            } catch (RuntimeException | Error e) {
                try {
                    abortStream(stream);
                } finally {
                    streamObservation.close(StreamOutcome.ERROR);
                }
                throw e;
            }
            QuicStreamWriter writer;
            try {
                writer = Http3StreamSupport.connectWriter(stream, connection, sendFrameListener);
            } catch (RuntimeException | Error e) {
                try {
                    reader.close();
                } finally {
                    try {
                        abortStream(stream);
                    } finally {
                        streamObservation.close(StreamOutcome.ERROR);
                    }
                }
                throw e;
            }
            Http3RequestStream requestStream = new Http3RequestStream(
                    this,
                    request,
                    stream,
                    reader,
                    writer,
                    sendFrameListener,
                    streamObservation,
                    responseExecutor,
                    this::requestFinished,
                    this::logResponseClosedEarly);
            boolean registered;
            lifecycleLock.lock();
            try {
                registered = lifecycle.get() == Lifecycle.OPEN
                        && !rejectsStream(stream.streamId())
                        && activeRequestStreams.putIfAbsent(stream.streamId(), requestStream) == null;
                if (registered) {
                    activeRequests.incrementAndGet();
                }
            } finally {
                lifecycleLock.unlock();
            }
            if (!registered) {
                logRequestOpenRejected(stream.streamId());
                try {
                    abortStream(stream);
                } finally {
                    requestStream.finish(StreamOutcome.REJECTED);
                }
                throw new CompletionException(requestOpenFailure());
            }
            return requestStream;
        }

        void close() {
            connection.termination()
                    .ifPresentOrElse(termination -> {
                        QuicClientObserver.TerminationOutcomes outcomes =
                                QuicClientObserver.classifyTermination(termination, null);
                        closeWithCause(termination.closeCause(), outcomes.openStreamOutcome());
                    },
                                     this::closeNormallyAfterControlBatch);
        }

        void requestFinished(long streamId) {
            if (activeRequestStreams.remove(streamId) == null) {
                return;
            }
            if (activeRequests.decrementAndGet() == 0) {
                Lifecycle current = lifecycle.get();
                if (current == Lifecycle.DRAINING) {
                    closeNormallyAfterControlBatch();
                } else if (current == Lifecycle.CLOSING) {
                    completeClosed();
                }
            }
        }

        void retire(String reason) {
            markRetired(reason);
            if (activeRequests.get() == 0) {
                closeNormallyAfterControlBatch();
            }
        }

        private void markRetired(String reason) {
            boolean retired;
            lifecycleLock.lock();
            try {
                retired = lifecycle.compareAndSet(Lifecycle.OPEN, Lifecycle.DRAINING);
            } finally {
                lifecycleLock.unlock();
            }
            if (retired) {
                logDebug(() -> "state=session-retire reason=%s activeRequests=%d goAwayStreamId=%s"
                        .formatted(reason, activeRequests.get(), goAwaySummary(peerGoAway.get())));
                try {
                    onClose.run();
                } catch (RuntimeException | Error failure) {
                    closeWithCause(failure, StreamOutcome.ERROR);
                    throw failure;
                }
            }
        }

        private void initialize(ConnectionConfig config) {
            CompletableFuture<ConnectionSession> readyFuture = ready;
            logDebug(() -> "state=session-opening peer=%s alternative=%s"
                    .formatted(connection.remotePeer().address(), config.target().alternative()));
            HandshakeObservation handshakeObservation = connectionObservation.handshakeStarted();
            try {
                connection.startHandshake()
                        .orTimeout(config.handshakeTimeout().toNanos(), TimeUnit.NANOSECONDS)
                        .whenComplete((_, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = Http3ExchangeClient.unwrap(throwable);
                                if (cause instanceof TimeoutException) {
                                    handshakeObservation.close(HandshakeOutcome.TIMEOUT);
                                    clientObserver.terminalOutcome(connection, ConnectionOutcome.TIMEOUT);
                                } else {
                                    handshakeObservation.close(QuicClientObserver.handshakeFailureOutcome(connection));
                                }
                                failEndpoint(cause);
                                return;
                            }
                            handshakeObservation.close(HandshakeOutcome.SUCCESS);
                            String applicationProtocol = connection.applicationProtocol().orElse("");
                            if (!Http3Protocol.ALPN.equals(applicationProtocol)) {
                                failEndpoint(new IllegalStateException(
                                        "QUIC handshake did not negotiate HTTP/3 ALPN: " + applicationProtocol));
                                return;
                            }
                            connectionObservation.protocolSelected(PROTOCOL_HTTP_3);
                            openAndPrimeSessionStreams(config, qpackContext, readyFuture);
                        });
            } catch (RuntimeException e) {
                handshakeObservation.close(QuicClientObserver.handshakeFailureOutcome(connection));
                failEndpoint(e);
            }
        }

        private void openAndPrimeSessionStreams(ConnectionConfig config,
                                                Http3QpackContext qpackContext,
                                                CompletableFuture<ConnectionSession> readyFuture) {
            openAndPrimeControlStream()
                    .thenApply(criticalStream -> {
                        try {
                            return addLocalCriticalStream(criticalStream);
                        } catch (RuntimeException | Error failure) {
                            resetFailedCriticalStream(criticalStream.stream(), failure);
                            throw failure;
                        }
                    })
                    .thenCompose(_ -> openAndPrimeInstructionStream(Http3StreamType.QPACK_ENCODER,
                                                                          qpackContext::encoderInstructionsSender))
                    .thenCompose(_ -> openAndPrimeInstructionStream(Http3StreamType.QPACK_DECODER,
                                                                          qpackContext::decoderInstructionsSender))
                    .thenRun(() -> completeReady(readyFuture))
                    .whenComplete((_, throwable) -> {
                        if (throwable != null) {
                            Throwable cause = Http3ExchangeClient.unwrap(throwable);
                            if (cause instanceof QuicStreamLimitException) {
                                failEndpoint(Http3ProtocolException.connectionError(
                                        Http3ErrorCode.STREAM_CREATION_ERROR,
                                        "HTTP/3 local critical streams cannot be created",
                                        cause));
                            } else {
                                fail(cause);
                            }
                        }
                    });
        }

        private boolean handleRemoteStream(QuicReceiverStream stream) {
            if (stream instanceof QuicBidiStream) {
                fail(stream.streamId(),
                     Http3ProtocolException.connectionError(
                             Http3ErrorCode.STREAM_CREATION_ERROR,
                             "HTTP/3 server created a prohibited bidirectional stream"));
                return true;
            }
            Http3StreamObservation observation = Http3ControlStreamSupport.observe(stream,
                                                                                    () -> Optional.of(qpackContext()),
                                                                                    peerCriticalStreams,
                                                                                    connection,
                                                                                    receiveFrameListener,
                                                                                    this);
            boolean registered;
            lifecycleLock.lock();
            try {
                registered = !closing() && observations.putIfAbsent(stream.streamId(), observation) == null;
            } finally {
                lifecycleLock.unlock();
            }
            if (!registered) {
                observation.close();
                return true;
            }
            observation.completion().whenComplete((_, throwable) -> {
                observations.remove(stream.streamId(), observation);
                if (throwable != null && !closing() && connection.isOpen()) {
                    fail(stream.streamId(), throwable);
                }
            });
            return true;
        }

        @Override
        public void onSettings(Http3Settings settings) {
            if (peerSettings != null) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.SETTINGS_ERROR,
                                                             "Peer HTTP/3 settings are already installed");
            }
            peerSettings = Objects.requireNonNull(settings, "settings");
            qpackContext.peerSettings(peerSettings.qpackMaxTableCapacity(), peerSettings.qpackBlockedStreams());
            logDebug(() -> "state=peer-settings settings=%s".formatted(peerSettings));
        }

        private CompletableFuture<PrimedUniStream> openAndPrimeControlStream() {
            return connection.openNewLocalUniStream(streamOpenTimeout)
                    .thenApply(stream -> {
                        try {
                            QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream,
                                                                                       connection,
                                                                                       Http3StreamType.CONTROL,
                                                                                       sendFrameListener);
                            writer.scheduleForWriting(
                                    BufferData.create(Http3Protocol.controlStreamPreamble(localSettings)),
                                    false);
                            return new PrimedUniStream(stream, writer, Http3StreamType.CONTROL);
                        } catch (RuntimeException | Error e) {
                            resetFailedCriticalStream(stream, e);
                            throw e;
                        }
                    });
        }

        private CompletableFuture<PrimedUniStream> openAndPrimeInstructionStream(Http3StreamType streamType,
                                                                                 InstructionRegistrar registrar) {
            return connection.openNewLocalUniStream(streamOpenTimeout)
                    .thenApply(stream -> {
                        try {
                            QuicStreamWriter writer = Http3StreamSupport.connectWriter(stream,
                                                                                       connection,
                                                                                       streamType,
                                                                                       sendFrameListener);
                            writer.scheduleForWriting(BufferData.create(Http3Protocol.qpackUniStreamPreamble(streamType)),
                                                      false);
                            PrimedUniStream primedStream = new PrimedUniStream(stream, writer, streamType);
                            addLocalCriticalStream(primedStream);
                            registrar.register(bytes -> writer.scheduleForWriting(BufferData.create(bytes), false));
                            return primedStream;
                        } catch (RuntimeException | Error e) {
                            resetFailedCriticalStream(stream, e);
                            throw e;
                        }
                    });
        }

        private static void resetFailedCriticalStream(QuicSenderStream stream, Throwable failure) {
            try {
                stream.reset(Http3ErrorCode.INTERNAL_ERROR.code());
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }

        private ConnectionSession addLocalCriticalStream(PrimedUniStream criticalStream) {
            boolean added = false;
            lifecycleLock.lock();
            try {
                if (!closing()) {
                    if (criticalStream.streamType() == Http3StreamType.CONTROL) {
                        if (localControlStream != null) {
                            throw new IllegalStateException("HTTP/3 local control stream is already initialized");
                        }
                        localControlStream = criticalStream;
                    }
                    localCriticalStreams.add(criticalStream);
                    added = true;
                }
            } finally {
                lifecycleLock.unlock();
            }
            if (!added) {
                try {
                    criticalStream.stream().reset(Http3ErrorCode.INTERNAL_ERROR.code());
                } catch (QuicStreamException _) {
                }
                throw new IllegalStateException("HTTP/3 session closed while opening " + criticalStream.streamType());
            }
            criticalStream.stream().futureSendingCompletion().whenComplete((state, throwable) -> {
                if (closing() || !connection.isOpen()) {
                    return;
                }
                Throwable cause = throwable == null
                        ? new IllegalStateException("HTTP/3 local critical stream entered terminal state " + state)
                        : throwable;
                Http3ProtocolException failure = Http3ProtocolException.connectionError(
                        Http3ErrorCode.CLOSED_CRITICAL_STREAM,
                        "HTTP/3 local critical stream closed: " + criticalStream.streamType(),
                        cause);
                if (criticalStream.streamType() == Http3StreamType.QPACK_ENCODER
                        || criticalStream.streamType() == Http3StreamType.QPACK_DECODER) {
                    qpackContext.instructionStreamFailed(criticalStream.streamType(), failure);
                } else {
                    fail(criticalStream.stream().streamId(), failure);
                }
            });
            return this;
        }

        private void completeReady(CompletableFuture<ConnectionSession> readyFuture) {
            lifecycleLock.lock();
            try {
                if (readyFuture != ready || lifecycle.get() != Lifecycle.OPEN) {
                    return;
                }
                PrimedUniStream control = Objects.requireNonNull(localControlStream,
                                                                "HTTP/3 local control stream is not initialized");
                if (!control.writer().connected()) {
                    throw new IllegalStateException("HTTP/3 local control writer is disconnected");
                }
            } finally {
                lifecycleLock.unlock();
            }
            boolean completed = readyFuture.complete(this);
            if (completed) {
                logDebug(() -> "state=session-ready peer=%s local=%s"
                        .formatted(connection.remotePeer().address(), connection.localPeer().address()));
            }
        }

        private void fail(Throwable throwable) {
            fail(OptionalLong.empty(), throwable);
        }

        void fail(long streamId, Throwable throwable) {
            fail(OptionalLong.of(streamId), throwable);
        }

        private void fail(OptionalLong streamId, Throwable throwable) {
            Throwable cause = Http3ExchangeClient.unwrap(throwable);
            Optional<Http3ProtocolException> protocolException = Http3ProtocolException.find(cause);
            if (protocolException.filter(it -> it.scope() == Http3ProtocolException.Scope.STREAM).isPresent()) {
                throw new IllegalArgumentException("Stream-scoped HTTP/3 signal reached the connection owner", cause);
            }
            logDebug(() -> "state=session-fail cause=%s"
                    .formatted(Http3RequestFailureSupport.throwableSummary(cause)));
            closeWithCause(streamId, cause, StreamOutcome.ERROR);
        }

        private void failEndpoint(Throwable throwable) {
            fail(Http3RequestFailureSupport.endpointUnavailable(Http3ExchangeClient.unwrap(throwable)));
        }

        private void closeNormally() {
            closeInternal(Optional.empty(),
                          Http3ErrorCode.NO_ERROR,
                          OptionalLong.empty(),
                          lifecycle.get() == Lifecycle.DRAINING
                                  ? "HTTP/3 connection retired"
                                  : "HTTP/3 connection cache eviction",
                          StreamOutcome.CANCELLED);
        }

        private void closeNormallyAfterControlBatch() {
            for (;;) {
                NormalCloseState state = normalCloseState.get();
                switch (state) {
                case AVAILABLE -> {
                    if (normalCloseState.compareAndSet(state, NormalCloseState.CLOSE_SELECTED)) {
                        closeNormally();
                        return;
                    }
                }
                case CONTROL_BATCH_PENDING -> {
                    if (normalCloseState.compareAndSet(state, NormalCloseState.CONTROL_BATCH_CLOSE_REQUESTED)) {
                        return;
                    }
                }
                case CONTROL_BATCH_CLOSE_REQUESTED, CLOSE_SELECTED -> {
                    return;
                }
                default -> throw new IllegalStateException("Unknown normal close state: " + state);
                }
            }
        }

        private void closeWithCause(Throwable throwable, StreamOutcome streamOutcome) {
            closeWithCause(OptionalLong.empty(), throwable, streamOutcome);
        }

        private void closeWithCause(OptionalLong streamId,
                                    Throwable throwable,
                                    StreamOutcome streamOutcome) {
            closeInternal(Optional.of(throwable),
                          connectionCloseCode(throwable),
                          streamId,
                          lifecycle.get() == Lifecycle.DRAINING
                                  ? "HTTP/3 connection retired"
                                  : "HTTP/3 connection cache eviction",
                          streamOutcome);
        }

        private static Http3ErrorCode connectionCloseCode(Throwable throwable) {
            Optional<Http3ProtocolException> protocolException = Http3ProtocolException.find(throwable);
            if (protocolException.filter(it -> it.scope() == Http3ProtocolException.Scope.STREAM).isPresent()) {
                throw new IllegalArgumentException("Stream-scoped HTTP/3 signal reached the connection owner", throwable);
            }
            return protocolException.map(Http3ProtocolException::errorCode)
                    .orElse(Http3ErrorCode.INTERNAL_ERROR);
        }

        private void closeInternal(Optional<Throwable> throwable,
                                   Http3ErrorCode closeCode,
                                   OptionalLong streamId,
                                   String closeReason,
                                   StreamOutcome streamOutcome) {
            Lifecycle previous;
            QuicRemoteStreamRegistration registration;
            List<Http3StreamObservation> currentObservations;
            List<Http3RequestStream> currentRequests;
            lifecycleLock.lock();
            try {
                previous = lifecycle.get();
                if (previous == Lifecycle.CLOSING || previous == Lifecycle.CLOSED) {
                    return;
                }
                lifecycle.set(Lifecycle.CLOSING);
                registrationClosed.set(true);
                registration = remoteStreamRegistration.getAndSet(null);
                currentObservations = List.copyOf(observations.values());
                observations.clear();
                currentRequests = List.copyOf(activeRequestStreams.values());
                localCriticalStreams.clear();
            } finally {
                lifecycleLock.unlock();
            }
            Lifecycle closeFrom = previous;

            logDebug(() -> "state=session-close retired=%s activeRequests=%d cause=%s"
                    .formatted(closeFrom == Lifecycle.DRAINING,
                               activeRequests.get(),
                               Http3RequestFailureSupport.throwableSummary(throwable.orElse(null))));
            Throwable closeCause = throwable.orElseGet(
                    () -> new IllegalStateException("HTTP/3 connection is closed"));
            Throwable cleanupFailure = null;
            if (closeFrom == Lifecycle.OPEN) {
                try {
                    onClose.run();
                } catch (Throwable failure) {
                    addSuppressed(closeCause, failure);
                    cleanupFailure = collectCleanupFailure(cleanupFailure, failure);
                }
            }

            if (!ready.isDone()) {
                ready.completeExceptionally(throwable
                                                    .<Throwable>map(Http3RequestFailureSupport::endpointUnavailable)
                                                    .orElseGet(() -> new IllegalStateException(
                                                            "HTTP/3 connection is closed")));
            }

            if (registration != null) {
                try {
                    registration.close();
                } catch (Throwable failure) {
                    addSuppressed(closeCause, failure);
                    cleanupFailure = collectCleanupFailure(cleanupFailure, failure);
                }
            }
            for (Http3StreamObservation observation : currentObservations) {
                try {
                    observation.close();
                } catch (Throwable failure) {
                    addSuppressed(closeCause, failure);
                    cleanupFailure = collectCleanupFailure(cleanupFailure, failure);
                }
            }
            try {
                qpackContext.close(closeCause);
            } catch (Throwable failure) {
                addSuppressed(closeCause, failure);
                cleanupFailure = collectCleanupFailure(cleanupFailure, failure);
            }
            try {
                peerCriticalStreams.close(closeCause);
            } catch (Throwable failure) {
                addSuppressed(closeCause, failure);
                cleanupFailure = collectCleanupFailure(cleanupFailure, failure);
            }
            for (Http3RequestStream requestStream : currentRequests) {
                try {
                    requestStream.sessionClosed(closeCause, streamOutcome);
                } catch (Throwable failure) {
                    addSuppressed(closeCause, failure);
                    cleanupFailure = collectCleanupFailure(cleanupFailure, failure);
                }
            }
            completeResponseTrailers(currentRequests, closeCause);
            if (connection.isOpen()) {
                try {
                    QuicCloseCommand command = throwable
                            .map(cause -> QuicCloseCommand.application(closeCode.code(), cause, closeReason))
                            .orElseGet(() -> QuicCloseCommand.application(closeCode.code(), closeReason));
                    if (streamId.isPresent()) {
                        command = command.withStream(streamId.orElseThrow());
                    }
                    String peerDetail = throwable.map(Throwable::getMessage).orElse(null);
                    if (sendErrorDetails && peerDetail != null) {
                        command = command.withPeerDetail(peerDetail);
                    }
                    connection.terminate(command);
                } catch (Throwable failure) {
                    addSuppressed(closeCause, failure);
                    cleanupFailure = collectCleanupFailure(cleanupFailure, failure);
                }
            }
            try {
                quicRuntime.close();
            } catch (Throwable failure) {
                addSuppressed(closeCause, failure);
                cleanupFailure = collectCleanupFailure(cleanupFailure, failure);
            }
            Throwable localCleanupFailure = cleanupFailure;
            transportCleanupComplete.whenComplete((_, transportThrowable) -> {
                Throwable combinedFailure = localCleanupFailure;
                if (transportThrowable != null) {
                    Throwable transportFailure = Http3ExchangeClient.unwrap(transportThrowable);
                    if (combinedFailure != transportFailure) {
                        addSuppressed(closeCause, transportFailure);
                    }
                    combinedFailure = collectCleanupFailure(combinedFailure, transportFailure);
                }
                if (combinedFailure == null) {
                    cleanupComplete.complete(null);
                } else {
                    cleanupComplete.completeExceptionally(combinedFailure);
                }
                completeClosed();
            });
        }

        static void completeResponseTrailers(List<Http3RequestStream> requestStreams, Throwable failure) {
            for (Http3RequestStream requestStream : requestStreams) {
                Thread.startVirtualThread(() -> requestStream.completeTrailersFailure(failure));
            }
        }

        private void completeClosed() {
            if (activeRequests.get() == 0
                    && cleanupComplete.isDone()
                    && lifecycle.compareAndSet(Lifecycle.CLOSING, Lifecycle.CLOSED)) {
                cleanupComplete.whenComplete((_, failure) -> {
                    if (failure == null) {
                        terminated.complete(null);
                    } else {
                        terminated.completeExceptionally(failure);
                    }
                });
            }
        }

        private boolean closing() {
            Lifecycle current = lifecycle.get();
            return current == Lifecycle.CLOSING || current == Lifecycle.CLOSED;
        }

        @Override
        public void onControlDataProcessing() {
            normalCloseState.compareAndSet(NormalCloseState.AVAILABLE, NormalCloseState.CONTROL_BATCH_PENDING);
        }

        @Override
        public void onGoAway(Http3GoAway goAway) {
            Http3GoAway previous = updatePeerGoAway(peerGoAway, goAway);
            logStreamDebug(goAway.identifier(),
                           () -> "state=goaway-observed previous=%s".formatted(goAwaySummary(previous)));
            markRetired("goaway");
        }

        @Override
        public void onControlDataProcessed() {
            boolean retiredAndIdle = lifecycle.get() == Lifecycle.DRAINING && activeRequests.get() == 0;
            for (;;) {
                NormalCloseState state = normalCloseState.get();
                NormalCloseState next = switch (state) {
                    case CONTROL_BATCH_PENDING -> retiredAndIdle
                            ? NormalCloseState.CLOSE_SELECTED
                            : NormalCloseState.AVAILABLE;
                    case CONTROL_BATCH_CLOSE_REQUESTED -> NormalCloseState.CLOSE_SELECTED;
                    case AVAILABLE, CLOSE_SELECTED -> null;
                };
                if (next == null) {
                    return;
                }
                if (normalCloseState.compareAndSet(state, next)) {
                    if (next == NormalCloseState.CLOSE_SELECTED) {
                        closeNormally();
                    }
                    return;
                }
            }
        }

        private enum NormalCloseState {
            AVAILABLE,
            CONTROL_BATCH_PENDING,
            CONTROL_BATCH_CLOSE_REQUESTED,
            CLOSE_SELECTED
        }

        private enum Lifecycle {
            OPEN,
            DRAINING,
            CLOSING,
            CLOSED
        }

        private static Http3GoAway updatePeerGoAway(AtomicReference<Http3GoAway> peerGoAway, Http3GoAway next) {
            Objects.requireNonNull(peerGoAway, "peerGoAway");
            Objects.requireNonNull(next, "next");
            if (next.type() != Http3GoAway.Type.REQUEST_STREAM_ID) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.ID_ERROR,
                                                             "Server GOAWAY must contain a request stream ID");
            }
            for (;;) {
                Http3GoAway current = peerGoAway.get();
                if (current != null && !next.isValidSuccessorOf(current)) {
                    throw Http3ProtocolException.connectionError(
                            Http3ErrorCode.ID_ERROR,
                            "HTTP/3 GOAWAY identifier increased from "
                                    + current.identifier()
                                    + " to "
                                    + next.identifier());
                }
                if (peerGoAway.compareAndSet(current, next)) {
                    return current;
                }
            }
        }

        private static void abortStream(QuicBidiStream stream) {
            stream.requestStopSending(Http3ErrorCode.REQUEST_CANCELLED.code());
            try {
                stream.reset(Http3ErrorCode.REQUEST_CANCELLED.code());
            } catch (QuicStreamException _) {
                // If the stream is already closed, retiring the connection is enough.
            }
        }

        private Throwable requestOpenFailure() {
            return Http3RequestFailureSupport.connectionRetired();
        }

        void logRequestOpen(long streamId, String method, URI uri, boolean retried) {
            logStreamDebug(streamId,
                           () -> "state=request-open retry=%s request=%s"
                                   .formatted(retried, requestTarget(method, uri)));
        }

        void logRequestFailure(long streamId, Throwable cause, boolean retryable) {
            logStreamDebug(streamId,
                           () -> "state=request-failure retryable=%s cause=%s"
                                   .formatted(retryable, Http3RequestFailureSupport.throwableSummary(cause)));
        }

        private void logFailureAction(String method, URI uri, Throwable cause, String action) {
            logDebug(() -> "state=request-terminal action=%s request=%s cause=%s"
                    .formatted(action,
                               requestTarget(method, uri),
                               Http3RequestFailureSupport.throwableSummary(cause)));
        }

        void logResponse(long streamId, int status, boolean hasEntity) {
            logStreamDebug(streamId,
                           () -> "state=response-head status=%d entity=%s".formatted(status, hasEntity));
        }

        private void logResponseClosedEarly(long streamId) {
            logStreamDebug(streamId, () -> "state=response-close-early");
        }

        private void logRequestOpenRejected(long streamId) {
            logStreamDebug(streamId,
                           () -> "state=request-open-rejected goAwayStreamId=%s retired=%s"
                                   .formatted(goAwaySummary(peerGoAway.get()), isRetired()));
        }

        private static String goAwaySummary(Http3GoAway goAway) {
            return goAway == null ? "none" : "0x" + Long.toHexString(goAway.identifier());
        }

        private void logDebug(Supplier<String> messageSupplier) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                connection.log(LOGGER, System.Logger.Level.DEBUG, "%s", messageSupplier.get());
            }
        }

        private void logStreamDebug(long streamId, Supplier<String> messageSupplier) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                connection.log(LOGGER, System.Logger.Level.DEBUG, "%d: %s", streamId, messageSupplier.get());
            }
        }

        @FunctionalInterface
        private interface InstructionRegistrar {
            void register(Http3QpackContext.InstructionSender sender);
        }

        private record PrimedUniStream(QuicSenderStream stream,
                                       QuicStreamWriter writer,
                                       Http3StreamType streamType) {
        }
    }
}
