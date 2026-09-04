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

import java.io.Serial;
import java.net.SocketTimeoutException;
import java.util.Objects;

import io.helidon.common.Api;

/**
 * Timeout while waiting for HTTP/3 message-stream or QPACK input progress.
 */
@Api.Internal
public final class Http3ReadTimeoutException extends SocketTimeoutException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Create an HTTP/3 read timeout.
     *
     * @param message timeout detail
     */
    public Http3ReadTimeoutException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    /**
     * Create an HTTP/3 read timeout.
     *
     * @param message timeout detail
     * @param cause timeout cause
     */
    public Http3ReadTimeoutException(String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"));
        initCause(Objects.requireNonNull(cause, "cause"));
    }
}
