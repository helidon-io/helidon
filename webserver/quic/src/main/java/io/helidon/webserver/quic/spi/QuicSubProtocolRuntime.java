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

package io.helidon.webserver.quic.spi;

import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;

import io.helidon.common.Api;
import io.helidon.quic.QuicConnection;
import io.helidon.webserver.spi.TransportBinding;

/**
 * Runtime of an application protocol hosted on a QUIC listener.
 */
@Api.Internal
public interface QuicSubProtocolRuntime extends AutoCloseable {
    /**
     * Default text formatter for application error codes.
     *
     * @param errorCode application error code
     * @return formatted error description
     */
    static String defaultApplicationErrorToString(long errorCode) {
        return "ApplicationError(code=0x" + HexFormat.of().toHexDigits(errorCode) + ")";
    }

    /**
     * ALPN identifiers owned by this runtime, in server preference order with the most preferred identifier first.
     * The returned list must be immutable and stable for the lifetime of this runtime.
     *
     * @return ordered ALPN identifiers hosted by this runtime
     */
    List<String> alpnIds();

    /**
     * Minimum number of peer-initiated unidirectional streams that this runtime requires the local QUIC transport to
     * allow. The requirement must not be negative. Multiple runtimes hosted by one listener share the same
     * per-connection limit, so the listener satisfies the largest runtime requirement rather than adding the requirements
     * together. The default implementation returns {@code 0}.
     *
     * @return minimum number of peer-initiated unidirectional streams
     */
    default long minimumPeerUniStreams() {
        return 0;
    }

    /**
     * Formatter for application error codes used by this runtime.
     *
     * @return error formatter
     */
    default LongFunction<String> applicationErrors() {
        return QuicSubProtocolRuntime::defaultApplicationErrorToString;
    }

    /**
     * Accept a negotiated QUIC connection for this runtime.
     *
     * @param connection accepted QUIC connection
     */
    void accept(QuicConnection connection);

    /**
     * Start the runtime once its owning listener binding is ready.
     */
    default void start() {
    }

    /**
     * Close the runtime gracefully.
     *
     * @param gracePeriod grace period to use
     * @return shutdown result
     */
    default TransportBinding.ShutdownResult closeGracefully(Duration gracePeriod) {
        Objects.requireNonNull(gracePeriod, "gracePeriod");
        close();
        return TransportBinding.ShutdownResult.GRACEFUL;
    }

    @Override
    default void close() {
    }
}
