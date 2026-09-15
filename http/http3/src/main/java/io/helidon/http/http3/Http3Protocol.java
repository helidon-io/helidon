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

package io.helidon.http.http3;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.uri.UriAuthority;
import io.helidon.common.uri.UriValidator;
import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.quic.VariableLengthEncoder;

/**
 * Shared HTTP/3 frame and header encoding utilities used by the Helidon client and server integrations.
 */
@Api.Internal
public final class Http3Protocol {
    /**
     * HTTP/3 ALPN identifier.
     */
    public static final String ALPN = "h3";
    /**
     * Minimum number of peer-initiated unidirectional streams that an HTTP/3 endpoint must allow.
     */
    public static final long MINIMUM_PEER_UNI_STREAMS = 3;
    /**
     * DATA frame type.
     */
    public static final long FRAME_DATA = 0x00;
    /**
     * HEADERS frame type.
     */
    public static final long FRAME_HEADERS = 0x01;
    /**
     * CANCEL_PUSH frame type.
     */
    public static final long FRAME_CANCEL_PUSH = 0x03;
    /**
     * SETTINGS frame type.
     */
    public static final long FRAME_SETTINGS = 0x04;
    /**
     * PUSH_PROMISE frame type.
     */
    public static final long FRAME_PUSH_PROMISE = 0x05;
    /**
     * GOAWAY frame type.
     */
    public static final long FRAME_GOAWAY = 0x07;
    /**
     * MAX_PUSH_ID frame type.
     */
    public static final long FRAME_MAX_PUSH_ID = 0x0d;
    /**
     * Largest request stream id that can be opened by an HTTP/3 client.
     */
    public static final long MAX_CLIENT_BIDIRECTIONAL_STREAM_ID = (1L << 62) - 4;

    private static final int MAX_SETTINGS_ENTRIES = 256;
    private static final HeaderName PSEUDO_METHOD = HeaderNames.createFromLowercase(":method");
    private static final HeaderName PSEUDO_SCHEME = HeaderNames.createFromLowercase(":scheme");
    private static final HeaderName PSEUDO_AUTHORITY = HeaderNames.createFromLowercase(":authority");
    private static final HeaderName PSEUDO_PATH = HeaderNames.createFromLowercase(":path");
    private static final HeaderName PSEUDO_STATUS = HeaderNames.createFromLowercase(":status");

    private Http3Protocol() {
    }

    /**
     * Return a diagnostic name for a known HTTP/3 or QPACK application error code.
     *
     * @param errorCode HTTP/3 or QPACK application error code
     * @return symbolic name for known codes, or a generic hexadecimal description for unknown codes
     */
    public static String applicationErrorToString(long errorCode) {
        return Http3ErrorCode.find(errorCode)
                .map(Http3ErrorCode::wireName)
                .orElseGet(() -> "ApplicationError(code=0x" + HexFormat.of().toHexDigits(errorCode) + ")");
    }

    /**
     * Create an HTTP/3 control stream preamble with explicit SETTINGS values.
     *
     * @param settings settings to advertise
     * @return encoded control stream preamble
     */
    @Api.Internal
    public static byte[] controlStreamPreamble(Http3Settings settings) {
        Objects.requireNonNull(settings, "settings");
        BufferData payload = BufferData.growing(32);
        writeSetting(payload,
                     Http3Settings.QPACK_MAX_TABLE_CAPACITY_ID,
                     settings.qpackMaxTableCapacity());
        OptionalLong maxFieldSectionSize = settings.maxFieldSectionSize();
        if (maxFieldSectionSize.isPresent()) {
            writeSetting(payload,
                         Http3Settings.MAX_FIELD_SECTION_SIZE_ID,
                         maxFieldSectionSize.orElseThrow());
        }
        writeSetting(payload,
                     Http3Settings.QPACK_BLOCKED_STREAMS_ID,
                     settings.qpackBlockedStreams());
        settings.extensionSettings().forEach((id, value) -> writeSetting(payload, id, value));

        BufferData output = BufferData.create(VariableLengthEncoder.encodedSize(Http3StreamType.CONTROL.code())
                                                      + frameSize(FRAME_SETTINGS, payload.available()));
        VariableLengthEncoder.encode(output, Http3StreamType.CONTROL.code());
        writeFrame(output, FRAME_SETTINGS, payload);
        return output.readBytes();
    }

    /**
     * Create the preamble for a QPACK encoder or decoder unidirectional stream.
     *
     * @param type stream type to encode
     * @return encoded unidirectional-stream preamble
     */
    public static byte[] qpackUniStreamPreamble(Http3StreamType type) {
        BufferData output = BufferData.create(VariableLengthEncoder.encodedSize(type.code()));
        VariableLengthEncoder.encode(output, type.code());
        return output.readBytes();
    }

    /**
     * Create an HTTP/3 GOAWAY frame.
     *
     * @param goAway GOAWAY value
     * @return encoded GOAWAY frame
     */
    @Api.Internal
    public static byte[] goAwayFrame(Http3GoAway goAway) {
        Objects.requireNonNull(goAway, "goAway");
        int payloadLength = VariableLengthEncoder.encodedSize(goAway.identifier());
        BufferData output = frameOutput(FRAME_GOAWAY, payloadLength);
        writeFrameHeader(output, FRAME_GOAWAY, payloadLength);
        VariableLengthEncoder.encode(output, goAway.identifier());
        return output.readBytes();
    }

    /**
     * Encode the HEADERS frame for an HTTP/3 request using QPACK dynamic-table state.
     *
     * @param qpackContext per-connection QPACK context
     * @param streamId     request stream id
     * @param uri          request URI
     * @param method       request method
     * @param headers      request headers
     * @return encoded HEADERS frame
     */
    @Api.Internal
    public static byte[] encodeRequestHeaders(Http3QpackContext qpackContext,
                                              long streamId,
                                              URI uri,
                                              String method,
                                              Headers headers) {
        Objects.requireNonNull(qpackContext, "qpackContext");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(headers, "headers");
        Method.create(method);
        String authority = requestAuthority(uri, headers);
        UriAuthority normalizedAuthority = UriAuthority.create(authority);
        if (Method.CONNECT_NAME.equals(method)) {
            if (!normalizedAuthority.hasPort()) {
                throw new IllegalArgumentException("CONNECT request authority must contain an explicit port");
            }
            if (normalizedAuthority.port() == 0) {
                throw new IllegalArgumentException("CONNECT request authority port must be between 1 and 65535");
            }
        } else if (uri.getScheme() == null || uri.getScheme().isEmpty()) {
            throw new IllegalArgumentException("HTTP/3 request scheme must not be empty");
        }
        return encodeHeadersFrameOrdered(qpackContext,
                                         streamId,
                                         requestHeaders(method,
                                                        uri.getScheme(),
                                                        authority,
                                                        pathOf(uri),
                                                        headers));
    }

    /**
     * Encode a HEADERS frame without QPACK dynamic-table references.
     *
     * @param headers headers to encode
     * @return encoded HEADERS frame
     */
    public static byte[] encodeHeadersFrame(Headers headers) {
        Objects.requireNonNull(headers, "headers");
        return encodeHeadersFrameOrdered(headers);
    }

    /**
     * Effective HTTP authority for a request. A {@code Host} header overrides the URI authority and is encoded as the
     * HTTP/3 {@code :authority} pseudo-header.
     *
     * @param uri request URI
     * @param headers request headers
     * @return effective request authority
     * @throws IllegalArgumentException if neither the {@code Host} header nor the URI provides a non-empty authority
     */
    @Api.Internal
    public static String requestAuthority(URI uri, Headers headers) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        String authority = headers.first(HeaderNames.HOST).orElseGet(() -> authorityOf(uri));
        if (authority == null || authority.isEmpty()) {
            throw new IllegalArgumentException("HTTP/3 request authority must not be empty");
        }
        return authority;
    }

    /**
     * Encode a HEADERS frame with QPACK dynamic-table support.
     *
     * @param qpackContext per-connection QPACK context
     * @param streamId     stream id for the encoded header section
     * @param headers      headers to encode
     * @return encoded HEADERS frame
     */
    @Api.Internal
    public static byte[] encodeHeadersFrame(Http3QpackContext qpackContext,
                                            long streamId,
                                            Headers headers) {
        Objects.requireNonNull(qpackContext, "qpackContext");
        Objects.requireNonNull(headers, "headers");
        return encodeHeadersFrameOrdered(qpackContext, streamId, headers);
    }

    /**
     * Encode the HEADERS frame for an HTTP/3 response.
     *
     * @param status  response status
     * @param headers response headers
     * @return encoded HEADERS frame
     */
    public static byte[] encodeResponseHeaders(int status, Headers headers) {
        Objects.requireNonNull(headers, "headers");
        return encodeHeadersFrameOrdered(responseHeaders(status, headers));
    }

    /**
     * Encode the HEADERS frame for an HTTP/3 response using QPACK dynamic-table state.
     *
     * @param qpackContext per-connection QPACK context
     * @param streamId     response stream id
     * @param status       response status
     * @param headers      response headers
     * @return encoded HEADERS frame
     */
    @Api.Internal
    public static byte[] encodeResponseHeaders(Http3QpackContext qpackContext,
                                               long streamId,
                                               int status,
                                               Headers headers) {
        Objects.requireNonNull(qpackContext, "qpackContext");
        Objects.requireNonNull(headers, "headers");
        return encodeHeadersFrameOrdered(qpackContext, streamId, responseHeaders(status, headers));
    }

    /**
     * Encode a DATA frame from the provided payload.
     *
     * @param payload frame payload
     * @return encoded DATA frame
     */
    public static byte[] encodeDataFrame(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        return encodeDataFrame(payload, 0, payload.length);
    }

    /**
     * Encode a DATA frame from a slice of the provided payload.
     *
     * @param payload  backing payload bytes
     * @param position start position in the payload
     * @param length   number of bytes to encode
     * @return encoded DATA frame
     */
    public static byte[] encodeDataFrame(byte[] payload, int position, int length) {
        return encodeDataFrameBuffer(payload, position, length).readBytes();
    }

    /**
     * Encode a DATA frame as an owned frame header followed by a borrowed read-only payload slice.
     * The caller must not modify the payload until the returned buffer is consumed.
     *
     * @param payload  backing payload bytes
     * @param position start position in the payload
     * @param length   number of bytes to encode
     * @return encoded DATA frame buffer
     */
    @Api.Internal
    public static BufferData encodeDataFrameBuffer(byte[] payload, int position, int length) {
        Objects.requireNonNull(payload, "payload");
        Objects.checkFromIndexSize(position, length, payload.length);
        BufferData header = BufferData.create(VariableLengthEncoder.encodedSize(FRAME_DATA)
                                                      + VariableLengthEncoder.encodedSize(length));
        writeFrameHeader(header, FRAME_DATA, length);
        return BufferData.create(header, BufferData.createReadOnly(payload, position, length));
    }

    static boolean isReservedHttp2FrameType(long frameType) {
        return frameType == 0x02
                || frameType == 0x06
                || frameType == 0x08
                || frameType == 0x09;
    }

    /**
     * Decode an HTTP/3 SETTINGS payload.
     *
     * @param payload SETTINGS payload bytes
     * @return decoded settings
     */
    static Http3Settings decodeSettingsPayload(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        BufferData buffer = BufferData.create(payload);
        Set<Long> seen = new HashSet<>();
        OptionalLong maxFieldSectionSize = OptionalLong.empty();
        long qpackMaxTableCapacity = 0;
        long qpackBlockedStreams = 0;
        Map<Long, Long> extensionSettings = new LinkedHashMap<>();
        int entryCount = 0;

        while (buffer.available() > 0) {
            long settingId = VariableLengthEncoder.decode(buffer);
            long value = VariableLengthEncoder.decode(buffer);
            if (settingId < 0 || value < 0) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_ERROR,
                                                             "Malformed HTTP/3 SETTINGS payload");
            }
            if (++entryCount > MAX_SETTINGS_ENTRIES) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.EXCESSIVE_LOAD,
                                                             "HTTP/3 SETTINGS entry count exceeds the local limit: "
                                                                     + MAX_SETTINGS_ENTRIES);
            }
            if (!seen.add(settingId)) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.SETTINGS_ERROR,
                                                             "Duplicate HTTP/3 setting: " + settingId);
            }
            //= https://www.rfc-editor.org/rfc/rfc9114#section-7.2.4.1
            //# These reserved settings MUST NOT be sent, and their receipt MUST
            //# be treated as a connection error of type H3_SETTINGS_ERROR.
            if (Http3Settings.isReservedId(settingId)) {
                throw Http3ProtocolException.connectionError(Http3ErrorCode.SETTINGS_ERROR,
                                                             "Reserved setting is not allowed in HTTP/3: " + settingId);
            }
            if (settingId == Http3Settings.MAX_FIELD_SECTION_SIZE_ID) {
                maxFieldSectionSize = OptionalLong.of(value);
            } else if (settingId == Http3Settings.QPACK_MAX_TABLE_CAPACITY_ID) {
                qpackMaxTableCapacity = value;
            } else if (settingId == Http3Settings.QPACK_BLOCKED_STREAMS_ID) {
                qpackBlockedStreams = value;
            } else {
                extensionSettings.put(settingId, value);
            }
        }
        return Http3Settings.create(maxFieldSectionSize,
                                    qpackMaxTableCapacity,
                                    qpackBlockedStreams,
                                    extensionSettings);
    }

    /**
     * Decode the payload of a GOAWAY frame.
     *
     * @param bytes GOAWAY payload bytes
     * @param type expected GOAWAY identifier type
     * @return decoded GOAWAY value
     */
    static Http3GoAway decodeGoAway(byte[] bytes, Http3GoAway.Type type) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(type, "type");
        BufferData buffer = BufferData.create(bytes);
        long identifier = tryReadVarInt(buffer);
        if (identifier < 0 || buffer.available() > 0) {
            throw Http3ProtocolException.connectionError(Http3ErrorCode.FRAME_ERROR, "Malformed HTTP/3 GOAWAY frame");
        }
        try {
            return switch (type) {
            case REQUEST_STREAM_ID -> Http3GoAway.requestStream(identifier);
            case PUSH_ID -> Http3GoAway.pushId(identifier);
            };
        } catch (IllegalArgumentException e) {
            throw Http3ProtocolException.connectionError(Http3ErrorCode.ID_ERROR,
                                                         "Invalid HTTP/3 GOAWAY identifier: " + identifier,
                                                         e);
        }
    }

    static Headers decodeHeadersPayload(byte[] payload) {
        return decodeHeadersPayload(payload, -1);
    }

    static Headers decodeHeadersPayload(byte[] payload,
                                        long maxFieldSectionSize) {
        Objects.requireNonNull(payload, "payload");
        try {
            return QpackCodec.decodeHeaders(BufferData.create(payload), maxFieldSectionSize);
        } catch (IllegalArgumentException e) {
            throw Http3ProtocolException.connectionError(Http3ErrorCode.QPACK_DECOMPRESSION_FAILED,
                                                         "Malformed QPACK field section",
                                                         e);
        }
    }

    static long tryReadVarInt(BufferData buffer) {
        return VariableLengthEncoder.decode(buffer);
    }

    static DecodedRequestHead decodeRequestHeaders(Iterable<Header> decodedHeaders) {
        String method = null;
        String scheme = null;
        String authority = null;
        String path = null;
        boolean authoritySensitive = false;
        WritableHeaders<?> headers = WritableHeaders.create();
        Set<String> seenPseudoHeaders = new HashSet<>();
        boolean regularHeadersSeen = false;

        //= https://www.rfc-editor.org/rfc/rfc9114#section-4.3
        //# Endpoints MUST treat a request or response that contains undefined
        //# or invalid pseudo-header fields as malformed.
        //# All pseudo-header fields MUST appear in the header section before
        //# regular header fields.
        //# Any request or response that contains a pseudo-header field that
        //# appears in a header section after a regular header field MUST be
        //# treated as malformed.
        //= https://www.rfc-editor.org/rfc/rfc9114#section-4.3.1
        //# All HTTP/3 requests MUST include exactly one value for the :method,
        //# :scheme, and :path pseudo-header fields, unless the request is a
        //# CONNECT request; see Section 4.4.
        //= https://www.rfc-editor.org/rfc/rfc9114#section-4.3.1
        //# An HTTP request that omits mandatory pseudo-header fields or
        //# contains invalid values for those pseudo-header fields is malformed.
        for (Header header : decodedHeaders) {
            String headerName = header.headerName().lowerCase();
            validateLowercaseHttp3HeaderName(headerName);
            if (!headerName.startsWith(":")) {
                regularHeadersSeen = true;
                QpackCodec.addDecodedHeader(headers, header);
                continue;
            }
            if (regularHeadersSeen) {
                throw requestMessageError("HTTP/3 pseudo-header field after regular headers: " + headerName);
            }
            if (header.valueCount() != 1 || !seenPseudoHeaders.add(headerName)) {
                throw requestMessageError("Duplicate HTTP/3 pseudo-header field: " + headerName);
            }
            String value = header.get();
            switch (headerName) {
            case ":method" -> method = value;
            case ":scheme" -> scheme = value;
            case ":authority" -> {
                authority = value;
                authoritySensitive = header.sensitive();
            }
            case ":path" -> path = value;
            default -> throw requestMessageError("Prohibited HTTP/3 pseudo-header field: " + headerName);
            }
        }
        if (method == null) {
            throw requestMessageError("Missing required HTTP/3 pseudo-header field: :method");
        }
        try {
            Method.create(method);
        } catch (IllegalArgumentException e) {
            throw requestMessageError("Invalid HTTP/3 :method pseudo-header field", e);
        }
        List<String> hostValues = headers.all(HeaderNames.HOST, List::of);
        if (hostValues.size() > 1) {
            throw requestMessageError("Repeated HTTP/3 Host field");
        }
        String host = hostValues.isEmpty() ? null : hostValues.getFirst();
        UriAuthority normalizedAuthority;
        try {
            normalizedAuthority = authority == null || authority.isEmpty() ? null : UriAuthority.create(authority);
            if (normalizedAuthority != null && host != null) {
                int defaultPort = switch (Objects.requireNonNullElse(scheme, "").toLowerCase(Locale.ROOT)) {
                    case "http" -> 80;
                    case "https" -> 443;
                    default -> -1;
                };
                UriAuthority hostValue = UriAuthority.create(host);
                if (!normalizedAuthority.host().equals(hostValue.host())
                        || normalizedAuthority.portOrDefault(defaultPort) != hostValue.portOrDefault(defaultPort)) {
                    throw requestMessageError("HTTP/3 Host field does not match :authority pseudo-header field");
                }
            }
        } catch (IllegalArgumentException e) {
            throw requestMessageError("Invalid HTTP/3 Host or :authority field", e);
        }
        if (authority == null || authority.isEmpty()) {
            authority = host;
            try {
                normalizedAuthority = authority == null ? null : UriAuthority.create(authority);
            } catch (IllegalArgumentException e) {
                throw requestMessageError("Invalid HTTP/3 Host field", e);
            }
        }
        if (authoritySensitive && authority != null && !authority.isEmpty()) {
            // Carry never-index metadata through the regular Host field used by the HTTP adapters.
            headers.set(HeaderValues.create(HeaderNames.HOST, false, true, authority));
        }
        if (Method.CONNECT_NAME.equals(method)) {
            if (!seenPseudoHeaders.contains(":authority") || authority == null || authority.isEmpty()) {
                throw requestMessageError("CONNECT request is missing required :authority pseudo-header field");
            }
            if (normalizedAuthority == null || !normalizedAuthority.hasPort()) {
                throw requestMessageError("CONNECT request :authority must contain an explicit port");
            }
            if (normalizedAuthority.port() == 0) {
                throw requestMessageError("CONNECT request :authority port must be between 1 and 65535");
            }
            if (scheme != null || path != null) {
                throw requestMessageError("CONNECT request contains prohibited :scheme or :path pseudo-header field");
            }
            return DecodedRequestHead.create(method,
                                             Optional.empty(),
                                             authority,
                                             normalizedAuthority,
                                             Optional.empty(),
                                             headers);
        }
        if (scheme == null || scheme.isEmpty()
                || authority == null || authority.isEmpty()
                || path == null || path.isEmpty()) {
            throw requestMessageError("Missing required HTTP/3 pseudo-header fields");
        }
        validateRequestTarget(method, scheme, path);
        return DecodedRequestHead.create(method,
                                         Optional.of(scheme),
                                         authority,
                                         normalizedAuthority,
                                         Optional.of(path),
                                         headers);
    }

    private static void validateRequestTarget(String method, String scheme, String path) {
        for (int i = 0; i < scheme.length(); i++) {
            char c = scheme.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                    || i > 0 && (c >= '0' && c <= '9' || c == '+' || c == '-' || c == '.')) {
                continue;
            }
            throw requestMessageError("Invalid HTTP/3 :scheme pseudo-header field");
        }
        if (path.charAt(0) != '/'
                && !(Method.OPTIONS_NAME.equals(method) && "*".equals(path))) {
            throw requestMessageError("Invalid HTTP/3 :path pseudo-header field");
        }
        try {
            // Path and query share their allowed characters; '?' separates them or appears in the query.
            UriValidator.validateQuery(path);
        } catch (IllegalArgumentException e) {
            throw requestMessageError("Invalid HTTP/3 :path pseudo-header field", e);
        }
    }

    private static List<Header> requestHeaders(String method,
                                               String scheme,
                                               String authority,
                                               String path,
                                               Headers headers) {
        List<Header> requestHeaders = new ArrayList<>(headers.size() + 4);
        requestHeaders.add(HeaderValues.create(PSEUDO_METHOD, method));
        boolean authoritySensitive = headers.contains(HeaderNames.HOST) && headers.get(HeaderNames.HOST).sensitive();
        requestHeaders.add(authoritySensitive
                                   ? HeaderValues.create(PSEUDO_AUTHORITY, false, true, authority)
                                   : HeaderValues.create(PSEUDO_AUTHORITY, authority));
        if (!Method.CONNECT_NAME.equals(method)) {
            requestHeaders.add(HeaderValues.create(PSEUDO_SCHEME, scheme));
            requestHeaders.add(HeaderValues.create(PSEUDO_PATH, path));
        }
        headers.forEach(header -> {
            if (!HeaderNames.HOST.equals(header.headerName())) {
                requestHeaders.add(header);
            }
        });
        return requestHeaders;
    }

    private static List<Header> responseHeaders(int status, Headers headers) {
        List<Header> responseHeaders = new ArrayList<>(headers.size() + 1);
        responseHeaders.add(HeaderValues.create(PSEUDO_STATUS, status));
        headers.forEach(responseHeaders::add);
        return responseHeaders;
    }

    private static void writeSetting(BufferData payload,
                                     long settingId,
                                     long value) {
        VariableLengthEncoder.encode(payload, settingId);
        VariableLengthEncoder.encode(payload, value);
    }

    private static byte[] encodeHeadersFrameOrdered(Iterable<Header> headers) {
        byte[] payload = QpackCodec.encodeHeaders(headers);
        BufferData output = frameOutput(FRAME_HEADERS, payload.length);
        writeFrame(output, FRAME_HEADERS, payload);
        return output.readBytes();
    }

    private static byte[] encodeHeadersFrameOrdered(Http3QpackContext qpackContext,
                                                    long streamId,
                                                    Iterable<Header> headers) {
        byte[] payload = qpackContext.encodeHeaders(streamId, headers);
        BufferData output = frameOutput(FRAME_HEADERS, payload.length);
        writeFrame(output, FRAME_HEADERS, payload);
        return output.readBytes();
    }

    private static BufferData frameOutput(long frameType, int payloadLength) {
        return BufferData.create(frameSize(frameType, payloadLength));
    }

    private static int frameSize(long frameType, int payloadLength) {
        return VariableLengthEncoder.encodedSize(frameType)
                + VariableLengthEncoder.encodedSize(payloadLength)
                + payloadLength;
    }

    private static String authorityOf(URI uri) {
        if (uri.getPort() < 0) {
            return uri.getHost();
        }
        return uri.getHost() + ":" + uri.getPort();
    }

    private static String pathOf(URI uri) {
        String rawPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String rawQuery = uri.getRawQuery();
        return rawQuery == null ? rawPath : rawPath + "?" + rawQuery;
    }

    private static void writeFrame(BufferData output, long frameType, byte[] payload) {
        writeFrame(output, frameType, payload, 0, payload.length);
    }

    private static void writeFrame(BufferData output,
                                   long frameType,
                                   BufferData payload) {
        writeFrameHeader(output, frameType, payload.available());
        output.write(payload);
    }

    private static void writeFrame(BufferData output,
                                   long frameType,
                                   byte[] payload,
                                   int position,
                                   int length) {
        writeFrameHeader(output, frameType, length);
        output.write(payload, position, length);
    }

    private static void writeFrameHeader(BufferData output, long frameType, int payloadLength) {
        VariableLengthEncoder.encode(output, frameType);
        VariableLengthEncoder.encode(output, payloadLength);
    }

    private static void validateLowercaseHttp3HeaderName(String headerName) {
        //= https://www.rfc-editor.org/rfc/rfc9114#section-4.2
        //# Characters in field names MUST be converted to lowercase prior to
        //# their encoding.
        //# A request or response containing uppercase characters in field names
        //# MUST be treated as malformed.
        //= https://www.rfc-editor.org/rfc/rfc9114#section-4.1.2
        //# Malformed requests or responses that are detected MUST be treated
        //# as a stream error of type H3_MESSAGE_ERROR.
        for (int i = 0; i < headerName.length(); i++) {
            char current = headerName.charAt(i);
            if (current >= 'A' && current <= 'Z') {
                throw requestMessageError("HTTP/3 header field name must be lowercase: " + headerName);
            }
        }
    }

    private static Http3ProtocolException requestMessageError(String message) {
        return Http3ProtocolException.streamError(Http3ErrorCode.MESSAGE_ERROR, message);
    }

    private static Http3ProtocolException requestMessageError(String message, Throwable cause) {
        return Http3ProtocolException.streamError(Http3ErrorCode.MESSAGE_ERROR, message, cause);
    }

    private static Headers copyHeaders(Headers headers) {
        return WritableHeaders.create(Objects.requireNonNull(headers, "headers"));
    }

    /**
     * Decoded HTTP/3 request headers without a request body.
     */
    public static final class DecodedRequestHead {
        private final String method;
        private final Optional<String> scheme;
        private final String authority;
        private final UriAuthority parsedAuthority;
        private final Optional<String> path;
        private final Headers headers;

        private DecodedRequestHead(String method,
                                   Optional<String> scheme,
                                   String authority,
                                   UriAuthority parsedAuthority,
                                   Optional<String> path,
                                   Headers headers) {
            this.method = Objects.requireNonNull(method, "method");
            this.scheme = Objects.requireNonNull(scheme, "scheme");
            this.authority = Objects.requireNonNull(authority, "authority");
            this.parsedAuthority = Objects.requireNonNull(parsedAuthority, "parsedAuthority");
            this.path = Objects.requireNonNull(path, "path");
            this.headers = copyHeaders(headers);
        }

        /**
         * Return the decoded request method.
         *
         * @return decoded request method
         */
        public String method() {
            return method;
        }

        /**
         * Return the decoded request scheme, if present. A classic {@code CONNECT} request has no scheme.
         *
         * @return decoded request scheme, or empty for a classic {@code CONNECT} request
         */
        public Optional<String> scheme() {
            return scheme;
        }

        /**
         * Return the decoded request authority.
         *
         * @return decoded request authority
         */
        public String authority() {
            return authority;
        }

        /**
         * Return the parsed representation of the decoded request authority.
         *
         * @return parsed request authority
         */
        public UriAuthority parsedAuthority() {
            return parsedAuthority;
        }

        /**
         * Return the decoded request path, if present. A classic {@code CONNECT} request has no path.
         *
         * @return decoded request path, or empty for a classic {@code CONNECT} request
         */
        public Optional<String> path() {
            return path;
        }

        /**
         * Return the decoded request headers without the required HTTP/3 pseudo-headers.
         * A sensitive {@code :authority} is represented by a sensitive {@code Host} field so that forwarding these
         * headers preserves its never-index metadata.
         *
         * @return decoded request headers
         */
        public Headers headers() {
            return headers;
        }

        /**
         * Create a decoded request-head view.
         *
         * @param method    decoded request method
         * @param scheme    decoded request scheme, or empty for a classic {@code CONNECT} request
         * @param authority decoded request authority
         * @param parsedAuthority parsed request authority
         * @param path      decoded request path, or empty for a classic {@code CONNECT} request
         * @param headers   decoded request headers
         * @return decoded request head
         */
        private static DecodedRequestHead create(String method,
                                                 Optional<String> scheme,
                                                 String authority,
                                                 UriAuthority parsedAuthority,
                                                 Optional<String> path,
                                                 Headers headers) {
            return new DecodedRequestHead(method, scheme, authority, parsedAuthority, path, headers);
        }
    }

}
