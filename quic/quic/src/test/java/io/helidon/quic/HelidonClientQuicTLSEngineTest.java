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

import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

import io.helidon.quic.spi.QuicPacketTLSEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.quic.QuicTLSEngine.HandshakeState.HANDSHAKE_CONFIRMED;
import static io.helidon.quic.QuicTLSEngine.HandshakeState.NEED_RECV_CRYPTO;
import static io.helidon.quic.QuicTLSEngine.HandshakeState.NEED_RECV_HANDSHAKE_DONE;
import static io.helidon.quic.QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO;
import static io.helidon.quic.QuicTLSEngine.KeySpace.HANDSHAKE;
import static io.helidon.quic.QuicTLSEngine.KeySpace.INITIAL;
import static io.helidon.quic.QuicTLSEngine.KeySpace.ONE_RTT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HelidonClientQuicTLSEngineTest {
    private static final HexFormat HEX = HexFormat.of();
    private static final QuicVersion VERSION = QuicVersion.QUIC_V1;
    // These transport-parameter bytes are arbitrary but stable so the ClientHello / restart tests can assert that
    // the Helidon-owned engine keeps the exact extension payload across retries and regenerations.
    private static final byte[] TRANSPORT_PARAMETERS = bytes("010203040506");
    // The restarted handshake uses a different payload to prove restartHandshake() really regenerates the ClientHello.
    private static final byte[] RESTARTED_TRANSPORT_PARAMETERS = bytes("0a0b0c0d0e0f");
    // HelloRetryRequest cookies are opaque to the client, so any non-empty payload is sufficient for copy-through.
    private static final byte[] HRR_COOKIE = bytes("a0a1a2a3a4a5");
    private static final byte[] SESSION_TICKET_NONCE = bytes("101112131415");
    private static final byte[] SESSION_TICKET = bytes("2021222324252627");
    // A fixed sample is enough to prove that the engine-installed Handshake keys match the independently derived peer keys.
    private static final byte[] HEADER_PROTECTION_SAMPLE = bytes("0102030405060708090a0b0c0d0e0f10");
    // The test packet header is intentionally small because the Handshake packet-protection code only needs stable AAD bytes.
    private static final int HEADER_LENGTH = 4;

    @Test
    void shouldMatchPackedAndLegacyInitialHeaderProtectionMasks() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine("example.com");
        QuicPacketTLSEngine packetEngine = packetEngine(engine);
        ByteBuffer legacy = packetEngine.computeHeaderProtectionMaskBuffer(
                INITIAL,
                false,
                ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE));

        assertThat(packetEngine.computeHeaderProtectionMaskBits(
                           INITIAL,
                           false,
                           ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE)),
                   is(packedHeaderProtectionMask(legacy)));
    }

    @Test
    void shouldEmitClientHelloAndInstallHandshakeKeysAfterServerHello() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine("example.com");

        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(clientHelloBytes);

        assertThat(engine.handshakeState(), is(NEED_RECV_CRYPTO));
        assertThat(clientHello.extension(QuicTlsExtensions.SERVER_NAME).isPresent(), is(true));
        assertThat(clientHello.extension(QuicTlsExtensions.APPLICATION_LAYER_PROTOCOL_NEGOTIATION).isPresent(), is(true));
        assertThat(clientHello.extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS).isPresent(), is(true));
        assertThat(clientHello.extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT).isPresent(), is(true));
        assertThat(clientHello.extension(QuicTlsExtensions.PSK_KEY_EXCHANGE_MODES).isPresent(), is(true));
        assertThat(QuicTlsSignatureScheme.decodeCertificateVerifyVector(
                           clientHello.extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS).orElseThrow().dataBuffer(),
                           "supported_signature_algorithms",
                           "ClientHello"),
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)));
        assertThat(QuicTlsSignatureScheme.decodeCertificateSignatureVector(
                           clientHello.extension(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT).orElseThrow().dataBuffer(),
                           "supported_certificate_signature_algorithms",
                           "ClientHello"),
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256,
                                   QuicTlsSignatureScheme.RSA_PKCS1_SHA256)));
        assertThat(clientHello.legacySessionId().length, is(0));
        assertThat(clientHello.extension(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS).orElseThrow().data(),
                   equalTo(TRANSPORT_PARAMETERS));

        QuicTls13CipherSuite cipherSuite = QuicTls13CipherSuite.forCodePoint(clientHello.cipherSuites().getFirst());
        QuicTlsKeySharePossession serverKeyShare =
                QuicTlsKeySharePossession.create(clientHello.keyShares().getFirst().namedGroup(), new SecureRandom());
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            cipherSuite,
                                                            QuicTlsSupportedVersions.TLS_1_3,
                                                            serverKeyShare.keyShareEntry());

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, serverHello.encode());

        QuicTls13ConnectionSecrets serverSecrets = QuicTls13ConnectionSecrets.create(VERSION,
                                                                                     cipherSuite,
                                                                                     serverKeyShare,
                                                                                     clientHello.keyShares().getFirst(),
                                                                                     false);
        QuicLongHeaderTrafficKeys serverHandshakeKeys =
                serverSecrets.deriveHandshakeTrafficKeys(engine.serverHelloTranscriptHash());

        assertThat(engine.keysAvailable(HANDSHAKE), is(true));
        assertThat(engine.currentSendKeySpace(), is(HANDSHAKE));
        assertThat(engine.handshakeState(), is(NEED_RECV_CRYPTO));
        ByteBuffer expectedHandshakeMask = serverHandshakeKeys.computeHeaderProtectionMask(
                true,
                ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE));
        assertThat(hex(packetEngine(engine).computeHeaderProtectionMaskBuffer(
                           HANDSHAKE,
                           false,
                           ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE))),
                   is(hex(expectedHandshakeMask)));
        assertThat(packetEngine(engine).computeHeaderProtectionMaskBits(
                           HANDSHAKE,
                           false,
                           ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE)),
                   is(packedHeaderProtectionMask(expectedHandshakeMask)));
        ByteBuffer expectedIncomingHandshakeMask = serverHandshakeKeys.computeHeaderProtectionMask(
                false,
                ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE));
        assertThat(packetEngine(engine).computeHeaderProtectionMaskBits(
                           HANDSHAKE,
                           true,
                           ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE)),
                   is(packedHeaderProtectionMask(expectedIncomingHandshakeMask)));
        assertThat(decryptHandshake(serverHandshakeKeys, encryptHandshake(engine, 7, (byte) 0x31)),
                   is((byte) 0x31));
        assertThat(decryptHandshake(engine, encryptHandshake(serverHandshakeKeys, 8, (byte) 0x32)),
                   is((byte) 0x32));
    }

    @Test
    void shouldNotEmitServerNameWhenExplicitlyDisabled() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine("example.com");
        SSLParameters sslParameters = engine.sslParameters();
        sslParameters.setServerNames(List.of());
        engine.sslParameters(sslParameters);

        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(clientHelloBytes);

        assertThat(clientHello.extension(QuicTlsExtensions.SERVER_NAME).isEmpty(), is(true));
    }

    @Test
    void shouldRejectUnsupportedSslPoliciesAtomically() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine("example.com");

        SSLParameters constrained = engine.sslParameters();
        constrained.setAlgorithmConstraints(QuicTlsTestSupport.allPermittingAlgorithmConstraints());
        IllegalArgumentException constraintsFailure =
                assertThrows(IllegalArgumentException.class, () -> engine.sslParameters(constrained));
        assertThat(constraintsFailure.getMessage(), containsString("AlgorithmConstraints"));
        assertThat(engine.sslParameters().getAlgorithmConstraints(), is((Object) null));

        SSLParameters matched = engine.sslParameters();
        matched.setSNIMatchers(List.of(SNIHostName.createSNIMatcher("example\\.com")));
        IllegalArgumentException matchersFailure =
                assertThrows(IllegalArgumentException.class, () -> engine.sslParameters(matched));
        assertThat(matchersFailure.getMessage(), containsString("SNIMatchers"));
        assertThat(engine.sslParameters().getSNIMatchers(), is((Object) null));
    }

    @Test
    void shouldRegenerateClientHelloAfterHelloRetryRequest() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine("example.com");
        QuicTlsClientHelloMessage clientHello1 =
                QuicTlsClientHelloMessage.decode(required(packetEngine(engine).handshakeBytesBuffer(INITIAL)));
        QuicTlsServerHelloMessage helloRetryRequest = helloRetryRequest(clientHello1.legacySessionId(),
                                                                        QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256,
                                                                        QuicTlsNamedGroup.SECP256_R1,
                                                                        HRR_COOKIE);

        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, helloRetryRequest.encode());

        assertThat(engine.handshakeState(), is(NEED_SEND_CRYPTO));
        assertThat(engine.currentSendKeySpace(), is(INITIAL));

        QuicTlsClientHelloMessage clientHello2 =
                QuicTlsClientHelloMessage.decode(required(packetEngine(engine).handshakeBytesBuffer(INITIAL)));

        assertThat(clientHello2.keyShares().stream().map(QuicTlsKeyShareEntry::namedGroup).toList(),
                   equalTo(List.of(QuicTlsNamedGroup.SECP256_R1)));
        assertThat(clientHello2.legacySessionId().length, is(0));
        assertThat(QuicTlsCookie.decode(clientHello2.extension(QuicTlsExtensions.COOKIE).orElseThrow().dataBuffer()),
                   equalTo(HRR_COOKIE));
        assertThat(clientHello2.extension(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS).orElseThrow().data(),
                   equalTo(TRANSPORT_PARAMETERS));
    }

    @Test
    void shouldRestartHandshakeWithFreshClientHello() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine("example.com");
        byte[] firstClientHello = copy(packetEngine(engine).handshakeBytesBuffer(INITIAL));

        packetEngine(engine).localQuicTransportParametersBuffer(ByteBuffer.wrap(RESTARTED_TRANSPORT_PARAMETERS));
        engine.restartHandshake();

        assertThat(engine.handshakeState(), is(NEED_SEND_CRYPTO));

        byte[] restartedClientHello = copy(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage restarted = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(restartedClientHello));

        assertThat(restartedClientHello, not(equalTo(firstClientHello)));
        assertThat(restarted.extension(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS).orElseThrow().data(),
                   equalTo(RESTARTED_TRANSPORT_PARAMETERS));
    }

    @Test
    void shouldSurfaceEncryptedExtensionsTransportParametersAndAlpn() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine("example.com");
        AtomicReference<byte[]> remoteTransportParameters = new AtomicReference<>();
        engine.remoteQuicTransportParametersConsumer(buffer -> remoteTransportParameters.set(copy(buffer)));

        QuicTlsClientHelloMessage clientHello =
                QuicTlsClientHelloMessage.decode(required(packetEngine(engine).handshakeBytesBuffer(INITIAL)));
        QuicTlsKeySharePossession serverKeyShare =
                QuicTlsKeySharePossession.create(clientHello.keyShares().getFirst().namedGroup(), new SecureRandom());
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256,
                                                            QuicTlsSupportedVersions.TLS_1_3,
                                                            serverKeyShare.keyShareEntry());

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, serverHello.encode());
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, encryptedExtensions(new byte[] {0x0A, 0x0B, 0x0C}, "h3"));

        assertThat(remoteTransportParameters.get(), equalTo(new byte[] {0x0A, 0x0B, 0x0C}));
        assertThat(required(engine.applicationProtocol()), is("h3"));
    }

    @Test
    void shouldRejectEncryptedExtensionsWithoutTransportParameters() throws Exception {
        assertRejectedEncryptedExtensions(encryptedExtensions(null, "h3"), 0x016dL);
    }

    @Test
    void shouldRejectEncryptedExtensionsWithoutAlpn() throws Exception {
        assertRejectedEncryptedExtensions(encryptedExtensions(TRANSPORT_PARAMETERS, null), 0x0178L);
    }

    @Test
    void shouldRejectEncryptedExtensionsWithoutEitherRequiredExtension() throws Exception {
        assertRejectedEncryptedExtensions(encryptedExtensions(null, null), 0x016dL);
    }

    @Test
    void shouldRejectUnofferedAlpnBeforePublishingTransportParameters() throws Exception {
        assertRejectedEncryptedExtensions(encryptedExtensions(TRANSPORT_PARAMETERS, "h2"), 0x012fL);
    }

    @Test
    void shouldRejectMalformedAlpnBeforePublishingTransportParameters() throws Exception {
        assertRejectedEncryptedExtensions(encryptedExtensions(TRANSPORT_PARAMETERS, ""), 0x0132L);
    }

    @ParameterizedTest
    @ValueSource(ints = {
            QuicTlsExtensions.KEY_SHARE,
            QuicTlsExtensions.SUPPORTED_VERSIONS,
            QuicTlsExtensions.PRE_SHARED_KEY,
            QuicTlsExtensions.SIGNATURE_ALGORITHMS,
            QuicTlsExtensions.PADDING,
            QuicTlsExtensions.COOKIE,
            QuicTlsExtensions.PSK_KEY_EXCHANGE_MODES,
            QuicTlsExtensions.CERTIFICATE_AUTHORITIES,
            QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT
    })
    void shouldRejectExtensionsForbiddenInEncryptedExtensions(int extensionType) throws Exception {
        assertRejectedEncryptedExtensions(encryptedExtensions(TRANSPORT_PARAMETERS,
                                                               "h3",
                                                               extension(extensionType, new byte[0])),
                                          0x012fL);
    }

    @Test
    void shouldRejectUnsolicitedUnknownEncryptedExtension() throws Exception {
        assertRejectedEncryptedExtensions(encryptedExtensions(TRANSPORT_PARAMETERS,
                                                               "h3",
                                                               extension(0xFAFA, new byte[0])),
                                          0x016eL);
    }

    @Test
    void shouldRejectEncryptedExtensionsServerNameWhenSniIsDisabled() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine("example.com", new RecordingTrustManager());
        SSLParameters sslParameters = engine.sslParameters();
        sslParameters.setServerNames(List.of());
        engine.sslParameters(sslParameters);

        assertRejectedEncryptedExtensions(engine,
                                          encryptedExtensions(TRANSPORT_PARAMETERS,
                                                              "h3",
                                                              extension(QuicTlsExtensions.SERVER_NAME, new byte[0])),
                                          0x016eL);
    }

    @Test
    void shouldAcceptOfferedServerNameAndSupportedGroupsInEncryptedExtensions() throws Exception {
        RecordingTrustManager trustManager = new RecordingTrustManager();
        HelidonClientQuicTLSEngine engine = newEngine("example.com", trustManager);
        List<byte[]> remoteTransportParameters = new ArrayList<>();
        engine.remoteQuicTransportParametersConsumer(buffer -> remoteTransportParameters.add(copy(buffer)));

        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(clientHelloBytes);
        assertThat(clientHello.extension(QuicTlsExtensions.SERVER_NAME).isPresent(), is(true));
        assertThat(clientHello.extension(QuicTlsExtensions.SUPPORTED_GROUPS).isPresent(), is(true));
        ServerHandshakeFlight flight = serverHandshakeFlight(
                copy(clientHelloBytes),
                clientHello,
                TRANSPORT_PARAMETERS,
                "h3",
                extension(QuicTlsExtensions.SERVER_NAME, new byte[0]),
                extension(QuicTlsExtensions.SUPPORTED_GROUPS,
                          copy(QuicTlsSupportedGroups.encode(List.of(clientHello.keyShares().getFirst().namedGroup())))));

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        assertThat(remoteTransportParameters.size(), is(1));
        assertThat(remoteTransportParameters.getFirst(), equalTo(TRANSPORT_PARAMETERS));
        assertThat(required(engine.applicationProtocol()), is("h3"));
        assertThat(trustManager.checkServerTrustedCalls, is(1));
        assertThat(engine.keysAvailable(ONE_RTT), is(true));
        assertThat(copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE)), equalTo(flight.expectedClientFinished()));
        assertThat(engine.isTLSHandshakeComplete(), is(true));
    }

    @Test
    void shouldVerifyServerAuthenticationAndDeriveOneRttKeys() throws Exception {
        RecordingTrustManager trustManager = new RecordingTrustManager();
        HelidonClientQuicTLSEngine engine = newEngine("example.com", trustManager);
        AtomicReference<byte[]> remoteTransportParameters = new AtomicReference<>();
        engine.remoteQuicTransportParametersConsumer(buffer -> remoteTransportParameters.set(copy(buffer)));
        engine.oneRttContext(() -> -1);

        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(clientHelloBytes);
        ServerHandshakeFlight flight = serverHandshakeFlight(copy(clientHelloBytes),
                                                             clientHello,
                                                             new byte[] {0x0A, 0x0B, 0x0C},
                                                             "h3");

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        assertThat(remoteTransportParameters.get(), equalTo(new byte[] {0x0A, 0x0B, 0x0C}));
        assertThat(required(engine.applicationProtocol()), is("h3"));
        assertThat(engine.serverHelloTranscriptHash(), equalTo(flight.serverHelloTranscriptHash()));
        assertThat(trustManager.checkServerTrustedCalls, is(1));
        assertThat(trustManager.authType, is("RSA"));
        assertThat(trustManager.peerHost, is("example.com"));
        assertThat(trustManager.handshakeApplicationProtocol, is("h3"));
        assertThat(trustManager.peerCertificatesLength, is(1));
        assertThat(trustManager.localSupportedSignatureAlgorithms,
                   arrayContaining("RSASSA-PSS", "SHA256withRSA"));
        assertThat(trustManager.peerSupportedSignatureAlgorithms, is(new String[0]));
        assertThat(engine.keysAvailable(ONE_RTT), is(true));
        assertThat(engine.handshakeState(), is(NEED_SEND_CRYPTO));
        assertThat(engine.currentSendKeySpace(), is(HANDSHAKE));

        assertThat(copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE)), equalTo(flight.expectedClientFinished()));
        assertThat(engine.handshakeState(), is(NEED_RECV_HANDSHAKE_DONE));
        assertThat(engine.currentSendKeySpace(), is(ONE_RTT));
        assertThat(engine.isTLSHandshakeComplete(), is(true));
        assertThat(((X509Certificate) engine.session().getPeerCertificates()[0]).getSubjectX500Principal().getName(),
                   containsString("CN=rsa"));
        ByteBuffer expectedOneRttMask = flight.serverOneRttKeys().computeHeaderProtectionMask(
                true,
                ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE));
        assertThat(hex(packetEngine(engine).computeHeaderProtectionMaskBuffer(
                           ONE_RTT,
                           false,
                           ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE))),
                   is(hex(expectedOneRttMask)));
        assertThat(packetEngine(engine).computeHeaderProtectionMaskBits(
                           ONE_RTT,
                           false,
                           ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE)),
                   is(packedHeaderProtectionMask(expectedOneRttMask)));
        ByteBuffer expectedIncomingOneRttMask = flight.serverOneRttKeys().computeHeaderProtectionMask(
                false,
                ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE));
        assertThat(packetEngine(engine).computeHeaderProtectionMaskBits(
                           ONE_RTT,
                           true,
                           ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE)),
                   is(packedHeaderProtectionMask(expectedIncomingOneRttMask)));
        assertThat(decryptOneRtt(flight.serverOneRttKeys(), encryptOneRtt(engine, 11, (byte) 0x41).packet()),
                   is((byte) 0x41));
        assertThat(decryptOneRtt(engine, encryptOneRtt(flight.serverOneRttKeys(), 12, (byte) 0x42).packet()),
                   is((byte) 0x42));
        assertThat(engine.tryReceiveHandshakeDone(), is(true));
        assertThat(engine.handshakeState(), is(HANDSHAKE_CONFIRMED));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectCertificateRequestWithoutSignatureAlgorithms(boolean withKeyManager) throws Exception {
        RecordingTrustManager trustManager = new RecordingTrustManager();
        HelidonClientQuicTLSEngine engine = withKeyManager
                ? newEngine("example.com", trustManager,
                            new StaticKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                                 QuicTlsRfc8448Vectors.rsaPrivateKey()))
                : newEngine("example.com", trustManager);
        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(clientHelloBytes);
        MutualTlsServerHandshakeFlight flight = mutualTlsServerHandshakeFlight(
                copy(clientHelloBytes), clientHello, new byte[] {0x0A, 0x0B, 0x0C}, "h3",
                List.of(QuicTlsSignatureScheme.RSA_PKCS1_SHA256));

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        ByteBuffer certificateRequest = QuicTlsCertificateRequestMessage.create(new byte[0], List.of()).encode();

        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                () -> packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, certificateRequest));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 109));
        assertThat(thrown.reason(), containsString("signature_algorithms"));
    }

    @Test
    void shouldRespondToCertificateRequestWithClientCertificate() throws Exception {
        StaticKeyManager keyManager = new StaticKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                                           QuicTlsRfc8448Vectors.rsaPrivateKey());
        HelidonClientQuicTLSEngine engine = newEngine("example.com",
                                                      new RecordingTrustManager(),
                                                      keyManager);

        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        byte[] clientHello = copy(clientHelloBytes);
        QuicTlsClientHelloMessage clientHelloMessage = QuicTlsClientHelloMessage.decode(clientHelloBytes);
        List<QuicTlsSignatureScheme> certificateSignatureSchemes =
                List.of(QuicTlsSignatureScheme.RSA_PKCS1_SHA256);
        MutualTlsServerHandshakeFlight flight = mutualTlsServerHandshakeFlight(clientHello,
                                                                               clientHelloMessage,
                                                                               new byte[] {0x0A, 0x0B, 0x0C},
                                                                               "h3",
                                                                               certificateSignatureSchemes);

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateRequest()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        byte[] actualClientCertificate = copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE));
        assertThat(actualClientCertificate, equalTo(flight.expectedClientCertificate()));
        byte[] actualClientCertificateVerifyBytes = copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE));
        QuicTlsCertificateVerifyMessage actualCertificateVerify =
                QuicTlsCertificateVerifyMessage.decode(ByteBuffer.wrap(actualClientCertificateVerifyBytes));
        QuicTlsHandshakeTranscript transcript = new QuicTlsHandshakeTranscript();
        transcript.add(ByteBuffer.wrap(clientHello));
        transcript.add(ByteBuffer.wrap(flight.serverHello()));
        transcript.add(ByteBuffer.wrap(flight.encryptedExtensions()));
        transcript.add(ByteBuffer.wrap(flight.certificateRequest()));
        transcript.add(ByteBuffer.wrap(flight.certificate()));
        transcript.add(ByteBuffer.wrap(flight.certificateVerify()));
        transcript.add(ByteBuffer.wrap(flight.finished()));
        transcript.add(ByteBuffer.wrap(actualClientCertificate));
        assertThat(actualCertificateVerify.signatureScheme(), is(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256));
        assertThat(actualCertificateVerify.verify(QuicTlsRfc8448Vectors.rsaCertificate().getPublicKey(),
                                                  transcript.hash(flight.cipherSuite()),
                                                  true),
                   is(true));
        transcript.add(ByteBuffer.wrap(actualClientCertificateVerifyBytes));
        QuicTlsFinishedMessage actualFinished =
                QuicTlsFinishedMessage.decode(ByteBuffer.wrap(copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE))));
        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(flight.cipherSuite());
        assertThat(actualFinished.verifyData(),
                   equalTo(secretSchedule.computeVerifyData(flight.clientFinishedKey(),
                                                            transcript.hash(flight.cipherSuite()))));
        assertThat(((X509Certificate) engine.session().getLocalCertificates()[0]).getSubjectX500Principal().getName(),
                   containsString("CN=rsa"));
        assertThat(keyManager.localSupportedSignatureAlgorithms,
                   arrayContaining("RSASSA-PSS", "SHA256withRSA"));
        assertThat(keyManager.peerSupportedSignatureAlgorithms,
                   arrayContaining("SHA256withRSA"));
    }

    @Test
    void shouldRespondToCertificateRequestWithEmptyCertificateWhenNoKeyManagerIsAvailable() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine("example.com", new RecordingTrustManager());

        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(clientHelloBytes);
        List<QuicTlsSignatureScheme> certificateSignatureSchemes =
                List.of(QuicTlsSignatureScheme.RSA_PKCS1_SHA256);
        MutualTlsServerHandshakeFlight flight = mutualTlsServerHandshakeFlight(copy(clientHelloBytes),
                                                                               clientHello,
                                                                               new byte[] {0x0A, 0x0B, 0x0C},
                                                                               "h3",
                                                                               certificateSignatureSchemes);

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateRequest()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        QuicTlsCertificateMessage clientCertificate =
                QuicTlsCertificateMessage.decode(ByteBuffer.wrap(copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE))));
        assertThat(clientCertificate.requestContext(), equalTo(new byte[0]));
        assertThat(clientCertificate.certificateEntries().isEmpty(), is(true));
        assertThat(copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE)), equalTo(flight.emptyClientFinished()));
    }

    @Test
    void shouldRespondWithEmptyCertificateWhenCertificateSignaturePolicyHasNoOverlap() throws Exception {
        StaticKeyManager keyManager = new StaticKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                                           QuicTlsRfc8448Vectors.rsaPrivateKey());
        HelidonClientQuicTLSEngine engine = newEngine("example.com",
                                                      new RecordingTrustManager(),
                                                      keyManager);
        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(clientHelloBytes);
        MutualTlsServerHandshakeFlight flight = mutualTlsServerHandshakeFlight(
                copy(clientHelloBytes),
                clientHello,
                new byte[] {0x0A, 0x0B, 0x0C},
                "h3",
                List.of(QuicTlsSignatureScheme.ECDSA_SECP256R1_SHA256));

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateRequest()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        QuicTlsCertificateMessage clientCertificate =
                QuicTlsCertificateMessage.decode(ByteBuffer.wrap(
                        copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE))));
        assertThat(clientCertificate.certificateEntries().isEmpty(), is(true));
        assertThat(copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE)),
                   equalTo(flight.emptyClientFinished()));
        assertThat(keyManager.localSupportedSignatureAlgorithms, is((String[]) null));
        assertThat(keyManager.peerSupportedSignatureAlgorithms, is((String[]) null));
    }

    @Test
    void shouldPreferCompatibleClientChainWhenKeyManagerIgnoresSignaturePolicy() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine(
                "example.com",
                new RecordingTrustManager(),
                QuicTlsCertificateChainFixtures.ignoringSignaturePolicyKeyManager(true));
        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(clientHelloBytes);
        MutualTlsServerHandshakeFlight flight = mutualTlsServerHandshakeFlight(
                copy(clientHelloBytes),
                clientHello,
                new byte[] {0x0A, 0x0B, 0x0C},
                "h3",
                List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256));

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateRequest()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        QuicTlsCertificateMessage clientCertificate =
                QuicTlsCertificateMessage.decode(ByteBuffer.wrap(
                        copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE))));
        X509Certificate[] certificateChain = clientCertificate.x509Certificates();
        assertThat(certificateChain.length, is(2));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           certificateChain,
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)),
                   is(true));
        QuicTlsCertificateVerifyMessage certificateVerify =
                QuicTlsCertificateVerifyMessage.decode(ByteBuffer.wrap(
                        copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE))));
        assertThat(certificateVerify.signatureScheme(), is(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256));
    }

    @Test
    void shouldSendEmptyCertificateWhenNoClientChainMatchesSignaturePolicy() throws Exception {
        HelidonClientQuicTLSEngine engine = newEngine(
                "example.com",
                new RecordingTrustManager(),
                QuicTlsCertificateChainFixtures.ignoringSignaturePolicyKeyManager(false));
        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(clientHelloBytes);
        MutualTlsServerHandshakeFlight flight = mutualTlsServerHandshakeFlight(
                copy(clientHelloBytes),
                clientHello,
                new byte[] {0x0A, 0x0B, 0x0C},
                "h3",
                List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256));

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateRequest()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        QuicTlsCertificateMessage clientCertificate =
                QuicTlsCertificateMessage.decode(ByteBuffer.wrap(
                        copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE))));
        assertThat(clientCertificate.certificateEntries(), empty());
        assertThat(copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE)),
                   equalTo(flight.emptyClientFinished()));
    }

    @Test
    void shouldCacheResumptionTicketFromNewSessionTicket() throws Exception {
        RecordingTrustManager trustManager = new RecordingTrustManager();
        RoutingQuicTlsEngineFactory factory = newEngineFactory(trustManager, null);
        CachedResumptionTicketResult cachedResult = cacheResumptionTicket(factory);
        QuicTlsResumptionTicket cached = cachedResult.cachedTicket();
        ServerHandshakeFlight flight = cachedResult.flight();

        assertThat(cached, is(notNullValue()));
        assertThat(cached.cipherSuite(), is(flight.cipherSuite()));
        assertThat(cached.ticketLifetimeSeconds(), is(3600L));
        assertThat(cached.ticketAgeAdd(), is(0x10203040L));
        assertThat(cached.ticketNonce(), equalTo(SESSION_TICKET_NONCE));
        assertThat(cached.ticket(), equalTo(SESSION_TICKET));
        assertThat(cached.applicationProtocol().orElseThrow(), is("h3"));
        assertThat(cached.remoteTransportParameters().orElseThrow(), equalTo(new byte[] {0x0A, 0x0B, 0x0C}));
        assertThat(cached.resumptionPsk(),
                   equalTo(flight.clientSecrets().deriveResumptionPsk(flight.clientFinishedTranscriptHash(),
                                                                      SESSION_TICKET_NONCE)));
    }

    @Test
    void shouldOfferCachedResumptionTicketInNextClientHello() throws Exception {
        RoutingQuicTlsEngineFactory factory = newEngineFactory(new RecordingTrustManager(), null);
        QuicTlsResumptionTicket cached = cacheResumptionTicket(factory).cachedTicket();

        QuicTLSEngine engine = newRoutingEngine(factory, "example.com");
        QuicTlsClientHelloMessage clientHello =
                QuicTlsClientHelloMessage.decode(required(packetEngine(engine).handshakeBytesBuffer(INITIAL)));

        assertThat(clientHello.extension(QuicTlsExtensions.EARLY_DATA).isPresent(), is(false));
        assertThat(clientHello.extension(QuicTlsExtensions.PRE_SHARED_KEY).isPresent(), is(true));
        assertThat(clientHello.extensions().getLast().type(), is(QuicTlsExtensions.PRE_SHARED_KEY));
        assertThat(clientHello.offeredPreSharedKeys().orElseThrow().identities().getFirst().identity(),
                   equalTo(cached.ticket()));
    }

    @Test
    void shouldRejectUnexpectedEncryptedExtensionsEarlyData() throws Exception {
        assertRejectedEncryptedExtensions(encryptedExtensions(TRANSPORT_PARAMETERS,
                                                               "h3",
                                                               extension(QuicTlsExtensions.EARLY_DATA, new byte[0])),
                                          0x016eL);
    }

    private static void assertRejectedEncryptedExtensions(ByteBuffer rejectedExtensions, long expectedErrorCode)
            throws Exception {
        assertRejectedEncryptedExtensions(newEngine("example.com", new RecordingTrustManager()),
                                          rejectedExtensions,
                                          expectedErrorCode);
    }

    private static void assertRejectedEncryptedExtensions(HelidonClientQuicTLSEngine engine,
                                                          ByteBuffer rejectedExtensions,
                                                          long expectedErrorCode) throws Exception {
        List<byte[]> remoteTransportParameters = new ArrayList<>();
        engine.remoteQuicTransportParametersConsumer(buffer -> remoteTransportParameters.add(copy(buffer)));

        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(clientHelloBytes);
        ServerHandshakeFlight flight = serverHandshakeFlight(copy(clientHelloBytes),
                                                             clientHello,
                                                             TRANSPORT_PARAMETERS,
                                                             "h3");
        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));

        QuicTransportException failure = assertThrows(QuicTransportException.class,
                                                       () -> packetEngine(engine).consumeHandshakeBytesBuffer(
                                                               HANDSHAKE, rejectedExtensions));

        assertThat(failure.errorCode(), is(expectedErrorCode));
        assertThat(remoteTransportParameters, empty());
        assertThat(engine.applicationProtocol(), is(Optional.empty()));
        assertThat(engine.handshakeState(), is(NEED_RECV_CRYPTO));
        assertThat(engine.keysAvailable(ONE_RTT), is(false));
        assertThat(engine.isTLSHandshakeComplete(), is(false));

        // Reuse the engine only to verify that rejection preserved its phase and transcript. A live QUIC connection
        // closes on the fatal error and does not continue the handshake.
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        assertThat(remoteTransportParameters.size(), is(1));
        assertThat(remoteTransportParameters.getFirst(), equalTo(TRANSPORT_PARAMETERS));
        assertThat(required(engine.applicationProtocol()), is("h3"));
        assertThat(copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE)), equalTo(flight.expectedClientFinished()));
        assertThat(engine.isTLSHandshakeComplete(), is(true));
    }

    private static HelidonClientQuicTLSEngine newEngine(String peerHost) throws Exception {
        return newEngine(peerHost, null);
    }

    private static HelidonClientQuicTLSEngine newEngine(String peerHost,
                                                        X509ExtendedTrustManager trustManager) throws Exception {
        return newEngine(peerHost, trustManager, null);
    }

    private static HelidonClientQuicTLSEngine newEngine(String peerHost,
                                                        X509ExtendedTrustManager trustManager,
                                                        X509ExtendedKeyManager keyManager) throws Exception {
        var effectiveTrustManager = trustManager == null
                ? QuicTlsTestSupport.defaultTrustManager()
                : trustManager;
        var tls = QuicTlsTestSupport.tls(keyManager, effectiveTrustManager);
        HelidonClientQuicTLSEngine engine =
                new HelidonClientQuicTLSEngine(
                        QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create())),
                        peerHost,
                        443);
        configureClientEngine(engine);
        return engine;
    }

    private static RoutingQuicTlsEngineFactory newEngineFactory(X509ExtendedTrustManager trustManager,
                                                                X509ExtendedKeyManager keyManager) {
        var effectiveTrustManager = trustManager == null
                ? QuicTlsTestSupport.defaultTrustManager()
                : trustManager;
        var tls = QuicTlsTestSupport.tls(keyManager, effectiveTrustManager);
        return new RoutingQuicTlsEngineFactory(
                QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create())))
                .withClientSessionCache(new QuicTlsSessionCache());
    }

    private static QuicTLSEngine newRoutingEngine(RoutingQuicTlsEngineFactory factory, String peerHost) throws Exception {
        QuicTLSEngine engine = factory.createEngine(peerHost, 443);
        configureClientEngine(engine);
        return engine;
    }

    private static CachedResumptionTicketResult cacheResumptionTicket(RoutingQuicTlsEngineFactory factory) throws Exception {
        QuicTLSEngine engine = newRoutingEngine(factory, "example.com");

        ByteBuffer clientHelloBytes = required(packetEngine(engine).handshakeBytesBuffer(INITIAL));
        byte[] clientHello = copy(clientHelloBytes);
        QuicTlsClientHelloMessage clientHelloMessage = QuicTlsClientHelloMessage.decode(clientHelloBytes);
        ServerHandshakeFlight flight = serverHandshakeFlight(clientHello,
                                                             clientHelloMessage,
                                                             new byte[] {0x0A, 0x0B, 0x0C},
                                                             "h3");

        engine.versionNegotiated(VERSION);
        packetEngine(engine).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(engine).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));
        assertThat(copy(packetEngine(engine).handshakeBytesBuffer(HANDSHAKE)), equalTo(flight.expectedClientFinished()));
        assertThat(engine.tryReceiveHandshakeDone(), is(true));

        byte[] maxEarlyDataSize = ByteBuffer.allocate(Integer.BYTES).putInt(-1).array();
        ByteBuffer encodedExtensions = QuicTlsExtensions.encode(List.of(
                QuicTlsExtension.create(QuicTlsExtensions.EARLY_DATA, maxEarlyDataSize)));
        int bodyLength = 4 + 4 + 1 + SESSION_TICKET_NONCE.length + 2 + SESSION_TICKET.length
                + encodedExtensions.remaining();
        ByteBuffer newSessionTicket = ByteBuffer.allocate(bodyLength);
        newSessionTicket.putInt(3600);
        newSessionTicket.putInt(0x10203040);
        newSessionTicket.put((byte) SESSION_TICKET_NONCE.length);
        newSessionTicket.put(SESSION_TICKET_NONCE);
        newSessionTicket.putShort((short) SESSION_TICKET.length);
        newSessionTicket.put(SESSION_TICKET);
        newSessionTicket.put(encodedExtensions);
        packetEngine(engine).consumeHandshakeBytesBuffer(
                ONE_RTT,
                handshakeMessage(QuicTlsHandshakeMessages.NEW_SESSION_TICKET, newSessionTicket.flip()));
        return new CachedResumptionTicketResult(factory.cachedResumptionTicket("example.com", 443, "h3").orElseThrow(),
                                                flight);
    }

    private static void configureClientEngine(QuicTLSEngine engine) throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        sslParameters.setApplicationProtocols(new String[] {"h3"});
        sslParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
        engine.clientMode(true);
        engine.sslParameters(sslParameters);
        packetEngine(engine).deriveInitialKeysBuffer(VERSION, ByteBuffer.wrap(bytes("8394c8f03e515708")));
        packetEngine(engine).localQuicTransportParametersBuffer(ByteBuffer.wrap(TRANSPORT_PARAMETERS));
    }

    private static QuicTlsServerHelloMessage helloRetryRequest(byte[] legacySessionId,
                                                               QuicTls13CipherSuite cipherSuite,
                                                               QuicTlsNamedGroup selectedGroup,
                                                               byte[] cookie) {
        return QuicTlsServerHelloMessage.helloRetryRequest(
                legacySessionId,
                cipherSuite.codePoint(),
                List.of(
                        QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                                QuicTlsKeyShares.encodeHelloRetryRequest(selectedGroup)),
                        QuicTlsExtension.create(QuicTlsExtensions.COOKIE, QuicTlsCookie.encode(cookie)),
                        QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                                QuicTlsSupportedVersions.encodeServerHello(
                                                        QuicTlsSupportedVersions.TLS_1_3))));
    }

    private static QuicTlsServerHelloMessage serverHello(byte[] legacySessionId,
                                                         QuicTls13CipherSuite cipherSuite,
                                                         int supportedVersion,
                                                         QuicTlsKeyShareEntry keyShare) {
        return serverHello(legacySessionId, cipherSuite, supportedVersion, keyShare, null);
    }

    private static QuicTlsServerHelloMessage serverHello(byte[] legacySessionId,
                                                         QuicTls13CipherSuite cipherSuite,
                                                         int supportedVersion,
                                                         QuicTlsKeyShareEntry keyShare,
                                                         Integer selectedIdentity) {
        List<QuicTlsExtension> extensions = new ArrayList<>();
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.KEY_SHARE,
                                               QuicTlsKeyShares.encodeServerHello(keyShare)));
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.SUPPORTED_VERSIONS,
                                               QuicTlsSupportedVersions.encodeServerHello(supportedVersion)));
        if (selectedIdentity != null) {
            extensions.add(QuicTlsExtension.create(QuicTlsExtensions.PRE_SHARED_KEY,
                                                   QuicTlsPreSharedKeys.encodeServerHello(selectedIdentity)));
        }
        return QuicTlsServerHelloMessage.create(
                0x0303,
                new byte[QuicTlsCodecSupport.RANDOM_LENGTH],
                legacySessionId,
                cipherSuite.codePoint(),
                0,
                List.copyOf(extensions));
    }

    private static ByteBuffer encryptedExtensions(byte[] transportParameters,
                                                  String applicationProtocol,
                                                  byte[]... additionalExtensions) {
        List<byte[]> extensions = new ArrayList<>();
        if (applicationProtocol != null) {
            extensions.add(extension(QuicTlsExtensions.APPLICATION_LAYER_PROTOCOL_NEGOTIATION,
                                     copy(encodeApplicationProtocol(applicationProtocol))));
        }
        if (transportParameters != null) {
            extensions.add(extension(QuicTlsHandshakeMessages.QUIC_TRANSPORT_PARAMETERS_EXTENSION,
                                     transportParameters));
        }
        extensions.addAll(Arrays.asList(additionalExtensions));
        ByteBuffer body = ByteBuffer.allocate(2 + extensions.stream().mapToInt(extension -> extension.length).sum());
        putVector(body, 0xFFFF, extensions.toArray(byte[][]::new));
        return handshakeMessage(QuicTlsHandshakeMessages.ENCRYPTED_EXTENSIONS, body.flip());
    }

    private static ByteBuffer certificateMessage(byte[] encodedCertificate) {
        return QuicTlsCertificateMessage.create(new byte[0],
                                                List.of(new QuicTlsCertificateMessage.CertificateEntry(encodedCertificate,
                                                                                                       List.of())))
                .encode();
    }

    private static ServerHandshakeFlight serverHandshakeFlight(byte[] clientHelloBytes,
                                                               QuicTlsClientHelloMessage clientHello,
                                                               byte[] transportParameters,
                                                               String applicationProtocol,
                                                               byte[]... additionalExtensions) throws Exception {
        QuicTls13CipherSuite cipherSuite = QuicTls13CipherSuite.forCodePoint(clientHello.cipherSuites().getFirst());
        QuicTlsKeySharePossession serverKeyShare =
                QuicTlsKeySharePossession.create(clientHello.keyShares().getFirst().namedGroup(), new SecureRandom());
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            cipherSuite,
                                                            QuicTlsSupportedVersions.TLS_1_3,
                                                            serverKeyShare.keyShareEntry());
        byte[] serverHelloBytes = copy(serverHello.encode());
        byte[] sharedSecret = serverKeyShare.sharedSecret(clientHello.keyShares().getFirst());
        QuicTls13ConnectionSecrets clientSecrets;
        QuicTls13ConnectionSecrets serverSecrets;
        try {
            clientSecrets = QuicTls13ConnectionSecrets.create(VERSION, cipherSuite, sharedSecret, true);
            serverSecrets = QuicTls13ConnectionSecrets.create(VERSION, cipherSuite, sharedSecret, false);
        } finally {
            Arrays.fill(sharedSecret, (byte) 0);
        }

        QuicTlsHandshakeTranscript transcript = new QuicTlsHandshakeTranscript();
        transcript.add(ByteBuffer.wrap(clientHelloBytes));
        transcript.add(ByteBuffer.wrap(serverHelloBytes));
        byte[] serverHelloTranscriptHash = transcript.hash(cipherSuite);

        byte[] encryptedExtensions = copy(encryptedExtensions(transportParameters, applicationProtocol, additionalExtensions));
        transcript.add(ByteBuffer.wrap(encryptedExtensions));

        byte[] certificate = copy(certificateMessage(QuicTlsRfc8448Vectors.rsaCertificateDer()));
        transcript.add(ByteBuffer.wrap(certificate));

        byte[] certificateVerify = copy(QuicTlsCertificateVerifyMessage.sign(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256,
                                                                             QuicTlsRfc8448Vectors.rsaPrivateKey(),
                                                                             transcript.hash(cipherSuite),
                                                                             false)
                                                .encode());
        transcript.add(ByteBuffer.wrap(certificateVerify));

        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(cipherSuite);
        SecretKey serverFinishedKey =
                secretSchedule.deriveFinishedKey(serverSecrets.serverHandshakeTrafficSecret(serverHelloTranscriptHash));
        byte[] finished = copy(QuicTlsFinishedMessage.create(secretSchedule.computeVerifyData(serverFinishedKey,
                                                                                              transcript.hash(cipherSuite)))
                                       .encode());
        transcript.add(ByteBuffer.wrap(finished));

        byte[] applicationTranscriptHash = transcript.hash(cipherSuite);
        SecretKey clientFinishedKey =
                secretSchedule.deriveFinishedKey(clientSecrets.clientHandshakeTrafficSecret(serverHelloTranscriptHash));
        byte[] expectedClientFinished = copy(QuicTlsFinishedMessage.create(secretSchedule.computeVerifyData(
                clientFinishedKey,
                applicationTranscriptHash)).encode());
        transcript.add(ByteBuffer.wrap(expectedClientFinished));
        byte[] clientFinishedTranscriptHash = transcript.hash(cipherSuite);
        QuicOneRttTrafficKeys serverOneRttKeys = serverSecrets.deriveOneRttTrafficKeys(applicationTranscriptHash);
        serverOneRttKeys.oneRttContext(() -> -1);
        return new ServerHandshakeFlight(serverHelloBytes,
                                         encryptedExtensions,
                                         certificate,
                                         certificateVerify,
                                         finished,
                                         serverHelloTranscriptHash,
                                         expectedClientFinished,
                                         serverOneRttKeys,
                                         cipherSuite,
                                         clientSecrets,
                                         clientFinishedTranscriptHash);
    }

    private static MutualTlsServerHandshakeFlight mutualTlsServerHandshakeFlight(
            byte[] clientHelloBytes,
            QuicTlsClientHelloMessage clientHello,
            byte[] transportParameters,
            String applicationProtocol,
            List<QuicTlsSignatureScheme> certificateSignatureSchemes)
            throws Exception {
        QuicTls13CipherSuite cipherSuite = QuicTls13CipherSuite.forCodePoint(clientHello.cipherSuites().getFirst());
        QuicTlsKeySharePossession serverKeyShare =
                QuicTlsKeySharePossession.create(clientHello.keyShares().getFirst().namedGroup(), new SecureRandom());
        QuicTlsServerHelloMessage serverHello = serverHello(clientHello.legacySessionId(),
                                                            cipherSuite,
                                                            QuicTlsSupportedVersions.TLS_1_3,
                                                            serverKeyShare.keyShareEntry());
        byte[] serverHelloBytes = copy(serverHello.encode());
        byte[] sharedSecret = serverKeyShare.sharedSecret(clientHello.keyShares().getFirst());
        QuicTls13ConnectionSecrets clientSecrets;
        QuicTls13ConnectionSecrets serverSecrets;
        try {
            clientSecrets = QuicTls13ConnectionSecrets.create(VERSION, cipherSuite, sharedSecret, true);
            serverSecrets = QuicTls13ConnectionSecrets.create(VERSION, cipherSuite, sharedSecret, false);
        } finally {
            Arrays.fill(sharedSecret, (byte) 0);
        }

        QuicTlsHandshakeTranscript transcript = new QuicTlsHandshakeTranscript();
        transcript.add(ByteBuffer.wrap(clientHelloBytes));
        transcript.add(ByteBuffer.wrap(serverHelloBytes));
        byte[] serverHelloTranscriptHash = transcript.hash(cipherSuite);

        byte[] encryptedExtensions = copy(encryptedExtensions(transportParameters, applicationProtocol));
        transcript.add(ByteBuffer.wrap(encryptedExtensions));

        byte[] certificateRequest = copy(QuicTlsCertificateRequestMessage.create(
                        new byte[0],
                        List.of(
                                QuicTlsExtension.create(QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                                                        QuicTlsSignatureScheme.encodeCertificateVerifyVector(
                                                                List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256))),
                                QuicTlsExtension.create(QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT,
                                                        QuicTlsSignatureScheme.encodeCertificateSignatureVector(
                                                                certificateSignatureSchemes))))
                                                 .encode());
        transcript.add(ByteBuffer.wrap(certificateRequest));

        byte[] certificate = copy(certificateMessage(QuicTlsRfc8448Vectors.rsaCertificateDer()));
        transcript.add(ByteBuffer.wrap(certificate));

        byte[] certificateVerify = copy(QuicTlsCertificateVerifyMessage.sign(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256,
                                                                             QuicTlsRfc8448Vectors.rsaPrivateKey(),
                                                                             transcript.hash(cipherSuite),
                                                                             false)
                                                .encode());
        transcript.add(ByteBuffer.wrap(certificateVerify));

        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(cipherSuite);
        SecretKey serverFinishedKey =
                secretSchedule.deriveFinishedKey(serverSecrets.serverHandshakeTrafficSecret(serverHelloTranscriptHash));
        byte[] finished = copy(QuicTlsFinishedMessage.create(secretSchedule.computeVerifyData(serverFinishedKey,
                                                                                              transcript.hash(cipherSuite)))
                                       .encode());
        transcript.add(ByteBuffer.wrap(finished));

        byte[] clientCertificate = copy(certificateMessage(QuicTlsRfc8448Vectors.rsaCertificateDer()));
        transcript.add(ByteBuffer.wrap(clientCertificate));

        byte[] clientCertificateVerify =
                copy(QuicTlsCertificateVerifyMessage.sign(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256,
                                                          QuicTlsRfc8448Vectors.rsaPrivateKey(),
                                                          transcript.hash(cipherSuite),
                                                          true)
                             .encode());
        transcript.add(ByteBuffer.wrap(clientCertificateVerify));

        SecretKey clientFinishedKey =
                secretSchedule.deriveFinishedKey(clientSecrets.clientHandshakeTrafficSecret(serverHelloTranscriptHash));
        byte[] clientFinished = copy(QuicTlsFinishedMessage.create(secretSchedule.computeVerifyData(
                clientFinishedKey,
                transcript.hash(cipherSuite))).encode());

        byte[] emptyClientCertificate = copy(QuicTlsCertificateMessage.create(new byte[0], List.of()).encode());
        QuicTlsHandshakeTranscript emptyClientTranscript = new QuicTlsHandshakeTranscript();
        emptyClientTranscript.add(ByteBuffer.wrap(clientHelloBytes));
        emptyClientTranscript.add(ByteBuffer.wrap(serverHelloBytes));
        emptyClientTranscript.add(ByteBuffer.wrap(encryptedExtensions));
        emptyClientTranscript.add(ByteBuffer.wrap(certificateRequest));
        emptyClientTranscript.add(ByteBuffer.wrap(certificate));
        emptyClientTranscript.add(ByteBuffer.wrap(certificateVerify));
        emptyClientTranscript.add(ByteBuffer.wrap(finished));
        emptyClientTranscript.add(ByteBuffer.wrap(emptyClientCertificate));
        byte[] emptyClientFinished = copy(QuicTlsFinishedMessage.create(secretSchedule.computeVerifyData(
                clientFinishedKey,
                emptyClientTranscript.hash(cipherSuite))).encode());

        return new MutualTlsServerHandshakeFlight(serverHelloBytes,
                                                  encryptedExtensions,
                                                  certificateRequest,
                                                  certificate,
                                                  certificateVerify,
                                                  finished,
                                                  clientCertificate,
                                                  clientCertificateVerify,
                                                  clientFinished,
                                                  emptyClientFinished,
                                                  cipherSuite,
                                                  clientFinishedKey);
    }

    private static ByteBuffer encodeApplicationProtocol(String applicationProtocol) {
        byte[] protocolBytes = applicationProtocol.getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer encoded = ByteBuffer.allocate(2 + 1 + protocolBytes.length);
        encoded.putShort((short) (1 + protocolBytes.length));
        encoded.put((byte) protocolBytes.length);
        encoded.put(protocolBytes);
        return encoded.flip();
    }

    private static QuicPacketTLSEngine packetEngine(QuicTLSEngine engine) {
        return QuicPacketTLSEngine.internal(engine);
    }

    private static byte[] encryptHandshake(QuicTLSEngine engine, long packetNumber, byte payloadByte) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(HEADER_LENGTH + 1 + QuicPacketProtection.AUTH_TAG_SIZE);
        packet.put(longHeader(packetNumber));
        ByteBuffer header = packet.slice(0, HEADER_LENGTH);
        packet.position(HEADER_LENGTH);
        packetEngine(engine).encryptPacketBuffer(HANDSHAKE,
                                                 packetNumber,
                                                 _ -> header.position(0).asReadOnlyBuffer(),
                                                 ByteBuffer.wrap(new byte[] {payloadByte}),
                                                 packet);
        return packet.array();
    }

    private static byte[] encryptHandshake(QuicLongHeaderTrafficKeys keys, long packetNumber, byte payloadByte) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(HEADER_LENGTH + 1 + QuicPacketProtection.AUTH_TAG_SIZE);
        packet.put(longHeader(packetNumber));
        ByteBuffer header = packet.slice(0, HEADER_LENGTH);
        packet.position(HEADER_LENGTH);
        keys.encryptPacket(packetNumber,
                           _ -> header.position(0).asReadOnlyBuffer(),
                           ByteBuffer.wrap(new byte[] {payloadByte}),
                           packet);
        return packet.array();
    }

    private static byte decryptHandshake(QuicLongHeaderTrafficKeys keys, byte[] encodedPacket) throws Exception {
        ByteBuffer packet = ByteBuffer.wrap(encodedPacket.clone());
        ByteBuffer input = packet.asReadOnlyBuffer();
        packet.position(HEADER_LENGTH);
        long packetNumber = decodePacketNumber(packet.array());
        keys.decryptPacket(packetNumber, -1, input, HEADER_LENGTH, packet);
        return packet.array()[HEADER_LENGTH];
    }

    private static byte decryptHandshake(QuicTLSEngine engine, byte[] encodedPacket) throws Exception {
        ByteBuffer packet = ByteBuffer.wrap(encodedPacket.clone());
        ByteBuffer input = packet.asReadOnlyBuffer();
        packet.position(HEADER_LENGTH);
        long packetNumber = decodePacketNumber(packet.array());
        packetEngine(engine).decryptPacketBuffer(HANDSHAKE, packetNumber, -1, input, HEADER_LENGTH, packet);
        return packet.array()[HEADER_LENGTH];
    }

    private static OneRttPacket encryptOneRtt(QuicTLSEngine engine, long packetNumber, byte payloadByte) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(HEADER_LENGTH + 1 + QuicPacketProtection.AUTH_TAG_SIZE);
        byte[] headerBytes = shortHeader(packetNumber, 0);
        packet.put(headerBytes);
        packet.position(HEADER_LENGTH);

        ByteBuffer header = packet.slice(0, HEADER_LENGTH);
        int[] keyPhase = {-1};
        packetEngine(engine).encryptPacketBuffer(ONE_RTT,
                                                 packetNumber,
                                                 phase -> {
                                                     keyPhase[0] = phase;
                                                     header.put(0, shortHeaderFirstByte(phase));
                                                     return header.position(0).asReadOnlyBuffer();
                                                 },
                                                 ByteBuffer.wrap(new byte[] {payloadByte}),
                                                 packet);
        return new OneRttPacket(packet.array(), keyPhase[0]);
    }

    private static OneRttPacket encryptOneRtt(QuicOneRttTrafficKeys keys, long packetNumber, byte payloadByte) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(HEADER_LENGTH + 1 + QuicPacketProtection.AUTH_TAG_SIZE);
        byte[] headerBytes = shortHeader(packetNumber, 0);
        packet.put(headerBytes);
        packet.position(HEADER_LENGTH);

        ByteBuffer header = packet.slice(0, HEADER_LENGTH);
        int[] keyPhase = {-1};
        keys.encryptPacket(packetNumber,
                           phase -> {
                               keyPhase[0] = phase;
                               header.put(0, shortHeaderFirstByte(phase));
                               return header.position(0).asReadOnlyBuffer();
                           },
                           ByteBuffer.wrap(new byte[] {payloadByte}),
                           packet);
        return new OneRttPacket(packet.array(), keyPhase[0]);
    }

    private static byte decryptOneRtt(QuicTLSEngine engine, byte[] encodedPacket) throws Exception {
        ByteBuffer packet = ByteBuffer.wrap(encodedPacket.clone());
        ByteBuffer input = packet.asReadOnlyBuffer();
        packet.position(HEADER_LENGTH);
        long packetNumber = decodePacketNumber(packet.array());
        int keyPhase = (packet.get(0) >>> 2) & 0x1;
        packetEngine(engine).decryptPacketBuffer(ONE_RTT, packetNumber, keyPhase, input, HEADER_LENGTH, packet);
        return packet.array()[HEADER_LENGTH];
    }

    private static byte decryptOneRtt(QuicOneRttTrafficKeys keys, byte[] encodedPacket) throws Exception {
        ByteBuffer packet = ByteBuffer.wrap(encodedPacket.clone());
        ByteBuffer input = packet.asReadOnlyBuffer();
        packet.position(HEADER_LENGTH);
        long packetNumber = decodePacketNumber(packet.array());
        int keyPhase = (packet.get(0) >>> 2) & 0x1;
        keys.decryptPacket(packetNumber, keyPhase, input, HEADER_LENGTH, packet);
        return packet.array()[HEADER_LENGTH];
    }

    private static byte[] longHeader(long packetNumber) {
        return new byte[] {
                (byte) 0xE0,
                (byte) ((packetNumber >>> 16) & 0xFF),
                (byte) ((packetNumber >>> 8) & 0xFF),
                (byte) (packetNumber & 0xFF)
        };
    }

    private static byte[] shortHeader(long packetNumber, int keyPhase) {
        return new byte[] {
                shortHeaderFirstByte(keyPhase),
                (byte) ((packetNumber >>> 16) & 0xFF),
                (byte) ((packetNumber >>> 8) & 0xFF),
                (byte) (packetNumber & 0xFF)
        };
    }

    private static byte shortHeaderFirstByte(int keyPhase) {
        return (byte) (0x40 | ((keyPhase & 0x1) << 2));
    }

    private static long decodePacketNumber(byte[] packet) {
        return ((packet[1] & 0xFFL) << 16)
                | ((packet[2] & 0xFFL) << 8)
                | (packet[3] & 0xFFL);
    }

    private static ByteBuffer handshakeMessage(int type, ByteBuffer body) {
        ByteBuffer payload = body.asReadOnlyBuffer();
        ByteBuffer result = ByteBuffer.allocate(QuicTlsHandshakeMessages.HEADER_LENGTH + payload.remaining());
        result.put((byte) type);
        putUint24(result, payload.remaining());
        result.put(payload);
        return result.flip();
    }

    private static byte[] extension(int extensionType, byte[] data) {
        ByteBuffer extension = ByteBuffer.allocate(2 + 2 + data.length);
        extension.putShort((short) extensionType);
        extension.putShort((short) data.length);
        extension.put(data);
        return copy(extension.flip());
    }

    private static void putVector(ByteBuffer buffer, int maxLength, byte[]... data) {
        int totalLength = 0;
        for (byte[] bytes : data) {
            totalLength += bytes.length;
        }
        if (maxLength != 0xFFFF) {
            throw new IllegalArgumentException("Unsupported vector length: " + maxLength);
        }
        buffer.putShort((short) totalLength);
        for (byte[] bytes : data) {
            buffer.put(bytes);
        }
    }

    private static void putUint24(ByteBuffer buffer, int value) {
        buffer.put((byte) ((value >>> 16) & 0xFF));
        buffer.put((byte) ((value >>> 8) & 0xFF));
        buffer.put((byte) (value & 0xFF));
    }

    private static byte[] copy(Optional<ByteBuffer> buffer) {
        return copy(required(buffer));
    }

    private static byte[] copy(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] result = new byte[duplicate.remaining()];
        duplicate.get(result);
        return result;
    }

    private static <T> T required(Optional<T> optional) {
        return optional.orElseThrow();
    }

    private static byte[] bytes(String hex) {
        return HEX.parseHex(hex);
    }

    private static String hex(ByteBuffer buffer) {
        return HEX.formatHex(copy(buffer));
    }

    private static long packedHeaderProtectionMask(ByteBuffer mask) {
        int offset = mask.position();
        return ((long) mask.get(offset) & 0xff) << 32
                | ((long) mask.get(offset + 1) & 0xff) << 24
                | ((long) mask.get(offset + 2) & 0xff) << 16
                | ((long) mask.get(offset + 3) & 0xff) << 8
                | (long) mask.get(offset + 4) & 0xff;
    }

    private record CachedResumptionTicketResult(QuicTlsResumptionTicket cachedTicket,
                                                ServerHandshakeFlight flight) {
    }

    private record OneRttPacket(byte[] packet, int keyPhase) {
    }

    private record ServerHandshakeFlight(byte[] serverHello,
                                         byte[] encryptedExtensions,
                                         byte[] certificate,
                                         byte[] certificateVerify,
                                         byte[] finished,
                                         byte[] serverHelloTranscriptHash,
                                         byte[] expectedClientFinished,
                                         QuicOneRttTrafficKeys serverOneRttKeys,
                                         QuicTls13CipherSuite cipherSuite,
                                         QuicTls13ConnectionSecrets clientSecrets,
                                         byte[] clientFinishedTranscriptHash) {
        private ServerHandshakeFlight {
            serverHello = serverHello.clone();
            encryptedExtensions = encryptedExtensions.clone();
            certificate = certificate.clone();
            certificateVerify = certificateVerify.clone();
            finished = finished.clone();
            serverHelloTranscriptHash = serverHelloTranscriptHash.clone();
            expectedClientFinished = expectedClientFinished.clone();
            clientFinishedTranscriptHash = clientFinishedTranscriptHash.clone();
        }
    }

    private record MutualTlsServerHandshakeFlight(byte[] serverHello,
                                                  byte[] encryptedExtensions,
                                                  byte[] certificateRequest,
                                                  byte[] certificate,
                                                  byte[] certificateVerify,
                                                  byte[] finished,
                                                  byte[] expectedClientCertificate,
                                                  byte[] expectedClientCertificateVerify,
                                                  byte[] expectedClientFinished,
                                                  byte[] emptyClientFinished,
                                                  QuicTls13CipherSuite cipherSuite,
                                                  SecretKey clientFinishedKey) {
        private MutualTlsServerHandshakeFlight {
            serverHello = serverHello.clone();
            encryptedExtensions = encryptedExtensions.clone();
            certificateRequest = certificateRequest.clone();
            certificate = certificate.clone();
            certificateVerify = certificateVerify.clone();
            finished = finished.clone();
            expectedClientCertificate = expectedClientCertificate.clone();
            expectedClientCertificateVerify = expectedClientCertificateVerify.clone();
            expectedClientFinished = expectedClientFinished.clone();
            emptyClientFinished = emptyClientFinished.clone();
        }
    }

    private static final class StaticKeyManager extends X509ExtendedKeyManager {
        private static final String ALIAS = "client";

        private final X509Certificate[] certificateChain;
        private final PrivateKey privateKey;
        private String[] localSupportedSignatureAlgorithms;
        private String[] peerSupportedSignatureAlgorithms;

        private StaticKeyManager(X509Certificate certificate, PrivateKey privateKey) {
            this.certificateChain = new X509Certificate[] {certificate};
            this.privateKey = privateKey;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return supports(keyType) ? new String[] {ALIAS} : new String[0];
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return firstSupported(keyType);
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return new String[0];
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return ALIAS.equals(alias) ? certificateChain.clone() : null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return ALIAS.equals(alias) ? privateKey : null;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            ExtendedSSLSession session = (ExtendedSSLSession) engine.getHandshakeSession();
            localSupportedSignatureAlgorithms = session.getLocalSupportedSignatureAlgorithms();
            peerSupportedSignatureAlgorithms = session.getPeerSupportedSignatureAlgorithms();
            return firstSupported(keyType);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return null;
        }

        private boolean supports(String keyType) {
            return certificateChain[0].getPublicKey().getAlgorithm().equalsIgnoreCase(keyType);
        }

        private String firstSupported(String[] keyTypes) {
            if (keyTypes == null) {
                return null;
            }
            for (String keyType : keyTypes) {
                if (supports(keyType)) {
                    return ALIAS;
                }
            }
            return null;
        }
    }

    private static final class RecordingTrustManager extends X509ExtendedTrustManager {
        private final X509Certificate trustedCertificate;
        private int checkServerTrustedCalls;
        private String authType;
        private String peerHost;
        private String handshakeApplicationProtocol;
        private int peerCertificatesLength;
        private String[] localSupportedSignatureAlgorithms;
        private String[] peerSupportedSignatureAlgorithms;

        private RecordingTrustManager() throws Exception {
            this.trustedCertificate = QuicTlsRfc8448Vectors.rsaCertificate();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Client mode only");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("SSLEngine context expected");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] {trustedCertificate};
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            throw new CertificateException("Client mode only");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            throw new CertificateException("SSLEngine context expected");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            throw new CertificateException("Client mode only");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            checkServerTrustedCalls++;
            this.authType = authType;
            this.peerHost = engine.getPeerHost();
            this.handshakeApplicationProtocol = engine.getHandshakeApplicationProtocol();
            this.peerCertificatesLength = chain.length;
            ExtendedSSLSession session = (ExtendedSSLSession) engine.getHandshakeSession();
            this.localSupportedSignatureAlgorithms = session.getLocalSupportedSignatureAlgorithms();
            this.peerSupportedSignatureAlgorithms = session.getPeerSupportedSignatureAlgorithms();
            if (chain.length != 1 || !Arrays.equals(chain[0].getEncoded(), trustedCertificate.getEncoded())) {
                throw new CertificateException("Unexpected certificate chain");
            }
        }
    }
}
