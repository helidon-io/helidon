/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.websocket;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;
import java.util.Set;

import io.helidon.common.LazyValue;
import io.helidon.common.socket.SocketContext;
import io.helidon.common.uri.UriInfo;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;
import io.helidon.webclient.http1.UpgradeResponse;
import io.helidon.websocket.WsListener;

class WsClientImpl implements WsClient {
    /**
     * Supported WebSocket version.
     */
    static final String SUPPORTED_VERSION = "13";
    static final Header HEADER_UPGRADE_WS = HeaderValues.createCached(HeaderNames.UPGRADE, "websocket");
    static final HeaderName HEADER_WS_PROTOCOL = HeaderNames.create("Sec-WebSocket-Protocol");
    private static final Header HEADER_WS_VERSION = HeaderValues.createCached(HeaderNames.create(
            "Sec-WebSocket-Version"), SUPPORTED_VERSION);

    private static final System.Logger LOGGER = System.getLogger(WsClient.class.getName());
    private static final Header HEADER_CONN_UPGRADE = HeaderValues.create(HeaderNames.CONNECTION, "Upgrade");
    private static final HeaderName HEADER_WS_ACCEPT = HeaderNames.create("Sec-WebSocket-Accept");
    private static final HeaderName HEADER_WS_KEY = HeaderNames.create("Sec-WebSocket-Key");
    private static final LazyValue<Random> RANDOM = LazyValue.create(SecureRandom::new);
    private static final byte[] KEY_SUFFIX = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.US_ASCII);
    private static final int KEY_SUFFIX_LENGTH = KEY_SUFFIX.length;
    private static final Base64.Encoder B64_ENCODER = Base64.getEncoder();
    private static final Set<String> SUPPORTED_SCHEMES = Set.of("wss", "ws", "https", "http");
    private final ClientRequestHeaders headers;
    private final WebClient webClient;
    private final Http1Client http1Client;
    private final WsClientConfig clientConfig;

    WsClientImpl(WebClient webClient, Http1Client http1Client, WsClientConfig clientConfig) {
        this.webClient = webClient;
        this.http1Client = http1Client;
        this.clientConfig = clientConfig;

        ClientRequestHeaders headers = http1Client.prototype().defaultRequestHeaders();
        headers.set(HEADER_UPGRADE_WS);
        headers.set(HEADER_CONN_UPGRADE);
        headers.set(HEADER_WS_VERSION);
        headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
        if (clientConfig.protocolConfig().subProtocols().isEmpty()) {
            headers.remove(HEADER_WS_PROTOCOL);
        } else {
            headers.set(HEADER_WS_PROTOCOL, clientConfig.protocolConfig().subProtocols());
        }
        this.headers = headers;
    }

    @Override
    public void connect(URI uri, WsListener listener) {
        // there is no connection pooling, as each connection is upgraded to be a websocket connection

        byte[] nonce = new byte[16];
        RANDOM.get().nextBytes(nonce);
        String secWsKey = B64_ENCODER.encodeToString(nonce);

        Http1ClientRequest upgradeRequest = http1Client.get()
                .headers(headers)
                .header(HEADER_WS_KEY, secWsKey)
                .uri(uri);
        UriInfo resolvedUri = upgradeRequest.resolvedUri();
        String scheme = resolvedUri.scheme();

        if (!SUPPORTED_SCHEMES.contains(scheme)) {
            throw new IllegalArgumentException(
                    String.format("Not supported scheme %s, web socket client supported schemes are: %s",
                                  scheme,
                                  String.join(", ", SUPPORTED_SCHEMES)
                    )
            );
        }

        if ("ws".equals(scheme)) {
            upgradeRequest.uri(ClientUri.create(resolvedUri).scheme("http"));
        } else if ("wss".equals(scheme)) {
            upgradeRequest.uri(ClientUri.create(resolvedUri).scheme("https"));
        }

        upgradeRequest.headers(headers -> headers.setIfAbsent(HeaderValues.create(HeaderNames.HOST, resolvedUri
                .authority())));

        UpgradeResponse upgradeResponse = upgradeRequest.upgrade("websocket");

        if (!upgradeResponse.isUpgraded()) {
            throw new WsClientException("Failed to upgrade to WebSocket. Response: " + upgradeResponse);
        }

        ClientWsConnection session;
        try (HttpClientResponse response = upgradeResponse.response()) {
            ClientResponseHeaders responseHeaders = response.headers();
            if (!responseHeaders.contains(HEADER_CONN_UPGRADE)) {
                throw new WsClientException("Failed to upgrade to WebSocket, expected Connection: Upgrade header. Headers: "
                                                    + responseHeaders);
            }
            if (!responseHeaders.contains(HEADER_UPGRADE_WS)) {
                throw new WsClientException("Failed to upgrade to WebSocket, expected Upgrade: websocket header. Headers: "
                                                    + responseHeaders);
            }
            if (!responseHeaders.contains(HEADER_WS_ACCEPT)) {
                throw new WsClientException("Failed to upgrade to WebSocket, expected Sec-WebSocket-Accept header. Headers: "
                                                    + responseHeaders);
            }
            ClientConnection connection = upgradeResponse.connection();
            String secWsAccept = responseHeaders.get(HEADER_WS_ACCEPT).value();
            if (!hash(connection.helidonSocket(), secWsKey).equals(secWsAccept)) {
                throw new WsClientException("Failed to upgrade to WebSocket, expected valid secWsKey. Headers: "
                                                    + responseHeaders);
            }
            // we are upgraded, let's switch to web socket
            if (headers.contains(HEADER_WS_PROTOCOL)) {
                session = new ClientWsConnection(connection, listener, headers.get(HEADER_WS_PROTOCOL).value());
            } else {
                session = new ClientWsConnection(connection, listener);
            }
        }

        webClient.executor().submit(session);
    }

    @Override
    public void connect(String path, WsListener listener) {
        connect(URI.create(path), listener);
    }

    @Override
    public WsClientConfig prototype() {
        return clientConfig;
    }

    protected String hash(SocketContext ctx, String wsKey) {
        byte[] wsKeyBytes = wsKey.getBytes(StandardCharsets.US_ASCII);
        int wsKeyBytesLength = wsKeyBytes.length;
        byte[] toHash = new byte[wsKeyBytesLength + KEY_SUFFIX_LENGTH];
        System.arraycopy(wsKeyBytes, 0, toHash, 0, wsKeyBytesLength);
        System.arraycopy(KEY_SUFFIX, 0, toHash, wsKeyBytesLength, KEY_SUFFIX_LENGTH);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
            return B64_ENCODER.encodeToString(digest.digest(toHash));
        } catch (NoSuchAlgorithmException e) {
            ctx.log(LOGGER, System.Logger.Level.ERROR, "SHA-1 must be provided for WebSocket to work", e);
            throw new IllegalStateException("SHA-1 not provided", e);
        }
    }

}
