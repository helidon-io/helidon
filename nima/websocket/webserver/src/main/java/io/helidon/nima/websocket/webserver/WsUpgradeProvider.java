/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.DirectHandler;
import io.helidon.common.http.HeadersWritable;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderName;
import io.helidon.common.http.HttpPrologue;
import io.helidon.nima.webserver.ConnectionContext;
import io.helidon.nima.webserver.http.HttpException;
import io.helidon.nima.webserver.http1.spi.Http1UpgradeProvider;
import io.helidon.nima.webserver.spi.ServerConnection;

/**
 * {@link java.util.ServiceLoader} provider implementation for upgrade from HTTP/1.1 to WebSocket.
 */
public class WsUpgradeProvider implements Http1UpgradeProvider {
    private static final System.Logger LOGGER = System.getLogger(WsUpgradeProvider.class.getName());
    private static final HeaderName WS_KEY = Header.create("Sec-WebSocket-Key");
    private static final HeaderName WS_VERSION = Header.create("Sec-WebSocket-Version");
    private static final HeaderName PROTOCOL = Header.create("Sec-WebSocket-Protocol");
    private static final byte[] KEY_SUFFIX = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.US_ASCII);
    private static final int KEY_SUFFIX_LENGTH = KEY_SUFFIX.length;
    private static final Base64.Decoder B64_DECODER = Base64.getDecoder();
    private static final Base64.Encoder B64_ENCODER = Base64.getEncoder();
    private static final String SWITCHING_PROTOCOL_PREFIX = "HTTP/1.1 101 Switching Protocols\r\n"
            + "Connection: Upgrade\r\n"
            + "Upgrade: websocket\r\n"
            + "Sec-WebSocket-Accept: ";
    private static final String SWITCHING_PROTOCOLS_SUFFIX = "\r\n\r\n";
    private static final String SUPPORTED_VERSION = "13";
    private static final Http.HeaderValue SUPPORTED_VERSION_HEADER = Http.HeaderValue.create(WS_VERSION, SUPPORTED_VERSION);

    private final Set<String> origins;
    private final boolean anyOrigin;

    /**
     * @deprecated This constructor is only to be used by {@link java.util.ServiceLoader}, use {@link #builder()}
     */
    @Deprecated()
    public WsUpgradeProvider() {
        this(builder());
    }

    WsUpgradeProvider(Builder builder) {
        this.origins = Set.copyOf(builder.origins);
        this.anyOrigin = this.origins.isEmpty();
    }

    /**
     * New builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String supportedProtocol() {
        return "websocket";
    }

    @Override
    public ServerConnection upgrade(ConnectionContext ctx, HttpPrologue prologue, HeadersWritable<?> headers) {
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
            throw HttpException.builder()
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .message("Unsupported WebSocket Version")
                    .header(SUPPORTED_VERSION_HEADER)
                    .build();
        }

        WebSocket route = ctx.router().routing(WebSocketRouting.class, WebSocketRouting.empty())
                .findRoute(prologue);

        if (route == null) {
            return null;
        }

        if (!anyOrigin) {
            if (headers.contains(Header.ORIGIN)) {
                String origin = headers.get(Header.ORIGIN).value();
                if (!origins.contains(origin)) {
                    throw HttpException.builder()
                            .message("Invalid Origin")
                            .type(DirectHandler.EventType.FORBIDDEN)
                            .build();
                }
            }
        }

        // todo support subprotocols (must be provided by route)
        // Sec-WebSocket-Protocol: sub-protocol (list provided in PROTOCOL header, separated by comma space
        DataWriter dataWriter = ctx.dataWriter();
        String switchingProtocols = SWITCHING_PROTOCOL_PREFIX + hash(ctx, wsKey) + SWITCHING_PROTOCOLS_SUFFIX;
        dataWriter.write(BufferData.create(switchingProtocols.getBytes(StandardCharsets.US_ASCII)));

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Upgraded to websocket version " + version);
        }

        return new WsConnection(ctx, prologue, headers, wsKey, route);
    }

    private String hash(ConnectionContext ctx, String wsKey) {
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
            throw HttpException.builder()
                    .type(DirectHandler.EventType.BAD_REQUEST)
                    .message("Invalid Sec-WebSocket-Key header")
                    .build();
        }
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
            ctx.log(LOGGER, Level.ERROR, "SHA-1 must be provided for WebSocket to work", e);
            throw new IllegalStateException("SHA-1 not provided", e);
        }
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.websocket.webserver.WsUpgradeProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, WsUpgradeProvider> {
        private final Set<String> origins = new HashSet<>();

        private Builder() {
        }

        @Override
        public WsUpgradeProvider build() {
            return new WsUpgradeProvider(this);
        }

        /**
         * Add supported origin.
         *
         * @param origin origin to add
         * @return updated builder
         */
        public Builder addOrigin(String origin) {
            origins.add(origin);
            return this;
        }
    }
}
