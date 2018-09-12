/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import io.helidon.common.http.Http;

/**
 * Runtime exception for applications.
 * <p>
 * This exception may be thrown during request processing if a specific
 * HTTP error response needs to be produced. Only effective if thrown
 * before the status code is sent.
 */
public class HttpException extends RuntimeException {

    private final Http.ResponseStatus status;

    /**
     * Creates {@link HttpException} associated with {@link Http.Status#INTERNAL_SERVER_ERROR_500}.
     *
     * @param message the message
     */
    public HttpException(String message) {
        this(message, Http.Status.INTERNAL_SERVER_ERROR_500);
    }

    /**
     * Creates {@link HttpException} associated with {@link Http.Status#INTERNAL_SERVER_ERROR_500}.
     *
     * @param message the message
     * @param cause the cause of this exception
     */
    public HttpException(String message, Throwable cause) {
        this(message, Http.Status.INTERNAL_SERVER_ERROR_500, cause);
    }

    /**
     * Creates {@link HttpException}.
     *
     * @param message the message
     * @param status the http status
     */
    public HttpException(String message, Http.ResponseStatus status) {
        super(message);

        this.status = status;
    }

    /**
     * Creates {@link HttpException}.
     *
     * @param message the message
     * @param status the http status
     * @param cause the cause of this exception
     */
    public HttpException(String message, Http.ResponseStatus status, Throwable cause) {
        super(message, cause);

        this.status = status;
    }

    /**
     * Obtain the associated http status.
     *
     * @return the http status
     */
    public final Http.ResponseStatus status() {
        return status;
    }
}
