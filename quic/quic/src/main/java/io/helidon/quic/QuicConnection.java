/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

import io.helidon.common.Api;
import io.helidon.common.socket.SocketContext;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicBidiStreamReservation;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;

/**
 * Semantic view of a QUIC connection used by an application protocol.
 *
 * <p>The transport retains ownership of packet processing, endpoints, congestion control, connection IDs, and TLS engine
 * state. Application protocols use this interface to open and accept streams, observe connection lifecycle, and initiate
 * connection termination.
 */
@Api.Internal
public interface QuicConnection extends SocketContext {

    /**
     * Returns the application protocol negotiated through ALPN.
     *
     * @return negotiated application protocol, or empty before negotiation completes
     */
    Optional<String> applicationProtocol();

    /**
     * Returns the active QUIC version.
     *
     * @return active QUIC version
     */
    QuicVersion quicVersion();

    /**
     * Creates a new locally initiated bidirectional stream.
     *
     * <p>Creation is limited by the maximum stream count advertised by the peer. If the limit is reached, the returned future
     * waits up to {@code limitIncreaseDuration} for additional stream credit.
     *
     * @param limitIncreaseDuration maximum duration to wait for stream credit
     * @return future completed with the new stream, or exceptionally when the stream cannot be opened
     */
    CompletableFuture<QuicBidiStream> openNewLocalBidiStream(Duration limitIncreaseDuration);

    /**
     * Reserves credit for a new locally initiated bidirectional stream.
     *
     * <p>The returned future waits without a transport-defined deadline when peer-advertised stream credit is
     * exhausted. Canceling the future cancels that pending credit request. A successful reservation must either be
     * {@linkplain QuicBidiStreamReservation#open() opened} or {@linkplain QuicBidiStreamReservation#close() closed}.
     *
     * @return future completed with reserved bidirectional-stream credit
     */
    CompletableFuture<QuicBidiStreamReservation> reserveNewLocalBidiStream();

    /**
     * Creates a new locally initiated unidirectional stream.
     *
     * <p>Creation is limited by the maximum stream count advertised by the peer. If the limit is reached, the returned future
     * waits up to {@code limitIncreaseDuration} for additional stream credit.
     *
     * @param limitIncreaseDuration maximum duration to wait for stream credit
     * @return future completed with the new stream, or exceptionally when the stream cannot be opened
     */
    CompletableFuture<QuicSenderStream> openNewLocalUniStream(Duration limitIncreaseDuration);

    /**
     * Registers a listener for remotely initiated streams.
     *
     * <p>The listener is first offered any unclaimed remote streams that are already open. These callbacks run synchronously
     * and may occur before this method returns. Returning {@code true} claims a stream for the listener. Closing the returned
     * registration prevents later callbacks.
     *
     * @param listener remote-stream listener
     * @return owned listener registration
     */
    QuicRemoteStreamRegistration addRemoteStreamListener(Predicate<? super QuicReceiverStream> listener);

    /**
     * Returns whether this connection is open for application use.
     *
     * @return {@code true} when the connection is open
     */
    boolean isOpen();

    /**
     * Returns the termination selected for this connection.
     *
     * @return selected termination, or empty while the connection is open
     */
    Optional<QuicTermination> termination();

    /**
     * Returns a read-only stage completed after connection termination cleanup finishes.
     * The stage completes with the selected immutable termination when cleanup succeeds,
     * or exceptionally when cleanup fails. Completion does not depend on a configured executor.
     *
     * @return connection termination stage
     */
    CompletionStage<QuicTermination> whenTerminated();

    /**
     * Terminates the connection if termination has not already started.
     *
     * @param command termination command
     */
    void terminate(QuicCloseCommand command);
}
