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

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.quic.spi.QuicPacketTLSEngine;

/**
 * Server-side public-JDK QUIC TLS implementation used until
 * {@code SSLContext#createQUICEngine(peerHost, peerPort, QuicTLSCallbacks)} is available.
 */
final class HelidonServerQuicTLSEngine implements QuicPacketTLSEngine {
    // The interim Helidon-owned server engine targets the same QUIC version set as the future JDK-backed engine so
    // routing can switch implementations without changing version negotiation behavior.
    private static final Set<QuicVersion> SUPPORTED_QUIC_VERSIONS = Set.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2);
    private static final long MAX_SESSION_TICKET_LIFETIME_SECONDS = 604_800L;
    private static final int SESSION_TICKET_NONCE_LENGTH = 16;
    private static final int SESSION_TICKET_LENGTH = 48;
    // The server helper extracts peer transport parameters directly from ClientHello until
    // QuicTLSCallbacks#remoteQuicTransportParameters(ByteBuffer) is available.
    private static final QuicTransportParametersConsumer NOOP_TRANSPORT_PARAMETERS_CONSUMER = buffer -> {
    };
    private final String peerHost;
    private final int peerPort;
    private final long aesGcmConfidentialityLimit;
    private final long chacha20Poly1305ConfidentialityLimit;
    private final QuicTlsServerSelector serverTlsSelector;
    private final QuicHandshakeMessageReassembler handshakeMessages;
    private final QuicTlsCallbackSession handshakeSession;
    private final Lock lock = new ReentrantLock();
    private final Deque<OutboundHandshakeMessage> outboundHandshakeMessages = new ArrayDeque<>();
    private volatile SSLParameters sslParameters;
    private volatile ServerConfig serverConfig;
    private volatile QuicTransportParametersConsumer remoteTransportParametersConsumer =
            NOOP_TRANSPORT_PARAMETERS_CONSUMER;
    private volatile QuicOneRttContext oneRttContext;
    private volatile QuicVersion initialVersion;
    private volatile QuicVersion negotiatedVersion;
    private volatile byte[] localQuicTransportParameters;
    private volatile QuicInitialKeys initialKeys;
    private volatile boolean initialKeysDiscarded;
    private volatile QuicLongHeaderTrafficKeys handshakeKeys;
    private volatile boolean handshakeKeysDiscarded;
    private volatile QuicOneRttTrafficKeys oneRttKeys;
    private volatile boolean oneRttKeysDiscarded;
    private volatile QuicTls13ServerHandshake serverHandshake;
    private volatile String applicationProtocol;
    private volatile HandshakeState handshakeState = HandshakeState.NEED_RECV_CRYPTO;
    private volatile KeySpace sendKeySpace = KeySpace.INITIAL;
    private volatile KeySpace nextSendKeySpaceAfterOutbound = KeySpace.ONE_RTT;
    private boolean serverTlsSelected;

    HelidonServerQuicTLSEngine(QuicTlsConfigSnapshot config) {
        this(config, Optional.empty(), -1, new QuicTlsServerSessionCache(0, config.sessionTimeout()), null);
    }

    HelidonServerQuicTLSEngine(QuicTlsConfigSnapshot config, QuicTlsServerSessionCache serverSessionCache) {
        this(config, Optional.empty(), -1, serverSessionCache, null);
    }

    HelidonServerQuicTLSEngine(QuicTlsConfigSnapshot config,
                               QuicTlsServerSessionCache serverSessionCache,
                               QuicTlsServerSelector serverTlsSelector) {
        this(config, Optional.empty(), -1, serverSessionCache, serverTlsSelector);
    }

    HelidonServerQuicTLSEngine(QuicTlsConfigSnapshot config, String peerHost, int peerPort) {
        this(config, Optional.of(Objects.requireNonNull(peerHost, "peerHost")),
             peerPort, new QuicTlsServerSessionCache(0, config.sessionTimeout()), null);
    }

    HelidonServerQuicTLSEngine(QuicTlsConfigSnapshot config,
                               String peerHost,
                               int peerPort,
                               QuicTlsServerSessionCache serverSessionCache) {
        this(config, Optional.of(Objects.requireNonNull(peerHost, "peerHost")), peerPort, serverSessionCache, null);
    }

    HelidonServerQuicTLSEngine(QuicTlsConfigSnapshot config,
                               String peerHost,
                               int peerPort,
                               QuicTlsServerSessionCache serverSessionCache,
                               QuicTlsServerSelector serverTlsSelector) {
        this(config,
             Optional.of(Objects.requireNonNull(peerHost, "peerHost")),
             peerPort,
             serverSessionCache,
             serverTlsSelector);
    }

    private HelidonServerQuicTLSEngine(QuicTlsConfigSnapshot config,
                                       Optional<String> peerHost,
                                       int peerPort,
                                       QuicTlsServerSessionCache serverSessionCache,
                                       QuicTlsServerSelector serverTlsSelector) {
        Objects.requireNonNull(config, "config");
        this.peerHost = peerHost.orElse(null);
        this.peerPort = peerPort;
        this.aesGcmConfidentialityLimit = config.aesGcmConfidentialityLimit();
        this.chacha20Poly1305ConfidentialityLimit = config.chacha20Poly1305ConfidentialityLimit();
        this.serverTlsSelector = serverTlsSelector;
        this.serverTlsSelected = serverTlsSelector == null;
        this.serverConfig = serverConfig(config, serverSessionCache);
        this.sslParameters = config.sslParameters();
        this.handshakeMessages = new QuicHandshakeMessageReassembler(this::handshakeState,
                                                                     config.maxHandshakeMessageSize(),
                                                                     false);
        this.handshakeSession = peerHost.isPresent()
                ? new QuicTlsCallbackSession(peerHost.orElseThrow(), peerPort, this.sslParameters)
                : new QuicTlsCallbackSession(this.sslParameters);
    }

    @Override
    public Set<QuicVersion> supportedQuicVersions() {
        return SUPPORTED_QUIC_VERSIONS;
    }

    @Override
    public boolean clientMode() {
        return false;
    }

    @Override
    public void clientMode(boolean mode) {
        if (mode) {
            throw new UnsupportedOperationException(
                    "Helidon-owned QUIC TLS engine currently implements server mode only");
        }
    }

    @Override
    public SSLParameters sslParameters() {
        return QuicTlsParameters.copy(sslParameters);
    }

    @Override
    public void sslParameters(SSLParameters sslParameters) {
        SSLParameters copy = validatedSslParameters(sslParameters, serverConfig.trustManager());
        this.sslParameters = copy;
        this.handshakeSession.sslParameters(copy);
    }

    @Override
    public Optional<String> applicationProtocol() {
        return Optional.ofNullable(applicationProtocol);
    }

    @Override
    public SSLSession session() {
        return handshakeSession;
    }

    @Override
    public Optional<SSLSession> handshakeSession() {
        return serverHandshake == null ? Optional.empty() : Optional.of(handshakeSession);
    }

    @Override
    public HandshakeState handshakeState() {
        return handshakeState;
    }

    @Override
    public boolean isTLSHandshakeComplete() {
        return handshakeState == HandshakeState.NEED_SEND_HANDSHAKE_DONE
                || handshakeState == HandshakeState.HANDSHAKE_CONFIRMED;
    }

    @Override
    public KeySpace currentSendKeySpace() {
        return sendKeySpace;
    }

    @Override
    public boolean keysAvailable(KeySpace keySpace) {
        return switch (keySpace) {
            case INITIAL -> initialKeys != null;
            case HANDSHAKE -> handshakeKeys != null;
            case ZERO_RTT -> false;
            case ONE_RTT -> oneRttKeys != null;
            case RETRY -> true;
        };
    }

    @Override
    public void discardKeys(KeySpace keySpace) {
        switch (keySpace) {
        case INITIAL -> {
            initialKeys = null;
            initialKeysDiscarded = true;
        }
        case HANDSHAKE -> {
            handshakeKeys = null;
            handshakeKeysDiscarded = true;
        }
        case ZERO_RTT -> {
        }
        case ONE_RTT -> {
            oneRttKeys = null;
            oneRttKeysDiscarded = true;
        }
        default -> throw new IllegalArgumentException("key discarding not implemented for " + keySpace);
        }
    }

    @Override
    public void localQuicTransportParametersBuffer(ByteBuffer params) {
        localQuicTransportParameters = copy(params);
    }

    @Override
    public void restartHandshake() {
        throw new IllegalStateException("Not expected to be called in server mode");
    }

    @Override
    public void remoteQuicTransportParametersConsumer(QuicTransportParametersConsumer consumer) {
        remoteTransportParametersConsumer = Objects.requireNonNull(consumer, "consumer");
    }

    @Override
    public void deriveInitialKeysBuffer(QuicVersion quicVersion, ByteBuffer connectionId) {
        if (!SUPPORTED_QUIC_VERSIONS.contains(Objects.requireNonNull(quicVersion, "quicVersion"))) {
            throw new IllegalArgumentException("Quic version " + quicVersion + " isn't enabled");
        }

        byte[] connectionIdBytes = copy(connectionId);
        initialKeys = QuicInitialKeys.create(quicVersion,
                                             connectionIdBytes,
                                             false,
                                             aesGcmConfidentialityLimit,
                                             chacha20Poly1305ConfidentialityLimit);
        initialKeysDiscarded = false;
        initialVersion = quicVersion;
    }

    @Override
    public int headerProtectionSampleSize(KeySpace keySpace) {
        return switch (keySpace) {
            case INITIAL, HANDSHAKE, ZERO_RTT, ONE_RTT -> QuicPacketProtection.HEADER_PROTECTION_SAMPLE_SIZE;
            default -> throw new IllegalArgumentException("Type '" + keySpace + "' not expected here");
        };
    }

    @Override
    public ByteBuffer computeHeaderProtectionMaskBuffer(KeySpace keySpace, boolean incoming, ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException {
        return switch (keySpace) {
            case INITIAL -> initialKeys().computeHeaderProtectionMask(incoming, sample);
            case HANDSHAKE -> handshakeKeys().computeHeaderProtectionMask(incoming, sample);
            case ZERO_RTT -> throw earlyDataUnsupported();
            case ONE_RTT -> oneRttKeys().computeHeaderProtectionMask(incoming, sample);
            default -> throw new IllegalArgumentException("No key manager available for key space: " + keySpace);
        };
    }

    @Override
    public long computeHeaderProtectionMaskBits(KeySpace keySpace, boolean incoming, ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException {
        return switch (keySpace) {
            case INITIAL -> initialKeys().computeHeaderProtectionMaskBits(incoming, sample);
            case HANDSHAKE -> handshakeKeys().computeHeaderProtectionMaskBits(incoming, sample);
            case ZERO_RTT -> throw earlyDataUnsupported();
            case ONE_RTT -> oneRttKeys().computeHeaderProtectionMaskBits(incoming, sample);
            default -> throw new IllegalArgumentException("No key manager available for key space: " + keySpace);
        };
    }

    @Override
    public int authTagSize() {
        return QuicPacketProtection.AUTH_TAG_SIZE;
    }

    @Override
    public void encryptPacketBuffer(KeySpace keySpace,
                                    long packetNumber,
                                    IntFunction<ByteBuffer> headerGenerator,
                                    ByteBuffer packetPayload,
                                    ByteBuffer output)
            throws QuicKeyUnavailableException, QuicTransportException, BufferOverflowException {
        switch (keySpace) {
        case INITIAL -> initialKeys().encryptPacket(packetNumber, headerGenerator, packetPayload, output);
        case HANDSHAKE -> handshakeKeys().encryptPacket(packetNumber, headerGenerator, packetPayload, output);
        case ZERO_RTT -> throw earlyDataUnsupported();
        case ONE_RTT -> oneRttKeys().encryptPacket(packetNumber, headerGenerator, packetPayload, output);
        default -> throw new IllegalArgumentException("No key manager available for key space: " + keySpace);
        }
    }

    @Override
    public void decryptPacketBuffer(KeySpace keySpace,
                                    long packetNumber,
                                    int keyPhase,
                                    ByteBuffer packet,
                                    int headerLength,
                                    ByteBuffer output)
            throws QuicKeyUnavailableException, QuicPacketAuthenticationException, QuicTransportException {
        switch (keySpace) {
        case INITIAL -> initialKeys().decryptPacket(packetNumber, keyPhase, packet, headerLength, output);
        case HANDSHAKE -> handshakeKeys().decryptPacket(packetNumber, keyPhase, packet, headerLength, output);
        case ZERO_RTT -> throw earlyDataUnsupported();
        case ONE_RTT -> oneRttKeys().decryptPacket(packetNumber, keyPhase, packet, headerLength, output);
        default -> throw new IllegalArgumentException("No key manager available for key space: " + keySpace);
        }
    }

    @Override
    public void signRetryPacketBuffer(QuicVersion version,
                                      ByteBuffer originalConnectionId,
                                      ByteBuffer packet,
                                      ByteBuffer output)
            throws BufferOverflowException, QuicTransportException {
        QuicRetryIntegrity.sign(version, originalConnectionId, packet, output);
    }

    @Override
    public void verifyRetryPacketBuffer(QuicVersion version, ByteBuffer originalConnectionId, ByteBuffer packet)
            throws QuicPacketAuthenticationException, QuicTransportException {
        QuicRetryIntegrity.verify(version, originalConnectionId, packet);
    }

    @Override
    public Optional<ByteBuffer> handshakeBytesBuffer(KeySpace keySpace) {
        lock.lock();
        try {
            if (keySpace != sendKeySpace) {
                throw new IllegalStateException("Unexpected key space: " + keySpace + " (expected " + sendKeySpace + ")");
            }
            boolean postHandshakeOneRtt = handshakeState != HandshakeState.NEED_SEND_CRYPTO
                    && keySpace == KeySpace.ONE_RTT
                    && sendKeySpace == KeySpace.ONE_RTT;
            if (handshakeState != HandshakeState.NEED_SEND_CRYPTO && !postHandshakeOneRtt) {
                return Optional.empty();
            }
            if (outboundHandshakeMessages.isEmpty()) {
                if (postHandshakeOneRtt) {
                    return Optional.empty();
                }
                throw new IllegalStateException(
                        "Handshake state requires outbound CRYPTO, but no handshake bytes are queued");
            }

            OutboundHandshakeMessage message = outboundHandshakeMessages.removeFirst();
            if (outboundHandshakeMessages.isEmpty()) {
                if (handshakeState == HandshakeState.NEED_SEND_CRYPTO) {
                    handshakeState = HandshakeState.NEED_RECV_CRYPTO;
                    sendKeySpace = nextSendKeySpaceAfterOutbound;
                } else {
                    sendKeySpace = KeySpace.ONE_RTT;
                }
            } else {
                sendKeySpace = outboundHandshakeMessages.getFirst().keySpace();
            }
            return Optional.of(message.bytes().asReadOnlyBuffer());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void consumeHandshakeBytesBuffer(KeySpace keySpace, ByteBuffer payload) throws QuicTransportException {
        lock.lock();
        try {
            handshakeMessages.consume(keySpace, payload, this::consumeHandshakeMessageLocked);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Runnable> delegatedTask() {
        return Optional.empty();
    }

    @Override
    public boolean tryMarkHandshakeDone() {
        lock.lock();
        try {
            if (handshakeState == HandshakeState.NEED_SEND_HANDSHAKE_DONE) {
                handshakeState = HandshakeState.HANDSHAKE_CONFIRMED;
                queueSessionTicketLocked();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean tryReceiveHandshakeDone() {
        throw new IllegalStateException("Not expected to be called in server mode");
    }

    @Override
    public void versionNegotiated(QuicVersion quicVersion) {
        Objects.requireNonNull(quicVersion, "quicVersion");
        if (!SUPPORTED_QUIC_VERSIONS.contains(quicVersion)) {
            throw new IllegalArgumentException("Quic version " + quicVersion + " is not enabled");
        }
        lock.lock();
        try {
            if (negotiatedVersion != null) {
                throw new IllegalStateException("A Quic version has already been negotiated previously");
            }
            negotiatedVersion = quicVersion;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void oneRttContext(QuicOneRttContext ctx) {
        oneRttContext = Objects.requireNonNull(ctx, "ctx");
        QuicOneRttTrafficKeys currentOneRttKeys = oneRttKeys;
        if (currentOneRttKeys != null) {
            currentOneRttKeys.oneRttContext(ctx);
        }
    }

    private static SSLParameters validatedSslParameters(SSLParameters sslParameters,
                                                        X509TrustManager trustManager) {
        SSLParameters copy = QuicTlsParameters.copy(Objects.requireNonNull(sslParameters, "sslParameters"));
        QuicTlsParameters.validateSupported(copy);
        String[] protocols = copy.getProtocols();
        if (protocols == null || protocols.length == 0) {
            throw new IllegalArgumentException("No TLS protocols set");
        }

        boolean tls13Present = false;
        for (String protocol : protocols) {
            if ("TLSv1.3".equals(protocol)) {
                tls13Present = true;
            } else {
                throw new IllegalArgumentException("Unsupported TLS protocol version " + protocol);
            }
        }
        if (!tls13Present) {
            throw new IllegalArgumentException("required TLSv1.3 protocol version hasn't been set");
        }
        if ((copy.getNeedClientAuth() || copy.getWantClientAuth()) && trustManager == null) {
            throw new IllegalArgumentException(QuicTlsTrustManagers.SERVER_CLIENT_AUTH_TRUST_MANAGER_REQUIRED_MESSAGE);
        }
        return copy;
    }

    private static QuicTransportException unsupportedHandshakeMessage(int messageType, KeySpace keySpace) {
        return new QuicTransportException("Helidon-owned QUIC TLS engine does not yet process "
                                                  + handshakeMessageName(messageType)
                                                  + " messages",
                                          keySpace,
                                          0,
                                          QuicTransportErrors.INTERNAL_ERROR);
    }

    private static String handshakeMessageName(int messageType) {
        return switch (messageType) {
            case QuicTlsHandshakeMessages.CLIENT_HELLO -> "ClientHello";
            case QuicTlsHandshakeMessages.SERVER_HELLO -> "ServerHello";
            case QuicTlsHandshakeMessages.ENCRYPTED_EXTENSIONS -> "EncryptedExtensions";
            case QuicTlsHandshakeMessages.CERTIFICATE_REQUEST -> "CertificateRequest";
            case QuicTlsHandshakeMessages.CERTIFICATE -> "Certificate";
            case QuicTlsHandshakeMessages.CERTIFICATE_VERIFY -> "CertificateVerify";
            case QuicTlsHandshakeMessages.FINISHED -> "Finished";
            case QuicTlsHandshakeMessages.NEW_SESSION_TICKET -> "NewSessionTicket";
            default -> "TLS handshake type 0x" + Integer.toHexString(messageType);
        };
    }

    private static byte[] copy(ByteBuffer buffer) {
        ByteBuffer duplicate = Objects.requireNonNull(buffer, "buffer").asReadOnlyBuffer();
        byte[] result = new byte[duplicate.remaining()];
        duplicate.get(result);
        return result;
    }

    private static ServerConfig serverConfig(QuicTlsConfigSnapshot config,
                                             QuicTlsServerSessionCache sessionCache) {
        long configuredTimeoutSeconds = config.sessionTimeout().getSeconds();
        if (config.sessionTimeout().getNano() > 0 && configuredTimeoutSeconds < MAX_SESSION_TICKET_LIFETIME_SECONDS) {
            configuredTimeoutSeconds++;
        }
        long sessionTicketLifetimeSeconds = config.sessionTimeout().isZero()
                ? MAX_SESSION_TICKET_LIFETIME_SECONDS
                : Math.clamp(configuredTimeoutSeconds, 1L, MAX_SESSION_TICKET_LIFETIME_SECONDS);
        return new ServerConfig(QuicTlsCompatibility.validatedSslContext(config),
                                config.secureRandom(),
                                QuicTlsKeyManagers.requiredKeyManager(config),
                                QuicTlsTrustManagers.optionalTrustManager(config),
                                sessionTicketLifetimeSeconds,
                                Objects.requireNonNull(sessionCache, "serverSessionCache"));
    }

    private void consumeHandshakeMessageLocked(KeySpace keySpace, ByteBuffer message) throws QuicTransportException {
        switch (keySpace) {
        case INITIAL -> consumeInitialMessageLocked(message);
        case HANDSHAKE -> consumeHandshakeLevelMessageLocked(message);
        case ONE_RTT -> consumePostHandshakeMessageLocked(message);
        default -> throw new IllegalArgumentException("Unsupported handshake key space: " + keySpace);
        }
    }

    private void consumeInitialMessageLocked(ByteBuffer message) throws QuicTransportException {
        if (handshakeState == HandshakeState.NEED_SEND_CRYPTO && !outboundHandshakeMessages.isEmpty()) {
            throw QuicTlsHandshakeMessages.unexpectedMessage("Received ClientHello while the server flight is still pending");
        }

        byte[] encodedMessage = copy(message);
        if (serverHandshake == null) {
            QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(encodedMessage));
            QuicTls13ServerHandshake.rejectMiddleboxCompatibility(clientHello);
            List<SNIServerName> requestedServerNames = QuicTls13ServerHandshake.decodeRequestedServerNames(clientHello);
            handshakeSession.requestedServerNames(requestedServerNames);
            if (!serverTlsSelected) {
                Optional<String> requestedServerName = requestedServerNames.stream()
                        .filter(SNIHostName.class::isInstance)
                        .map(SNIHostName.class::cast)
                        .map(SNIHostName::getAsciiName)
                        .findFirst();
                QuicTlsServerSelector.Selection selection = serverTlsSelector.select(requestedServerName);
                ServerConfig selectedConfig = serverConfig(selection.config(), selection.sessionCache());
                SSLParameters selectedParameters = validatedSslParameters(selection.sslParameters(),
                                                                           selectedConfig.trustManager());
                serverConfig = selectedConfig;
                sslParameters = selectedParameters;
                handshakeSession.sslParameters(selectedParameters);
                handshakeSession.requestedServerNames(requestedServerNames);
                serverTlsSelected = true;
            }
        }

        QuicTls13ServerHandshake currentHandshake = serverHandshakeLocked();
        QuicTls13ServerHandshake.Result result = currentHandshake.consumeClientHello(ByteBuffer.wrap(encodedMessage));
        if (result instanceof QuicTls13ServerHandshake.HelloRetryRequestResult retryResult) {
            outboundHandshakeMessages.clear();
            queueOutbound(KeySpace.INITIAL, retryResult.helloRetryRequest());
            sendKeySpace = KeySpace.INITIAL;
            nextSendKeySpaceAfterOutbound = KeySpace.INITIAL;
            handshakeState = HandshakeState.NEED_SEND_CRYPTO;
            return;
        }

        QuicTls13ServerHandshake.ServerFlight flight = (QuicTls13ServerHandshake.ServerFlight) result;

        handshakeKeys = currentHandshake.handshakeTrafficKeys();
        handshakeKeysDiscarded = false;
        oneRttKeys = currentHandshake.oneRttTrafficKeys();
        if (oneRttContext != null) {
            oneRttKeys.oneRttContext(oneRttContext);
        }
        oneRttKeysDiscarded = false;
        applicationProtocol = flight.applicationProtocol();
        handshakeSession.protocol("TLSv1.3");
        handshakeSession.cipherSuite(currentHandshake.cipherSuite().name());
        handshakeSession.localCertificates(flight.localCertificates());
        remoteTransportParametersConsumer.accept(ByteBuffer.wrap(flight.remoteTransportParameters()).asReadOnlyBuffer());

        outboundHandshakeMessages.clear();
        queueOutbound(KeySpace.INITIAL, flight.serverHello());
        queueOutbound(KeySpace.HANDSHAKE, flight.encryptedExtensions());
        if (flight.certificateRequest() != null) {
            queueOutbound(KeySpace.HANDSHAKE, flight.certificateRequest());
        }
        if (flight.certificate() != null) {
            queueOutbound(KeySpace.HANDSHAKE, flight.certificate());
        }
        if (flight.certificateVerify() != null) {
            queueOutbound(KeySpace.HANDSHAKE, flight.certificateVerify());
        }
        queueOutbound(KeySpace.HANDSHAKE, flight.finished());
        sendKeySpace = KeySpace.INITIAL;
        nextSendKeySpaceAfterOutbound = KeySpace.ONE_RTT;
        handshakeState = HandshakeState.NEED_SEND_CRYPTO;
    }

    private void consumeHandshakeLevelMessageLocked(ByteBuffer message) throws QuicTransportException {
        QuicTls13ServerHandshake currentHandshake = serverHandshake;
        if (currentHandshake == null) {
            throw QuicTlsHandshakeMessages.unexpectedMessage("Received Handshake-level TLS message before ClientHello");
        }
        try {
            switch (QuicTlsHandshakeMessages.messageType(message)) {
            case QuicTlsHandshakeMessages.CERTIFICATE -> {
                currentHandshake.consumeClientCertificate(message);
                handshakeSession.peerCertificates(currentHandshake.peerCertificates());
            }
            case QuicTlsHandshakeMessages.CERTIFICATE_VERIFY -> currentHandshake.consumeClientCertificateVerify(message);
            case QuicTlsHandshakeMessages.FINISHED -> {
                currentHandshake.consumeClientFinished(message);
                handshakeState = HandshakeState.NEED_SEND_HANDSHAKE_DONE;
                sendKeySpace = KeySpace.ONE_RTT;
            }
            default -> throw QuicTlsHandshakeMessages.unexpectedMessage(
                    "Expected client Certificate, CertificateVerify, or Finished message");
            }
        } catch (IllegalStateException e) {
            throw QuicTlsHandshakeMessages.unexpectedMessage(e.getMessage());
        }
    }

    private void consumePostHandshakeMessageLocked(ByteBuffer message) throws QuicTransportException {
        throw unsupportedHandshakeMessage(QuicTlsHandshakeMessages.messageType(message), KeySpace.ONE_RTT);
    }

    private QuicTls13ServerHandshake serverHandshakeLocked() throws QuicTransportException {
        QuicTls13ServerHandshake currentHandshake = serverHandshake;
        if (currentHandshake != null) {
            return currentHandshake;
        }
        if (localQuicTransportParameters == null) {
            throw QuicPacketProtection.internalError("Local QUIC transport parameters not set");
        }

        ServerConfig currentConfig = serverConfig;
        currentHandshake = QuicTls13ServerHandshake.start(
                currentQuicVersionLocked(),
                new QuicTls13ServerHandshake.StartParameters(
                        currentConfig.sslContext(),
                        sslParameters,
                        currentConfig.keyManager(),
                        currentConfig.trustManager(),
                        localQuicTransportParameters,
                        currentConfig.secureRandom(),
                        peerHost,
                        peerPort,
                        currentConfig.sessionCache(),
                        new QuicAeadLimits.Confidentiality(aesGcmConfidentialityLimit,
                                                           chacha20Poly1305ConfidentialityLimit)));
        serverHandshake = currentHandshake;
        return currentHandshake;
    }

    private QuicVersion currentQuicVersionLocked() throws QuicTransportException {
        QuicVersion currentVersion = negotiatedVersion;
        if (currentVersion != null) {
            return currentVersion;
        }
        currentVersion = initialVersion;
        if (currentVersion != null) {
            return currentVersion;
        }
        throw QuicPacketProtection.internalError("Initial QUIC keys not derived");
    }

    private void queueOutbound(KeySpace keySpace, byte[] encodedMessage) {
        outboundHandshakeMessages.addLast(new OutboundHandshakeMessage(keySpace, ByteBuffer.wrap(encodedMessage)));
    }

    private void queueSessionTicketLocked() {
        QuicTls13ServerHandshake currentHandshake = serverHandshake;
        if (currentHandshake == null) {
            throw new IllegalStateException("Server handshake state is not available for session tickets");
        }
        byte[] ticketNonce = new byte[SESSION_TICKET_NONCE_LENGTH];
        byte[] ticket = new byte[SESSION_TICKET_LENGTH];
        long ticketAgeAdd;
        ServerConfig currentConfig = serverConfig;
        try {
            currentConfig.secureRandom().nextBytes(ticketNonce);
            currentConfig.secureRandom().nextBytes(ticket);
            ticketAgeAdd = Integer.toUnsignedLong(currentConfig.secureRandom().nextInt());
        } catch (ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to generate a QUIC resumption ticket", e);
        }
        QuicTlsResumptionTicket resumptionTicket = currentHandshake.newResumptionTicket(
                currentConfig.sessionTicketLifetimeSeconds(),
                ticketAgeAdd,
                ticketNonce,
                ticket);
        if (!currentConfig.sessionCache().cache(resumptionTicket)) {
            return;
        }
        QuicTlsNewSessionTicket newSessionTicket = QuicTlsNewSessionTicket.create(
                currentConfig.sessionTicketLifetimeSeconds(),
                ticketAgeAdd,
                ticketNonce,
                ticket);
        queueOutbound(KeySpace.ONE_RTT, copy(newSessionTicket.encode()));
        sendKeySpace = KeySpace.ONE_RTT;
    }

    private QuicInitialKeys initialKeys() throws QuicKeyUnavailableException {
        QuicInitialKeys keys = initialKeys;
        if (keys != null) {
            return keys;
        }
        throw new QuicKeyUnavailableException(
                initialKeysDiscarded ? "Keys have been discarded" : "Keys not available",
                KeySpace.INITIAL);
    }

    private QuicLongHeaderTrafficKeys handshakeKeys() throws QuicKeyUnavailableException {
        QuicLongHeaderTrafficKeys keys = handshakeKeys;
        if (keys != null) {
            return keys;
        }
        throw new QuicKeyUnavailableException(
                handshakeKeysDiscarded ? "Keys have been discarded" : "Keys not available",
                KeySpace.HANDSHAKE);
    }

    private QuicKeyUnavailableException earlyDataUnsupported() {
        return new QuicKeyUnavailableException("0-RTT is not supported", KeySpace.ZERO_RTT);
    }

    private QuicOneRttTrafficKeys oneRttKeys() throws QuicKeyUnavailableException {
        QuicOneRttTrafficKeys keys = oneRttKeys;
        if (keys != null) {
            return keys;
        }
        throw new QuicKeyUnavailableException(
                oneRttKeysDiscarded ? "Keys have been discarded" : "Keys not available",
                KeySpace.ONE_RTT);
    }

    private record OutboundHandshakeMessage(KeySpace keySpace, ByteBuffer bytes) {
    }

    private record ServerConfig(SSLContext sslContext,
                                SecureRandom secureRandom,
                                X509KeyManager keyManager,
                                X509TrustManager trustManager,
                                long sessionTicketLifetimeSeconds,
                                QuicTlsServerSessionCache sessionCache) {
    }
}
