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

package io.helidon.webclient.http2;

import java.net.UnixDomainSocketAddress;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.FullClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.UnixDomainSocketClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.UpgradeResponse;
import io.helidon.webclient.http2.Http2ConnectionAttemptResult.Result;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

// a representation of a single remote endpoint
// this may use one or more connections (depending on parallel streams)
class Http2ClientConnectionHandler {
    private static final System.Logger LOGGER = System.getLogger(Http2ClientConnectionHandler.class.getName());
    private static final Header CONNECTION_UPGRADE_HEADER = HeaderValues.createCached(HeaderNames.CONNECTION,
                                                                                      "Upgrade, HTTP2-Settings");
    // h2c stands for HTTP/2 plaintext protocol (only used without TLS)
    private static final Header UPGRADE_HEADER = HeaderValues.createCached(HeaderNames.UPGRADE, "h2c");
    private static final HeaderName HTTP2_SETTINGS_HEADER = HeaderNames.create("HTTP2-Settings");

    // todo requires handling of timeouts and removal from this queue
    private final Map<ClientConnection, Http2ClientConnection> h2ConnByConn =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private final Map<Http2ClientConnection, ClientConnectionTarget> allConnections =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Http2ClientConnection> pendingUpgradedConnections =
            Collections.newSetFromMap(new IdentityHashMap<>());
    // Creation ordered and compared by identity. Mutated only while holding lifecycleLock.
    private final List<Http2ClientConnection> selectableConnections = new ArrayList<>();
    private final AtomicReference<Http2ClientConnection> activeConnection = new AtomicReference<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final AtomicReference<Result> result = new AtomicReference<>(Result.UNKNOWN);
    private final int connectionCacheSize;
    private boolean closed;
    private boolean retired;
    private boolean connectionsRetired;
    private int leases;

    Http2ClientConnectionHandler(int connectionCacheSize) {
        if (connectionCacheSize < 1) {
            throw new IllegalArgumentException("Connection cache size must be greater than zero");
        }
        this.connectionCacheSize = connectionCacheSize;
    }

    boolean acquire() {
        lifecycleLock.lock();
        try {
            if (closed || retired) {
                return false;
            }
            leases++;
            return true;
        } finally {
            lifecycleLock.unlock();
        }
    }

    void release() {
        boolean retireConnections;
        lifecycleLock.lock();
        try {
            if (leases == 0) {
                throw new IllegalStateException("HTTP/2 handler lease underflow");
            }
            leases--;
            retireConnections = retired && leases == 0 && !closed && !connectionsRetired;
            if (retireConnections) {
                connectionsRetired = true;
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (retireConnections) {
            retireConnections();
        }
    }

    void retire() {
        boolean retireConnections;
        lifecycleLock.lock();
        try {
            if (closed || retired) {
                return;
            }
            retired = true;
            retireConnections = leases == 0 && !connectionsRetired;
            if (retireConnections) {
                connectionsRetired = true;
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (retireConnections) {
            retireConnections();
        }
    }

    void close() {
        // this is to prevent concurrent modification (connections remove themselves from the map)
        Set<Http2ClientConnection> toClose = new HashSet<>();
        lifecycleLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            toClose.addAll(pendingUpgradedConnections);
            toClose.addAll(allConnections.keySet());
            pendingUpgradedConnections.clear();
            allConnections.clear();
            h2ConnByConn.clear();
            selectableConnections.clear();
            Http2ClientConnection active = activeConnection.getAndSet(null);
            if (active != null) {
                toClose.add(active);
            }
        } finally {
            lifecycleLock.unlock();
        }
        toClose.forEach(Http2ClientConnection::close);
    }

    Http2ConnectionAttemptResult newStream(Http2ClientImpl http2Client,
                                           ClientConnectionTarget requestTarget,
                                           Http2ClientRequestImpl request,
                                           ClientUri initialUri,
                                           WebClientServiceRequest serviceRequest,
                                           Http1FallbackHandler http1FallbackHandler) {
        try {
            Optional<ClientConnection> maybeConnection = request.connection();
            if (maybeConnection.isPresent()) {
                return explicitConnection(http2Client,
                                          requestTarget,
                                          request,
                                          initialUri,
                                          serviceRequest,
                                          http1FallbackHandler,
                                          maybeConnection.get());
            }
            if (requestTarget.proxyRoute().forwardProxy() && !request.priorKnowledge()) {
                if (!http1FallbackAllowed(request)) {
                    throw unsupportedHttp1Fallback(initialUri, request, http1FallbackHandler);
                }
                result.set(Result.HTTP_1);
                return http1(http2Client, requestTarget, request, initialUri, serviceRequest, http1FallbackHandler);
            }

            return switch (result.get()) {
                case HTTP_1 -> {
                    if (!http1FallbackAllowed(request)) {
                        throw unsupportedHttp1Fallback(initialUri, request, http1FallbackHandler);
                    }
                    yield http1(http2Client, requestTarget, request, initialUri, serviceRequest, http1FallbackHandler);
                }
                case HTTP_2 -> http2(http2Client, requestTarget, request, initialUri, serviceRequest, http1FallbackHandler);
                case UNKNOWN -> httpX(http2Client,
                                      requestTarget,
                                      request,
                                      initialUri,
                                      serviceRequest,
                                      http1FallbackHandler);
            };
        } catch (RuntimeException | Error e) {
            http1FallbackHandler.completeSentExceptionally(e);
            throw e;
        }
    }

    Http2ConnectionAttemptResult reuseStream(Http2ClientImpl http2Client,
                                             Http2ClientRequestImpl request) {
        if (result.get() != Result.HTTP_2) {
            return null;
        }
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
        try {
            ExistingStream existingStream = existingStream(http2Client, request);
            if (existingStream == null) {
                return null;
            }
            return new Http2ConnectionAttemptResult(Result.HTTP_2,
                                                    existingStream.stream(),
                                                    null,
                                                    this,
                                                    existingStream.connectionTarget());
        } finally {
            lock.unlock();
        }
    }

    Http2ConnectionAttemptResult http2(Http2ClientImpl http2Client,
                                       ClientConnectionTarget requestTarget,
                                       Http2ClientRequestImpl request,
                                       ClientUri initialUri,
                                       WebClientServiceRequest serviceRequest,
                                       Http1FallbackHandler http1FallbackHandler) {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
        try {
            // read/write lock to obtain a stream or create a new connection
            ExistingStream existingStream = existingStream(http2Client, request);
            if (existingStream == null) {
                Http2ClientConnection connection;
                try {
                    connection = createConnection(http2Client,
                                                  requestTarget,
                                                  request,
                                                  initialUri,
                                                  serviceRequest,
                                                  http1FallbackHandler);
                } catch (Http1FallbackResponse e) {
                    http1FallbackHandler.completeSent(serviceRequest);
                    return new Http2ConnectionAttemptResult(Result.HTTP_1,
                                                            null,
                                                            e.response(),
                                                            this,
                                                            requestTarget);
                }
                result.set(Result.HTTP_2);
                // we must assume that a new connection can handle a new stream
                Http2ClientStream stream = createStreamOnNewConnection(http2Client, connection, request);
                return new Http2ConnectionAttemptResult(Result.HTTP_2, stream, null, this, requestTarget);
            }

            return new Http2ConnectionAttemptResult(Result.HTTP_2,
                                                    existingStream.stream(),
                                                    null,
                                                    this,
                                                    existingStream.connectionTarget());
        } finally {
            lock.unlock();
        }
    }

    private ExistingStream existingStream(Http2ClientImpl http2Client, Http2ClientRequestImpl request) {
        Http2ClientProtocolConfig protocolConfig = http2Client.protocolConfig();
        Http2ClientConnection connection = activeConnection.get();
        if (connection != null) {
            ClientConnectionTarget connectionTarget = allConnections.get(connection);
            if (connectionTarget != null) {
                if (connection.closed(protocolConfig)) {
                    discardConnection(connection);
                } else {
                    Http2ClientStream stream = connection.tryStream(request,
                                                                    http2Client.clientConfig(),
                                                                    http2Client.sendListener(),
                                                                    http2Client.recvListener());
                    if (stream != null) {
                        return new ExistingStream(stream, connectionTarget);
                    }
                }
            }
        }

        List<Http2ClientConnection> connections;
        lifecycleLock.lock();
        try {
            connections = List.copyOf(selectableConnections);
        } finally {
            lifecycleLock.unlock();
        }
        for (Http2ClientConnection candidate : connections) {
            if (candidate == connection) {
                continue;
            }
            ClientConnectionTarget connectionTarget = allConnections.get(candidate);
            if (connectionTarget == null) {
                continue;
            }
            if (candidate.closed(protocolConfig)) {
                discardConnection(candidate);
                continue;
            }
            Http2ClientStream stream = candidate.tryStream(request,
                                                           http2Client.clientConfig(),
                                                           http2Client.sendListener(),
                                                           http2Client.recvListener());
            if (stream != null) {
                lifecycleLock.lock();
                try {
                    for (Http2ClientConnection selectableConnection : selectableConnections) {
                        if (selectableConnection == candidate) {
                            activeConnection.set(candidate);
                            break;
                        }
                    }
                } finally {
                    lifecycleLock.unlock();
                }
                return new ExistingStream(stream, connectionTarget);
            }
        }
        return null;
    }

    private Http2ConnectionAttemptResult httpX(Http2ClientImpl http2Client,
                                               ClientConnectionTarget requestTarget,
                                               Http2ClientRequestImpl request,
                                               ClientUri initialUri,
                                               WebClientServiceRequest serviceRequest,
                                               Http1FallbackHandler http1FallbackHandler) {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
        try {
            WebClient webClient = http2Client.webClient();
            if (request.tls().enabled() && "https".equals(initialUri.scheme())) {
                // use ALPN, not upgrade
                List<String> alpn = alpnProtocolIds(request);
                ClientConnection clientConnection = connectClient(webClient, requestTarget, request, initialUri, alpn);
                if (clientConnection.helidonSocket().protocolNegotiated()) {
                    if (Http2Client.PROTOCOL_ID.equals(clientConnection.helidonSocket().protocol())) {
                        result.set(Result.HTTP_2);
                        // this should always be true
                        Http2ClientConnection connection = createHttp2Connection(http2Client,
                                                                                 clientConnection,
                                                                                 true);
                        activateConnection(requestTarget, clientConnection, connection);
                        return http2(http2Client,
                                     requestTarget,
                                     request,
                                     initialUri,
                                     serviceRequest,
                                     http1FallbackHandler);
                    } else {
                        if (!http1FallbackAllowed(request)) {
                            closeClientConnection(clientConnection);
                            throw unsupportedHttp1Fallback(initialUri, request, http1FallbackHandler);
                        }
                        result.set(Result.HTTP_1);
                        return http1WithProbeConnection(http2Client,
                                                        requestTarget,
                                                        request,
                                                        initialUri,
                                                        serviceRequest,
                                                        http1FallbackHandler,
                                                        clientConnection);
                    }
                } else {
                    if (!request.priorKnowledge()) {
                        if (!http1FallbackAllowed(request)) {
                            closeClientConnection(clientConnection);
                            throw unsupportedHttp1Fallback(initialUri, request, http1FallbackHandler);
                        }
                        result.set(Result.HTTP_1);
                        return http1WithProbeConnection(http2Client,
                                                        requestTarget,
                                                        request,
                                                        initialUri,
                                                        serviceRequest,
                                                        http1FallbackHandler,
                                                        clientConnection);
                    }
                    result.set(Result.HTTP_2);
                    Http2ClientConnection connection = createHttp2Connection(http2Client,
                                                                             clientConnection,
                                                                             true);
                    activateConnection(requestTarget, clientConnection, connection);
                    return http2(http2Client,
                                 requestTarget,
                                 request,
                                 initialUri,
                                 serviceRequest,
                                 http1FallbackHandler);
                }
            }

            if (result.get() != Result.UNKNOWN) {
                return http2(http2Client,
                             requestTarget,
                             request,
                             initialUri,
                             serviceRequest,
                             http1FallbackHandler);
            }
            // we need to connect
            if (request.priorKnowledge()) {
                // there is no fallback to HTTP/1 with prior knowledge - it must work or fail
                return http2(http2Client,
                             requestTarget,
                             request,
                             initialUri,
                             serviceRequest,
                             http1FallbackHandler);
            }
            // attempt an upgrade to HTTP/2
            UpgradeResponse upgradeResponse = upgrade(http2Client,
                                                      requestTarget,
                                                      request,
                                                      initialUri,
                                                      serviceRequest,
                                                      http1FallbackHandler,
                                                      http2Client.protocolConfig());
            if (upgradeResponse.isUpgraded()) {
                result.set(Result.HTTP_2);
                ClientConnection connection = upgradeResponse.connection();
                Http2ClientConnection conn = createUpgradedHttp2Connection(http2Client, connection);
                activateConnection(requestTarget, connection, conn);
                return http2(http2Client,
                             requestTarget,
                             request,
                             initialUri,
                             serviceRequest,
                             http1FallbackHandler);
            } else {
                HttpClientResponse response = upgradeResponse.response();
                if (request.followRedirects() && RedirectionProcessor.redirectionStatusCode(response.status())) {
                    // Surface redirect responses instead of treating them as unexpected upgrade failures.
                    http1FallbackHandler.completeSent(serviceRequest);
                    return new Http2ConnectionAttemptResult(Result.UNKNOWN,
                                                            null,
                                                            response,
                                                            this,
                                                            requestTarget);
                }
                if (!http1FallbackHandler.upgradeFailureResponseAllowed()) {
                    try (response) {
                        IllegalStateException failure = unsupportedUpgradeFallback(request, response);
                        http1FallbackHandler.completeSentExceptionally(failure);
                        throw failure;
                    }
                }
                result.set(Result.HTTP_1);
                http1FallbackHandler.completeSent(serviceRequest);
                return new Http2ConnectionAttemptResult(Result.HTTP_1,
                                                        null,
                                                        response,
                                                        this,
                                                        requestTarget);
            }
        } finally {
            lock.unlock();
        }
    }

    private Http2ConnectionAttemptResult http1WithProbeConnection(Http2ClientImpl http2Client,
                                                                  ClientConnectionTarget requestTarget,
                                                                  Http2ClientRequestImpl request,
                                                                  ClientUri initialUri,
                                                                  WebClientServiceRequest serviceRequest,
                                                                  Http1FallbackHandler http1FallbackHandler,
                                                                  ClientConnection clientConnection) {
        request.connection(clientConnection);
        try {
            return http1(http2Client, requestTarget, request, initialUri, serviceRequest, http1FallbackHandler);
        } catch (RuntimeException | Error e) {
            closeClientConnection(clientConnection);
            throw e;
        }
    }

    private UpgradeResponse upgrade(Http2ClientImpl http2Client,
                                    ClientConnectionTarget requestTarget,
                                    Http2ClientRequestImpl request,
                                    ClientUri requestUri,
                                    WebClientServiceRequest serviceRequest,
                                    Http1FallbackHandler http1FallbackHandler,
                                    Http2ClientProtocolConfig protocolConfig) {
        try {
            Http1ClientRequest upgradeRequest = http1Request(http2Client.http1FallbackClient(),
                                                             requestTarget,
                                                             request,
                                                             requestUri);
            Http1FallbackHandler.copyFinalHeaders(upgradeRequest, serviceRequest);
            UpgradeResponse upgradeResponse = upgradeRequest.header(UPGRADE_HEADER)
                    .header(CONNECTION_UPGRADE_HEADER)
                    .header(HTTP2_SETTINGS_HEADER, settingsForUpgrade(protocolConfig))
                    .followRedirects(false)
                    .upgrade("h2c");
            return upgradeResponse;
        } catch (RuntimeException | Error e) {
            http1FallbackHandler.completeSentExceptionally(e);
            throw e;
        }
    }

    private Http2ConnectionAttemptResult explicitConnection(Http2ClientImpl http2Client,
                                                            ClientConnectionTarget requestTarget,
                                                            Http2ClientRequestImpl request,
                                                            ClientUri initialUri,
                                                            WebClientServiceRequest serviceRequest,
                                                            Http1FallbackHandler http1FallbackHandler,
                                                            ClientConnection clientConnection) {
        if (clientConnection.helidonSocket().protocolNegotiated()
                && !Http2Client.PROTOCOL_ID.equals(clientConnection.helidonSocket().protocol())) {
            if (!http1FallbackAllowed(request)) {
                throw unsupportedHttp1Fallback(initialUri, request, http1FallbackHandler);
            }
            return http1(http2Client,
                         requestTarget,
                         request,
                         initialUri,
                         serviceRequest,
                         http1FallbackHandler);
        }
        return http2ExplicitConnection(http2Client, requestTarget, request, clientConnection);
    }

    private Http2ConnectionAttemptResult http2ExplicitConnection(Http2ClientImpl http2Client,
                                                                 ClientConnectionTarget requestTarget,
                                                                 Http2ClientRequestImpl request,
                                                                 ClientConnection clientConnection) {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
        try {
            boolean ownsExplicitConnection = ownsExplicitConnection(request);
            Http2ClientConnection connection = ownsExplicitConnection ? h2ConnByConn.get(clientConnection) : null;
            if (connection != null && connection.closed(http2Client.protocolConfig())) {
                removeConnection(connection);
                connection = null;
            }
            if (connection == null) {
                try {
                    connection = createHttp2Connection(http2Client, clientConnection, true);
                } catch (RuntimeException | Error e) {
                    if (ownsExplicitConnection) {
                        closeClientConnection(clientConnection);
                    }
                    throw e;
                }
            }
            if (ownsExplicitConnection) {
                result.set(Result.HTTP_2);
                activateConnection(requestTarget, clientConnection, connection);
            }

            return new Http2ConnectionAttemptResult(Result.HTTP_2,
                                                    connection.createStream(request,
                                                                            http2Client.clientConfig(),
                                                                            http2Client.sendListener(),
                                                                            http2Client.recvListener()),
                                                    null,
                                                    this,
                                                    requestTarget);
        } finally {
            lock.unlock();
        }
    }

    static boolean ownsExplicitConnection(Http2ClientRequestImpl request) {
        return request.ownsExplicitConnection();
    }

    private String settingsForUpgrade(Http2ClientProtocolConfig protocolConfig) {
        Http2Settings settings = Http2ClientConnection.settings(protocolConfig);
        BufferData settingsFrameData = settings.toFrameData(null, 0, Http2Flag.SettingsFlags.create(0))
                .data();
        byte[] b = new byte[settingsFrameData.available()];
        settingsFrameData.read(b);
        return Base64.getUrlEncoder().encodeToString(b);
    }

    private Http2ConnectionAttemptResult http1(Http2ClientImpl http2Client,
                                               ClientConnectionTarget requestTarget,
                                               Http2ClientRequestImpl request,
                                               ClientUri initialUri,
                                               WebClientServiceRequest serviceRequest,
                                               Http1FallbackHandler http1FallbackHandler) {
        try {
            Http1ClientRequest http1Request = http1Request(http2Client.http1FallbackClient(),
                                                           requestTarget,
                                                           request,
                                                           initialUri);
            return new Http2ConnectionAttemptResult(Result.HTTP_1,
                                                    null,
                                                    http1FallbackHandler.apply(http1Request, serviceRequest),
                                                    this,
                                                    requestTarget);
        } catch (RuntimeException | Error e) {
            http1FallbackHandler.completeSentExceptionally(e);
            throw e;
        }
    }

    private Http1ClientRequest http1Request(Http1Client http1Client,
                                           ClientConnectionTarget requestTarget,
                                           Http2ClientRequestImpl request,
                                           ClientUri initialUri) {
        Http1ClientRequest http1Request = http1Client.method(request.method())
                .uri(initialUri)
                .keepAlive(request.keepAlive())
                .headers(request.headers())
                .skipUriEncoding(request.skipUriEncoding())
                .tls(request.tls())
                .readTimeout(request.readTimeout())
                .readContinueTimeout(request.readContinueTimeout())
                .proxy(request.proxy())
                .maxRedirects(request.maxRedirects())
                .followRedirects(request.followRedirects());
        request.connection().ifPresent(http1Request::connection);
        request.address().ifPresent(http1Request::address);
        request.sni().ifPresent(http1Request::sni);
        request.sendExpectContinue().ifPresent(http1Request::sendExpectContinue);
        // This is a manual HTTP/2-to-HTTP/1 request copy used for h2c probing/fallback. Properties carry internal
        // redirect state, including whether a previous redirect already crossed an origin boundary.
        request.properties().forEach(http1Request::property);
        if (requestTarget.transportAddress().isEmpty()
                && http1Request instanceof FullClientRequest<?> fullClientRequest) {
            fullClientRequest.selectedProxyRoute(requestTarget.proxyRoute());
        }
        return http1Request;
    }

    private Http2ClientConnection createConnection(Http2ClientImpl http2Client,
                                                   ClientConnectionTarget requestTarget,
                                                   Http2ClientRequestImpl request,
                                                   ClientUri requestUri,
                                                   WebClientServiceRequest serviceRequest,
                                                   Http1FallbackHandler http1FallbackHandler) {
        WebClient webClient = http2Client.webClient();
        Http2ClientProtocolConfig protocolConfig = http2Client.protocolConfig();
        Optional<ClientConnection> maybeConnection = request.connection();
        Http2ClientConnection usedConnection;

        if (maybeConnection.isPresent()) {
            // TLS is ignored (we cannot do a TLS negotiation on a connected connection)
            // we cannot cache this connection, it will be a one-off
            usedConnection = createHttp2Connection(http2Client, maybeConnection.get(), true);
        } else {
            ClientConnection connection;

            // we know that this is HTTP/2 capable server - still need to support all three (prior, upgrade, alpn)
            if (request.tls().enabled() && "https".equals(requestUri.scheme())) {
                connection = connectClient(webClient,
                                           requestTarget,
                                           request,
                                           requestUri,
                                           List.of(Http2Client.PROTOCOL_ID));
                usedConnection = createHttp2Connection(http2Client, connection, true);
            } else {
                if (request.priorKnowledge()) {
                    connection = connectClient(webClient,
                                               requestTarget,
                                               request,
                                               requestUri,
                                               List.of(Http2Client.PROTOCOL_ID));
                    usedConnection = createHttp2Connection(http2Client, connection, true);
                } else {
                    // attempt an upgrade to HTTP/2
                    UpgradeResponse upgradeResponse = upgrade(http2Client,
                                                              requestTarget,
                                                              request,
                                                              requestUri,
                                                              serviceRequest,
                                                              http1FallbackHandler,
                                                              protocolConfig);
                    if (upgradeResponse.isUpgraded()) {
                        result.set(Result.HTTP_2);
                        connection = upgradeResponse.connection();
                        usedConnection = createUpgradedHttp2Connection(http2Client, connection);
                    } else {
                        HttpClientResponse response = upgradeResponse.response();
                        if (request.followRedirects() && RedirectionProcessor.redirectionStatusCode(response.status())) {
                            // Surface redirect responses instead of treating them as unexpected upgrade failures.
                            throw new Http1FallbackResponse(response);
                        }
                        try (response) {
                            if (LOGGER.isLoggable(TRACE)) {
                                LOGGER.log(TRACE, "Failed to upgrade to HTTP/2");
                            }
                            IllegalStateException failure = new IllegalStateException(
                                    "Failed to upgrade to HTTP/2, even though it succeeded before. Status: "
                                            + response.status());
                            http1FallbackHandler.completeSentExceptionally(failure);
                            throw failure;
                        }
                    }
                }
            }

            // only set these for requests that do not have an explicit connection defined
            activateConnection(requestTarget, connection, usedConnection);
        }

        return usedConnection;
    }

    private Http2ClientStream createStreamOnNewConnection(Http2ClientImpl http2Client,
                                                          Http2ClientConnection connection,
                                                          Http2ClientRequestImpl request) {
        try {
            return connection.createStream(request,
                                           http2Client.clientConfig(),
                                           http2Client.sendListener(),
                                           http2Client.recvListener());
        } catch (RuntimeException e) {
            discardConnection(connection);
            throw e;
        }
    }

    private void discardConnection(Http2ClientConnection connection) {
        removeConnection(connection);
        connection.close();
    }

    private Http2ClientConnection createHttp2Connection(Http2ClientImpl http2Client,
                                                        ClientConnection clientConnection,
                                                        boolean sendSettings) {
        return Http2ClientConnection.create(http2Client, clientConnection, sendSettings, this::removeConnection);
    }

    private Http2ClientConnection createUpgradedHttp2Connection(Http2ClientImpl http2Client,
                                                                ClientConnection clientConnection) {
        return Http2ClientConnection.createUpgraded(http2Client,
                                                    clientConnection,
                                                    this::removeConnection,
                                                    this::registerPendingUpgradedConnection);
    }

    private void registerPendingUpgradedConnection(Http2ClientConnection connection) {
        lifecycleLock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("HTTP/2 connection handler is closed");
            }
            pendingUpgradedConnections.add(connection);
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void activateConnection(ClientConnectionTarget connectionTarget,
                                    ClientConnection clientConnection,
                                    Http2ClientConnection connection) {
        Http2ClientConnection displacedConnection = null;
        boolean closeConnection;
        lifecycleLock.lock();
        try {
            pendingUpgradedConnections.remove(connection);
            closeConnection = closed;
            if (!closeConnection) {
                allConnections.put(connection, connectionTarget);
                h2ConnByConn.put(clientConnection, connection);
                boolean alreadySelectable = false;
                for (Http2ClientConnection selectableConnection : selectableConnections) {
                    if (selectableConnection == connection) {
                        alreadySelectable = true;
                        break;
                    }
                }
                if (!alreadySelectable) {
                    selectableConnections.add(connection);
                }
                activeConnection.set(connection);
                if (selectableConnections.size() > connectionCacheSize) {
                    displacedConnection = selectableConnections.getFirst();
                    removeSelectableConnection(displacedConnection);
                }
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (closeConnection) {
            connection.close();
            throw new IllegalStateException("HTTP/2 connection handler is closed");
        }
        if (displacedConnection != null) {
            displacedConnection.retire();
        }
    }

    private void removeConnection(Http2ClientConnection connection) {
        lifecycleLock.lock();
        try {
            pendingUpgradedConnections.remove(connection);
            allConnections.remove(connection);
            removeSelectableConnection(connection);
        } finally {
            lifecycleLock.unlock();
        }
    }

    // Caller must hold lifecycleLock.
    private void removeSelectableConnection(Http2ClientConnection connection) {
        h2ConnByConn.values().removeIf(it -> it == connection);
        selectableConnections.removeIf(it -> it == connection);
        activeConnection.compareAndSet(connection, null);
    }

    private void retireConnections() {
        Set<Http2ClientConnection> toRetire = new HashSet<>();
        lifecycleLock.lock();
        try {
            toRetire.addAll(pendingUpgradedConnections);
            toRetire.addAll(allConnections.keySet());
            Http2ClientConnection active = activeConnection.get();
            if (active != null) {
                toRetire.add(active);
            }
        } finally {
            lifecycleLock.unlock();
        }
        toRetire.forEach(Http2ClientConnection::retire);
    }

    private static void closeClientConnection(ClientConnection clientConnection) {
        try {
            clientConnection.closeResource();
        } catch (RuntimeException e) {
            LOGGER.log(DEBUG, "Failed to close internally created HTTP/2 probe connection", e);
        }
    }

    private static boolean http1FallbackAllowed(Http2ClientRequestImpl request) {
        return request.tcpProtocolIds().contains(Http1Client.PROTOCOL_ID);
    }

    private static IllegalArgumentException unsupportedHttp1Fallback(ClientUri uri, Http2ClientRequestImpl request) {
        return new IllegalArgumentException("Cannot handle request to " + uri
                                                   + ", negotiated HTTP/1.1 fallback is not enabled. HTTP versions supported: "
                                                   + request.tcpProtocolIds());
    }

    private static IllegalArgumentException unsupportedHttp1Fallback(ClientUri uri,
                                                                     Http2ClientRequestImpl request,
                                                                     Http1FallbackHandler http1FallbackHandler) {
        IllegalArgumentException failure = unsupportedHttp1Fallback(uri, request);
        http1FallbackHandler.completeSentExceptionally(failure);
        return failure;
    }

    private static IllegalStateException unsupportedUpgradeFallback(Http2ClientRequestImpl request,
                                                                    HttpClientResponse response) {
        return new IllegalStateException("Cannot use failed h2c upgrade response as HTTP/1.1 fallback for "
                                                 + request.method()
                                                 + " request with an entity. Status: "
                                                 + response.status());
    }

    private static List<String> alpnProtocolIds(Http2ClientRequestImpl request) {
        return request.priorKnowledge() ? List.of(Http2Client.PROTOCOL_ID) : request.tcpProtocolIds();
    }

    private ClientConnection connectClient(WebClient webClient,
                                           ClientConnectionTarget requestTarget,
                                           Http2ClientRequestImpl request,
                                           ClientUri uri,
                                           List<String> alpn) {
        var address = request.address();
        if (address.isPresent() && address.get() instanceof UnixDomainSocketAddress) {
            return UnixDomainSocketClientConnection.create(webClient,
                                                          requestTarget,
                                                          alpn,
                                                          connection -> false,
                                                          this::removeClientConnection)
                    .connect();
        }
        return TcpClientConnection.create(webClient,
                                          requestTarget,
                                          alpn,
                                          connection -> false,
                                          this::removeClientConnection)
                .connect();
    }

    private void removeClientConnection(ClientConnection clientConnection) {
        lifecycleLock.lock();
        try {
            Http2ClientConnection connection = h2ConnByConn.remove(clientConnection);
            if (connection != null) {
                pendingUpgradedConnections.remove(connection);
                allConnections.remove(connection);
                removeSelectableConnection(connection);
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private record ExistingStream(Http2ClientStream stream, ClientConnectionTarget connectionTarget) {
    }

    private static class Http1FallbackResponse extends RuntimeException {
        private final HttpClientResponse response;

        private Http1FallbackResponse(HttpClientResponse response) {
            this.response = response;
        }

        private HttpClientResponse response() {
            return response;
        }
    }
}
