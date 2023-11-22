/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import java.util.function.Function;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2Settings;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.http1.UpgradeResponse;
import io.helidon.webclient.http2.Http2ConnectionAttemptResult.Result;

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

    private final Map<Http2ClientConnection, Boolean> allConnections = Collections.synchronizedMap(new IdentityHashMap<>());
    private final ConnectionKey connectionKey;
    private final AtomicReference<Http2ClientConnection> activeConnection = new AtomicReference<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<Result> result = new AtomicReference<>(Result.UNKNOWN);

    Http2ClientConnectionHandler(ConnectionKey connectionKey) {
        this.connectionKey = connectionKey;
    }

    void close() {
        // this is to prevent concurrent modification (connections remove themselves from the map)
        Set<Http2ClientConnection> toClose = new HashSet<>(allConnections.keySet());
        toClose.forEach(Http2ClientConnection::close);
        Http2ClientConnection active = this.activeConnection.getAndSet(null);
        if (active != null) {
            active.close();
        }
        this.allConnections.clear();
    }

    Http2ConnectionAttemptResult newStream(Http2ClientImpl http2Client,
                                           Http2ClientRequestImpl request,
                                           ClientUri initialUri,
                                           Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler) {

        return switch (result.get()) {
            case HTTP_1 -> http1(http2Client, request, initialUri, http1EntityHandler);
            case HTTP_2 -> http2(http2Client, request, initialUri);
            case UNKNOWN -> httpX(http2Client, request, initialUri, http1EntityHandler);
        };
    }

    Http2ConnectionAttemptResult http2(Http2ClientImpl http2Client,
                                       Http2ClientRequestImpl request,
                                       ClientUri initialUri) {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
        try {
            // read/write lock to obtain a stream or create a new connection
            Http2ClientConnection conn = activeConnection.updateAndGet(c -> c != null && c.closed() ? null : c);
            Http2ClientStream stream;
            if (conn == null) {
                conn = createConnection(http2Client, request, initialUri);
                // we must assume that a new connection can handle a new stream
                stream = conn.createStream(request);
            } else {
                stream = conn.tryStream(request);
                if (stream == null) {
                    // either the connection is closed, or it ran out of streams
                    conn = createConnection(http2Client, request, initialUri);
                    stream = conn.createStream(request);
                }
            }

            return new Http2ConnectionAttemptResult(Result.HTTP_2, stream, null);
        } finally {
            lock.unlock();
        }
    }

    private Http2ConnectionAttemptResult httpX(Http2ClientImpl http2Client,
                                               Http2ClientRequestImpl request,
                                               ClientUri initialUri,
                                               Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler) {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
        try {
            WebClient webClient = http2Client.webClient();
            if (request.tls().enabled() && "https".equals(initialUri.scheme())) {
                // use ALPN, not upgrade, if prior, only h2, otherwise both
                List<String> alpn;
                if (request.priorKnowledge()) {
                    alpn = List.of(Http2Client.PROTOCOL_ID);
                } else {
                    alpn = List.of(Http2Client.PROTOCOL_ID, Http1Client.PROTOCOL_ID);
                }
                ClientConnection tcpClientConnection = connectClient(webClient, alpn);
                if (tcpClientConnection.helidonSocket().protocolNegotiated()) {
                    if (Http2Client.PROTOCOL_ID.equals(tcpClientConnection.helidonSocket().protocol())) {
                        result.set(Result.HTTP_2);
                        // this should always be true
                        Http2ClientConnection connection = Http2ClientConnection.create(http2Client,
                                                                                        tcpClientConnection,
                                                                                        true);
                        allConnections.put(connection, true);
                        h2ConnByConn.put(tcpClientConnection, connection);
                        this.activeConnection.set(connection);
                        return http2(http2Client, request, initialUri);
                    } else {
                        result.set(Result.HTTP_1);
                        request.connection(tcpClientConnection);
                        return http1(http2Client, request, initialUri, http1EntityHandler);
                    }
                } else {
                    // this should not really happen, as H2 is depending on ALPN, but let's support it anyway, and hope we can
                    // do this later
                    request.connection(tcpClientConnection);
                }
            }

            if (result.get() != Result.UNKNOWN) {
                return http2(http2Client, request, initialUri);
            }
            // we need to connect
            if (request.priorKnowledge()) {
                // there is no fallback to HTTP/1 with prior knowledge - it must work or fail
                return http2(http2Client, request, initialUri);
            }
            // attempt an upgrade to HTTP/2
            UpgradeResponse upgradeResponse = http1Request(webClient, request, initialUri)
                    .header(UPGRADE_HEADER)
                    .header(CONNECTION_UPGRADE_HEADER)
                    .header(HTTP2_SETTINGS_HEADER, settingsForUpgrade(http2Client.protocolConfig()))
                    .upgrade("h2c");
            if (upgradeResponse.isUpgraded()) {
                result.set(Result.HTTP_2);
                Http2ClientConnection conn = Http2ClientConnection.create(http2Client,
                                                                          upgradeResponse.connection(),
                                                                          false);
                activeConnection.set(conn);
                return http2(http2Client, request, initialUri);
            } else {
                result.set(Result.HTTP_1);
                return new Http2ConnectionAttemptResult(Result.HTTP_1,
                                                        null,
                                                        upgradeResponse.response());
            }
        } finally {
            lock.unlock();
        }
    }

    private String settingsForUpgrade(Http2ClientProtocolConfig protocolConfig) {
        Http2Settings settings = Http2ClientConnection.settings(protocolConfig);
        BufferData settingsFrameData = settings.toFrameData(null, 0, Http2Flag.SettingsFlags.create(0))
                .data();
        byte[] b = new byte[settingsFrameData.available()];
        settingsFrameData.read(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private Http2ConnectionAttemptResult http1(Http2ClientImpl http2Client,
                                               Http2ClientRequestImpl request, ClientUri initialUri,
                                               Function<Http1ClientRequest, Http1ClientResponse> http1EntityHandler) {
        return new Http2ConnectionAttemptResult(Result.HTTP_1,
                                                null,
                                                http1EntityHandler.apply(http1Request(
                                                        http2Client.webClient(),
                                                        request,
                                                        initialUri)));
    }

    private Http1ClientRequest http1Request(WebClient webClient, Http2ClientRequestImpl request, ClientUri initialUri) {
        return webClient.client(Http1Client.PROTOCOL)
                .method(request.method())
                .uri(initialUri)
                .keepAlive(request.keepAlive())
                .headers(request.headers())
                .skipUriEncoding(request.skipUriEncoding())
                .tls(request.tls())
                .readTimeout(request.readTimeout())
                .proxy(request.proxy())
                .maxRedirects(request.maxRedirects())
                .followRedirects(request.followRedirects());
    }

    private Http2ClientConnection createConnection(Http2ClientImpl http2Client,
                                                   Http2ClientRequestImpl request,
                                                   ClientUri requestUri) {
        WebClient webClient = http2Client.webClient();
        Http2ClientProtocolConfig protocolConfig = http2Client.protocolConfig();
        Optional<ClientConnection> maybeConnection = request.connection();
        Http2ClientConnection usedConnection;

        if (maybeConnection.isPresent()) {
            // TLS is ignored (we cannot do a TLS negotiation on a connected connection)
            // we cannot cache this connection, it will be a one-off
            usedConnection = Http2ClientConnection.create(http2Client, maybeConnection.get(), true);
        } else {
            ClientConnection connection;

            // we know that this is HTTP/2 capable server - still need to support all three (prior, upgrade, alpn)
            if (request.tls().enabled() && "https".equals(requestUri.scheme())) {
                connection = connectClient(webClient, List.of(Http2Client.PROTOCOL_ID));
                usedConnection = Http2ClientConnection.create(http2Client, connection, true);
            } else {
                if (request.priorKnowledge()) {
                    connection = connectClient(webClient, List.of(Http2Client.PROTOCOL_ID));
                    usedConnection = Http2ClientConnection.create(http2Client, connection, true);
                } else {
                    // attempt an upgrade to HTTP/2
                    UpgradeResponse upgradeResponse = http1Request(webClient, request, requestUri)
                            .header(UPGRADE_HEADER)
                            .header(CONNECTION_UPGRADE_HEADER)
                            .header(HTTP2_SETTINGS_HEADER, settingsForUpgrade(protocolConfig))
                            .upgrade("h2c");
                    if (upgradeResponse.isUpgraded()) {
                        result.set(Result.HTTP_2);
                        connection = upgradeResponse.connection();
                        usedConnection = Http2ClientConnection.create(http2Client, connection, false);
                    } else {
                        try (HttpClientResponse response = upgradeResponse.response()) {
                            if (LOGGER.isLoggable(TRACE)) {
                                upgradeResponse.connection().helidonSocket()
                                        .log(LOGGER, TRACE, "Failed to upgrade to HTTP/2");
                            }
                            upgradeResponse.connection().closeResource();
                            throw new IllegalStateException(
                                    "Failed to upgrade to HTTP/2, even though it succeeded before. Status: "
                                            + response.status());
                        }
                    }
                }
            }

            // only set these for requests that do not have an explicit connection defined
            activeConnection.set(usedConnection);
            allConnections.put(usedConnection, true);
            h2ConnByConn.put(connection, usedConnection);
        }

        return usedConnection;
    }

    private ClientConnection connectClient(WebClient webClient, List<String> alpn) {
        return TcpClientConnection.create(webClient,
                                          connectionKey,
                                          alpn,
                                          connection -> false,
                                          connection -> {
                                              Http2ClientConnection h2conn = h2ConnByConn.remove(
                                                      connection);
                                              if (h2conn != null) {
                                                  allConnections.remove(h2conn);
                                              }
                                          })
                .connect();
    }
}
