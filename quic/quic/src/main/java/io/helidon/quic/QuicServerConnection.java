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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLParameters;

import io.helidon.common.buffers.BufferData;
import io.helidon.quic.QuicTransportParameters.ParameterId;
import io.helidon.quic.frame.NewTokenFrame;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;

final class QuicServerConnection extends QuicConnectionImpl {
    private static final System.Logger LOGGER = System.getLogger(QuicServerConnection.class.getName());

    private final QuicServerRuntime runtime;
    private final AtomicBoolean accepted = new AtomicBoolean();
    private volatile QuicConnectionId admittedInitialDestinationId;
    private volatile QuicConnectionId originalDestinationConnectionId;
    private volatile QuicConnectionId retrySourceConnectionId;
    private volatile byte[] expectedInitialToken = BufferData.EMPTY_BYTES;

    QuicServerConnection(QuicVersion firstFlightVersion,
                         QuicServerRuntime runtime,
                         QuicRuntimeConfig runtimeConfig,
                         InetSocketAddress peerAddress,
                         SSLParameters sslParameters,
                         QuicTLSContext quicTLSContext,
                         long labelId) {
        super(firstFlightVersion,
              runtime,
              runtimeConfig,
              peerAddress,
              peerAddress.getHostString(),
              peerAddress.getPort(),
              sslParameters,
              quicTLSContext,
              "QuicServerConnection(%s)",
              labelId);
        this.runtime = runtime;
    }

    void initialize(QuicConnectionId admittedInitialDestinationId,
                    QuicConnectionId originalDestinationConnectionId,
                    QuicConnectionId retrySourceConnectionId,
                    byte[] expectedInitialToken,
                    boolean addressValidated) {
        this.admittedInitialDestinationId = admittedInitialDestinationId;
        this.originalDestinationConnectionId = originalDestinationConnectionId;
        this.retrySourceConnectionId = retrySourceConnectionId;
        this.expectedInitialToken = expectedInitialToken.clone();
        initializeServerHandshake(admittedInitialDestinationId);
        if (addressValidated) {
            pathManager().addressValidated(peerAddress());
        }
    }

    @Override
    public boolean isClientConnection() {
        return false;
    }

    @Override
    public List<QuicConnectionId> connectionIds() {
        List<QuicConnectionId> ids = new ArrayList<>(super.connectionIds());
        QuicConnectionId admittedInitialDestination = admittedInitialDestinationId;
        if (admittedInitialDestination != null) {
            ids.add(admittedInitialDestination);
        }
        return List.copyOf(ids);
    }

    @Override
    public Optional<QuicConnectionId> initialConnectionId() {
        return Optional.ofNullable(admittedInitialDestinationId);
    }

    @Override
    protected void customizeInitialParameters(QuicTransportParameters params) {
        QuicConnectionId originalDestination = originalDestinationConnectionId;
        if (originalDestination != null && !params.isPresent(ParameterId.original_destination_connection_id)) {
            params.parameter(ParameterId.original_destination_connection_id, originalDestination.bytes());
        }
        QuicConnectionId retrySource = retrySourceConnectionId;
        if (retrySource != null && !params.isPresent(ParameterId.retry_source_connection_id)) {
            params.parameter(ParameterId.retry_source_connection_id, retrySource.bytes());
        }
    }

    @Override
    protected boolean verifyToken(QuicConnectionId destinationId, byte[] token) {
        QuicConnectionId admittedDestination = admittedInitialDestinationId;
        // RFC 9000, Section 7.2 requires subsequent Initial packets to use the server's source connection ID.
        // Endpoint routing has already matched an active local connection ID, so only the token remains to be verified.
        return admittedDestination != null
                && MessageDigest.isEqual(expectedInitialToken, token);
    }

    @Override
    protected void completeHandshakeCF() {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(LOGGER,
                System.Logger.Level.DEBUG,
                "server completeHandshakeCF invoked with tlsState=%s, accepted=%s",
                tlsEngine().handshakeState(),
                accepted.get());
        }
        if (accepted.compareAndSet(false, true)) {
            if (!runtime.handshakeSucceeded(this)) {
                return;
            }
            successfulHandshakeCF().whenComplete((_, failure) -> {
                if (failure != null) {
                    terminate(QuicCloseCommand.transport(failure));
                    return;
                }
                try {
                    runtime.newToken(peerAddress(), quicVersion()).ifPresent(token -> {
                        enqueue1RTTFrame(NewTokenFrame.create(ByteBuffer.wrap(token)));
                        packetSpace(PacketNumberSpace.APPLICATION).runTransmitter();
                    });
                } catch (RuntimeException e) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(LOGGER, System.Logger.Level.DEBUG, "%s", e, "failed to issue NEW_TOKEN");
                    }
                }
                runtime.acceptedConnection(this);
            });
            super.completeHandshakeCF();
        }
    }
}
