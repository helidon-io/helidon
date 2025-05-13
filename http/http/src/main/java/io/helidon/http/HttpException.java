/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

/**
 * Runtime exception for applications.
 * <p>
 * This exception may be thrown during request processing if a specific
 * HTTP error response needs to be produced. Only effective if thrown
 * before the status code is sent.
 */
public class HttpException extends RuntimeException {
    /**
     * HTTP status to return.
     */
    private final Status status;
    /**
     * Whether the connection can be kept alive.
     */
    private final boolean keepAlive;
    /**
     * Header to return with the response.
     */
    private final ServerResponseHeaders headers = ServerResponseHeaders.create();

    /**
     * Creates {@link HttpException} associated with {@link Status#INTERNAL_SERVER_ERROR_500}.
     *
     * @param message the message
     */
    public HttpException(String message) {
        this(message, Status.INTERNAL_SERVER_ERROR_500);
    }

    /**
     * Creates {@link HttpException} associated with {@link Status#INTERNAL_SERVER_ERROR_500}.
     *
     * @param message the message
     * @param cause the cause of this exception
     */
    public HttpException(String message, Throwable cause) {
        this(message, Status.INTERNAL_SERVER_ERROR_500, cause);
    }

    /**
     * Creates {@link HttpException}.
     *
     * @param message the message
     * @param status the http status
     */
    public HttpException(String message, Status status) {
        this(message, status, null);
    }

    /**
     * Creates {@link HttpException}.
     *
     * @param message the message
     * @param status the http status
     * @param keepAlive whether to keep the connection alive
     */
    public HttpException(String message, Status status, boolean keepAlive) {
        this(message, status, null, keepAlive);
    }


    /**
     * Creates {@link HttpException}.
     *
     * @param message the message
     * @param status the http status
     * @param cause the cause of this exception
     */
    public HttpException(String message, Status status, Throwable cause) {
        this(message, status, cause, false);
    }

    /**
     * Creates {@link HttpException}.
     *
     * @param message the message
     * @param status the http status
     * @param cause the cause of this exception
     * @param keepAlive whether to keep this connection alive
     */
    public HttpException(String message, Status status, Throwable cause, boolean keepAlive) {
        super(message, cause);

        this.status = status;
        this.keepAlive = keepAlive;
    }

    /**
     * Obtain the associated http status.
     *
     * @return the http status
     */
    public final Status status() {
        return status;
    }

    /**
     * Whether we should attempt to keep the connection alive (if enabled for it).
     * Some exceptions may allow the connection to be further used (such as {@link NotFoundException}.
     *
     * @return whether to keep alive
     */
    public boolean keepAlive() {
        return keepAlive;
    }

    /**
     * Set a response header that should be used if this exception is not handled.
     *
     * @param header header to set
     * @return updated instance
     */
    public HttpException header(Header header) {
        headers.set(header);
        return this;
    }

    /**
     * Headers as currently configured in this exception.
     *
     * @return headers configured for this exception
     */
    public Headers headers() {
        return headers;
    }
}
