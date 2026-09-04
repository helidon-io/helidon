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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import io.helidon.common.Api;
import io.helidon.common.socket.SocketContext;

/**
 * Handshake-complete QUIC session for an application protocol.
 *
 * <p>The session owns only application use of one connection. Its creating {@link QuicClient} or {@link QuicServer} owns
 * the UDP transport, timers, TLS state, and connection registry. Stream operations block and are intended for virtual
 * threads.
 *
 * <p>Explicit stream-credit timeouts and close error codes are validated when the operation is invoked, before it changes
 * QUIC state.
 */
@Api.Incubating
public interface QuicSession extends SocketContext {

    /**
     * Application protocol negotiated through ALPN.
     *
     * @return negotiated application protocol
     */
    String applicationProtocol();

    /**
     * Negotiated QUIC version.
     *
     * @return negotiated version
     */
    QuicVersion quicVersion();

    /**
     * Opens a locally initiated bidirectional stream using the configured stream-open timeout.
     *
     * @return opened stream
     */
    QuicBidirectionalStream openBidirectionalStream();

    /**
     * Opens a locally initiated bidirectional stream.
     *
     * <p>The timeout must be positive and fit in signed 64-bit nanoseconds.
     *
     * @param streamCreditTimeout maximum duration to wait for peer stream credit
     * @return opened stream
     * @throws IllegalArgumentException if the timeout is not positive or does not fit in signed 64-bit nanoseconds
     */
    QuicBidirectionalStream openBidirectionalStream(Duration streamCreditTimeout);

    /**
     * Opens a locally initiated unidirectional stream using the configured stream-open timeout.
     *
     * @return opened stream
     */
    QuicSendStream openUnidirectionalStream();

    /**
     * Opens a locally initiated unidirectional stream.
     *
     * <p>The timeout must be positive and fit in signed 64-bit nanoseconds.
     *
     * @param streamCreditTimeout maximum duration to wait for peer stream credit
     * @return opened stream
     * @throws IllegalArgumentException if the timeout is not positive or does not fit in signed 64-bit nanoseconds
     */
    QuicSendStream openUnidirectionalStream(Duration streamCreditTimeout);

    /**
     * Waits for and claims the next remotely initiated stream.
     *
     * <p>Only one accept operation may be outstanding for a session. A concurrent call is rejected immediately.
     *
     * @return next remote stream
     */
    QuicReceiveStream acceptStream();

    /**
     * Whether the session is open for application use.
     *
     * @return {@code true} when open
     */
    boolean isOpen();

    /**
     * Selected termination, when termination has started.
     *
     * @return selected termination
     */
    Optional<QuicSessionTermination> termination();

    /**
     * Stage completed after connection termination cleanup.
     *
     * @return read-only termination stage
     */
    CompletionStage<QuicSessionTermination> whenTerminated();

    /**
     * Gracefully closes the session with no peer detail.
     *
     * <p>The application error code must be between {@code 0} and 2<sup>62</sup> - 1, inclusive.
     *
     * @param applicationErrorCode application protocol error code
     * @return this session
     * @throws IllegalArgumentException if the application error code is outside the QUIC application error-code range
     */
    QuicSession close(long applicationErrorCode);

    /**
     * Gracefully closes the session and explicitly discloses bounded, sanitized detail to the peer.
     *
     * <p>The detail is peer-visible. It must not contain secrets or untrusted exception text.
     *
     * <p>The application error code must be between {@code 0} and 2<sup>62</sup> - 1, inclusive.
     *
     * @param applicationErrorCode application protocol error code
     * @param peerDetail detail to disclose to the peer
     * @return this session
     * @throws IllegalArgumentException if the application error code is outside the QUIC application error-code range
     */
    QuicSession close(long applicationErrorCode, String peerDetail);
}
