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

package io.helidon.nima.websocket.webserver;

import java.lang.System.Logger.Level;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.HttpPrologue;
import io.helidon.common.http.NotFoundException;
import io.helidon.common.http.RequestException;
import io.helidon.common.http.WritableHeaders;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http1.spi.Http1Upgrader;
import io.helidon.nima.webserver.spi.ServerConnection;
import io.helidon.nima.websocket.WsUpgradeException;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * {@link io.helidon.nima.webserver.http1.spi.Http1Upgrader} implementation to upgrade from HTTP/1.1 to WebSocket.
 */
public class WsUpgrader implements Http1Upgrader {

    /**
     * Websocket key header name.
     */
    public static final HeaderName WS_KEY = Header.create("Sec-WebSocket-Key");

    /**
     * Websocket version header name.
     */
    public static final HeaderName WS_VERSION = Header.create("Sec-WebSocket-Version");

    /**
     * Websocket protocol header name.
     */
    public static final HeaderName PROTOCOL = Header.create("Sec-WebSocket-Protocol");

    /**
     * Websocket protocol header name.
     */
    public static final HeaderName EXTENSIONS = Header.create("Sec-WebSocket-Extensions");

    /**
     * Switching response prefix.
     */
    protected static final String SWITCHING_PROTOCOL_PREFIX = "HTTP/1.1 101 Switching Protocols\r\n"
            + "Connection: Upgrade\r\n"
            + "Upgrade: websocket\r\n"
            + "Sec-WebSocket-Accept: ";

    /**
     * Switching response suffix.
     */
    protected static final String SWITCHING_PROTOCOLS_SUFFIX = "\r\n\r\n";

    /**
     * Supported version.
     */
    protected static final String SUPPORTED_VERSION = "13";

    /**
     * Supported version header.
     */
    protected static final Http.HeaderValue SUPPORTED_VERSION_HEADER = Header.create(WS_VERSION, SUPPORTED_VERSION);
    static final Headers EMPTY_HEADERS = WritableHeaders.create();
    private static final System.Logger LOGGER = System.getLogger(WsUpgrader.class.getName());
    private static final byte[] KEY_SUFFIX = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(US_ASCII);
    private static final int KEY_SUFFIX_LENGTH = KEY_SUFFIX.length;
    private static final Base64.Decoder B64_DECODER = Base64.getDecoder();
    private static final Base64.Encoder B64_ENCODER = Base64.getEncoder();
    private static final byte[] HEADERS_SEPARATOR = "\r\n".getBytes(US_ASCII);
    private final Set<String> origins;
    private final boolean anyOrigin;

    protected WsUpgrader(WsConfig wsConfig) {
        this.origins = wsConfig.origins();
        this.anyOrigin = this.origins.isEmpty();
    }

    /**
     * WebSocket upgrader for HTTP/1.
     *
     * @param config configuration of web socket protocol
     * @return a new upgrader
     */
    public static WsUpgrader create(WsConfig config) {
        return new WsUpgrader(config);
    }

    @Override
    public String supportedProtocol() {
        return "websocket";
    }

    @Override
    public ServerConnection upgrade(ConnectionContext ctx, HttpPrologue prologue, WritableHeaders<?> headers) {
        String wsKey;
        if (headers.contains(WS_KEY)) {
            wsKey = headers.get(WS_KEY).value();
        } else {
            // this header is required
            return null;
        }
        // protocol version
        String version;
        if (headers.contains(WS_VERSION)) {
            version = headers.get(WS_VERSION).value();
        } else {
            version = SUPPORTED_VERSION;
        }

        if (!SUPPORTED_VERSION.equals(version)) {
            throw RequestException.builder()
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .message("Unsupported WebSocket Version")
                    .header(SUPPORTED_VERSION_HEADER)
                    .build();
        }

        WsRoute route;

        try {
            route = ctx.router().routing(WsRouting.class, WsRouting.empty())
                    .findRoute(prologue);
        } catch (NotFoundException e) {
            return null;
        }

        if (!anyOrigin()) {
            if (headers.contains(Header.ORIGIN)) {
                String origin = headers.get(Header.ORIGIN).value();
                if (!origins().contains(origin)) {
                    throw RequestException.builder()
                            .message("Invalid Origin")
                            .type(DirectHandler.EventType.FORBIDDEN)
                            .build();
                }
            }
        }

        // invoke user-provided HTTP upgrade handler
        Optional<Headers> upgradeHeaders;
        try {
            upgradeHeaders = route.listener().onHttpUpgrade(prologue, headers);
        } catch (WsUpgradeException e) {
            LOGGER.log(Level.TRACE, "Websocket upgrade rejected", e);
            return null;
        }

        // write switch protocol response including headers from listener
        DataWriter dataWriter = ctx.dataWriter();
        String switchingProtocols = SWITCHING_PROTOCOL_PREFIX + hash(ctx, wsKey);
        dataWriter.write(BufferData.create(switchingProtocols.getBytes(US_ASCII)));
        BufferData separator = BufferData.create(HEADERS_SEPARATOR);
        dataWriter.write(separator);
        upgradeHeaders.ifPresent(hs -> {
            BufferData headerData = BufferData.growing(128);
            hs.forEach(h -> h.writeHttp1Header(headerData));
            dataWriter.write(headerData);
        });
        dataWriter.write(separator.rewind());

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Upgraded to websocket version " + version);
        }

        return WsConnection.create(ctx, prologue, upgradeHeaders.orElse(EMPTY_HEADERS), wsKey, route);
    }

    protected boolean anyOrigin() {
        return anyOrigin;
    }

    protected Set<String> origins() {
        return origins;
    }

    protected String hash(ConnectionContext ctx, String wsKey) {
        byte[] decodedBytes = B64_DECODER.decode(wsKey);
        if (decodedBytes.length != 16) {
            // this is required by the specification (RFC-6455)
            /*
            The request MUST include a header field with the name
            |Sec-WebSocket-Key|.  The value of this header field MUST be a
            nonce consisting of a randomly selected 16-byte value that has
            been base64-encoded (see Section 4 of [RFC4648]).  The nonce
            MUST be selected randomly for each connection.
             */
            throw RequestException.builder()
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .message("Invalid Sec-WebSocket-Key header")
                    .build();
        }
        byte[] wsKeyBytes = wsKey.getBytes(US_ASCII);
        int wsKeyBytesLength = wsKeyBytes.length;
        byte[] toHash = new byte[wsKeyBytesLength + KEY_SUFFIX_LENGTH];
        System.arraycopy(wsKeyBytes, 0, toHash, 0, wsKeyBytesLength);
        System.arraycopy(KEY_SUFFIX, 0, toHash, wsKeyBytesLength, KEY_SUFFIX_LENGTH);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
            return B64_ENCODER.encodeToString(digest.digest(toHash));
        } catch (NoSuchAlgorithmException e) {
            ctx.log(LOGGER, Level.ERROR, "SHA-1 must be provided for WebSocket to work", e);
            throw new IllegalStateException("SHA-1 not provided", e);
        }
    }

}
