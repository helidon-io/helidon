/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

import io.helidon.common.Api;

/**
 * Responsible for managing the connection termination of a QUIC connection.
 */
@Api.Internal
public sealed interface ConnectionTerminator permits ConnectionTerminatorImpl {

    /**
     * Records that a packet from the peer was processed successfully, restarting the
     * idle timeout and permitting one subsequent ACK-eliciting send to restart it again.
     */
    void peerPacketProcessed();

    /**
     * Records that an ACK-eliciting packet was sent. The idle timeout is restarted only
     * for the first such packet sent after a peer packet was processed successfully.
     */
    void ackElicitingPacketSent();

    /**
     * Terminates the connection, if not already terminated, with the given command.
     * <p>
     * A connection is terminated only once. However, this method
     * can be called any number of times. If the connection is not already terminated,
     * then this method does the necessary work to terminate the connection. Any subsequent
     * invocations of this method, after the connection has been terminated, will not
     * change the selected termination.
     *
     * @param command local close command
     */
    void terminate(QuicCloseCommand command);

    /**
     * Returns {@code true} if the connection is allowed for use, {@code false} otherwise.
     * <p>
     * This method is typically called when a connection that has been idle, is about to be used
     * for handling some request. This method allows for co-ordination between the connection usage
     * and the connection terminator to prevent the connection from being idle timed out when it is
     * about to be used for some request. The connection must only be used if this method
     * returns {@code true}.
     *
     * @return true if the connection can be used, false otherwise
     */
    boolean tryReserveForUse();

    /**
     * Instructs the connection terminator that the application layer allows the
     * connection to stay idle for the given {@code maxIdle} duration. If the QUIC
     * layer has negotiated an idle timeout for the connection, that's lower than
     * the application's {@code maxIdle} duration, then the connection terminator
     * upon noticing absence of traffic over the connection for certain duration,
     * calls the {@code trafficGenerationCheck} to check if the QUIC layer should
     * explicitly generate some traffic to prevent the connection
     * from idle terminating.
     * <p>
     * When the {@code trafficGenerationCheck} is invoked, the application layer
     * must return {@code true} only if explicit traffic generation is necessary
     * to keep the connection alive.
     * <p>
     * If the application layer wishes to never idle terminate the connection, then
     * a {@code maxIdle} duration of {@linkplain Duration#MAX Duration.MAX} is recommended.
     *
     * @param maxIdle                the maximum idle duration of the connection,
     *                              at the application layer
     * @param trafficGenerationCheck the callback that will be invoked by the connection
     *                              terminator to decide if the QUIC layer should generate
     *                              any traffic to prevent the connection from idle terminating
     * @throws NullPointerException     if either {@code maxIdle} or {@code trafficGenerationCheck}
     *                                 is null
     * @throws IllegalArgumentException if {@code maxIdle} is
     *                                 {@linkplain Duration#isNegative() negative} or
     *                                 {@linkplain Duration#isZero() zero}
     */
    void appLayerMaxIdle(Duration maxIdle, Supplier<Boolean> trafficGenerationCheck);
}
