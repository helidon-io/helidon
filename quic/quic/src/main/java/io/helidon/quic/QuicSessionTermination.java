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

import java.util.Optional;
import java.util.OptionalLong;

import io.helidon.common.Api;

/**
 * Sanitized immutable observation of QUIC session termination.
 */
@Api.Incubating
public interface QuicSessionTermination {

    /**
     * Endpoint which initiated termination.
     *
     * @return termination origin
     */
    Origin origin();

    /**
     * Termination kind.
     *
     * @return termination kind
     */
    Kind kind();

    /**
     * Protocol layer which owns the close code.
     *
     * @return close-code layer
     */
    Layer layer();

    /**
     * Close error code.
     *
     * @return close code, or empty when no close frame supplied one
     */
    OptionalLong errorCode();

    /**
     * Associated stream identifier.
     *
     * @return stream identifier when known
     */
    OptionalLong streamId();

    /**
     * Sanitized detail explicitly disclosed to the peer by this endpoint.
     *
     * @return outgoing peer detail
     */
    Optional<String> outgoingDetail();

    /**
     * Reason received from the peer.
     *
     * <p>This is untrusted peer input and is not included in {@link #diagnostic()}.
     *
     * @return peer reason
     */
    Optional<String> peerReason();

    /**
     * Safe local diagnostic text.
     *
     * @return local diagnostic
     */
    String diagnostic();

    /**
     * Termination origin.
     */
    enum Origin {
        /**
         * Selected by this endpoint.
         */
        LOCAL,
        /**
         * Observed from the peer.
         */
        PEER
    }

    /**
     * Termination kind.
     */
    enum Kind {
        /**
         * QUIC connection-close frame.
         */
        CONNECTION_CLOSE,
        /**
         * Local silent termination.
         */
        SILENT,
        /**
         * Peer stateless reset.
         */
        STATELESS_RESET
    }

    /**
     * Protocol layer which owns a close code.
     */
    enum Layer {
        /**
         * QUIC transport.
         */
        TRANSPORT,
        /**
         * Application protocol.
         */
        APPLICATION
    }
}
