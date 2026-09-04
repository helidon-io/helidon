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

import java.nio.BufferOverflowException;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;

/**
 * QUIC TLS engine for one QUIC connection.
 * <p>
 * This is Helidon's temporary abstraction until the JDK exposes public QUIC TLS support.
 * At that point the transport should drive the JDK engine using
 * {@code SSLEngine#beginHandshake()}, {@code SSLEngine#wrap(empty, cryptoData)}, and
 * {@code SSLEngine#unwrap(payload, empty)} from the engine returned by
 * {@code SSLContext#createQUICEngine(peerHost, peerPort, QuicTLSCallbacks)}.
 */
@Api.Internal
public interface QuicTLSEngine {

    /**
     * Returns the QUIC versions supported by this engine.
     *
     * @return the QUIC versions supported by this engine
     */
    Set<QuicVersion> supportedQuicVersions();

    /**
     * Checks whether this QuicTLSEngine is operating in client mode.
     *
     * @return true if this QuicTLSEngine is operating in client mode, false otherwise
     */
    boolean clientMode();

    /**
     * If {@code mode} is {@code true} then configures this QuicTLSEngine to
     * operate in client mode. If {@code false}, then this QuicTLSEngine
     * operates in server mode.
     *
     * @param mode true to make this QuicTLSEngine operate in client
     *            mode, false otherwise
     */
    void clientMode(boolean mode);

    /**
     * Returns the SSLParameters in effect for this engine.
     *
     * @return the SSLParameters in effect for this engine
     */
    SSLParameters sslParameters();

    /**
     * Sets the {@code SSLParameters} to be used by this engine.
     *
     * @param sslParameters the SSLParameters
     * @throws IllegalArgumentException if
     *                                 {@linkplain SSLParameters#getProtocols() TLS protocol versions} on the
     *                                 {@code sslParameters} is either empty or contains anything other
     *                                 than {@code TLSv1.3}, or if the parameters configure
     *                                 {@linkplain SSLParameters#getAlgorithmConstraints() algorithm constraints}
     *                                 or non-empty {@linkplain SSLParameters#getSNIMatchers() SNI matchers}, which
     *                                 the Helidon QUIC TLS engine does not support
     * @throws NullPointerException     if {@code sslParameters} is null
     */
    void sslParameters(SSLParameters sslParameters);

    /**
     * Returns the most recent application protocol value negotiated by the
     * engine.
     *
     * @return the most recent application protocol value negotiated by the engine
     */
    Optional<String> applicationProtocol();

    /**
     * Returns the SSLSession.
     *
     * @return the SSLSession
     * @see SSLEngine#getSession()
     */
    SSLSession session();

    /**
     * Returns the SSLSession being constructed during a QUIC handshake.
     *
     * @return handshake session when the current handshake has progressed far enough to create it,
     *        otherwise {@link Optional#empty()}
     * @see SSLEngine#getHandshakeSession()
     */
    Optional<SSLSession> handshakeSession();

    /**
     * Returns the current handshake state of the connection. Sometimes packets
     * that could be decrypted can be received before the handshake has
     * completed, but should not be decrypted until it is complete.
     *
     * @return the HandshakeState
     */
    HandshakeState handshakeState();

    /**
     * Returns true if the TLS handshake is considered complete.
     * <p>
     * The TLS handshake is considered complete when the TLS stack
     * has reported that the handshake is complete. This happens when
     * the TLS stack has both sent a {@code Finished} message and verified
     * the peer's {@code Finished} message.
     *
     * @return true if TLS handshake is complete, false otherwise.
     */
    boolean isTLSHandshakeComplete();

    /**
     * Returns the current sending key space (encryption level).
     *
     * @return the current sending key space
     */
    KeySpace currentSendKeySpace();

    /**
     * Checks whether the keys for the given key space are available.
     * <p>
     * Keys are available when they are already computed and not discarded yet.
     *
     * @param keySpace key space to check
     * @return true if the given keys are available
     */
    boolean keysAvailable(KeySpace keySpace);

    /**
     * Discard the keys used by the {@code keySpace}.
     * <p>
     * Once the keys for a particular {@code keySpace} have been discarded, the
     * keySpace will no longer be able to
     * {@linkplain #encryptPacket(KeySpace, long, IntFunction,
     * BufferData, BufferData) encrypt} or
     * {@linkplain #decryptPacket(KeySpace, long, int, BufferData, int, BufferData)
     * decrypt} packets.
     *
     * @param keySpace The keyspace whose current keys should be discarded
     */
    void discardKeys(KeySpace keySpace);

    /**
     * Provide quic_transport_parameters for inclusion in handshake message.
     * <p>
     * Future JDK migration point: this should move to
     * {@code QuicTLSCallbacks#localQuicTransportParameters()}.
     *
     * @param params encoded quic_transport_parameters
     */
    void localQuicTransportParameters(BufferData params);

    /**
     * Reset the handshake state and produce a new ClientHello message.
     *
     * When a Quic client receives a Version Negotiation packet,
     * it restarts the handshake by calling this method after updating the
     * {@linkplain #localQuicTransportParameters(BufferData) transport parameters}
     * with the new version information.
     *
     * @throws IllegalStateException if handshake restart fails in the underlying TLS engine
     */
    void restartHandshake();

    /**
     * Set consumer for quic_transport_parameters sent by the remote side.
     * Consumer will receive a byte buffer containing the value of
     * quic_transport_parameters extension sent by the remote endpoint.
     * <p>
     * Future JDK migration point: this should move to
     * {@code QuicTLSCallbacks#remoteQuicTransportParameters(ByteBuffer)}.
     *
     * @param consumer consumer for remote quic transport parameters
     */
    void remoteQuicTransportParametersConsumer(
            QuicTransportParametersConsumer consumer);

    /**
     * Derive initial keys for the given QUIC version and connection ID.
     *
     * @param quicVersion  QUIC protocol version
     * @param connectionId initial destination connection ID
     * @throws IllegalArgumentException if the {@code quicVersion} isn't
     *                                 {@linkplain #supportedQuicVersions() supported} on this
     *                                 {@code QuicTLSEngine}
     * @throws IllegalStateException    if key derivation fails
     */
    void deriveInitialKeys(QuicVersion quicVersion, BufferData connectionId);

    /**
     * Returns the sample size for the header protection algorithm.
     *
     * @param keySpace Packet key space
     * @return required sample size for header protection
     * @throws IllegalArgumentException when keySpace does not require
     *                                 header protection
     */
    int headerProtectionSampleSize(KeySpace keySpace);

    /**
     * Compute the header protection mask for the given sample,
     * packet key space and direction (incoming/outgoing).
     *
     * @param keySpace Packet key space
     * @param incoming true for incoming packets, false for outgoing
     * @param sample   sampled data
     * @return mask bytes, at least 5.
     * @throws IllegalArgumentException    when keySpace does not require
     *                                    header protection or sample length is different from required
     * @throws QuicKeyUnavailableException if keys for the given key space are unavailable
     * @throws QuicTransportException      if header-protection processing exceeds cryptographic limits
     * <p>Specification: https://www.rfc-editor.org/rfc/rfc9001.html#name-header-protection-applicati
     *        RFC 9001, Section 5.4.1 Header Protection Application
     * @see #headerProtectionSampleSize(KeySpace)
     */
    BufferData computeHeaderProtectionMask(KeySpace keySpace,
                                           boolean incoming, BufferData sample)
            throws QuicKeyUnavailableException, QuicTransportException;

    /**
     * Returns the authentication tag size. Encryption adds this number of bytes.
     *
     * @return authentication tag size
     */
    int authTagSize();

    /**
     * Encrypt into {@code output}, the given {@code packetPayload} bytes using the
     * keys for the given {@code keySpace}.
     * <p>
     * Before encrypting the {@code packetPayload}, this method invokes the {@code headerGenerator}
     * passing it the key phase corresponding to the encryption key that's in use.
     * For {@code KeySpace}s where key phase isn't applicable, the {@code headerGenerator} will
     * be invoked with a value of {@code 0} for the key phase.
     * <p>
     * The {@code headerGenerator} is expected to return a {@code BufferData} representing the
     * packet header and where applicable, the returned header must contain the key phase
     * that was passed to the {@code headerGenerator}. The packet header will be used as
     * the Additional Authentication Data (AAD) for encrypting the {@code packetPayload}.
     * <p>
     * Upon return, the {@code output} will contain the encrypted packet payload bytes
     * and the authentication tag. The {@code packetPayload} and the packet header, returned
     * by the {@code headerGenerator}, are consumed by this call.
     * <p>
     * Implementations may still use more efficient internal buffer types below this public seam.
     * Callers that need to avoid fixed-size output buffers can use a growing {@link BufferData}.
     * <p>
     * A typical flow is:
     * <pre>
     * input:  header + plaintext payload
     * output: encrypted payload + AEAD tag
     * </pre>
     *
     * @param keySpace        Packet key space
     * @param packetNumber    full packet number
     * @param headerGenerator an {@link IntFunction} which takes a key phase and returns
     *                       the packet header
     * @param packetPayload   buffer containing unencrypted packet payload
     * @param output          buffer into which the encrypted packet payload will be written
     * @throws QuicKeyUnavailableException if keys are not available
     * @throws QuicTransportException      if encrypting the packet would result
     *                                    in exceeding the AEAD cipher confidentiality limit
     * @throws BufferOverflowException     if the output buffer is too small
     */
    void encryptPacket(KeySpace keySpace, long packetNumber,
                       IntFunction<BufferData> headerGenerator,
                       BufferData packetPayload,
                       BufferData output)
            throws QuicKeyUnavailableException, QuicTransportException, BufferOverflowException;

    /**
     * Decrypt the given packet bytes using keys for the given packet key space.
     * Header protection must be removed before calling this method.
     * <p>
     * The input buffer contains the packet header and the encrypted packet payload.
     * The packet header (first {@code headerLength} bytes of the input buffer)
     * is consumed by this method, but is not decrypted.
     * The packet payload (bytes following the packet header) is decrypted
     * by this method. This method consumes the entire input buffer.
     * <p>
     * The decrypted payload bytes are written
     * to the output buffer.
     * <p>
     * The provided {@code packet} is consumed by this call. Implementations may still use
     * more efficient internal buffer types below this public seam.
     * <p>
     * A typical flow is:
     * <pre>
     * input:  header + encrypted payload + AEAD tag
     * output: decrypted payload
     * </pre>
     *
     * @param keySpace     Packet key space
     * @param packetNumber full packet number
     * @param keyPhase     key phase bit (0 or 1) found on the packet, or -1
     *                    if the packet does not have a key phase bit
     * @param packet       buffer containing encrypted packet bytes
     * @param headerLength length of the packet header
     * @param output       buffer where decrypted packet bytes will be stored
     * @throws IllegalArgumentException    if keyPhase bit is invalid
     * @throws QuicKeyUnavailableException if keys are not available
     * @throws QuicPacketAuthenticationException if the provided packet's authentication tag is incorrect
     * @throws BufferOverflowException     if the output buffer is too small
     * @throws QuicTransportException      if decrypting invalid packets exceeds the AEAD cipher integrity limit
     */
    void decryptPacket(KeySpace keySpace, long packetNumber, int keyPhase,
                       BufferData packet, int headerLength, BufferData output)
            throws QuicKeyUnavailableException, QuicPacketAuthenticationException, QuicTransportException;

    /**
     * Sign the provided retry packet. Input buffer contains the retry packet
     * payload. Integrity tag is stored in the output buffer.
     *
     * @param version              Quic version
     * @param originalConnectionId original destination connection ID,
     *                            without length
     * @param packet               retry packet bytes without tag
     * @param output               buffer where integrity tag will be stored
     * @throws BufferOverflowException  if output buffer is too short to hold the tag
     * @throws IllegalArgumentException if originalConnectionId is
     *                                 longer than 255 bytes
     * @throws IllegalArgumentException if {@code version} isn't
     *                                 {@linkplain #supportedQuicVersions() supported}
     * @throws QuicTransportException   if retry-packet signing fails due to transport-level constraints
     */
    void signRetryPacket(QuicVersion version, BufferData originalConnectionId,
                         BufferData packet, BufferData output) throws BufferOverflowException, QuicTransportException;

    /**
     * Verify the provided retry packet.
     *
     * @param version              Quic version
     * @param originalConnectionId original destination connection ID,
     *                            without length
     * @param packet               retry packet bytes with tag
     * @throws QuicPacketAuthenticationException if integrity tag is invalid
     * @throws IllegalArgumentException if originalConnectionId is
     *                                 longer than 255 bytes
     * @throws IllegalArgumentException if {@code version} isn't
     *                                 {@linkplain #supportedQuicVersions() supported}
     * @throws QuicTransportException   if retry-packet verification fails due to transport-level constraints
     */
    void verifyRetryPacket(QuicVersion version, BufferData originalConnectionId,
                           BufferData packet) throws QuicPacketAuthenticationException, QuicTransportException;

    /**
     * If the current handshake state is {@link HandshakeState#NEED_SEND_CRYPTO}
     * meaning that a CRYPTO frame needs to be sent then this method is called
     * to obtain the contents of the frame. Current handshake state
     * can be obtained from {@link #handshakeState()}, and the current
     * key space can be obtained with {@link #currentSendKeySpace()}.
     * The bytes returned by this call are used to build a CRYPTO frame.
     * <p>
     * Implementations may also return post-handshake TLS CRYPTO in
     * {@link KeySpace#ONE_RTT}, for example {@code NewSessionTicket},
     * after the handshake has been confirmed.
     * <p>
     * Future JDK migration point: this maps to
     * {@code SSLEngine#wrap(empty, cryptoData)} on a QUIC engine.
     *
     * @param keySpace the key space of the packet in which the
     *                requested data will be placed
     * @return buffer containing data that will be put by caller in a CRYPTO
     *        frame, or {@link Optional#empty()} if there are no more handshake bytes to send in
     *        this key space at this time
     * @throws IllegalStateException if handshake bytes cannot be produced
     */
    Optional<BufferData> handshakeBytes(KeySpace keySpace);

    /**
     * This method consumes crypto stream.
     * <p>
     * Future JDK migration point: this maps to
     * {@code SSLEngine#unwrap(payload, empty)} on a QUIC engine.
     *
     * @param keySpace the key space of the packet in which the provided
     *                crypto data was encountered.
     * @param payload  contents of the next CRYPTO frame
     * @throws IllegalArgumentException if keySpace is ZERORTT or
     *                                 payload is empty
     * @throws QuicTransportException   if the handshake failed
     */
    void consumeHandshakeBytes(KeySpace keySpace, BufferData payload)
            throws QuicTransportException;

    /**
     * Returns a delegated {@code Runnable} task for
     * this {@code QuicTLSEngine}.
     * <P>
     * {@code QuicTLSEngine} operations may require the results of
     * operations that block, or may take an extended period of time to
     * complete.  This method is used to obtain an outstanding {@link
     * java.lang.Runnable} operation (task).  Each task must be assigned
     * a thread (possibly the current) to perform the {@link
     * java.lang.Runnable#run() run} operation.  Once the
     * {@code run} method returns, the {@code Runnable} object
     * is no longer needed and may be discarded.
     * <P>
     * A call to this method will return each outstanding task
     * exactly once.
     * <P>
     * Multiple delegated tasks can be run in parallel.
     *
     * @return delegated {@code Runnable} task when one is available, otherwise {@link Optional#empty()}
     */
    Optional<Runnable> delegatedTask();

    /**
     * Called to check if a {@code HANDSHAKE_DONE} frame needs to be sent by the
     * server. This method will only be called for a {@code QuicTLSEngine} which
     * is in {@linkplain #clientMode() server mode}. If the current TLS handshake
     * state is
     * {@link  HandshakeState#NEED_SEND_HANDSHAKE_DONE
     * NEED_SEND_HANDSHAKE_DONE} then this method returns {@code true} and
     * advances the TLS handshake state to
     * {@link  HandshakeState#HANDSHAKE_CONFIRMED HANDSHAKE_CONFIRMED}. Else
     * returns {@code false}.
     *
     * @return true if handshake state was {@code NEED_SEND_HANDSHAKE_DONE},
     *        false otherwise
     * @throws IllegalStateException If this {@code QuicTLSEngine} is
     *                              not in server mode
     */
    boolean tryMarkHandshakeDone() throws IllegalStateException;

    /**
     * Called when HANDSHAKE_DONE message is received from the server. This
     * method will only be called for a {@code QuicTLSEngine} which is in
     * {@linkplain #clientMode() client mode}. If the current TLS handshake state
     * is
     * {@link  HandshakeState#NEED_RECV_HANDSHAKE_DONE
     * NEED_RECV_HANDSHAKE_DONE} then this method returns {@code true} and
     * advances the TLS handshake state to
     * {@link  HandshakeState#HANDSHAKE_CONFIRMED HANDSHAKE_CONFIRMED}. Else
     * returns {@code false}.
     *
     * @return true if handshake state was {@code NEED_RECV_HANDSHAKE_DONE},
     *        false otherwise
     * @throws IllegalStateException if this {@code QuicTLSEngine} is
     *                              not in client mode
     */
    boolean tryReceiveHandshakeDone() throws IllegalStateException;

    /**
     * Called when the client and the server, during the connection creation
     * handshake, have settled on a Quic version to use for the connection. This
     * can happen either due to an explicit version negotiation (as outlined in
     * Quic RFC) or the server accepting the Quic version that the client chose
     * in its first INITIAL packet. In either of those cases, this method will
     * be called.
     *
     * @param quicVersion the negotiated {@code QuicVersion}
     * @throws IllegalArgumentException if the {@code quicVersion} isn't
     *                                 {@linkplain #supportedQuicVersions() supported} on this engine
     */
    void versionNegotiated(QuicVersion quicVersion);

    /**
     * Sets the {@link QuicOneRttContext} on the {@code QuicTLSEngine}.
     * <p> The {@code ctx} will be used by the {@code QuicTLSEngine} to access contextual 1-RTT
     * data that might be required for the TLS operations.
     *
     * @param ctx the 1-RTT context to set
     * @throws NullPointerException if {@code ctx} is null
     */
    void oneRttContext(QuicOneRttContext ctx);

    /**
     * Represents the encryption level associated with a packet encryption or
     * decryption. A QUIC connection has a current keyspace for sending and
     * receiving which can be queried.
     */
    enum KeySpace {
        /**
         * Initial encryption level.
         */
        INITIAL,
        /**
         * Handshake encryption level.
         */
        HANDSHAKE,
        /**
         * Retry pseudo-key space used by retry packet protection.
         */
        RETRY, // Special algorithm used for this packet
        /**
         * 0-RTT encryption level.
         */
        ZERO_RTT,
        /**
         * 1-RTT encryption level.
         */
        ONE_RTT;

        /**
         * Returns the canonical key-space text.
         *
         * @return the canonical key-space text
         */
        public String text() {
            return name();
        }
    }

    /**
     * TLS handshake progression states exposed to QUIC packet processing.
     */
    enum HandshakeState {
        /**
         * Need to receive a CRYPTO frame.
         */
        NEED_RECV_CRYPTO,
        /**
         * Need to receive a HANDSHAKE_DONE frame from server to complete the
         * handshake, but application data can be sent in this state (client
         * only state).
         */
        NEED_RECV_HANDSHAKE_DONE,
        /**
         * Need to send a CRYPTO frame.
         */
        NEED_SEND_CRYPTO,
        /**
         * Need to send a HANDSHAKE_DONE frame to complete the handshake, but
         * application data can be sent in this state (server only state).
         */
        NEED_SEND_HANDSHAKE_DONE,
        /**
         * Need to execute a task.
         */
        NEED_TASK,
        /**
         * Handshake is confirmed, as specified in section 4.1.2 of RFC-9001.
         */
        // On client side this happens when client receives HANDSHAKE_DONE
        // frame. On server side this happens when the TLS stack has both
        // sent a Finished message and verified the peer's Finished message.
        HANDSHAKE_CONFIRMED,
    }
}
