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

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import javax.net.ssl.SSLParameters;

import org.junit.jupiter.api.Test;

import static io.helidon.quic.QuicTLSEngine.HandshakeState.NEED_RECV_CRYPTO;
import static io.helidon.quic.QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO;
import static io.helidon.quic.QuicTLSEngine.KeySpace.HANDSHAKE;
import static io.helidon.quic.QuicTLSEngine.KeySpace.INITIAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicTlsKeyShareLifecycleTest {
    private static final QuicVersion VERSION = QuicVersion.QUIC_V1;
    private static final QuicTls13CipherSuite CIPHER_SUITE = QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256;
    private static final byte[] TRANSPORT_PARAMETERS = {1, 2, 3, 4};
    private static final byte[] HEADER_PROTECTION_SAMPLE =
            HexFormat.of().parseHex("0102030405060708090a0b0c0d0e0f10");

    @Test
    void shouldDiscardClientSharesAfterServerHello() throws Exception {
        var keyShares = QuicTlsLocalKeyShares.create(List.of(QuicTlsNamedGroup.X25519, QuicTlsNamedGroup.SECP256_R1),
                                                      new SecureRandom());
        var clientHello = clientHello(keyShares);
        var handshake = QuicTls13ClientHandshake.start(VERSION, clientHello, keyShares, new SecureRandom());

        completeServerHello(handshake);

        for (var publicShare : clientHello.keyShares()) {
            assertDiscarded(keyShares, publicShare);
        }
    }

    @Test
    void shouldDiscardSupersededSharesAfterHelloRetryRequest() throws Exception {
        var keyShares = QuicTlsLocalKeyShares.create(List.of(QuicTlsNamedGroup.X25519), new SecureRandom());
        var clientHello = clientHello(keyShares);
        var publicShare = clientHello.keyShares().getFirst();
        var handshake = QuicTls13ClientHandshake.start(VERSION, clientHello, keyShares, new SecureRandom());
        var retry = QuicTlsServerHelloMessage.helloRetryRequest(
                clientHello.legacySessionId(),
                CIPHER_SUITE.codePoint(),
                List.of(QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                                 QuicTlsKeyShares.encodeHelloRetryRequest(QuicTlsNamedGroup.SECP256_R1)),
                        QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                                 QuicTlsSupportedVersions.encodeServerHello(
                                                         QuicTlsSupportedVersions.TLS_1_3))));

        var result = (QuicTls13ClientHandshake.HelloRetryRequestResult) handshake.consumeServerHello(retry.encode());

        assertDiscarded(keyShares, publicShare);
        assertThat(result.clientHelloMessage().keyShares().getFirst().namedGroup(), is(QuicTlsNamedGroup.SECP256_R1));
        completeServerHello(handshake);
    }

    @Test
    void shouldUseFreshClientSharesWhenRestartingWithIdenticalParameters() throws Exception {
        var engine = newEngine();
        var firstHello = QuicTlsClientHelloMessage.decode(engine.handshakeBytesBuffer(INITIAL).orElseThrow());
        var firstPublicShare = firstHello.keyShares().getFirst();

        engine.restartHandshake();

        assertThat(engine.handshakeState(), is(NEED_SEND_CRYPTO));
        var restartedHello = QuicTlsClientHelloMessage.decode(engine.handshakeBytesBuffer(INITIAL).orElseThrow());
        var restartedPublicShare = restartedHello.keyShares().getFirst();
        assertThat(restartedHello.supportedGroups(), equalTo(firstHello.supportedGroups()));
        assertThat(restartedHello.cipherSuites(), equalTo(firstHello.cipherSuites()));
        assertThat(restartedHello.extension(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS),
                   equalTo(firstHello.extension(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS)));
        assertThat(restartedPublicShare.namedGroup(), is(firstPublicShare.namedGroup()));
        assertThat(restartedPublicShare.keyExchange(), not(equalTo(firstPublicShare.keyExchange())));

        completeServerHello(engine, restartedHello);
    }

    @Test
    void shouldDiscardClientSharesAfterFatalServerHello() throws Exception {
        var keyShares = QuicTlsLocalKeyShares.create(List.of(QuicTlsNamedGroup.X25519), new SecureRandom());
        var clientHello = clientHello(keyShares);
        var publicShare = clientHello.keyShares().getFirst();
        var handshake = QuicTls13ClientHandshake.start(VERSION, clientHello, keyShares, new SecureRandom());
        var malformed = ByteBuffer.wrap(new byte[] {QuicTlsHandshakeMessages.SERVER_HELLO, 0, 0, 0});

        var failure = assertThrows(QuicTransportException.class,
                                   () -> handshake.consumeServerHello(malformed.duplicate()));

        assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
        assertDiscarded(keyShares, publicShare);
        assertThrows(IllegalStateException.class, () -> handshake.consumeServerHello(malformed.duplicate()));
    }

    @Test
    void shouldRejectServerHelloAfterReassemblerFailure() throws Exception {
        var engine = newEngine();
        var clientHello = QuicTlsClientHelloMessage.decode(engine.handshakeBytesBuffer(INITIAL).orElseThrow());
        var serverShare = QuicTlsKeySharePossession.create(clientHello.keyShares().getFirst().namedGroup(), new SecureRandom());
        try {
            var validServerHello = serverHello(clientHello, serverShare.keyShareEntry());
            // A Handshake-level message in Initial is rejected before the hello-state machine receives it.
            var wrongKeySpace = ByteBuffer.wrap(new byte[] {QuicTlsHandshakeMessages.ENCRYPTED_EXTENSIONS, 0, 0, 0});

            var failure = assertThrows(QuicTransportException.class,
                                       () -> engine.consumeHandshakeBytesBuffer(INITIAL, wrongKeySpace));

            assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 10));
            assertThat(failure.getCause().getMessage(), containsString("received in INITIAL but should be HANDSHAKE"));
            var subsequentFailure = assertThrows(QuicTransportException.class,
                                                 () -> engine.consumeHandshakeBytesBuffer(INITIAL, validServerHello.encode()));
            assertThat(subsequentFailure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 10));
            assertThat(subsequentFailure.getCause().getMessage(), containsString("discarded"));
            assertThat(engine.keysAvailable(HANDSHAKE), is(false));
        } finally {
            serverShare.discard();
        }
    }

    @Test
    void shouldPreventHelloExchangeAfterInitialKeysAreDiscarded() throws Exception {
        var engine = newEngine();
        var clientHello = QuicTlsClientHelloMessage.decode(engine.handshakeBytesBuffer(INITIAL).orElseThrow());
        var serverShare = QuicTlsKeySharePossession.create(clientHello.keyShares().getFirst().namedGroup(), new SecureRandom());
        try {
            var validServerHello = serverHello(clientHello, serverShare.keyShareEntry());

            engine.discardKeys(INITIAL);
            engine.discardKeys(INITIAL);

            assertThat(engine.keysAvailable(INITIAL), is(false));
            var helloFailure = assertThrows(QuicTransportException.class,
                                            () -> engine.consumeHandshakeBytesBuffer(INITIAL, validServerHello.encode()));
            assertThat(helloFailure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 10));
            assertThat(helloFailure.getCause().getMessage(), containsString("discarded"));
            assertThat(engine.keysAvailable(HANDSHAKE), is(false));
            engine.restartHandshake();
            var failure = assertThrows(IllegalStateException.class, () -> engine.handshakeBytesBuffer(INITIAL));
            assertThat(failure.getMessage(), containsString("Initial QUIC keys already discarded"));
        } finally {
            serverShare.discard();
        }
    }

    @Test
    void shouldDiscardSuppliedClientSharesWhenHandshakeConstructionFails() throws Exception {
        var keyShares = QuicTlsLocalKeyShares.create(List.of(QuicTlsNamedGroup.X25519), new SecureRandom());
        var clientHello = clientHello(keyShares,
                                      QuicTlsExtension.create(QuicTlsExtensions.COOKIE, QuicTlsCookie.encode(new byte[] {1})));
        var publicShare = clientHello.keyShares().getFirst();

        var failure = assertThrows(IllegalArgumentException.class,
                                   () -> QuicTls13ClientHandshake.start(VERSION, clientHello, keyShares, new SecureRandom()));

        assertThat(failure.getMessage(), containsString("Initial ClientHello must not include a cookie"));
        assertDiscarded(keyShares, publicShare);
    }

    private static HelidonClientQuicTLSEngine newEngine() throws Exception {
        var tls = QuicTlsTestSupport.tls(null, QuicTlsTestSupport.defaultTrustManager());
        var engine = new HelidonClientQuicTLSEngine(
                QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create())),
                "example.com",
                443);
        var sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        sslParameters.setCipherSuites(new String[] {CIPHER_SUITE.name()});
        sslParameters.setApplicationProtocols(new String[] {"h3"});
        sslParameters.setNamedGroups(new String[] {"x25519", "secp256r1"});
        sslParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
        engine.clientMode(true);
        engine.sslParameters(sslParameters);
        engine.deriveInitialKeysBuffer(VERSION, ByteBuffer.wrap(HexFormat.of().parseHex("8394c8f03e515708")));
        engine.localQuicTransportParametersBuffer(ByteBuffer.wrap(TRANSPORT_PARAMETERS));
        return engine;
    }

    private static QuicTlsClientHelloMessage clientHello(QuicTlsLocalKeyShares keyShares,
                                                         QuicTlsExtension... additionalExtensions) {
        List<QuicTlsExtension> extensions = new ArrayList<>(List.of(
                QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_GROUPS,
                                        QuicTlsSupportedGroups.encode(List.of(QuicTlsNamedGroup.X25519,
                                                                              QuicTlsNamedGroup.SECP256_R1))),
                QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                        QuicTlsSupportedVersions.encodeClientHello(List.of(QuicTlsSupportedVersions.TLS_1_3))),
                QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                        QuicTlsKeyShares.encodeClientHello(keyShares.keyShareEntries())),
                QuicTlsExtension.create(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS, TRANSPORT_PARAMETERS)));
        extensions.addAll(List.of(additionalExtensions));
        return QuicTlsClientHelloMessage.create(0x0303,
                                                 new byte[QuicTlsCodecSupport.RANDOM_LENGTH],
                                                 new byte[0],
                                                 List.of(CIPHER_SUITE.codePoint()),
                                                 new byte[] {0},
                                                 extensions);
    }

    private static QuicTlsServerHelloMessage serverHello(QuicTlsClientHelloMessage clientHello,
                                                         QuicTlsKeyShareEntry serverShare) {
        return QuicTlsServerHelloMessage.create(
                0x0303,
                new byte[QuicTlsCodecSupport.RANDOM_LENGTH],
                clientHello.legacySessionId(),
                CIPHER_SUITE.codePoint(),
                0,
                List.of(QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                                 QuicTlsKeyShares.encodeServerHello(serverShare)),
                        QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                                 QuicTlsSupportedVersions.encodeServerHello(
                                                         QuicTlsSupportedVersions.TLS_1_3))));
    }

    private static void completeServerHello(QuicTls13ClientHandshake handshake) throws Exception {
        var clientHello = handshake.clientHelloMessage();
        var clientShare = clientHello.keyShares().getFirst();
        var serverShare = QuicTlsKeySharePossession.create(clientShare.namedGroup(), new SecureRandom());
        try {
            var serverHello = serverHello(clientHello, serverShare.keyShareEntry());
            var serverSecrets = QuicTls13ConnectionSecrets.create(VERSION, CIPHER_SUITE, serverShare, clientShare, false);

            var result = (QuicTls13ClientHandshake.CompleteResult) handshake.consumeServerHello(serverHello.encode());

            assertThat(result.connectionSecrets().handshakeSecret().getEncoded(),
                       equalTo(serverSecrets.handshakeSecret().getEncoded()));
            var serverKeys = serverSecrets.deriveHandshakeTrafficKeys(result.serverHelloTranscriptHash());
            assertThat(result.handshakeTrafficKeys().computeHeaderProtectionMask(false, ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE)),
                       equalTo(serverKeys.computeHeaderProtectionMask(true, ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE))));
        } finally {
            serverShare.discard();
        }
    }

    private static void completeServerHello(HelidonClientQuicTLSEngine engine,
                                            QuicTlsClientHelloMessage clientHello) throws Exception {
        var clientShare = clientHello.keyShares().getFirst();
        var serverShare = QuicTlsKeySharePossession.create(clientShare.namedGroup(), new SecureRandom());
        try {
            var serverHello = serverHello(clientHello, serverShare.keyShareEntry());
            var serverSecrets = QuicTls13ConnectionSecrets.create(VERSION, CIPHER_SUITE, serverShare, clientShare, false);

            engine.versionNegotiated(VERSION);
            engine.consumeHandshakeBytesBuffer(INITIAL, serverHello.encode());

            var serverKeys = serverSecrets.deriveHandshakeTrafficKeys(engine.serverHelloTranscriptHash());
            assertThat(engine.keysAvailable(HANDSHAKE), is(true));
            assertThat(engine.handshakeState(), is(NEED_RECV_CRYPTO));
            assertThat(engine.computeHeaderProtectionMaskBuffer(HANDSHAKE, false, ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE)),
                       equalTo(serverKeys.computeHeaderProtectionMask(true, ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE))));
        } finally {
            serverShare.discard();
        }
    }

    private static void assertDiscarded(QuicTlsLocalKeyShares keyShares, QuicTlsKeyShareEntry originalPublicShare) {
        var peer = QuicTlsKeySharePossession.create(originalPublicShare.namedGroup(), new SecureRandom());
        try {
            var failure = assertThrows(IllegalStateException.class, () -> keyShares.sharedSecret(peer.keyShareEntry()));
            assertThat(failure.getMessage(), containsString("discarded"));
            assertThat(keyShares.keyShareEntries(), hasItem(originalPublicShare));
            assertThat(keyShares.keyShareEntry(originalPublicShare.namedGroup()), is(originalPublicShare));
        } finally {
            peer.discard();
        }
    }
}
