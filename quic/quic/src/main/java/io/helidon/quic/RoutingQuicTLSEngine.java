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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import io.helidon.quic.spi.QuicPacketTLSEngine;

final class RoutingQuicTLSEngine implements QuicPacketTLSEngine {
    // Both currently reachable implementations expose the same QUIC version set, so callers can inspect support
    // before the routing engine has selected the concrete client or server implementation.
    private static final Set<QuicVersion> SUPPORTED_QUIC_VERSIONS = Set.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2);
    // The routing wrapper keeps an inert callback until a concrete engine exists so early configuration calls do not
    // have to instantiate the client or server implementation.
    private static final QuicTransportParametersConsumer NOOP_TRANSPORT_PARAMETERS_CONSUMER = buffer -> {
    };
    // The mode decides which implementation gets created, so delayed selection still requires the caller to choose
    // client or server mode before the handshake starts.
    private static final String MODE_NOT_SET_MESSAGE = "QUIC TLS engine mode must be set before starting the handshake";
    // Once a concrete engine exists, switching modes would require swapping implementations mid-flight, so the
    // wrapper rejects mode changes after initialization.
    private static final String MODE_CHANGE_AFTER_INIT_MESSAGE =
            "QUIC TLS engine mode cannot change after the implementation has been initialized";

    private final QuicTlsConfigSnapshot config;
    private final String peerHost;
    private final int peerPort;
    private final QuicTlsSessionCache sessionCache;
    private final QuicTlsServerSessionCache serverSessionCache;
    private final QuicTlsServerSelector serverTlsSelector;
    private final Lock lock = new ReentrantLock();

    private volatile QuicPacketTLSEngine delegate;
    private volatile Boolean useClientMode;
    private volatile SSLParameters sslParameters;
    private volatile boolean sslParametersConfigured;
    private volatile QuicTransportParametersConsumer remoteTransportParametersConsumer =
            NOOP_TRANSPORT_PARAMETERS_CONSUMER;
    private volatile byte[] localQuicTransportParameters;
    private volatile QuicOneRttContext oneRttContext;

    RoutingQuicTLSEngine(QuicTlsConfigSnapshot config,
                         QuicTlsSessionCache sessionCache,
                         QuicTlsServerSessionCache serverSessionCache,
                         QuicTlsServerSelector serverTlsSelector) {
        this.config = Objects.requireNonNull(config, "config");
        this.peerHost = null;
        this.peerPort = -1;
        this.sessionCache = Objects.requireNonNull(sessionCache, "sessionCache");
        this.serverSessionCache = Objects.requireNonNull(serverSessionCache, "serverSessionCache");
        this.serverTlsSelector = serverTlsSelector;
        this.sslParameters = config.sslParameters();
    }

    RoutingQuicTLSEngine(QuicTlsConfigSnapshot config,
                         String peerHost,
                         int peerPort,
                         QuicTlsSessionCache sessionCache,
                         QuicTlsServerSessionCache serverSessionCache,
                         QuicTlsServerSelector serverTlsSelector) {
        this.config = Objects.requireNonNull(config, "config");
        this.peerHost = Objects.requireNonNull(peerHost, "peerHost");
        this.peerPort = peerPort;
        this.sessionCache = Objects.requireNonNull(sessionCache, "sessionCache");
        this.serverSessionCache = Objects.requireNonNull(serverSessionCache, "serverSessionCache");
        this.serverTlsSelector = serverTlsSelector;
        this.sslParameters = config.sslParameters();
    }

    @Override
    public Set<QuicVersion> supportedQuicVersions() {
        QuicPacketTLSEngine current = delegate;
        return current == null ? SUPPORTED_QUIC_VERSIONS : current.supportedQuicVersions();
    }

    @Override
    public boolean clientMode() {
        QuicPacketTLSEngine current = delegate;
        return current != null ? current.clientMode() : Boolean.TRUE.equals(useClientMode);
    }

    @Override
    public void clientMode(boolean mode) {
        lock.lock();
        try {
            QuicPacketTLSEngine current = delegate;
            if (current != null) {
                if (current.clientMode() != mode) {
                    throw new IllegalStateException(MODE_CHANGE_AFTER_INIT_MESSAGE);
                }
                return;
            }
            if (!mode && !QuicTlsKeyManagers.supportsPublicServerEngine(config, sslParameters)) {
                throw new UnsupportedOperationException(
                        QuicTlsKeyManagers.unsupportedServerModeMessage(config, sslParameters));
            }
            if (mode && !QuicTlsTrustManagers.supportsPublicClientEngine(config)) {
                throw new UnsupportedOperationException(QuicTlsTrustManagers.CLIENT_TRUST_MANAGER_REQUIRED_MESSAGE);
            }
            useClientMode = mode;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SSLParameters sslParameters() {
        QuicPacketTLSEngine current = delegate;
        return current != null ? current.sslParameters() : QuicTlsParameters.copy(sslParameters);
    }

    @Override
    public void sslParameters(SSLParameters sslParameters) {
        SSLParameters copy = QuicTlsParameters.copy(Objects.requireNonNull(sslParameters, "sslParameters"));
        QuicTlsParameters.validateSupported(copy);
        lock.lock();
        try {
            QuicPacketTLSEngine current = delegate;
            if (current != null) {
                current.sslParameters(copy);
            }
            this.sslParameters = copy;
            this.sslParametersConfigured = true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<String> applicationProtocol() {
        QuicPacketTLSEngine current = delegate;
        return current == null ? Optional.empty() : current.applicationProtocol();
    }

    @Override
    public SSLSession session() {
        return delegate().session();
    }

    @Override
    public Optional<SSLSession> handshakeSession() {
        QuicPacketTLSEngine current = delegate;
        return current == null ? Optional.empty() : current.handshakeSession();
    }

    @Override
    public HandshakeState handshakeState() {
        return delegate().handshakeState();
    }

    @Override
    public boolean isTLSHandshakeComplete() {
        QuicPacketTLSEngine current = delegate;
        return current != null && current.isTLSHandshakeComplete();
    }

    @Override
    public KeySpace currentSendKeySpace() {
        return delegate().currentSendKeySpace();
    }

    @Override
    public boolean keysAvailable(KeySpace keySpace) {
        QuicPacketTLSEngine current = delegate;
        if (current == null) {
            return keySpace == KeySpace.RETRY;
        }
        return current.keysAvailable(keySpace);
    }

    @Override
    public void discardKeys(KeySpace keySpace) {
        delegate().discardKeys(keySpace);
    }

    @Override
    public void localQuicTransportParametersBuffer(ByteBuffer params) {
        byte[] copy = copy(Objects.requireNonNull(params, "params"));
        localQuicTransportParameters = copy;
        QuicPacketTLSEngine current = delegate;
        if (current != null) {
            current.localQuicTransportParametersBuffer(ByteBuffer.wrap(copy).asReadOnlyBuffer());
        }
    }

    @Override
    public void restartHandshake() {
        delegate().restartHandshake();
    }

    @Override
    public void remoteQuicTransportParametersConsumer(QuicTransportParametersConsumer consumer) {
        QuicTransportParametersConsumer callback = Objects.requireNonNull(consumer, "consumer");
        remoteTransportParametersConsumer = callback;
        QuicPacketTLSEngine current = delegate;
        if (current != null) {
            current.remoteQuicTransportParametersConsumer(callback);
        }
    }

    @Override
    public void deriveInitialKeysBuffer(QuicVersion quicVersion, ByteBuffer connectionId) {
        delegate().deriveInitialKeysBuffer(quicVersion, connectionId);
    }

    @Override
    public int headerProtectionSampleSize(KeySpace keySpace) {
        return delegate().headerProtectionSampleSize(keySpace);
    }

    @Override
    public ByteBuffer computeHeaderProtectionMaskBuffer(KeySpace keySpace, boolean incoming, ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException {
        return delegate().computeHeaderProtectionMaskBuffer(keySpace, incoming, sample);
    }

    @Override
    public long computeHeaderProtectionMaskBits(KeySpace keySpace, boolean incoming, ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException {
        return delegate().computeHeaderProtectionMaskBits(keySpace, incoming, sample);
    }

    @Override
    public int authTagSize() {
        return delegate().authTagSize();
    }

    @Override
    public void encryptPacketBuffer(KeySpace keySpace,
                                    long packetNumber,
                                    IntFunction<ByteBuffer> headerGenerator,
                                    ByteBuffer packetPayload,
                                    ByteBuffer output)
            throws QuicKeyUnavailableException, QuicTransportException, BufferOverflowException {
        delegate().encryptPacketBuffer(keySpace, packetNumber, headerGenerator, packetPayload, output);
    }

    @Override
    public void decryptPacketBuffer(KeySpace keySpace,
                                    long packetNumber,
                                    int keyPhase,
                                    ByteBuffer packet,
                                    int headerLength,
                                    ByteBuffer output)
            throws QuicKeyUnavailableException, QuicPacketAuthenticationException, QuicTransportException {
        delegate().decryptPacketBuffer(keySpace, packetNumber, keyPhase, packet, headerLength, output);
    }

    @Override
    public void signRetryPacketBuffer(QuicVersion version,
                                      ByteBuffer originalConnectionId,
                                      ByteBuffer packet,
                                      ByteBuffer output)
            throws BufferOverflowException, QuicTransportException {
        delegate().signRetryPacketBuffer(version, originalConnectionId, packet, output);
    }

    @Override
    public void verifyRetryPacketBuffer(QuicVersion version, ByteBuffer originalConnectionId, ByteBuffer packet)
            throws QuicPacketAuthenticationException, QuicTransportException {
        delegate().verifyRetryPacketBuffer(version, originalConnectionId, packet);
    }

    @Override
    public Optional<ByteBuffer> handshakeBytesBuffer(KeySpace keySpace) {
        return delegate().handshakeBytesBuffer(keySpace);
    }

    @Override
    public void consumeHandshakeBytesBuffer(KeySpace keySpace, ByteBuffer payload) throws QuicTransportException {
        delegate().consumeHandshakeBytesBuffer(keySpace, payload);
    }

    @Override
    public Optional<Runnable> delegatedTask() {
        QuicPacketTLSEngine current = delegate;
        return current == null ? Optional.empty() : current.delegatedTask();
    }

    @Override
    public boolean tryMarkHandshakeDone() throws IllegalStateException {
        return delegate().tryMarkHandshakeDone();
    }

    @Override
    public boolean tryReceiveHandshakeDone() throws IllegalStateException {
        return delegate().tryReceiveHandshakeDone();
    }

    @Override
    public void versionNegotiated(QuicVersion quicVersion) {
        delegate().versionNegotiated(quicVersion);
    }

    @Override
    public void oneRttContext(QuicOneRttContext ctx) {
        QuicOneRttContext context = Objects.requireNonNull(ctx, "ctx");
        oneRttContext = context;
        QuicPacketTLSEngine current = delegate;
        if (current != null) {
            current.oneRttContext(context);
        }
    }

    private static byte[] copy(ByteBuffer buffer) {
        ByteBuffer input = buffer.slice();
        byte[] bytes = new byte[input.remaining()];
        input.get(bytes);
        return bytes;
    }

    private QuicPacketTLSEngine delegate() {
        QuicPacketTLSEngine current = delegate;
        if (current != null) {
            return current;
        }
        lock.lock();
        try {
            current = delegate;
            if (current != null) {
                return current;
            }
            Boolean clientMode = useClientMode;
            if (clientMode == null) {
                throw new IllegalStateException(MODE_NOT_SET_MESSAGE);
            }
            current = createDelegate(clientMode);
            current.clientMode(clientMode);
            if (sslParametersConfigured) {
                current.sslParameters(sslParameters);
            }
            current.remoteQuicTransportParametersConsumer(remoteTransportParametersConsumer);
            QuicOneRttContext context = oneRttContext;
            if (context != null) {
                current.oneRttContext(context);
            }
            byte[] transportParameters = localQuicTransportParameters;
            if (transportParameters != null) {
                current.localQuicTransportParametersBuffer(ByteBuffer.wrap(transportParameters).asReadOnlyBuffer());
            }
            delegate = current;
            return current;
        } finally {
            lock.unlock();
        }
    }

    private QuicPacketTLSEngine createDelegate(boolean clientMode) {
        if (clientMode) {
            if (!QuicTlsTrustManagers.supportsPublicClientEngine(config)) {
                throw new UnsupportedOperationException(QuicTlsTrustManagers.CLIENT_TRUST_MANAGER_REQUIRED_MESSAGE);
            }
            return peerHost == null
                    ? new HelidonClientQuicTLSEngine(config, sessionCache)
                    : new HelidonClientQuicTLSEngine(config, peerHost, peerPort, sessionCache);
        }
        if (QuicTlsKeyManagers.supportsPublicServerEngine(config, sslParameters)) {
            return peerHost == null
                    ? new HelidonServerQuicTLSEngine(config, serverSessionCache, serverTlsSelector)
                    : new HelidonServerQuicTLSEngine(config, peerHost, peerPort, serverSessionCache, serverTlsSelector);
        }
        throw new UnsupportedOperationException(QuicTlsKeyManagers.unsupportedServerModeMessage(config, sslParameters));
    }
}
