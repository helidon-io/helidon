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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Api;

/**
 * Standard HTTP/3 and QPACK application error codes.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9114#section-8.1">RFC 9114, section 8.1</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9204#section-6">RFC 9204, section 6</a>
 */
@Api.Internal
public enum Http3ErrorCode {
    /** No error. */
    NO_ERROR(0x0100, "H3_NO_ERROR"),
    /** General HTTP/3 protocol error. */
    GENERAL_PROTOCOL_ERROR(0x0101, "H3_GENERAL_PROTOCOL_ERROR"),
    /** Internal HTTP/3 implementation error. */
    INTERNAL_ERROR(0x0102, "H3_INTERNAL_ERROR"),
    /** Peer-created stream was not accepted. */
    STREAM_CREATION_ERROR(0x0103, "H3_STREAM_CREATION_ERROR"),
    /** A critical HTTP/3 stream was closed. */
    CLOSED_CRITICAL_STREAM(0x0104, "H3_CLOSED_CRITICAL_STREAM"),
    /** Frame was not permitted in its current context. */
    FRAME_UNEXPECTED(0x0105, "H3_FRAME_UNEXPECTED"),
    /** Frame layout or size was invalid. */
    FRAME_ERROR(0x0106, "H3_FRAME_ERROR"),
    /** Peer behavior generated excessive load. */
    EXCESSIVE_LOAD(0x0107, "H3_EXCESSIVE_LOAD"),
    /** Stream or push identifier was used incorrectly. */
    ID_ERROR(0x0108, "H3_ID_ERROR"),
    /** SETTINGS payload was invalid. */
    SETTINGS_ERROR(0x0109, "H3_SETTINGS_ERROR"),
    /** Initial control-stream SETTINGS frame was missing. */
    MISSING_SETTINGS(0x010a, "H3_MISSING_SETTINGS"),
    /** Request was rejected without application processing. */
    REQUEST_REJECTED(0x010b, "H3_REQUEST_REJECTED"),
    /** Request or response was cancelled. */
    REQUEST_CANCELLED(0x010c, "H3_REQUEST_CANCELLED"),
    /** Request stream ended before carrying a complete request. */
    REQUEST_INCOMPLETE(0x010d, "H3_REQUEST_INCOMPLETE"),
    /** HTTP message was malformed. */
    MESSAGE_ERROR(0x010e, "H3_MESSAGE_ERROR"),
    /** CONNECT tunnel ended abnormally. */
    CONNECT_ERROR(0x010f, "H3_CONNECT_ERROR"),
    /** Operation needs an HTTP/1.1 fallback. */
    VERSION_FALLBACK(0x0110, "H3_VERSION_FALLBACK"),
    /** QPACK field-section decompression failed. */
    QPACK_DECOMPRESSION_FAILED(0x0200, "QPACK_DECOMPRESSION_FAILED"),
    /** QPACK encoder-stream instruction was invalid. */
    QPACK_ENCODER_STREAM_ERROR(0x0201, "QPACK_ENCODER_STREAM_ERROR"),
    /** QPACK decoder-stream instruction was invalid. */
    QPACK_DECODER_STREAM_ERROR(0x0202, "QPACK_DECODER_STREAM_ERROR");

    private static final Map<Long, Http3ErrorCode> BY_CODE;

    static {
        Map<Long, Http3ErrorCode> codes = new HashMap<>();
        for (Http3ErrorCode errorCode : values()) {
            codes.put(errorCode.code, errorCode);
        }
        BY_CODE = Map.copyOf(codes);
    }

    private final long code;
    private final String wireName;

    Http3ErrorCode(long code, String wireName) {
        this.code = code;
        this.wireName = wireName;
    }

    /**
     * Find a standard error code by its wire value.
     *
     * @param code wire error code
     * @return standard error code, or empty for an extension or unknown code
     */
    public static Optional<Http3ErrorCode> find(long code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    /**
     * Numeric wire value.
     *
     * @return wire value
     */
    public long code() {
        return code;
    }

    /**
     * Standard protocol name.
     *
     * @return standard protocol name
     */
    public String wireName() {
        return wireName;
    }
}
