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

package io.helidon.quic;

import java.io.Serial;
import java.util.Objects;
import java.util.OptionalLong;

import io.helidon.common.Api;

/**
 * Failure of a standalone QUIC stream operation because one direction of the stream reached a terminal condition.
 */
@Api.Incubating
public final class QuicStreamTerminationException extends QuicException {
    @Serial
    private static final long serialVersionUID = -2463183586956858385L;

    /**
     * Stream identifier.
     */
    private final long streamId;
    /**
     * Terminal stream condition.
     */
    private final Kind kind;
    /**
     * Whether an application error code is available.
     */
    private final boolean hasApplicationErrorCode;
    /**
     * Application error code when available.
     */
    private final long applicationErrorCode;

    QuicStreamTerminationException(long streamId,
                                   Kind kind,
                                   OptionalLong applicationErrorCode,
                                   String message,
                                   Throwable cause) {
        super(message, cause);
        this.streamId = streamId;
        this.kind = Objects.requireNonNull(kind, "kind");
        OptionalLong errorCode = Objects.requireNonNull(applicationErrorCode, "applicationErrorCode");
        this.hasApplicationErrorCode = errorCode.isPresent();
        this.applicationErrorCode = errorCode.orElse(0);
    }

    /**
     * Stream identifier.
     *
     * @return stream identifier
     */
    public long streamId() {
        return streamId;
    }

    /**
     * Terminal stream condition.
     *
     * @return terminal stream condition
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Application error code associated with the terminal condition.
     *
     * @return application error code, when present
     */
    public OptionalLong applicationErrorCode() {
        return hasApplicationErrorCode ? OptionalLong.of(applicationErrorCode) : OptionalLong.empty();
    }

    /**
     * Terminal stream condition.
     */
    public enum Kind {
        /**
         * The stream direction was already closed.
         */
        CLOSED,
        /**
         * The local endpoint reset the stream.
         */
        RESET_LOCALLY,
        /**
         * The peer reset the stream.
         */
        RESET_BY_PEER,
        /**
         * The peer requested that stream sending stop.
         */
        STOP_SENDING
    }
}
