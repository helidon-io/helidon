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

package io.helidon.nima.http2.webclient;

import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.http.Http;
import io.helidon.nima.http2.Http2Flag;
import io.helidon.nima.http2.Http2Settings;
import io.helidon.nima.http2.webclient.Http2ConnectionAttemptResult.Result;
import io.helidon.nima.webclient.api.ClientConnection;
import io.helidon.nima.webclient.api.ClientUri;
import io.helidon.nima.webclient.api.ConnectionKey;
import io.helidon.nima.webclient.api.TcpClientConnection;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;
import io.helidon.nima.webclient.http1.UpgradeResponse;

import static java.lang.System.Logger.Level.TRACE;

// a representation of a single remote endpoint
// this may use one or more connections (depending on parallel streams)
class Http2ClientConnectionHandler {
    private static final System.Logger LOGGER = System.getLogger(Http2ClientConnectionHandler.class.getName());
    private static final Http.HeaderValue CONNECTION_UPGRADE_HEADER = Http.Header.createCached(Http.Header.CONNECTION,
                                                                                               "Upgrade, HTTP2-Settings");
    // h2c stands for HTTP/2 plaintext protocol (only used without TLS)
    private static final Http.HeaderValue UPGRADE_HEADER = Http.Header.createCached(Http.Header.UPGRADE, "h2c");
    private static final Http.HeaderName HTTP2_SETTINGS_HEADER = Http.Header.create("HTTP2-Settings");

    // todo requires handling of timeouts and removal from this queue
    private final Map<ClientConnection, Http2ClientConnection> h2ConnByConn =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Http2ClientConnection> allConnections =
            Collections.synchronizedSet(new IdentityHashMap<Http2ClientConnection, Boolean>().keySet());

    private final ConnectionKey connectionKey;
    private final AtomicReference<Http2ClientConnection> activeConnection = new AtomicReference<>();
    // simple solution for now
    private final Semaphore semaphore = new Semaphore(1);
    private final AtomicReference<Result> result = new AtomicReference<>(Result.UNKNOWN);

    Http2ClientConnectionHandler(ConnectionKey connectionKey) {
        this.connectionKey = connectionKey;
    }

    Http2ConnectionAttemptResult newStream(WebClient webClient,
                                           Http2ClientProtocolConfig protocolConfig,
                                           Http2ClientRequestImpl request,
                                           ClientUri initialUri) {
        return switch (result.get()) {
            case HTTP_1 -> http1(webClient, request, initialUri);
            case HTTP_2 -> http2(webClient, protocolConfig, request, initialUri);
            case UNKNOWN -> maybeNewConnection(webClient, protocolConfig, request, initialUri);
        };
    }

    Http2ConnectionAttemptResult http2(WebClient webClient,
                                       Http2ClientProtocolConfig protocolConfig,
                                       Http2ClientRequestImpl request,
                                       ClientUri initialUri) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
        try {
            // read/write lock to obtain a stream or create a new connection
            Http2ClientConnection conn = activeConnection.get();
            Http2ClientStream stream;
            if (conn == null) {
                conn = createConnection(webClient, protocolConfig, request, initialUri);
                // we must assume that a new connection can handle a new stream
                stream = conn.createStream(request);
            } else {
                stream = conn.tryStream(request);
                if (stream == null) {
                    // either the connection is closed, or it ran out of streams
                    conn = createConnection(webClient, protocolConfig, request, initialUri);
                    stream = conn.createStream(request);
                }
            }

            return new Http2ConnectionAttemptResult(Result.HTTP_2, stream, null);
        } finally {
            semaphore.release();
        }
    }

    private Http2ConnectionAttemptResult maybeNewConnection(WebClient webClient,
                                                            Http2ClientProtocolConfig protocolConfig,
                                                            Http2ClientRequestImpl request,
                                                            ClientUri initialUri) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
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
                    Http2ClientConnection connection = Http2ClientConnection.create(webClient,
                                                                                    protocolConfig,
                                                                                    tcpClientConnection);
                    allConnections.add(connection);
                    h2ConnByConn.put(tcpClientConnection, connection);
                    this.activeConnection.set(connection);
                    return http2(webClient, protocolConfig, request, initialUri);
                } else {
                    result.set(Result.HTTP_1);
                    request.connection(tcpClientConnection);
                    return http1(webClient, request, initialUri);
                }
            } else {
                // this should not really happen, as H2 is depending on ALPN, but let's support it anyway, and hope we can
                // do this later
                request.connection(tcpClientConnection);
            }
        }
        try {
            if (result.get() != Result.UNKNOWN) {
                return http2(webClient, protocolConfig, request, initialUri);
            }
            // we need to connect
            if (request.priorKnowledge()) {
                // there is no fallback to HTTP/1 with prior knowledge - it must work or fail
                return http2(webClient, protocolConfig, request, initialUri);
            }
            // attempt an upgrade to HTTP/2
            UpgradeResponse upgradeResponse = http1Request(webClient, request, initialUri)
                    .header(UPGRADE_HEADER)
                    .header(CONNECTION_UPGRADE_HEADER)
                    .header(HTTP2_SETTINGS_HEADER, settingsForUpgrade())
                    .upgrade("h2c");
            if (upgradeResponse.isUpgraded()) {
                result.set(Result.HTTP_2);
                Http2ClientConnection conn = Http2ClientConnection.create(webClient, protocolConfig, upgradeResponse.connection());
                activeConnection.set(conn);
                return http2(webClient, protocolConfig, request, initialUri);
            } else {
                result.set(Result.HTTP_1);
                return new Http2ConnectionAttemptResult(Result.HTTP_1,
                                                        null,
                                                        upgradeResponse.response());
            }
        } finally {
            semaphore.release();
        }
    }

    private String settingsForUpgrade() {
        BufferData settingsFrameData = Http2Settings.create()
                .toFrameData(null, 0, Http2Flag.SettingsFlags.create(0))
                .data();
        byte[] b = new byte[settingsFrameData.available()];
        settingsFrameData.write(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private Http2ConnectionAttemptResult http1(WebClient webClient, Http2ClientRequestImpl request, ClientUri initialUri) {
        return new Http2ConnectionAttemptResult(Result.HTTP_1, null,
                                                //TODO entity
                                                http1Request(webClient, request, initialUri).request());
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

    private Http2ClientConnection createConnection(WebClient webClient,
                                                   Http2ClientProtocolConfig protocolConfig,
                                                   Http2ClientRequestImpl request,
                                                   ClientUri requestUri) {
        Optional<ClientConnection> maybeConnection = request.connection();
        Http2ClientConnection usedConnection;

        if (maybeConnection.isPresent()) {
            // TLS is ignored (we cannot do a TLS negotiation on a connected connection)
            // we cannot cache this connection, it will be a one-off
            usedConnection = Http2ClientConnection.create(webClient, protocolConfig, maybeConnection.get());
            usedConnection.sendPreface(protocolConfig, true);
        } else {
            ClientConnection connection;

            // we know that this is HTTP/2 capable server - still need to support all three (prior, upgrade, alpn)
            if (request.tls().enabled() && "https".equals(requestUri.scheme())) {
                connection = connectClient(webClient, List.of(Http2Client.PROTOCOL_ID));
                usedConnection = Http2ClientConnection.create(webClient, protocolConfig, connection);
            } else {
                if (request.priorKnowledge()) {
                    connection = connectClient(webClient, List.of(Http2Client.PROTOCOL_ID));
                    usedConnection = Http2ClientConnection.create(webClient, protocolConfig, connection);
                    usedConnection.sendPreface(protocolConfig, true);
                } else {
                    // attempt an upgrade to HTTP/2
                    UpgradeResponse upgradeResponse = http1Request(webClient, request, requestUri)
                            .header(UPGRADE_HEADER)
                            .header(CONNECTION_UPGRADE_HEADER)
                            .header(HTTP2_SETTINGS_HEADER, settingsForUpgrade())
                            .upgrade("h2c");
                    if (upgradeResponse.isUpgraded()) {
                        result.set(Result.HTTP_2);
                        connection = upgradeResponse.connection();
                        usedConnection = Http2ClientConnection.create(webClient, protocolConfig, connection);
                        usedConnection.sendPreface(protocolConfig, false);
                    } else {
                        if (LOGGER.isLoggable(TRACE)) {
                            upgradeResponse.connection().helidonSocket()
                                    .log(LOGGER, TRACE, "Failed to upgrade to HTTP/2");
                        }
                        upgradeResponse.connection().close();
                        throw new IllegalStateException("Failed to upgrade to HTTP/2, even though it succeeded before. Status: "
                                                                + upgradeResponse.response().status());
                    }
                }
            }

            // only set these for requests that do not have an explicit connection defined
            activeConnection.set(usedConnection);
            allConnections.add(usedConnection);
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
