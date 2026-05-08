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

package io.helidon.webserver.websocket;

import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.DirectHandler;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.NotFoundException;
import io.helidon.http.RequestException;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http1.spi.Http1Upgrader;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsUpgradeException;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * {@link io.helidon.webserver.http1.spi.Http1Upgrader} implementation to upgrade from HTTP/1.1 to WebSocket.
 */
public class WsUpgrader implements Http1Upgrader {

    /**
     * Websocket key header name.
     */
    public static final HeaderName WS_KEY = HeaderNames.create("Sec-WebSocket-Key");

    /**
     * Websocket version header name.
     */
    public static final HeaderName WS_VERSION = HeaderNames.create("Sec-WebSocket-Version");

    /**
     * Websocket protocol header name.
     */
    public static final HeaderName PROTOCOL = HeaderNames.create("Sec-WebSocket-Protocol");

    /**
     * Websocket protocol header name.
     */
    public static final HeaderName EXTENSIONS = HeaderNames.create("Sec-WebSocket-Extensions");

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
    protected static final Header SUPPORTED_VERSION_HEADER = HeaderValues.create(WS_VERSION, SUPPORTED_VERSION);
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
            wsKey = headers.get(WS_KEY).get();
        } else {
            // this header is required
            return null;
        }
        // protocol version
        String version;
        if (headers.contains(WS_VERSION)) {
            version = headers.get(WS_VERSION).get();
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

        if (headers.contains(HeaderNames.ORIGIN)) {
            validateOrigin(headers, headers.get(HeaderNames.ORIGIN).get());
        }

        // invoke user-provided HTTP upgrade handler
        Optional<Headers> upgradeHeaders;
        WsListener wsListener = route.listener();
        try {
            upgradeHeaders = wsListener.onHttpUpgrade(prologue, headers);
        } catch (WsUpgradeException e) {
            LOGGER.log(Level.TRACE, "Websocket upgrade rejected", e);
            return null;
        }

        // write switch protocol response including headers from listener
        String switchingProtocols = SWITCHING_PROTOCOL_PREFIX + hash(ctx, wsKey);
        BufferData responseData = BufferData.growing(128);
        responseData.write(switchingProtocols.getBytes(US_ASCII));
        responseData.write(HEADERS_SEPARATOR);
        upgradeHeaders.ifPresent(hs -> hs.forEach(h -> h.writeHttp1Header(responseData)));
        responseData.write(HEADERS_SEPARATOR);
        writeUpgradeResponse(ctx.dataWriter(), responseData);

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Upgraded to websocket version " + version);
        }

        return WsConnection.create(ctx, prologue, upgradeHeaders.orElse(EMPTY_HEADERS), wsKey, wsListener);
    }

    private static void writeUpgradeResponse(DataWriter dataWriter, BufferData responseData) {
        try {
            dataWriter.writeNow(responseData);
        } catch (SocketWriterException | UncheckedIOException e) {
            throw new ServerConnectionException("Failed to write websocket upgrade response", e);
        }
    }

    protected boolean anyOrigin() {
        return anyOrigin;
    }

    protected Set<String> origins() {
        return origins;
    }

    /**
     * Validate the request origin against either the configured allowlist or the request host authority.
     *
     * @param headers request headers
     * @param origin origin header value
     */
    private void validateOrigin(WritableHeaders<?> headers, String origin) {
        if (!anyOrigin()) {
            if (origins().contains(origin)) {
                return;
            }
        } else if (matchesAuthority(headers, origin)) {
            return;
        }

        throw RequestException.builder()
                .message("Invalid Origin")
                .type(DirectHandler.EventType.FORBIDDEN)
                .build();
    }

    /**
     * Check whether the origin authority matches the request host authority.
     *
     * @param headers request headers
     * @param origin origin header value
     * @return {@code true} if the authorities match
     */
    private boolean matchesAuthority(WritableHeaders<?> headers, String origin) {
        URI originUri;
        try {
            originUri = new URI(origin);
        } catch (URISyntaxException e) {
            return false;
        }

        if (originUri.getScheme() == null || originUri.getHost() == null) {
            return false;
        }

        if (!headers.contains(HeaderNames.HOST)) {
            return false;
        }

        String hostHeader = headers.get(HeaderNames.HOST).get();
        return normalizeOrigin(originUri).equals(normalizeAuthority(hostHeader, originUri.getScheme()));
    }

    /**
     * Normalize a host header authority into a lowercase {@code host:port} form.
     *
     * @param authority host header value
     * @param scheme scheme to use for default-port resolution
     * @return normalized authority
     */
    private static String normalizeAuthority(String authority, String scheme) {
        String host;
        int port = -1;

        if (authority.startsWith("[")) {
            int closingBracket = authority.indexOf(']');
            if (closingBracket < 0) {
                // malformed bracketed IPv6 authority, fail the later equality check
                return authority.toLowerCase();
            }
            host = authority.substring(1, closingBracket);
            if (closingBracket + 1 < authority.length()) {
                port = Integer.parseInt(authority.substring(closingBracket + 2));
            }
        } else {
            int firstColon = authority.indexOf(':');
            int lastColon = authority.lastIndexOf(':');
            if (firstColon > -1 && firstColon == lastColon) {
                host = authority.substring(0, firstColon);
                port = Integer.parseInt(authority.substring(firstColon + 1));
            } else {
                host = authority;
            }
        }

        if (port == -1) {
            port = defaultPort(scheme);
        }

        return host.toLowerCase() + ":" + port;
    }

    /**
     * Normalize an origin URI into a lowercase {@code host:port} form.
     *
     * @param originUri parsed origin URI
     * @return normalized origin authority
     */
    private static String normalizeOrigin(URI originUri) {
        String scheme = originUri.getScheme().toLowerCase();
        String host = originUri.getHost().toLowerCase();
        int port = originUri.getPort();
        if (port == -1) {
            port = defaultPort(scheme);
        }
        return host + ":" + port;
    }

    /**
     * Resolve the default port for a scheme.
     *
     * @param scheme URI scheme
     * @return default port for the scheme
     */
    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
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
