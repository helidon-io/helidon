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
import java.security.MessageDigest;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;

import javax.crypto.SecretKey;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.quic.spi.QuicPacketTLSEngine;

/**
 * Client-side public-JDK QUIC TLS implementation used until
 * {@code SSLContext#createQUICEngine(peerHost, peerPort, QuicTLSCallbacks)} is available.
 */
final class HelidonClientQuicTLSEngine implements QuicPacketTLSEngine {
    // The interim Helidon-owned engine targets the same QUIC versions as the future JDK-backed engine so the
    // transport can switch between implementations without changing its version gating.
    private static final Set<QuicVersion> SUPPORTED_QUIC_VERSIONS = Set.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2);
    // Transport parameters are surfaced only through the explicit extraction path in this engine so later certificate
    // work can keep one consistent callback contract for ClientHello and EncryptedExtensions processing.
    private static final QuicTransportParametersConsumer NOOP_TRANSPORT_PARAMETERS_CONSUMER = buffer -> {
    };
    private final SSLContext sslContext;
    private final SecureRandom secureRandom;
    private final String peerHost;
    private final int peerPort;
    private final QuicTlsSessionCache sessionCache;
    private final X509KeyManager keyManager;
    private final X509TrustManager trustManager;
    private final QuicTlsClientHelloFactory clientHelloFactory;
    private final long aesGcmConfidentialityLimit;
    private final long chacha20Poly1305ConfidentialityLimit;
    private final QuicHandshakeMessageReassembler handshakeMessages;
    private final QuicTlsHandshakeTranscript transcript = new QuicTlsHandshakeTranscript();
    private final Lock lock = new ReentrantLock();
    private final Deque<ByteBuffer> outboundHandshakeMessages = new ArrayDeque<>();
    private final QuicTlsCallbackSession handshakeSession;
    private volatile SSLParameters sslParameters;
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
    private volatile QuicTls13ClientHandshake helloHandshake;
    private volatile QuicTls13ConnectionSecrets connectionSecrets;
    private volatile byte[] serverHelloTranscriptHash;
    private volatile X509Certificate[] peerCertificates;
    private volatile byte[] clientCertificateRequestContext;
    private volatile ClientCredentials clientCredentials;
    private volatile String applicationProtocol;
    private volatile byte[] remoteTransportParameters;
    private volatile boolean serverSelectedPreSharedKey;
    private volatile ClientHandshakePhase handshakePhase = ClientHandshakePhase.EXPECT_SERVER_HELLO;
    private volatile boolean outboundFlightCompletesHandshake;
    private volatile HandshakeState handshakeState = HandshakeState.NEED_SEND_CRYPTO;
    private volatile KeySpace sendKeySpace = KeySpace.INITIAL;
    HelidonClientQuicTLSEngine(QuicTlsConfigSnapshot config) {
        this(config, Optional.empty(), -1, null);
    }

    HelidonClientQuicTLSEngine(QuicTlsConfigSnapshot config, QuicTlsSessionCache sessionCache) {
        this(config, Optional.empty(), -1, sessionCache);
    }

    HelidonClientQuicTLSEngine(QuicTlsConfigSnapshot config, String peerHost, int peerPort) {
        this(config, Optional.of(Objects.requireNonNull(peerHost, "peerHost")), peerPort, null);
    }

    HelidonClientQuicTLSEngine(QuicTlsConfigSnapshot config,
                               String peerHost,
                               int peerPort,
                               QuicTlsSessionCache sessionCache) {
        this(config, Optional.of(Objects.requireNonNull(peerHost, "peerHost")), peerPort, sessionCache);
    }

    private HelidonClientQuicTLSEngine(QuicTlsConfigSnapshot config,
                                       Optional<String> peerHost,
                                       int peerPort,
                                       QuicTlsSessionCache sessionCache) {
        Objects.requireNonNull(config, "config");
        this.sslContext = QuicTlsCompatibility.validatedSslContext(config);
        this.secureRandom = config.secureRandom();
        this.peerHost = peerHost.orElse(null);
        this.peerPort = peerPort;
        this.sessionCache = sessionCache;
        this.keyManager = config.keyManager().orElse(null);
        this.trustManager = QuicTlsTrustManagers.requiredTrustManager(config);
        this.sslParameters = config.sslParameters();
        this.clientHelloFactory = peerHost.isPresent()
                ? new QuicTlsClientHelloFactory(sslContext, peerHost.orElseThrow(), peerPort)
                : new QuicTlsClientHelloFactory(sslContext);
        this.aesGcmConfidentialityLimit = config.aesGcmConfidentialityLimit();
        this.chacha20Poly1305ConfidentialityLimit = config.chacha20Poly1305ConfidentialityLimit();
        this.handshakeMessages = new QuicHandshakeMessageReassembler(this::handshakeState,
                                                                     config.maxHandshakeMessageSize(),
                                                                     true);
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
        return true;
    }

    @Override
    public void clientMode(boolean mode) {
        if (!mode) {
            throw new UnsupportedOperationException(
                    "Helidon-owned QUIC TLS engine currently implements client mode only");
        }
    }

    @Override
    public SSLParameters sslParameters() {
        return QuicTlsParameters.copy(sslParameters);
    }

    @Override
    public void sslParameters(SSLParameters sslParameters) {
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
        return helloHandshake == null ? Optional.empty() : Optional.of(handshakeSession);
    }

    @Override
    public HandshakeState handshakeState() {
        return handshakeState;
    }

    @Override
    public boolean isTLSHandshakeComplete() {
        return handshakeState == HandshakeState.NEED_RECV_HANDSHAKE_DONE
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
        lock.lock();
        try {
            if (negotiatedVersion != null) {
                throw new IllegalStateException("Version already negotiated");
            }
            if (sendKeySpace != KeySpace.INITIAL || handshakeState != HandshakeState.NEED_RECV_CRYPTO) {
                throw new IllegalStateException("Unexpected handshake state");
            }
            resetHandshakeStateLocked();
        } finally {
            lock.unlock();
        }
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
                                             true,
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
        case ONE_RTT -> {
            if (!isTLSHandshakeComplete()) {
                throw new QuicKeyUnavailableException("QUIC TLS handshake not yet complete", KeySpace.ONE_RTT);
            }
            oneRttKeys().decryptPacket(packetNumber, keyPhase, packet, headerLength, output);
        }
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
            if (handshakeState != HandshakeState.NEED_SEND_CRYPTO) {
                return Optional.empty();
            }
            if (outboundHandshakeMessages.isEmpty()) {
                if (helloHandshake == null && keySpace == KeySpace.INITIAL) {
                    startClientHelloLocked();
                } else {
                    throw new IllegalStateException(
                            "Handshake state requires outbound CRYPTO, but no handshake bytes are queued");
                }
            }

            ByteBuffer message = outboundHandshakeMessages.removeFirst();
            if (outboundHandshakeMessages.isEmpty()) {
                if (outboundFlightCompletesHandshake) {
                    handshakeState = HandshakeState.NEED_RECV_HANDSHAKE_DONE;
                    sendKeySpace = KeySpace.ONE_RTT;
                } else {
                    handshakeState = HandshakeState.NEED_RECV_CRYPTO;
                }
            }
            return Optional.of(message.asReadOnlyBuffer());
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
        throw new IllegalStateException("Not expected to be called in client mode");
    }

    @Override
    public boolean tryReceiveHandshakeDone() {
        if (handshakeState == HandshakeState.NEED_RECV_HANDSHAKE_DONE) {
            handshakeState = HandshakeState.HANDSHAKE_CONFIRMED;
            return true;
        }
        return false;
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

    byte[] serverHelloTranscriptHash() {
        byte[] transcriptHash = serverHelloTranscriptHash;
        return transcriptHash == null ? null : transcriptHash.clone();
    }

    private static ByteBuffer encodeCertificateMessage(byte[] requestContext, X509Certificate[] certificateChain)
            throws QuicTransportException {
        try {
            List<QuicTlsCertificateMessage.CertificateEntry> certificateEntries = new ArrayList<>(certificateChain.length);
            for (X509Certificate certificate : certificateChain) {
                certificateEntries.add(new QuicTlsCertificateMessage.CertificateEntry(certificate.getEncoded(), List.of()));
            }
            return QuicTlsCertificateMessage.create(requestContext, certificateEntries).encode();
        } catch (CertificateEncodingException | ProviderException e) {
            throw QuicTlsHandshakeMessages.internalError("Failed to encode the local client certificate chain", e);
        }
    }

    private static Optional<QuicTlsSignatureScheme> selectCertificateVerifyScheme(
            List<QuicTlsSignatureScheme> requestedSignatureSchemes,
            List<QuicTlsSignatureScheme> localSignatureSchemes,
            PrivateKey privateKey,
            PublicKey publicKey) {
        for (QuicTlsSignatureScheme signatureScheme : requestedSignatureSchemes) {
            if (!localSignatureSchemes.contains(signatureScheme)) {
                continue;
            }
            if (!supportsSignatureScheme(signatureScheme, privateKey, publicKey)) {
                continue;
            }
            return Optional.of(signatureScheme);
        }
        return Optional.empty();
    }

    private static boolean supportsSignatureScheme(QuicTlsSignatureScheme signatureScheme,
                                                   PrivateKey privateKey,
                                                   PublicKey publicKey) {
        if (!signatureScheme.certificateVerify()
                || !signatureScheme.keyType().equalsIgnoreCase(privateKey.getAlgorithm())
                || !signatureScheme.keyType().equalsIgnoreCase(publicKey.getAlgorithm())) {
            return false;
        }

        if (publicKey instanceof ECPublicKey ecPublicKey) {
            QuicTlsNamedGroup requiredGroup = signatureScheme.requiredCertificateGroup();
            if (requiredGroup != null) {
                ECParameterSpec parameterSpec = ecPublicKey.getParams();
                return QuicTlsNamedGroup.forEcParameterSpec(parameterSpec)
                        .map(requiredGroup::equals)
                        .orElse(false);
            }
        } else if (signatureScheme.requiredCertificateGroup() != null) {
            return false;
        }

        return signatureScheme.supports(privateKey, publicKey);
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

    private void startClientHelloLocked() {
        if (localQuicTransportParameters == null) {
            throw new IllegalStateException("Local QUIC transport parameters not set");
        }
        if (initialVersion == null) {
            throw new IllegalStateException("Initial QUIC keys not derived");
        }

        QuicVersion currentVersion = currentQuicVersionLocked();
        Optional<QuicTlsResumptionTicket> resumptionTicket =
                sessionCache == null
                        ? Optional.empty()
                        : sessionCache.cachedResumptionTicket(peerHost,
                                                              peerPort,
                                                              sessionCacheServerName(),
                                                              sslParameters.getApplicationProtocols(),
                                                              ticket -> clientHelloFactory.compatibleResumptionTicket(
                                                                      ticket,
                                                                      currentVersion,
                                                                      sslParameters));
        helloHandshake = clientHelloFactory.start(currentVersion,
                                                  sslParameters,
                                                  localQuicTransportParameters,
                                                  resumptionTicket.orElse(null),
                                                  secureRandom,
                                                  new QuicAeadLimits.Confidentiality(aesGcmConfidentialityLimit,
                                                                                     chacha20Poly1305ConfidentialityLimit));
        transcript.reset();
        outboundHandshakeMessages.clear();
        ByteBuffer clientHello = helloHandshake.clientHello();
        transcript.add(clientHello.asReadOnlyBuffer());
        outboundHandshakeMessages.addLast(clientHello.asReadOnlyBuffer());
        handshakeState = HandshakeState.NEED_SEND_CRYPTO;
        sendKeySpace = KeySpace.INITIAL;
        applicationProtocol = null;
        connectionSecrets = null;
        serverHelloTranscriptHash = null;
        peerCertificates = null;
        handshakeSession.peerCertificates(null);
        handshakeKeys = null;
        handshakeKeysDiscarded = false;
        oneRttKeys = null;
        oneRttKeysDiscarded = false;
        serverSelectedPreSharedKey = false;
        handshakePhase = ClientHandshakePhase.EXPECT_SERVER_HELLO;
        outboundFlightCompletesHandshake = false;
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
        if (helloHandshake == null) {
            throw QuicTlsHandshakeMessages.unexpectedMessage("Received ServerHello before sending ClientHello");
        }

        byte[] encodedMessage = copy(message);
        try {
            QuicTls13ClientHandshake.Result result = helloHandshake.consumeServerHello(ByteBuffer.wrap(encodedMessage));
            if (result instanceof QuicTls13ClientHandshake.HelloRetryRequestResult retryResult) {
                transcript.add(ByteBuffer.wrap(encodedMessage));
                ByteBuffer retryClientHello = retryResult.clientHello();
                transcript.add(retryClientHello.asReadOnlyBuffer());
                outboundHandshakeMessages.clear();
                outboundHandshakeMessages.addLast(retryClientHello.asReadOnlyBuffer());
                sendKeySpace = KeySpace.INITIAL;
                handshakeState = HandshakeState.NEED_SEND_CRYPTO;
                serverSelectedPreSharedKey = false;
                handshakePhase = ClientHandshakePhase.EXPECT_SERVER_HELLO;
                outboundFlightCompletesHandshake = false;
                return;
            }

            QuicTls13ClientHandshake.CompleteResult completeResult =
                    (QuicTls13ClientHandshake.CompleteResult) result;
            transcript.add(ByteBuffer.wrap(encodedMessage));
            connectionSecrets = completeResult.connectionSecrets();
            serverHelloTranscriptHash = completeResult.serverHelloTranscriptHash();
            handshakeKeys = completeResult.handshakeTrafficKeys();
            handshakeKeysDiscarded = false;
            serverSelectedPreSharedKey = completeResult.preSharedKeySelected();
            handshakeSession.protocol("TLSv1.3");
            handshakeSession.cipherSuite(connectionSecrets.cipherSuite().name());
            sendKeySpace = KeySpace.HANDSHAKE;
            handshakeState = HandshakeState.NEED_RECV_CRYPTO;
            handshakePhase = ClientHandshakePhase.EXPECT_ENCRYPTED_EXTENSIONS;
            outboundFlightCompletesHandshake = false;
        } catch (IllegalStateException e) {
            throw QuicTlsHandshakeMessages.unexpectedMessage(e.getMessage());
        }
    }

    private void consumeHandshakeLevelMessageLocked(ByteBuffer message) throws QuicTransportException {
        if (connectionSecrets == null || handshakeKeys == null) {
            throw QuicTlsHandshakeMessages.unexpectedMessage(
                    "Received Handshake-level TLS message before ServerHello completed");
        }

        int messageType = QuicTlsHandshakeMessages.messageType(message);
        switch (handshakePhase) {
        case EXPECT_ENCRYPTED_EXTENSIONS -> {
            if (messageType != QuicTlsHandshakeMessages.ENCRYPTED_EXTENSIONS) {
                throw QuicTlsHandshakeMessages.unexpectedMessage("Expected EncryptedExtensions message");
            }
            consumeEncryptedExtensionsMessageLocked(message);
            handshakePhase = serverSelectedPreSharedKey
                    ? ClientHandshakePhase.EXPECT_FINISHED
                    : ClientHandshakePhase.EXPECT_CERTIFICATE_REQUEST_OR_CERTIFICATE;
        }
        case EXPECT_CERTIFICATE_REQUEST_OR_CERTIFICATE -> {
            if (messageType == QuicTlsHandshakeMessages.CERTIFICATE_REQUEST) {
                consumeCertificateRequestMessageLocked(message);
                handshakePhase = ClientHandshakePhase.EXPECT_CERTIFICATE;
                return;
            }
            if (messageType != QuicTlsHandshakeMessages.CERTIFICATE) {
                throw QuicTlsHandshakeMessages.unexpectedMessage("Expected CertificateRequest or Certificate message");
            }
            consumeCertificateMessageLocked(message);
            handshakePhase = ClientHandshakePhase.EXPECT_CERTIFICATE_VERIFY;
        }
        case EXPECT_CERTIFICATE -> {
            if (messageType != QuicTlsHandshakeMessages.CERTIFICATE) {
                throw QuicTlsHandshakeMessages.unexpectedMessage("Expected Certificate message");
            }
            consumeCertificateMessageLocked(message);
            handshakePhase = ClientHandshakePhase.EXPECT_CERTIFICATE_VERIFY;
        }
        case EXPECT_CERTIFICATE_VERIFY -> {
            if (messageType != QuicTlsHandshakeMessages.CERTIFICATE_VERIFY) {
                throw QuicTlsHandshakeMessages.unexpectedMessage("Expected CertificateVerify message");
            }
            consumeCertificateVerifyMessageLocked(message);
            handshakePhase = ClientHandshakePhase.EXPECT_FINISHED;
        }
        case EXPECT_FINISHED -> {
            if (messageType != QuicTlsHandshakeMessages.FINISHED) {
                throw QuicTlsHandshakeMessages.unexpectedMessage("Expected Finished message");
            }
            consumeFinishedMessageLocked(message);
        }
        case WAIT_HANDSHAKE_DONE -> throw QuicTlsHandshakeMessages.unexpectedMessage(
                "Unexpected Handshake-level TLS message after client Finished");
        case EXPECT_SERVER_HELLO -> throw QuicTlsHandshakeMessages.unexpectedMessage(
                "Received Handshake-level TLS message before ServerHello completed");
        default -> throw QuicTlsHandshakeMessages.unexpectedMessage("Unexpected client handshake phase: " + handshakePhase);
        }
    }

    private void consumeEncryptedExtensionsMessageLocked(ByteBuffer message) throws QuicTransportException {
        ByteBuffer body = QuicTlsCodecSupport.handshakeBody(message, "EncryptedExtensions");
        List<QuicTlsExtension> extensions = QuicTlsExtensions.decode(body, "EncryptedExtensions");
        QuicTlsCodecSupport.ensureConsumed(body, "EncryptedExtensions");
        QuicTlsExtensions.validateServerResponse(extensions,
                                                helloHandshake.clientHelloMessage(),
                                                QuicTlsHandshakeMessages.ENCRYPTED_EXTENSIONS);

        QuicTlsExtension earlyData = QuicTlsExtensions.find(extensions, QuicTlsExtensions.EARLY_DATA).orElse(null);
        if (earlyData != null) {
            if (earlyData.data().length != 0) {
                throw QuicTlsHandshakeMessages.decodeError("Malformed EncryptedExtensions message: early_data");
            }
            throw QuicTlsHandshakeMessages.illegalParameter("EncryptedExtensions early_data is not supported");
        }

        QuicTlsExtension transportParameters =
                QuicTlsExtensions.find(extensions, QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS).orElse(null);
        if (transportParameters == null) {
            throw QuicTlsHandshakeMessages.missingExtension(
                    "EncryptedExtensions missing quic_transport_parameters extension");
        }
        QuicTlsExtension alpn = QuicTlsExtensions.find(extensions,
                                                       QuicTlsExtensions.APPLICATION_LAYER_PROTOCOL_NEGOTIATION)
                .orElse(null);
        if (alpn == null) {
            throw QuicTlsHandshakeMessages.noApplicationProtocol(
                    "EncryptedExtensions missing application_layer_protocol_negotiation extension");
        }
        String selectedProtocol = selectedApplicationProtocol(alpn);
        byte[] transportParametersData = transportParameters.data();
        remoteTransportParameters = transportParametersData;
        remoteTransportParametersConsumer.accept(ByteBuffer.wrap(transportParametersData).asReadOnlyBuffer());
        applicationProtocol = selectedProtocol;
        transcript.add(message.asReadOnlyBuffer());
    }

    private void consumeCertificateRequestMessageLocked(ByteBuffer message) throws QuicTransportException {
        QuicTlsCertificateRequestMessage certificateRequest = QuicTlsCertificateRequestMessage.decode(message);
        clientCertificateRequestContext = certificateRequest.requestContext();
        clientCredentials = chooseClientCredentials(certificateRequest);
        transcript.add(message.asReadOnlyBuffer());
    }

    private void consumeCertificateMessageLocked(ByteBuffer message) throws QuicTransportException {
        QuicTlsCertificateMessage certificateMessage = QuicTlsCertificateMessage.decode(message);
        if (certificateMessage.requestContext().length != 0) {
            throw QuicTlsHandshakeMessages.decodeError(
                    "Malformed Certificate message: non-empty certificate_request_context");
        }

        X509Certificate[] certificates = certificateMessage.x509Certificates();
        if (certificates.length == 0) {
            throw QuicTlsHandshakeMessages.badCertificate("Empty server certificate chain");
        }

        validateServerCertificates(certificates);
        peerCertificates = certificates.clone();
        handshakeSession.peerCertificates(certificates);
        transcript.add(message.asReadOnlyBuffer());
    }

    private void consumeCertificateVerifyMessageLocked(ByteBuffer message) throws QuicTransportException {
        QuicTlsCertificateVerifyMessage certificateVerify = QuicTlsCertificateVerifyMessage.decode(message);
        if (!clientOfferedSignatureScheme(certificateVerify.signatureScheme())) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "Server selected a CertificateVerify signature scheme that was not offered");
        }

        certificateVerify.verify(QuicTlsHandshakeMessages.peerCertificatePublicKey(peerCertificates()[0]),
                                 transcript.hash(connectionSecrets.cipherSuite()),
                                 false);
        transcript.add(message.asReadOnlyBuffer());
    }

    private void consumeFinishedMessageLocked(ByteBuffer message) throws QuicTransportException {
        QuicTlsFinishedMessage finished = QuicTlsFinishedMessage.decode(message);
        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(connectionSecrets.cipherSuite());

        SecretKey serverHandshakeTrafficSecret =
                connectionSecrets.serverHandshakeTrafficSecret(Objects.requireNonNull(serverHelloTranscriptHash,
                                                                                      "serverHelloTranscriptHash"));
        SecretKey serverFinishedKey = secretSchedule.deriveFinishedKey(serverHandshakeTrafficSecret);
        byte[] expectedVerifyData = secretSchedule.computeVerifyData(
                serverFinishedKey,
                transcript.hash(connectionSecrets.cipherSuite()));
        if (!MessageDigest.isEqual(expectedVerifyData, finished.verifyData())) {
            throw QuicTlsHandshakeMessages.decryptError("The Finished message cannot be verified");
        }

        transcript.add(message.asReadOnlyBuffer());
        oneRttKeys = connectionSecrets.deriveOneRttTrafficKeys(transcript.hash(connectionSecrets.cipherSuite()));
        if (oneRttContext != null) {
            oneRttKeys.oneRttContext(oneRttContext);
        }
        oneRttKeysDiscarded = false;
        queueClientAuthenticationFlightLocked(secretSchedule);
    }

    private void consumePostHandshakeMessageLocked(ByteBuffer message) throws QuicTransportException {
        if (!QuicTlsHandshakeMessages.isNewSessionTicket(message)) {
            throw unsupportedHandshakeMessage(QuicTlsHandshakeMessages.messageType(message), KeySpace.ONE_RTT);
        }
        QuicTlsNewSessionTicket newSessionTicket = QuicTlsNewSessionTicket.decode(message);
        cacheResumptionTicketLocked(newSessionTicket);
    }

    private String selectedApplicationProtocol(QuicTlsExtension extension) throws QuicTransportException {
        String selected = clientHelloFactory.decodeSelectedApplicationProtocol(extension.dataBuffer());
        String[] offeredProtocols = sslParameters.getApplicationProtocols();
        if (offeredProtocols == null || offeredProtocols.length == 0) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "EncryptedExtensions selected ALPN even though the client offered none");
        }
        if (!Arrays.asList(offeredProtocols).contains(selected)) {
            throw QuicTlsHandshakeMessages.illegalParameter(
                    "EncryptedExtensions selected an ALPN protocol that was not offered by the client");
        }
        return selected;
    }

    private void validateServerCertificates(X509Certificate[] certificates) throws QuicTransportException {
        String[] localSupportedSignatureSchemes = localCertificateSignatureSchemes().stream()
                .map(QuicTlsSignatureScheme::tlsName)
                .toArray(String[]::new);
        SSLParameters callbackParameters = QuicTlsParameters.copy(sslParameters);
        callbackParameters.setSignatureSchemes(localSupportedSignatureSchemes);
        QuicTlsCallbackEngine callbackEngine = QuicTlsManagerCallbacks.callbackEngine(true,
                                                                                       callbackParameters,
                                                                                       localSupportedSignatureSchemes,
                                                                                       new String[0],
                                                                                       peerHost,
                                                                                       peerPort);
        callbackEngine.handshakeApplicationProtocol(applicationProtocol);
        callbackEngine.callbackSession().protocol("TLSv1.3");
        callbackEngine.callbackSession().cipherSuite(connectionSecrets.cipherSuite().name());
        callbackEngine.callbackSession().peerCertificates(certificates);
        QuicTlsManagerCallbacks.checkServerTrusted(trustManager,
                                                   certificates.clone(),
                                                   QuicTlsHandshakeMessages.peerCertificateAuthType(certificates[0]),
                                                   callbackEngine);
    }

    private boolean clientOfferedSignatureScheme(QuicTlsSignatureScheme signatureScheme) throws QuicTransportException {
        if (helloHandshake == null) {
            return false;
        }
        QuicTlsExtension signatureAlgorithms = helloHandshake.clientHelloMessage()
                .extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS)
                .orElse(null);
        if (signatureAlgorithms == null) {
            return false;
        }
        return QuicTlsSignatureScheme.decodeCertificateVerifyVector(signatureAlgorithms.dataBuffer(),
                                                                    "supported_signature_algorithms",
                                                                    "ClientHello")
                .contains(signatureScheme);
    }

    private X509Certificate[] peerCertificates() throws QuicTransportException {
        X509Certificate[] certificates = peerCertificates;
        if (certificates != null && certificates.length > 0) {
            return certificates.clone();
        }
        throw QuicTlsHandshakeMessages.unexpectedMessage("Received CertificateVerify before Certificate");
    }

    private ClientCredentials chooseClientCredentials(QuicTlsCertificateRequestMessage certificateRequest)
            throws QuicTransportException {
        Objects.requireNonNull(certificateRequest, "certificateRequest");
        if (keyManager == null) {
            return null;
        }

        List<QuicTlsSignatureScheme> requestedSignatureSchemes = certificateRequest.signatureAlgorithms();
        List<QuicTlsSignatureScheme> requestedCertificateSignatureSchemes =
                certificateRequest.effectiveCertificateSignatureAlgorithms();
        List<QuicTlsSignatureScheme> localCertificateVerifySchemes = localCertificateVerifySchemes();
        List<QuicTlsSignatureScheme> localCertificateSignatureSchemes = localCertificateSignatureSchemes();
        Set<String> candidateKeyTypes = new LinkedHashSet<>();
        for (QuicTlsSignatureScheme requestedSignatureScheme : requestedSignatureSchemes) {
            if (localCertificateVerifySchemes.contains(requestedSignatureScheme)) {
                candidateKeyTypes.add(requestedSignatureScheme.keyType());
            }
        }
        if (candidateKeyTypes.isEmpty()) {
            return null;
        }

        List<QuicTlsSignatureScheme> effectiveCertificateSignatureSchemes =
                requestedCertificateSignatureSchemes.stream()
                        .filter(localCertificateSignatureSchemes::contains)
                        .toList();
        if (effectiveCertificateSignatureSchemes.isEmpty()) {
            return null;
        }

        String[] localSupportedSignatureSchemes = localCertificateSignatureSchemes.stream()
                .map(QuicTlsSignatureScheme::tlsName)
                .toArray(String[]::new);
        String[] peerSupportedSignatureSchemes = effectiveCertificateSignatureSchemes.stream()
                .map(QuicTlsSignatureScheme::tlsName)
                .toArray(String[]::new);
        SSLParameters callbackParameters = QuicTlsParameters.copy(sslParameters);
        callbackParameters.setSignatureSchemes(localSupportedSignatureSchemes);
        Principal[] issuers = certificateRequest.certificateAuthorities();
        Principal[] requestedIssuers = issuers.length == 0 ? null : issuers;
        String[] keyTypes = candidateKeyTypes.toArray(String[]::new);
        Set<String> candidateAliases = new LinkedHashSet<>();
        QuicTlsCallbackEngine callbackEngine = QuicTlsManagerCallbacks.callbackEngine(true,
                                                                                       callbackParameters,
                                                                                       localSupportedSignatureSchemes,
                                                                                       peerSupportedSignatureSchemes,
                                                                                       peerHost,
                                                                                       peerPort);
        String selectedAlias = QuicTlsManagerCallbacks.chooseClientAlias(keyManager,
                                                                         keyTypes,
                                                                         requestedIssuers,
                                                                         callbackEngine);
        if (selectedAlias == null) {
            return null;
        }
        candidateAliases.add(selectedAlias);
        for (String keyType : keyTypes) {
            String[] listedAliases;
            try {
                listedAliases = keyManager.getClientAliases(keyType, requestedIssuers);
            } catch (ProviderException e) {
                throw QuicTlsHandshakeMessages.internalError(
                        "Client key manager failed to list certificate aliases",
                        e);
            }
            if (listedAliases != null) {
                for (String listedAlias : listedAliases) {
                    if (listedAlias != null) {
                        candidateAliases.add(listedAlias);
                    }
                }
            }
        }

        X509Certificate selectedCertificate = null;
        for (String alias : candidateAliases) {
            PrivateKey privateKey = QuicTlsManagerCallbacks.privateKey(keyManager, alias);
            X509Certificate[] certificateChain = QuicTlsManagerCallbacks.certificateChain(keyManager, alias);
            if (privateKey == null || certificateChain == null || certificateChain.length == 0) {
                throw QuicTlsHandshakeMessages.internalError(
                        "The local client certificate alias does not provide complete credentials",
                        new IllegalStateException("Incomplete client credentials for alias " + alias));
            }
            for (X509Certificate certificate : certificateChain) {
                if (certificate == null) {
                    throw QuicTlsHandshakeMessages.internalError(
                            "The local client certificate chain contains a null certificate",
                            new IllegalStateException("Invalid client certificate chain for alias " + alias));
                }
            }
            if (alias.equals(selectedAlias)) {
                selectedCertificate = certificateChain[0];
            } else if (selectedCertificate == null
                    || !QuicTlsManagerCallbacks.sameCertificateIdentity(selectedCertificate, certificateChain[0])) {
                continue;
            }
            if (!QuicTlsSignatureScheme.supportsCertificateChain(certificateChain,
                                                                 effectiveCertificateSignatureSchemes)) {
                continue;
            }

            PublicKey publicKey;
            try {
                publicKey = certificateChain[0].getPublicKey();
            } catch (ProviderException e) {
                throw QuicTlsHandshakeMessages.internalError(
                        "Failed to read the local client certificate public key",
                        e);
            }
            if (publicKey == null) {
                throw QuicTlsHandshakeMessages.internalError(
                        "The local client certificate does not provide a public key",
                        new IllegalStateException("Invalid client certificate for alias " + alias));
            }

            Optional<QuicTlsSignatureScheme> signatureScheme =
                    selectCertificateVerifyScheme(requestedSignatureSchemes,
                                                  localCertificateVerifySchemes,
                                                  privateKey,
                                                  publicKey);
            if (signatureScheme.isPresent()) {
                return new ClientCredentials(privateKey, certificateChain, signatureScheme.orElseThrow());
            }
        }
        return null;
    }

    private List<QuicTlsSignatureScheme> localCertificateVerifySchemes() throws QuicTransportException {
        if (helloHandshake == null) {
            throw QuicTlsHandshakeMessages.unexpectedMessage("ClientHello has not been produced yet");
        }
        QuicTlsExtension signatureAlgorithms = helloHandshake.clientHelloMessage()
                .extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS)
                .orElseThrow(() -> QuicTlsHandshakeMessages.missingExtension(
                        "ClientHello missing mandatory signature_algorithms extension"));
        return QuicTlsSignatureScheme.decodeCertificateVerifyVector(signatureAlgorithms.dataBuffer(),
                                                                    "supported_signature_algorithms",
                                                                    "ClientHello");
    }

    private List<QuicTlsSignatureScheme> localCertificateSignatureSchemes() throws QuicTransportException {
        if (helloHandshake == null) {
            throw QuicTlsHandshakeMessages.unexpectedMessage("ClientHello has not been produced yet");
        }
        QuicTlsExtension signatureAlgorithms = helloHandshake.clientHelloMessage()
                .extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS)
                .orElseThrow(() -> QuicTlsHandshakeMessages.missingExtension(
                        "ClientHello missing mandatory signature_algorithms extension"));
        QuicTlsExtension certificateSignatureAlgorithms = helloHandshake.clientHelloMessage()
                .extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT)
                .orElse(null);
        return QuicTlsSignatureScheme.decodeCertificateSignatureVector(
                certificateSignatureAlgorithms == null
                        ? signatureAlgorithms.dataBuffer()
                        : certificateSignatureAlgorithms.dataBuffer(),
                certificateSignatureAlgorithms == null
                        ? "supported_signature_algorithms"
                        : "supported_certificate_signature_algorithms",
                "ClientHello");
    }

    private void queueClientAuthenticationFlightLocked(QuicTls13SecretSchedule secretSchedule)
            throws QuicTransportException {
        byte[] requestContext = clientCertificateRequestContext;
        ClientCredentials credentials = clientCredentials;

        outboundHandshakeMessages.clear();
        if (requestContext != null) {
            ByteBuffer certificate = encodeCertificateMessage(requestContext,
                                                              credentials == null ? new X509Certificate[0]
                                                                      : credentials.certificateChain());
            transcript.add(certificate.asReadOnlyBuffer());
            outboundHandshakeMessages.addLast(certificate.asReadOnlyBuffer());
            handshakeSession.localCertificates(credentials == null ? null : credentials.certificateChain());

            if (credentials != null) {
                ByteBuffer certificateVerify =
                        QuicTlsCertificateVerifyMessage.sign(credentials.signatureScheme(),
                                                             credentials.privateKey(),
                                                             transcript.hash(connectionSecrets.cipherSuite()),
                                                             true)
                                .encode();
                transcript.add(certificateVerify.asReadOnlyBuffer());
                outboundHandshakeMessages.addLast(certificateVerify.asReadOnlyBuffer());
            }
        } else {
            handshakeSession.localCertificates(null);
        }

        SecretKey clientHandshakeTrafficSecret =
                connectionSecrets.clientHandshakeTrafficSecret(Objects.requireNonNull(serverHelloTranscriptHash,
                                                                                      "serverHelloTranscriptHash"));
        SecretKey clientFinishedKey = secretSchedule.deriveFinishedKey(clientHandshakeTrafficSecret);
        ByteBuffer clientFinished = QuicTlsFinishedMessage.create(
                secretSchedule.computeVerifyData(clientFinishedKey,
                                                 transcript.hash(connectionSecrets.cipherSuite())))
                .encode();
        transcript.add(clientFinished.asReadOnlyBuffer());
        outboundHandshakeMessages.addLast(clientFinished.asReadOnlyBuffer());
        handshakeState = HandshakeState.NEED_SEND_CRYPTO;
        sendKeySpace = KeySpace.HANDSHAKE;
        handshakePhase = ClientHandshakePhase.WAIT_HANDSHAKE_DONE;
        outboundFlightCompletesHandshake = true;
    }

    private void cacheResumptionTicketLocked(QuicTlsNewSessionTicket newSessionTicket) throws QuicTransportException {
        if (sessionCache == null) {
            return;
        }

        QuicTls13ConnectionSecrets secrets = Objects.requireNonNull(connectionSecrets, "connectionSecrets");
        byte[] clientFinishedTranscriptHash = transcript.hash(secrets.cipherSuite());
        byte[] resumptionPsk = secrets.deriveResumptionPsk(clientFinishedTranscriptHash,
                                                          newSessionTicket.ticketNonce());

        sessionCache.cache(peerHost,
                           peerPort,
                           sessionCacheServerName(),
                           new QuicTlsResumptionTicket(currentQuicVersionLocked(),
                                                       secrets.cipherSuite(),
                                                       newSessionTicket.ticketLifetimeSeconds(),
                                                       newSessionTicket.ticketAgeAdd(),
                                                       newSessionTicket.ticketNonce(),
                                                       newSessionTicket.ticket(),
                                                       resumptionPsk,
                                                       applicationProtocol,
                                                       remoteTransportParameters,
                                                       System.currentTimeMillis()));
    }

    private String sessionCacheServerName() {
        List<SNIServerName> configuredServerNames = sslParameters.getServerNames();
        if (configuredServerNames != null) {
            for (var configuredServerName : configuredServerNames) {
                if (configuredServerName instanceof SNIHostName hostName) {
                    return hostName.getAsciiName();
                }
            }
        }
        return peerHost;
    }

    private void resetHandshakeStateLocked() {
        handshakeMessages.reset();
        transcript.reset();
        outboundHandshakeMessages.clear();
        helloHandshake = null;
        connectionSecrets = null;
        serverHelloTranscriptHash = null;
        peerCertificates = null;
        clientCertificateRequestContext = null;
        clientCredentials = null;
        applicationProtocol = null;
        remoteTransportParameters = null;
        handshakeSession.peerCertificates(null);
        handshakeSession.localCertificates(null);
        handshakeKeys = null;
        handshakeKeysDiscarded = false;
        oneRttKeys = null;
        oneRttKeysDiscarded = false;
        serverSelectedPreSharedKey = false;
        handshakePhase = ClientHandshakePhase.EXPECT_SERVER_HELLO;
        outboundFlightCompletesHandshake = false;
        sendKeySpace = KeySpace.INITIAL;
        handshakeState = HandshakeState.NEED_SEND_CRYPTO;
    }

    private QuicVersion currentQuicVersionLocked() {
        QuicVersion currentVersion = negotiatedVersion;
        if (currentVersion != null) {
            return currentVersion;
        }
        if (initialVersion != null) {
            return initialVersion;
        }
        throw new IllegalStateException("Quic version hasn't been negotiated yet");
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

    private enum ClientHandshakePhase {
        EXPECT_SERVER_HELLO,
        EXPECT_ENCRYPTED_EXTENSIONS,
        EXPECT_CERTIFICATE_REQUEST_OR_CERTIFICATE,
        EXPECT_CERTIFICATE,
        EXPECT_CERTIFICATE_VERIFY,
        EXPECT_FINISHED,
        WAIT_HANDSHAKE_DONE
    }

    private record ClientCredentials(PrivateKey privateKey,
                                     X509Certificate[] certificateChain,
                                     QuicTlsSignatureScheme signatureScheme) {
        private ClientCredentials {
            Objects.requireNonNull(privateKey, "privateKey");
            certificateChain = Objects.requireNonNull(certificateChain, "certificateChain").clone();
            Objects.requireNonNull(signatureScheme, "signatureScheme");
        }

        @Override
        public X509Certificate[] certificateChain() {
            return certificateChain.clone();
        }
    }
}
