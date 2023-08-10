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

package io.helidon.http.http2;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP/2 error codes.
 */
public enum Http2ErrorCode {
    /**
     * The associated condition is not a result of an
     * error.  For example, a GOAWAY might include this code to indicate
     * graceful shutdown of a connection.
     */
    NO_ERROR(0x0),
    /**
     * The endpoint detected an unspecific protocol
     * error.  This error is for use when a more specific error code is
     * not available.
     */
    PROTOCOL(0x1),
    /**
     * The endpoint encountered an unexpected
     * internal error.
     */
    INTERNAL(0x2),
    /**
     * The endpoint detected that its peer
     * violated the flow-control protocol.
     */
    FLOW_CONTROL(0x3),
    /**
     * The endpoint sent a SETTINGS frame but did
     * not receive a response in a timely manner.  See Section 6.5.3
     * ("Settings Synchronization").
     */
    SETTINGS_TIMEOUT(0x4),
    /**
     * The endpoint received a frame after a stream
     * was half-closed.
     */
    STREAM_CLOSED(0x5),
    /**
     * The endpoint received a frame with an
     * invalid size.
     */
    FRAME_SIZE(0x6),
    /**
     * The endpoint refused the stream prior to
     * performing any application processing (see Section 8.1.4 for
     * details).
     */
    REFUSED_STREAM(0x7),
    /**
     * Used by the endpoint to indicate that the stream is no
     * longer needed.
     */
    CANCEL(0x8),
    /**
     * The endpoint is unable to maintain the
     * header compression context for the connection.
     */
    COMPRESSION(0x9),
    /**
     * The connection established in response to a
     * CONNECT request (Section 8.3) was reset or abnormally closed.
     */
    CONNECT(0xa),
    /**
     * The endpoint detected that its peer is
     * exhibiting a behavior that might be generating excessive load.
     */
    ENHANCE_YOUR_CALM(0xb),
    /**
     * The underlying transport has properties
     * that do not meet minimum security requirements (see Section 9.2).
     */
    INADEQUATE_SECURITY(0xc),
    /**
     * The endpoint requires that HTTP/1.1 be used
     * instead of HTTP/2.
     */
    HTTP_1_1_REQUIRED(0xd),
    /**
     * Request header fields are too large.
     * RFC6585
     */
    REQUEST_HEADER_FIELDS_TOO_LARGE(431);

    private static final Map<Integer, Http2ErrorCode> BY_CODE;

    static {
        Map<Integer, Http2ErrorCode> map = new HashMap<>();
        for (Http2ErrorCode value : Http2ErrorCode.values()) {
            map.put(value.code, value);
        }
        BY_CODE = Map.copyOf(map);
    }

    private final int code;

    Http2ErrorCode(int code) {
        this.code = code;
    }

    /**
     * Get error code enum based on error code number.
     *
     * @param errorCode error code
     * @return enum for the error code, returns {@link #INTERNAL} when not found
     */
    public static Http2ErrorCode get(int errorCode) {
        Http2ErrorCode code = BY_CODE.get(errorCode);
        if (code == null) {
            return Http2ErrorCode.INTERNAL;
        }
        return code;
    }

    /**
     * Numeric code of this error.
     *
     * @return error code
     */
    public int code() {
        return code;
    }
}
