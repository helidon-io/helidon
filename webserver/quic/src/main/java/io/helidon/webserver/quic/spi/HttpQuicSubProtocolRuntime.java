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

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.quic.QuicConnection;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.webserver.SniContext;

/**
 * HTTP-specific capability of an application protocol hosted on a QUIC listener.
 *
 * <p>The owning listener supplies the physical HTTP connection observation. The runtime owns that borrowed observation
 * only for publishing protocol and stream lifecycle; the listener closes it when the physical QUIC connection terminates.
 */
@Api.Internal
public interface HttpQuicSubProtocolRuntime extends QuicSubProtocolRuntime {
    /**
     * Accept a negotiated QUIC connection and its HTTP transport observation.
     *
     * @param connection accepted QUIC connection
     * @param observation borrowed physical HTTP connection observation
     */
    void accept(QuicConnection connection, ConnectionObservation observation);

    /**
     * Accept a negotiated QUIC connection with its selected SNI context.
     *
     * @param connection accepted QUIC connection
     * @param observation borrowed physical HTTP connection observation
     * @param sniContext SNI context selected during the TLS handshake
     * @throws UnsupportedOperationException unless the implementation supports SNI-aware HTTP policy
     */
    default void accept(QuicConnection connection,
                        ConnectionObservation observation,
                        SniContext sniContext) {
        Objects.requireNonNull(sniContext, "sniContext");
        throw new UnsupportedOperationException("HTTP QUIC sub-protocol does not support SNI context");
    }

    @Override
    default void accept(QuicConnection connection) {
        accept(connection, ConnectionObservation.noop());
    }

    /**
     * Returns whether a QUIC termination is a normal close for this HTTP application protocol.
     *
     * <p>The default recognizes a silent local close and QUIC/application error code zero. Protocols whose normal
     * application close code is non-zero must override this method.
     *
     * @param termination immutable QUIC termination
     * @return {@code true} for a normal application-protocol close
     */
    default boolean isNormalTermination(QuicTermination termination) {
        return defaultNormalTermination(termination);
    }

    /**
     * Returns whether a QUIC termination is a normal close under the default HTTP classification.
     *
     * @param termination immutable QUIC termination
     * @return {@code true} for a normal default close
     */
    static boolean defaultNormalTermination(QuicTermination termination) {
        Objects.requireNonNull(termination, "termination");
        return termination.cause().isEmpty()
                && (termination.kind() == QuicTermination.Kind.SILENT
                        || termination.errorCode().orElse(-1) == QuicTransportErrors.NO_ERROR.code());
    }
}
