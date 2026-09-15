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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import io.helidon.common.Api;

/**
 * Internal cross-module HTTP/3 protocol signal.
 */
@Api.Internal
public final class Http3ProtocolException extends RuntimeException {
    /**
     * Peer-visible HTTP/3 or QPACK application error code.
     */
    private final Http3ErrorCode errorCode;
    /**
     * Scope of the protocol action required for this error.
     */
    private final Scope scope;

    private Http3ProtocolException(Http3ErrorCode errorCode,
                                   Scope scope,
                                   String message,
                                   Optional<Throwable> cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause").orElse(null));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    /**
     * Create a connection-scoped HTTP/3 application error.
     *
     * @param errorCode HTTP/3 or QPACK application error code
     * @param message failure description
     * @return protocol exception
     */
    public static Http3ProtocolException connectionError(Http3ErrorCode errorCode, String message) {
        return new Http3ProtocolException(errorCode, Scope.CONNECTION, message, Optional.empty());
    }

    /**
     * Create a connection-scoped HTTP/3 application error.
     *
     * @param errorCode HTTP/3 or QPACK application error code
     * @param message failure description
     * @param cause original failure
     * @return protocol exception
     */
    public static Http3ProtocolException connectionError(Http3ErrorCode errorCode, String message, Throwable cause) {
        return new Http3ProtocolException(errorCode,
                                          Scope.CONNECTION,
                                          message,
                                          Optional.of(Objects.requireNonNull(cause, "cause")));
    }

    /**
     * Create a stream-scoped HTTP/3 application error.
     *
     * @param errorCode HTTP/3 or QPACK application error code
     * @param message failure description
     * @return protocol exception
     */
    public static Http3ProtocolException streamError(Http3ErrorCode errorCode, String message) {
        return new Http3ProtocolException(errorCode, Scope.STREAM, message, Optional.empty());
    }

    /**
     * Create a stream-scoped HTTP/3 application error.
     *
     * @param errorCode HTTP/3 or QPACK application error code
     * @param message failure description
     * @param cause original failure
     * @return protocol exception
     */
    public static Http3ProtocolException streamError(Http3ErrorCode errorCode, String message, Throwable cause) {
        return new Http3ProtocolException(errorCode,
                                          Scope.STREAM,
                                          message,
                                          Optional.of(Objects.requireNonNull(cause, "cause")));
    }

    /**
     * Find a nested HTTP/3 protocol exception in the supplied throwable chain.
     *
     * @param throwable throwable to inspect
     * @return nested protocol exception, or {@link Optional#empty()} when absent
     */
    public static Optional<Http3ProtocolException> find(Throwable throwable) {
        Throwable current = Objects.requireNonNull(throwable, "throwable");
        while (current != null) {
            if (current instanceof Http3ProtocolException protocolException) {
                return Optional.of(protocolException);
            }
            if (current instanceof CompletionException || current instanceof ExecutionException) {
                current = current.getCause();
                continue;
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    /**
     * Return the peer-visible HTTP/3 or QPACK application error code.
     *
     * @return error code
     */
    public Http3ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * Scope of the required protocol action.
     *
     * @return protocol action scope
     */
    public Scope scope() {
        return scope;
    }

    /**
     * Scope of an HTTP/3 protocol signal.
     */
    public enum Scope {
        /**
         * The owning stream performs the wire action.
         */
        STREAM,
        /**
         * The owning HTTP/3 connection performs the wire action.
         */
        CONNECTION
    }
}
