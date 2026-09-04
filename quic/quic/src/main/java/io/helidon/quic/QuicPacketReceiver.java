/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.quic.QuicEndpoint.QuicDatagram;
import io.helidon.quic.packet.QuicPacket;

/**
 * The {@code QuicPacketReceiver} is an abstraction that defines the
 * interface between a {@link QuicEndpoint} and a {@link QuicConnection}.
 * This defines the minimum set of methods that the endpoint will need
 * in order to be able to dispatch a received {@link io.helidon.quic.packet.QuicPacket}
 * to its destination. This abstraction is typically useful when dealing with
 * {@linkplain QuicEndpoint.ClosedConnection
 * closed connections, which need to remain alive for a certain time
 * after being closed in order to satisfy the requirement of the quic
 * protocol (typically for retransmitting the CLOSE_CONNECTION frame
 * if needed)}.
 */
@Api.Internal
public interface QuicPacketReceiver {

    /**
     * Returns the local connection IDs for this connection.
     *
     * @return local connection IDs for this connection
     */
    List<QuicConnectionId> connectionIds();

    /**
     * Returns active peer stateless reset tokens for this connection.
     *
     * @return active peer stateless reset tokens for this connection
     */
    List<PeerResetToken> activeResetTokens();

    /**
     * Returns the initial connection ID assigned by the peer.
     * On the client side, this is always {@link Optional#empty()}.
     * On the server side, it contains the initial connection id
     * that was assigned by the client in the first INITIAL packet.
     *
     * @return initial connection ID assigned by the peer
     * @implSpec The default implementation of this method returns {@link Optional#empty()}
     */
    default Optional<QuicConnectionId> initialConnectionId() {
        return Optional.empty();
    }

    /**
     * Called when an incoming datagram is received.
     * <p>
     * The buffer is positioned at the start of the datagram to process.
     * The buffer may contain more than one QUIC packet.
     *
     * @param source      The peer address, as received from the UDP stack
     * @param destConnId  Destination connection id bytes included in the packet
     * @param headersType The quic packet type
     * @param buffer      A buffer positioned at the start of the quic packet,
     *                   not yet decrypted, and possibly containing coalesced
     *                   packets.
     */
    void processIncoming(SocketAddress source, ByteBuffer destConnId,
                         QuicPacket.HeadersType headersType, ByteBuffer buffer);

    /**
     * Called when a datagram scheduled for writing by this connection
     * could not be written to the network.
     *
     * @param t the error that occurred
     */
    void onWriteError(Throwable t);

    /**
     * Called when a stateless reset token is received.
     */
    void processStatelessReset();

    /**
     * Called to shut a closed connection down.
     * This is the last step when closing a connection, and typically
     * only release resources held by all packet spaces.
     */
    void shutdown();

    /**
     * Called after a datagram has been written to the socket.
     * At this point the datagram's ByteBuffer can typically be released,
     * or returned to a buffer pool.
     *
     * @param datagram the datagram that was sent
     * @implSpec The default implementation of this method does nothing.
     */
    default void datagramSent(QuicDatagram datagram) {
    }

    /**
     * Called after a datagram has been discarded as a result of
     * some error being raised, for instance, when an attempt
     * to write it to the socket has failed, or if the encryption
     * of a packet in the datagram has failed.
     * At this point the datagram's ByteBuffer can typically be released,
     * or returned to a buffer pool.
     *
     * @param datagram the datagram that was discarded
     * @implSpec The default implementation of this method does nothing.
     */
    default void datagramDiscarded(QuicDatagram datagram) {
    }

    /**
     * Called after a datagram has been dropped. Typically, this
     * could happen if the datagram was only partly written, or if
     * the connection was closed before the datagram could be sent.
     * At this point the datagram's ByteBuffer can typically be released,
     * or returned to a buffer pool.
     *
     * @param datagram the datagram that was dropped
     * @implSpec The default implementation of this method does nothing.
     */
    default void datagramDropped(QuicDatagram datagram) {
    }

    /**
     * Returns whether this receiver accepts packets from the given source.
     *
     * @param source the sender address
     * @return whether this receiver accepts packets from the given source
     */
    default boolean accepts(SocketAddress source) {
        return true;
    }

    /**
     * A peer-issued stateless reset token and the remote address on which its
     * connection ID was used.
     */
    final class PeerResetToken {
        private static final int TOKEN_LENGTH = 16;

        private final byte[] token;
        private final SocketAddress peerAddress;

        private PeerResetToken(byte[] token, SocketAddress peerAddress) {
            byte[] checkedToken = Objects.requireNonNull(token, "token");
            if (checkedToken.length != TOKEN_LENGTH) {
                throw new IllegalArgumentException("Invalid stateless reset token length " + checkedToken.length);
            }
            this.token = checkedToken.clone();
            this.peerAddress = Objects.requireNonNull(peerAddress, "peerAddress");
        }

        /**
         * Creates a token/address association.
         *
         * @param token       16-byte stateless reset token
         * @param peerAddress peer address associated with the token
         * @return token/address association
         * @throws IllegalArgumentException if the token is not 16 bytes long
         */
        public static PeerResetToken create(byte[] token, SocketAddress peerAddress) {
            return new PeerResetToken(token, peerAddress);
        }

        /**
         * Returns the reset token.
         *
         * @return reset token
         */
        public byte[] token() {
            return token.clone();
        }

        /**
         * Returns the associated peer address.
         *
         * @return peer address
         */
        public SocketAddress peerAddress() {
            return peerAddress;
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(token) + peerAddress.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof PeerResetToken other
                    && Arrays.equals(token, other.token)
                    && peerAddress.equals(other.peerAddress);
        }
    }
}
