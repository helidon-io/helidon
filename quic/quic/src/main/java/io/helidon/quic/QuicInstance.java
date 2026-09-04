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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLParameters;

import io.helidon.common.Api;
import io.helidon.quic.packet.QuicPacket;

/**
 * A {@code QuicInstance} represents a common abstraction which is
 * either a client-side or server-side QUIC runtime. It defines the
 * subset of public methods that a {@code QuicEndpoint} and a
 * {@code QuicSelector} need to operate with that runtime.
 */
@Api.Internal
public interface QuicInstance {

    /**
     * The executor used by this quic instance when a task needs to
     * be offloaded to a separate thread.
     *
     * @return the executor used by this QUIC instance
     */
    Executor executor();

    /**
     * Returns an endpoint to associate with a connection.
     *
     * @return an endpoint to associate with a connection
     * @throws java.io.UncheckedIOException if the endpoint cannot be created or acquired
     */
    QuicEndpoint endpoint();

    /**
     * This method is called when a quic packet that couldn't be attributed
     * to a registered connection is received.
     *
     * @param source the source address of the datagram
     * @param type   the packet type
     * @param buffer the complete datagram payload, positioned at the start of its first quic packet
     */
    void unmatchedQuicPacket(SocketAddress source, QuicPacket.HeadersType type, ByteBuffer buffer);

    /**
     * Notifies this runtime that an owned transport component failed fatally.
     *
     * @param failure fatal transport failure
     */
    void runtimeFailed(Throwable failure);

    /**
     * Checks whether the passed version is available for use on this instance.
     *
     * @param quicVersion version to test
     * @return true if the passed version is available for use on this instance, false otherwise
     */
    boolean isVersionAvailable(QuicVersion quicVersion);

    /**
     * Returns the versions that are available for use on this instance.
     *
     * @return the versions that are available for use on this instance
     */
    List<QuicVersion> availableVersions();

    /**
     * Checks whether this instance initiates client-side handshakes.
     *
     * @return true if this instance initiates client-side handshakes, false otherwise
     */
    default boolean isClient() {
        return false;
    }

    /**
     * Instance ID used for debugging traces.
     *
     * @return A string uniquely identifying this instance.
     */
    String instanceId();

    /**
     * Returns the QuicTLSContext used by this QUIC instance.
     *
     * @return the QuicTLSContext used by this QUIC instance
     */
    QuicTLSContext quicTlsContext();

    /**
     * Returns the transport parameters this instance advertises to peers.
     *
     * @return the transport parameters this instance advertises to peers
     */
    default QuicTransportParameters transportParameters() {
        return QuicTransportParametersConfigSupport.createTransportParameters(quicConfig().transportParameters());
    }

    /**
     * Returns a cached token that should be attached to a future Initial packet sent to the given
     * peer.
     *
     * @param peerAddress peer address
     * @return initial token to use for the peer, or {@link Optional#empty()} if none is available
     */
    default Optional<byte[]> initialTokenFor(InetSocketAddress peerAddress) {
        return Optional.empty();
    }

    /**
     * Returns a cached token for a future Initial packet sent to the given peer using the given QUIC version.
     *
     * @param peerAddress peer address
     * @param version QUIC version
     * @return initial token to use for the peer and version, or {@link Optional#empty()} if none is available
     */
    default Optional<byte[]> initialTokenFor(InetSocketAddress peerAddress, QuicVersion version) {
        return initialTokenFor(peerAddress);
    }

    /**
     * Registers a token learned from a peer for use in a later Initial packet.
     *
     * @param peerAddress peer address
     * @param token       initial token to cache
     */
    default void registerInitialToken(InetSocketAddress peerAddress, byte[] token) {
    }

    /**
     * Registers a token learned from a peer for use in a later Initial packet with the given QUIC version.
     *
     * @param peerAddress peer address
     * @param version QUIC version
     * @param token initial token to cache
     */
    default void registerInitialToken(InetSocketAddress peerAddress, QuicVersion version, byte[] token) {
        registerInitialToken(peerAddress, token);
    }

    /**
     * Registers a token received in a {@code NEW_TOKEN} frame for use in a later Initial packet with the given QUIC
     * version.
     *
     * <p>The default implementation delegates to {@link #registerInitialToken(InetSocketAddress, QuicVersion, byte[])}.
     * A client runtime can override this method to discard duplicate {@code NEW_TOKEN} values while retaining explicit
     * token registration as a restore or injection hook.
     *
     * @param peerAddress peer address
     * @param version QUIC version
     * @param token initial token to cache
     */
    default void registerNewToken(InetSocketAddress peerAddress, QuicVersion version, byte[] token) {
        registerInitialToken(peerAddress, version, token);
    }

    /**
     * Returns the {@link SSLParameters} for this QUIC instance.
     *
     * @return the {@code SSLParameters} for this QUIC instance
     * @implSpec The default implementation of this method returns a new {@link SSLParameters} instance.
     */
    default SSLParameters sslParameters() {
        return new SSLParameters();
    }

    /**
     * Returns the QUIC transport configuration for this instance.
     *
     * @return the QUIC transport configuration for this instance
     */
    default QuicConfig quicConfig() {
        return QuicConfig.create();
    }

    /**
     * Returns the configured {@linkplain java.net.StandardSocketOptions#SO_RCVBUF
     * UDP receive buffer} size this instance should use.
     *
     * @return the configured UDP receive buffer size this instance should use
     */
    default int receiveBufferSize() {
        return quicConfig().socketReceiveBufferSize().orElse(0);
    }

    /**
     * Returns the configured {@linkplain java.net.StandardSocketOptions#SO_SNDBUF
     * UDP send buffer} size this instance should use.
     *
     * @return the configured UDP send buffer size this instance should use
     */
    default int sendBufferSize() {
        return quicConfig().socketSendBufferSize().orElse(0);
    }

    /**
     * Returns a string describing the given application error code.
     * <p>
     * This method is typically used for logging and/or debugging purposes, to generate a more
     * user-friendly log message.
     *
     * @param errorCode an application error code
     * @return non-null string describing the given application error code
     * @implSpec By default, this method returns a generic
     *        string containing the hexadecimal value of the given errorCode.
     *        Subclasses built for supporting a given application protocol,
     *        such as HTTP/3, may override this method to return more
     *        specific names, such as for instance, {@code "H3_REQUEST_CANCELLED"}
     *        for {@code 0x010c}.
     */
    default String appErrorToString(long errorCode) {
        return "ApplicationError(code=0x" + HexFormat.of().toHexDigits(errorCode) + ")";
    }

    /**
     * Returns a human-readable name for this instance.
     *
     * @return a human-readable name for this instance
     */
    default String name() {
        return String.format("%s(%s)", this.getClass().getSimpleName(), instanceId());
    }

}
