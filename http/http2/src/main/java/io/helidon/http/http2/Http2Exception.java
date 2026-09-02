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

package io.helidon.http.http2;

/**
 * HTTP/2 exception.
 */
public class Http2Exception extends RuntimeException {
    /**
     * Type of the HTTP/2 error.
     */
    private final Http2ErrorCode type;
    /**
     * Whether this is a request-target error.
     */
    private final boolean requestTarget;

    /**
     * Exception with type and message.
     *
     * @param errorCode error code
     * @param message   descriptive message
     */
    public Http2Exception(Http2ErrorCode errorCode, String message) {
        this(errorCode, message, false);
    }

    /**
     * Exception with type, message and cause.
     *
     * @param errorCode error code
     * @param message   descriptive message
     * @param cause     cause
     */
    public Http2Exception(Http2ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.type = errorCode;
        this.requestTarget = false;
    }

    Http2Exception(Http2ErrorCode errorCode, String message, boolean requestTarget) {
        super(message);
        this.type = errorCode;
        this.requestTarget = requestTarget;
    }

    /**
     * Error code.
     *
     * @return error code
     */
    public Http2ErrorCode code() {
        return type;
    }

    /**
     * Whether this exception reports a malformed HTTP/2 request target.
     *
     * @return whether this is a request-target error
     */
    public boolean requestTarget() {
        return requestTarget;
    }
}
