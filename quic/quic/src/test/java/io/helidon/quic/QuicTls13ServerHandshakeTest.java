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
import java.security.Principal;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.crypto.SecretKey;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

import io.helidon.quic.spi.QuicPacketTLSEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.quic.QuicTLSEngine.KeySpace.HANDSHAKE;
import static io.helidon.quic.QuicTLSEngine.KeySpace.INITIAL;
import static io.helidon.quic.QuicTLSEngine.KeySpace.ONE_RTT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuicTls13ServerHandshakeTest {
    private static final QuicVersion VERSION = QuicVersion.QUIC_V1;
    // Distinct client/server transport-parameter payloads make it easy to prove the helper keeps each direction's
    // QUIC transport parameters separate while building the first server flight.
    private static final byte[] CLIENT_TRANSPORT_PARAMETERS = bytes("010203040506");
    private static final byte[] SERVER_TRANSPORT_PARAMETERS = bytes("0a0b0c0d0e0f");

    @ParameterizedTest
    @MethodSource("requestedServerNames")
    void shouldProduceServerFlightAndVerifyClientFinished(List<SNIServerName> serverNames) throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        configureServerNames(client, serverNames);
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));
        assertThat("ClientHello server_name extension presence",
                   QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(clientHello))
                           .extension(QuicTlsExtensions.SERVER_NAME).isPresent(),
                   is(!serverNames.isEmpty()));

        RecordingKeyManager keyManager = new RecordingKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                                                 QuicTlsRfc8448Vectors.rsaPrivateKey(),
                                                                 true);
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      false,
                                                                      false,
                                                                      null,
                                                                      keyManager);
        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(ByteBuffer.wrap(clientHello));

        assertThat(flight.applicationProtocol(), is("h3"));
        assertThat(flight.remoteTransportParameters(), equalTo(CLIENT_TRANSPORT_PARAMETERS));
        assertThat(flight.localCertificates()[0].getSubjectX500Principal().getName(), containsString("CN=rsa"));
        assertThat(flight.localCertificates()[0].getSigAlgName(), is("SHA256withRSA"));
        assertThat(QuicTlsCertificateVerifyMessage.decode(ByteBuffer.wrap(flight.certificateVerify())).signatureScheme(),
                   is(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256));
        assertThat(keyManager.localSupportedSignatureAlgorithms,
                   arrayContaining("RSASSA-PSS", "SHA256withRSA"));
        assertThat(keyManager.peerSupportedSignatureAlgorithms,
                   arrayContaining("RSASSA-PSS", "SHA256withRSA"));
        assertThat(keyManager.requestedServerNames,
                   equalTo(serverNames.stream().filter(SNIHostName.class::isInstance).toList()));
        assertServerNameAcknowledgment(flight, serverNames);
        assertThat(serverHandshake.handshakeTrafficKeys(), notNullValue());
        assertThat(serverHandshake.oneRttTrafficKeys(), notNullValue());

        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        assertThat(required(client.applicationProtocol()), is("h3"));
        assertThat(client.serverHelloTranscriptHash(), equalTo(flight.serverHelloTranscriptHash()));
        assertThat(client.keysAvailable(ONE_RTT), is(true));

        byte[] clientFinished = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        serverHandshake.consumeClientFinished(ByteBuffer.wrap(clientFinished));

        assertThat(serverHandshake.complete(), is(true));
        assertThat(serverHandshake.applicationProtocol(), is("h3"));
        assertThat(serverHandshake.remoteTransportParameters(), equalTo(CLIENT_TRANSPORT_PARAMETERS));
        assertThat(serverHandshake.serverHelloTranscriptHash(), equalTo(flight.serverHelloTranscriptHash()));
        assertThat(((X509Certificate) client.session().getPeerCertificates()[0]).getSubjectX500Principal().getName(),
                   containsString("CN=rsa"));
    }

    @Test
    void shouldReportDecryptErrorForInvalidServerCertificateVerify() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3");
        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));
        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        byte[] certificateVerify = flight.certificateVerify().clone();
        certificateVerify[certificateVerify.length - 1] ^= 1;

        QuicTransportException failure =
                assertThrows(QuicTransportException.class,
                             () -> packetEngine(client).consumeHandshakeBytesBuffer(
                                     HANDSHAKE,
                                     ByteBuffer.wrap(certificateVerify)));

        assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 51));
    }

    @ParameterizedTest
    @MethodSource("requestedServerNames")
    void shouldProduceHelloRetryRequestAndCompleteHandshakeOnRetriedClientHello(List<SNIServerName> serverNames)
            throws Exception {
        HelidonClientQuicTLSEngine client =
                newClientEngine("example.com", new String[] {"x25519", "secp256r1"});
        configureServerNames(client, serverNames);
        byte[] clientHello1 = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));

        QuicTls13ServerHandshake serverHandshake =
                newServerHandshake("h3", new String[] {"secp256r1"});
        QuicTls13ServerHandshake.HelloRetryRequestResult helloRetryRequest =
                (QuicTls13ServerHandshake.HelloRetryRequestResult) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(clientHello1));

        QuicTlsServerHelloMessage helloRetryRequestMessage =
                QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(helloRetryRequest.helloRetryRequest()));
        assertThat(helloRetryRequestMessage.helloRetryRequest(), is(true));
        assertThat(helloRetryRequestMessage.helloRetryRequestSelectedGroup().orElseThrow(),
                   is(QuicTlsNamedGroup.SECP256_R1));

        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(helloRetryRequest.helloRetryRequest()));
        byte[] clientHello2 = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage retriedClientHello = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(clientHello2));
        assertThat(retriedClientHello.keyShares().stream().map(QuicTlsKeyShareEntry::namedGroup).toList(),
                   equalTo(List.of(QuicTlsNamedGroup.SECP256_R1)));

        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(ByteBuffer.wrap(clientHello2));
        assertServerNameAcknowledgment(flight, serverNames);

        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        byte[] clientFinished = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        serverHandshake.consumeClientFinished(ByteBuffer.wrap(clientFinished));

        assertThat(serverHandshake.complete(), is(true));
        assertThat(required(client.applicationProtocol()), is("h3"));
        assertThat(client.serverHelloTranscriptHash(), equalTo(flight.serverHelloTranscriptHash()));
    }

    @ParameterizedTest
    @MethodSource("requestedServerNames")
    void shouldResumeWithCachedPskAndAbbreviatedServerFlight(List<SNIServerName> serverNames) throws Exception {
        QuicTlsResumptionTicket resumptionTicket = newResumptionTicket(serverNames);
        QuicTlsServerSessionCache serverSessionCache = new QuicTlsServerSessionCache();
        serverSessionCache.cache(resumptionTicket);

        HelidonClientQuicTLSEngine resumedClient = newClientEngine("example.com", resumptionTicket);
        configureServerNames(resumedClient, serverNames);
        byte[] resumedClientHello = copy(packetEngine(resumedClient).handshakeBytesBuffer(INITIAL));

        QuicTls13ServerHandshake resumedServerHandshake = newServerHandshake("h3",
                                                                             null,
                                                                             false,
                                                                             false,
                                                                             null,
                                                                             null,
                                                                             serverSessionCache);
        QuicTls13ServerHandshake.ServerFlight resumedFlight =
                (QuicTls13ServerHandshake.ServerFlight) resumedServerHandshake.consumeClientHello(
                        ByteBuffer.wrap(resumedClientHello));

        QuicTlsServerHelloMessage resumedServerHello =
                QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(resumedFlight.serverHello()));
        assertThat(resumedServerHello.selectedIdentity().orElseThrow(), is(0));
        assertThat(resumedFlight.certificateRequest(), is((byte[]) null));
        assertThat(resumedFlight.certificate(), is((byte[]) null));
        assertThat(resumedFlight.certificateVerify(), is((byte[]) null));
        assertThat(resumedFlight.localCertificates(), is((X509Certificate[]) null));
        assertServerNameAcknowledgment(resumedFlight, serverNames);

        resumedClient.versionNegotiated(VERSION);
        packetEngine(resumedClient).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(resumedFlight.serverHello()));
        packetEngine(resumedClient).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(resumedFlight.encryptedExtensions()));
        packetEngine(resumedClient).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(resumedFlight.finished()));

        assertThat(required(resumedClient.applicationProtocol()), is("h3"));

        resumedServerHandshake.consumeClientFinished(
                ByteBuffer.wrap(copy(packetEngine(resumedClient).handshakeBytesBuffer(HANDSHAKE))));

        assertThat(resumedServerHandshake.complete(), is(true));

        HelidonClientQuicTLSEngine differentServerNameClient = newClientEngine("other.example", resumptionTicket);
        byte[] differentServerNameHello = copy(
                packetEngine(differentServerNameClient).handshakeBytesBuffer(INITIAL));
        QuicTls13ServerHandshake differentServerNameHandshake = newServerHandshake("h3",
                                                                                    null,
                                                                                    false,
                                                                                    false,
                                                                                    null,
                                                                                    null,
                                                                                    serverSessionCache);
        QuicTls13ServerHandshake.ServerFlight differentServerNameFlight =
                (QuicTls13ServerHandshake.ServerFlight) differentServerNameHandshake.consumeClientHello(
                        ByteBuffer.wrap(differentServerNameHello));
        QuicTlsServerHelloMessage differentServerNameServerHello =
                QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(differentServerNameFlight.serverHello()));
        assertThat(differentServerNameServerHello.selectedIdentity().isEmpty(), is(true));
        assertThat(differentServerNameFlight.certificate() != null, is(true));
    }

    @Test
    void shouldRejectEarlyDataAndResumeWithCachedPsk() throws Exception {
        QuicTlsResumptionTicket resumptionTicket = newResumptionTicket();
        QuicTlsServerSessionCache serverSessionCache = new QuicTlsServerSessionCache();
        serverSessionCache.cache(resumptionTicket);
        ForeignClientHello clientHello =
                foreignClientHello(resumptionTicket,
                                   new byte[0],
                                   copy(QuicTlsPskKeyExchangeModes.encodeClientHello(
                                           List.of(QuicTlsPskKeyExchangeModes.PSK_DHE_KE))),
                                   false);
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      false,
                                                                      false,
                                                                      null,
                                                                      null,
                                                                      serverSessionCache);

        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(clientHello.encoded()));

        QuicTlsServerHelloMessage serverHello =
                QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(flight.serverHello()));
        assertThat(serverHello.selectedIdentity().orElseThrow(), is(0));
        assertThat(encryptedExtensions(flight).stream()
                           .noneMatch(extension -> extension.type() == QuicTlsExtensions.EARLY_DATA),
                   is(true));
        completeForeignClientHandshake(clientHello, serverHandshake, flight);
    }

    @Test
    void shouldRejectEarlyDataAndUseFullHandshakeForUnknownTicket() throws Exception {
        ForeignClientHello clientHello =
                foreignClientHello(newResumptionTicket(),
                                   new byte[0],
                                   copy(QuicTlsPskKeyExchangeModes.encodeClientHello(
                                           List.of(QuicTlsPskKeyExchangeModes.PSK_DHE_KE))));
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3");

        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(clientHello.encoded()));

        QuicTlsServerHelloMessage serverHello =
                QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(flight.serverHello()));
        assertThat(serverHello.selectedIdentity().isEmpty(), is(true));
        assertThat(flight.certificate(), notNullValue());
        assertThat(encryptedExtensions(flight).stream()
                           .noneMatch(extension -> extension.type() == QuicTlsExtensions.EARLY_DATA),
                   is(true));
        completeForeignClientHandshake(clientHello, serverHandshake, flight);
    }

    @Test
    void shouldRequireSignatureAlgorithmsWhenUnknownPskNeedsCertificateFallback() throws Exception {
        ForeignClientHello clientHello =
                foreignClientHello(newResumptionTicket(),
                                   new byte[0],
                                   copy(QuicTlsPskKeyExchangeModes.encodeClientHello(
                                           List.of(QuicTlsPskKeyExchangeModes.PSK_DHE_KE))),
                                   false);

        QuicTransportException failure =
                assertThrows(QuicTransportException.class,
                             () -> newServerHandshake("h3").consumeClientHello(
                                     ByteBuffer.wrap(clientHello.encoded())));

        assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 109));
        assertThat(failure.reason(), containsString("missing signature_algorithms"));
    }

    @Test
    void shouldRejectInvalidBinderForCompatibleCachedPsk() throws Exception {
        QuicTlsResumptionTicket resumptionTicket = newResumptionTicket();
        QuicTlsServerSessionCache serverSessionCache = new QuicTlsServerSessionCache();
        serverSessionCache.cache(resumptionTicket);
        ForeignClientHello validClientHello =
                foreignClientHello(resumptionTicket,
                                   new byte[0],
                                   copy(QuicTlsPskKeyExchangeModes.encodeClientHello(
                                           List.of(QuicTlsPskKeyExchangeModes.PSK_DHE_KE))));
        QuicTlsClientHelloMessage decoded =
                QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(validClientHello.encoded()));
        QuicTlsPreSharedKeys.OfferedPsks offeredPsks = decoded.offeredPreSharedKeys().orElseThrow();
        byte[] invalidBinder = offeredPsks.binders().getFirst().clone();
        invalidBinder[0] ^= 1;
        QuicTlsClientHelloMessage invalidClientHello =
                QuicTlsClientHelloMessage.create(
                        decoded.legacyVersion(),
                        decoded.random(),
                        decoded.legacySessionId(),
                        decoded.cipherSuites(),
                        decoded.legacyCompressionMethods(),
                        replaceExtension(
                                decoded.extensions(),
                                QuicTlsExtensions.PRE_SHARED_KEY,
                                copy(QuicTlsPreSharedKeys.encodeClientHello(
                                        new QuicTlsPreSharedKeys.OfferedPsks(
                                                offeredPsks.identities(),
                                                List.of(invalidBinder))))));
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      false,
                                                                      false,
                                                                      null,
                                                                      null,
                                                                      serverSessionCache);

        QuicTransportException failure =
                assertThrows(QuicTransportException.class,
                             () -> serverHandshake.consumeClientHello(invalidClientHello.encode()));

        assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 51));
        assertThat(failure.reason(), containsString("binder cannot be verified"));
    }

    @Test
    void shouldRejectMalformedSignatureSchemesBeforePskResumption() throws Exception {
        QuicTlsResumptionTicket resumptionTicket = newResumptionTicket();
        QuicTlsServerSessionCache serverSessionCache = new QuicTlsServerSessionCache();
        serverSessionCache.cache(resumptionTicket);
        ForeignClientHello validClientHello =
                foreignClientHello(resumptionTicket,
                                   new byte[0],
                                   copy(QuicTlsPskKeyExchangeModes.encodeClientHello(
                                           List.of(QuicTlsPskKeyExchangeModes.PSK_DHE_KE))));
        QuicTlsClientHelloMessage decoded =
                QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(validClientHello.encoded()));
        QuicTlsPreSharedKeys.OfferedPsks offeredPsks = decoded.offeredPreSharedKeys().orElseThrow();
        QuicTlsPreSharedKeys.OfferedPsks placeholderPsks =
                new QuicTlsPreSharedKeys.OfferedPsks(
                        offeredPsks.identities(),
                        List.of(new byte[resumptionTicket.cipherSuite().hashLength()]));

        for (int extensionType : new int[] {
                QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT
        }) {
            for (byte[] malformedExtensionData : List.of(
                    new byte[QuicTlsCodecSupport.UINT16_LENGTH],
                    bytes("0002080400"))) {
                List<QuicTlsExtension> malformedExtensions =
                        replaceExtension(decoded.extensions(), extensionType, malformedExtensionData);
                QuicTlsClientHelloMessage placeholderClientHello =
                        QuicTlsClientHelloMessage.create(
                                decoded.legacyVersion(),
                                decoded.random(),
                                decoded.legacySessionId(),
                                decoded.cipherSuites(),
                                decoded.legacyCompressionMethods(),
                                replaceExtension(malformedExtensions,
                                                 QuicTlsExtensions.PRE_SHARED_KEY,
                                                 copy(QuicTlsPreSharedKeys.encodeClientHello(placeholderPsks))));
                byte[] binder = QuicTlsPreSharedKeys.computeBinder(resumptionTicket,
                                                                   null,
                                                                   null,
                                                                   copy(placeholderClientHello.encode()));
                QuicTlsClientHelloMessage malformedClientHello =
                        QuicTlsClientHelloMessage.create(
                                decoded.legacyVersion(),
                                decoded.random(),
                                decoded.legacySessionId(),
                                decoded.cipherSuites(),
                                decoded.legacyCompressionMethods(),
                                replaceExtension(
                                        placeholderClientHello.extensions(),
                                        QuicTlsExtensions.PRE_SHARED_KEY,
                                        copy(QuicTlsPreSharedKeys.encodeClientHello(
                                                new QuicTlsPreSharedKeys.OfferedPsks(
                                                        offeredPsks.identities(),
                                                        List.of(binder))))));
                QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                              null,
                                                                              false,
                                                                              false,
                                                                              null,
                                                                              null,
                                                                              serverSessionCache);

                QuicTransportException failure =
                        assertThrows(QuicTransportException.class,
                                     () -> serverHandshake.consumeClientHello(malformedClientHello.encode()));

                assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
            }
        }
    }

    @Test
    void shouldUseFullHandshakeWhenPskDheModeIsNotOffered() throws Exception {
        QuicTlsResumptionTicket resumptionTicket = newResumptionTicket();
        QuicTlsServerSessionCache serverSessionCache = new QuicTlsServerSessionCache();
        serverSessionCache.cache(resumptionTicket);
        ForeignClientHello clientHello =
                foreignClientHello(resumptionTicket,
                                   new byte[0],
                                   copy(QuicTlsPskKeyExchangeModes.encodeClientHello(List.of(0))));
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      false,
                                                                      false,
                                                                      null,
                                                                      null,
                                                                      serverSessionCache);

        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(clientHello.encoded()));

        assertThat(QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(flight.serverHello()))
                           .selectedIdentity()
                           .isEmpty(),
                   is(true));
        assertThat(flight.certificate(), notNullValue());
        completeForeignClientHandshake(clientHello, serverHandshake, flight);
    }

    @Test
    void shouldRejectMalformedEarlyDataAndPskModeExtensions() throws Exception {
        QuicTlsResumptionTicket resumptionTicket = newResumptionTicket();
        ForeignClientHello nonEmptyEarlyData =
                foreignClientHello(resumptionTicket,
                                   new byte[] {1},
                                   copy(QuicTlsPskKeyExchangeModes.encodeClientHello(
                                           List.of(QuicTlsPskKeyExchangeModes.PSK_DHE_KE))));
        ForeignClientHello malformedPskModes =
                foreignClientHello(resumptionTicket, new byte[0], new byte[] {0});
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        QuicTlsClientHelloMessage ordinaryClientHello = QuicTlsClientHelloMessage.decode(
                ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));
        QuicTlsClientHelloMessage malformedStandalonePskModes =
                QuicTlsClientHelloMessage.create(
                        ordinaryClientHello.legacyVersion(),
                        ordinaryClientHello.random(),
                        ordinaryClientHello.legacySessionId(),
                        ordinaryClientHello.cipherSuites(),
                        ordinaryClientHello.legacyCompressionMethods(),
                        replaceExtension(ordinaryClientHello.extensions(),
                                         QuicTlsExtensions.PSK_KEY_EXCHANGE_MODES,
                                         new byte[] {0}));

        QuicTransportException earlyDataFailure =
                assertThrows(QuicTransportException.class,
                             () -> newServerHandshake("h3").consumeClientHello(
                                     ByteBuffer.wrap(nonEmptyEarlyData.encoded())));
        QuicTransportException pskModesFailure =
                assertThrows(QuicTransportException.class,
                             () -> newServerHandshake("h3").consumeClientHello(
                                     ByteBuffer.wrap(malformedPskModes.encoded())));
        QuicTransportException standalonePskModesFailure =
                assertThrows(QuicTransportException.class,
                             () -> newServerHandshake("h3").consumeClientHello(
                                     malformedStandalonePskModes.encode()));

        assertThat(earlyDataFailure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
        assertThat(pskModesFailure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
        assertThat(standalonePskModesFailure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
    }

    @Test
    void shouldRejectEarlyDataWithoutPreSharedKey() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(
                ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));
        List<QuicTlsExtension> extensions = new ArrayList<>(clientHello.extensions());
        extensions.add(QuicTlsExtension.create(QuicTlsExtensions.EARLY_DATA, new byte[0]));
        QuicTlsClientHelloMessage invalidClientHello =
                QuicTlsClientHelloMessage.create(clientHello.legacyVersion(),
                                                 clientHello.random(),
                                                 clientHello.legacySessionId(),
                                                 clientHello.cipherSuites(),
                                                 clientHello.legacyCompressionMethods(),
                                                 extensions);

        QuicTransportException failure =
                assertThrows(QuicTransportException.class,
                             () -> newServerHandshake("h3").consumeClientHello(invalidClientHello.encode()));

        assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 47));
        assertThat(failure.reason(), containsString("early_data requires pre_shared_key"));
    }

    @Test
    void shouldRemoveEarlyDataAndRecomputePskAfterHelloRetryRequest() throws Exception {
        QuicTlsResumptionTicket resumptionTicket = newResumptionTicket();
        QuicTlsServerSessionCache serverSessionCache = new QuicTlsServerSessionCache();
        serverSessionCache.cache(resumptionTicket);
        ForeignClientHello initialClientHello =
                foreignClientHello(resumptionTicket,
                                   new byte[0],
                                   copy(QuicTlsPskKeyExchangeModes.encodeClientHello(
                                           List.of(QuicTlsPskKeyExchangeModes.PSK_DHE_KE))));
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      new String[] {"secp256r1"},
                                                                      false,
                                                                      false,
                                                                      null,
                                                                      null,
                                                                      serverSessionCache);
        QuicTls13ServerHandshake.HelloRetryRequestResult retryRequest =
                (QuicTls13ServerHandshake.HelloRetryRequestResult) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(initialClientHello.encoded()));
        ForeignClientHello retriedClientHello =
                retriedForeignClientHello(initialClientHello,
                                          retryRequest.helloRetryRequest(),
                                          false,
                                          new byte[] {0, 0, 0, 0});

        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(retriedClientHello.encoded()));

        assertThat(QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(flight.serverHello()))
                           .selectedIdentity()
                           .isEmpty(),
                   is(true));
        assertThat(flight.certificate(), notNullValue());
        completeForeignClientHandshake(retriedClientHello, serverHandshake, flight);
    }

    @Test
    void shouldRejectEarlyDataRetainedAfterHelloRetryRequest() throws Exception {
        QuicTlsResumptionTicket resumptionTicket = newResumptionTicket();
        ForeignClientHello initialClientHello =
                foreignClientHello(resumptionTicket,
                                   new byte[0],
                                   copy(QuicTlsPskKeyExchangeModes.encodeClientHello(
                                           List.of(QuicTlsPskKeyExchangeModes.PSK_DHE_KE))));
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3", new String[] {"secp256r1"});
        QuicTls13ServerHandshake.HelloRetryRequestResult retryRequest =
                (QuicTls13ServerHandshake.HelloRetryRequestResult) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(initialClientHello.encoded()));
        ForeignClientHello retriedClientHello =
                retriedForeignClientHello(initialClientHello, retryRequest.helloRetryRequest(), true, null);

        QuicTransportException failure =
                assertThrows(QuicTransportException.class,
                             () -> serverHandshake.consumeClientHello(ByteBuffer.wrap(retriedClientHello.encoded())));

        assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 47));
        assertThat(failure.reason(), containsString("retained early_data"));
    }

    @Test
    void shouldRejectRetriedClientHelloThatChangesTransportParameters() throws Exception {
        HelidonClientQuicTLSEngine client =
                newClientEngine("example.com", new String[] {"x25519", "secp256r1"});
        byte[] clientHello1 = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));

        QuicTls13ServerHandshake serverHandshake =
                newServerHandshake("h3", new String[] {"secp256r1"});
        QuicTls13ServerHandshake.HelloRetryRequestResult helloRetryRequest =
                (QuicTls13ServerHandshake.HelloRetryRequestResult) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(clientHello1));

        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(helloRetryRequest.helloRetryRequest()));
        byte[] clientHello2 = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage retriedClientHello = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(clientHello2));
        QuicTlsClientHelloMessage changedTransportParameters = QuicTlsClientHelloMessage.create(
                retriedClientHello.legacyVersion(),
                retriedClientHello.random(),
                retriedClientHello.legacySessionId(),
                retriedClientHello.cipherSuites(),
                retriedClientHello.legacyCompressionMethods(),
                replaceExtension(retriedClientHello.extensions(),
                                 QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS,
                                 bytes("0b0c0d0e0f10")));

        QuicTransportException thrown =
                assertThrows(QuicTransportException.class,
                             () -> serverHandshake.consumeClientHello(changedTransportParameters.encode()));

        assertThat(thrown.reason(), containsString("changed an extension that must remain identical"));
        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 47));
    }

    @Test
    void shouldRejectClientHelloWithoutTransportParametersAsMissingExtension() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(
                ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));
        QuicTlsClientHelloMessage missingTransportParameters = QuicTlsClientHelloMessage.create(
                clientHello.legacyVersion(),
                clientHello.random(),
                clientHello.legacySessionId(),
                clientHello.cipherSuites(),
                clientHello.legacyCompressionMethods(),
                clientHello.extensions().stream()
                        .filter(extension -> extension.type() != QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS)
                        .toList());
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3");

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> serverHandshake.consumeClientHello(missingTransportParameters.encode()));

        assertThat(failure.errorCode(), is(0x016dL));
        assertThat(failure.reason(), containsString("missing quic_transport_parameters extension"));
        assertThat(serverHandshake.complete(), is(false));
    }

    @Test
    void shouldRejectClientHelloWhenAlpnDoesNotMatch() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));

        QuicTls13ServerHandshake serverHandshake = newServerHandshake("hq-29");
        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> serverHandshake.consumeClientHello(ByteBuffer.wrap(clientHello)));

        assertThat(thrown.reason(), containsString("No matching application layer protocol values"));
        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 120));
    }

    @Test
    void shouldRejectClientHelloWithLegacySessionIdAsProtocolViolation() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(
                ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));
        QuicTlsClientHelloMessage compatibilityModeClientHello = QuicTlsClientHelloMessage.create(
                clientHello.legacyVersion(),
                clientHello.random(),
                bytes("01020304"),
                clientHello.cipherSuites(),
                clientHello.legacyCompressionMethods(),
                clientHello.extensions());
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3");

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> serverHandshake.consumeClientHello(compatibilityModeClientHello.encode()));

        assertThat(failure.reason(), containsString("legacy_session_id"));
        assertThat(failure.errorCode(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
        assertThat(failure.frameType(), is(0L));
    }

    @Test
    void shouldRejectEmptySignatureSchemeListsInClientHello() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(
                ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));

        for (int extensionType : new int[] {
                QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT
        }) {
            QuicTlsClientHelloMessage invalidClientHello = QuicTlsClientHelloMessage.create(
                    clientHello.legacyVersion(),
                    clientHello.random(),
                    clientHello.legacySessionId(),
                    clientHello.cipherSuites(),
                    clientHello.legacyCompressionMethods(),
                    replaceExtension(clientHello.extensions(),
                                     extensionType,
                                     new byte[QuicTlsCodecSupport.UINT16_LENGTH]));

            QuicTransportException failure = assertThrows(
                    QuicTransportException.class,
                    () -> newServerHandshake("h3").consumeClientHello(invalidClientHello.encode()));

            assertThat(failure.reason(), containsString("signature_algorithms"));
            assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 50));
        }
    }

    @Test
    void shouldRejectInvalidClientKeyShareAsIllegalParameter() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        QuicTlsClientHelloMessage clientHello = QuicTlsClientHelloMessage.decode(
                ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));
        QuicTlsClientHelloMessage invalidClientHello = QuicTlsClientHelloMessage.create(
                clientHello.legacyVersion(),
                clientHello.random(),
                clientHello.legacySessionId(),
                clientHello.cipherSuites(),
                clientHello.legacyCompressionMethods(),
                replaceExtension(clientHello.extensions(),
                                 QuicTlsExtensions.KEY_SHARE,
                                 copy(QuicTlsKeyShares.encodeClientHello(
                                         List.of(QuicTlsKeyShareEntry.create(QuicTlsNamedGroup.X25519,
                                                                            new byte[32]))))));
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3");

        QuicTransportException failure =
                assertThrows(QuicTransportException.class,
                             () -> serverHandshake.consumeClientHello(invalidClientHello.encode()));

        assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 47));
    }

    @Test
    void shouldProduceCertificateRequestAndCompleteMutualTlsHandshake() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com", true);
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));
        AcceptAllTrustManager trustManager = new AcceptAllTrustManager();

        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      true,
                                                                      false,
                                                                      trustManager);
        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(ByteBuffer.wrap(clientHello));

        assertThat(flight.certificateRequest(), notNullValue());
        QuicTlsCertificateRequestMessage certificateRequest =
                QuicTlsCertificateRequestMessage.decode(ByteBuffer.wrap(flight.certificateRequest()));
        assertThat(certificateRequest.signatureAlgorithms(),
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256)));
        assertThat(certificateRequest.effectiveCertificateSignatureAlgorithms(),
                   equalTo(List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256,
                                   QuicTlsSignatureScheme.RSA_PKCS1_SHA256)));

        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateRequest()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        byte[] clientCertificate = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        byte[] clientCertificateVerify = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        byte[] clientFinished = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        serverHandshake.consumeClientCertificate(ByteBuffer.wrap(clientCertificate));
        serverHandshake.consumeClientCertificateVerify(ByteBuffer.wrap(clientCertificateVerify));
        serverHandshake.consumeClientFinished(ByteBuffer.wrap(clientFinished));

        assertThat(serverHandshake.complete(), is(true));
        assertThat(serverHandshake.peerCertificates()[0].getSubjectX500Principal().getName(), containsString("CN=rsa"));
        assertThat(((X509Certificate) client.session().getLocalCertificates()[0]).getSubjectX500Principal().getName(),
                   containsString("CN=rsa"));
        assertThat(trustManager.localSupportedSignatureAlgorithms,
                   arrayContaining("RSASSA-PSS", "SHA256withRSA"));
        assertThat(trustManager.peerSupportedSignatureAlgorithms,
                   arrayContaining("RSASSA-PSS", "SHA256withRSA"));
    }

    @Test
    void shouldFallbackToConfiguredCertificateWhenClientCertificateSignaturePolicyHasNoOverlap() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        byte[] random = new byte[QuicTlsCodecSupport.RANDOM_LENGTH];
        secureRandom.nextBytes(random);
        ByteBuffer unsupportedCertificateSignatureScheme =
                ByteBuffer.allocate(2 * QuicTlsCodecSupport.UINT16_LENGTH);
        unsupportedCertificateSignatureScheme.putShort((short) QuicTlsCodecSupport.UINT16_LENGTH);
        unsupportedCertificateSignatureScheme.putShort((short) 0x1234);
        QuicTls13ClientHandshake clientHandshake = QuicTls13ClientHandshake.start(
                VERSION,
                new QuicTls13ClientHandshake.ClientHelloParameters(
                        random,
                        new byte[0],
                        List.of(QuicTls13CipherSuite.TLS_AES_128_GCM_SHA256),
                        List.of(QuicTlsNamedGroup.X25519),
                        List.of(QuicTlsNamedGroup.X25519),
                        List.of(
                                QuicTlsExtension.create(
                                        QuicTlsExtensions.APPLICATION_LAYER_PROTOCOL_NEGOTIATION,
                                        QuicTlsApplicationProtocols.encodeClientHello(new String[] {"h3"})),
                                QuicTlsExtension.create(
                                        QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                                        QuicTlsSignatureScheme.encodeCertificateVerifyVector(
                                                List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256))),
                                QuicTlsExtension.create(
                                        QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT,
                                        unsupportedCertificateSignatureScheme.flip()),
                                QuicTlsExtension.create(QuicTlsExtensions.QUIC_TRANSPORT_PARAMETERS,
                                                       CLIENT_TRANSPORT_PARAMETERS)),
                        null),
                secureRandom);
        byte[] clientHello = copy(clientHandshake.clientHello());
        RecordingKeyManager keyManager = new RecordingKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                                                 QuicTlsRfc8448Vectors.rsaPrivateKey(),
                                                                 true);
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      false,
                                                                      false,
                                                                      null,
                                                                      keyManager);

        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(clientHello));

        assertThat(flight.localCertificates()[0].getSigAlgName(), is("SHA256withRSA"));
        QuicTlsCertificateVerifyMessage certificateVerify =
                QuicTlsCertificateVerifyMessage.decode(ByteBuffer.wrap(flight.certificateVerify()));
        assertThat(certificateVerify.signatureScheme(), is(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256));
        assertThat(keyManager.localSupportedSignatureAlgorithms,
                   arrayContaining("RSASSA-PSS", "SHA256withRSA"));
        assertThat(keyManager.peerSupportedSignatureAlgorithms, is(new String[0]));

        QuicTls13ClientHandshake.CompleteResult clientHelloResult =
                (QuicTls13ClientHandshake.CompleteResult) clientHandshake.consumeServerHello(
                        ByteBuffer.wrap(flight.serverHello()));
        QuicTls13ConnectionSecrets clientSecrets = clientHelloResult.connectionSecrets();
        QuicTls13CipherSuite cipherSuite = clientSecrets.cipherSuite();
        byte[] serverHelloTranscriptHash = clientHelloResult.serverHelloTranscriptHash();
        QuicTlsHandshakeTranscript transcript = new QuicTlsHandshakeTranscript();
        transcript.add(ByteBuffer.wrap(clientHello));
        transcript.add(ByteBuffer.wrap(flight.serverHello()));
        transcript.add(ByteBuffer.wrap(flight.encryptedExtensions()));
        transcript.add(ByteBuffer.wrap(flight.certificate()));
        X509Certificate serverCertificate =
                QuicTlsCertificateMessage.decode(ByteBuffer.wrap(flight.certificate())).x509Certificates()[0];
        assertThat(certificateVerify.verify(serverCertificate.getPublicKey(),
                                            transcript.hash(cipherSuite),
                                            false),
                   is(true));
        transcript.add(ByteBuffer.wrap(flight.certificateVerify()));

        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(cipherSuite);
        SecretKey serverFinishedKey =
                secretSchedule.deriveFinishedKey(clientSecrets.serverHandshakeTrafficSecret(serverHelloTranscriptHash));
        QuicTlsFinishedMessage serverFinished =
                QuicTlsFinishedMessage.decode(ByteBuffer.wrap(flight.finished()));
        assertThat(serverFinished.verifyData(),
                   equalTo(secretSchedule.computeVerifyData(serverFinishedKey, transcript.hash(cipherSuite))));
        transcript.add(ByteBuffer.wrap(flight.finished()));

        SecretKey clientFinishedKey =
                secretSchedule.deriveFinishedKey(clientSecrets.clientHandshakeTrafficSecret(serverHelloTranscriptHash));
        ByteBuffer clientFinished =
                QuicTlsFinishedMessage.create(secretSchedule.computeVerifyData(clientFinishedKey,
                                                                               transcript.hash(cipherSuite)))
                        .encode();
        serverHandshake.consumeClientFinished(clientFinished);

        assertThat(serverHandshake.complete(), is(true));
    }

    @Test
    void shouldPreferCompatibleServerChainWhenKeyManagerIgnoresSignaturePolicy() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));
        QuicTls13ServerHandshake serverHandshake = newServerHandshake(
                "h3",
                null,
                false,
                false,
                null,
                QuicTlsCertificateChainFixtures.ignoringSignaturePolicyKeyManager(true));

        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(clientHello));

        assertThat(flight.localCertificates().length, is(2));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           flight.localCertificates(),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256,
                                   QuicTlsSignatureScheme.RSA_PKCS1_SHA256)),
                   is(true));
    }

    @Test
    void shouldFallbackToServerChainAfterCompatibleAlternativesAreExhausted() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));
        QuicTls13ServerHandshake serverHandshake = newServerHandshake(
                "h3",
                null,
                false,
                false,
                null,
                QuicTlsCertificateChainFixtures.ignoringSignaturePolicyKeyManager(false));

        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(clientHello));

        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           flight.localCertificates(),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA384)),
                   is(true));
        assertThat(QuicTlsSignatureScheme.supportsCertificateChain(
                           flight.localCertificates(),
                           List.of(QuicTlsSignatureScheme.RSA_PSS_RSAE_SHA256,
                                   QuicTlsSignatureScheme.RSA_PKCS1_SHA256)),
                   is(false));
    }

    @Test
    void shouldReportDecryptErrorForInvalidClientCertificateVerify() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com", true);
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      true,
                                                                      false,
                                                                      new AcceptAllTrustManager());
        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(
                        ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));
        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateRequest()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));
        byte[] clientCertificate = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        byte[] clientCertificateVerify = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        clientCertificateVerify[clientCertificateVerify.length - 1] ^= 1;
        serverHandshake.consumeClientCertificate(ByteBuffer.wrap(clientCertificate));

        QuicTransportException failure =
                assertThrows(QuicTransportException.class,
                             () -> serverHandshake.consumeClientCertificateVerify(
                                     ByteBuffer.wrap(clientCertificateVerify)));

        assertThat(failure.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 51));
    }

    @Test
    void shouldAllowEmptyClientCertificateWhenClientAuthIsOptional() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));

        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      false,
                                                                      true,
                                                                      new AcceptAllTrustManager());
        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(ByteBuffer.wrap(clientHello));

        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateRequest()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        byte[] clientCertificate = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        byte[] clientFinished = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        serverHandshake.consumeClientCertificate(ByteBuffer.wrap(clientCertificate));
        serverHandshake.consumeClientFinished(ByteBuffer.wrap(clientFinished));

        assertThat(serverHandshake.complete(), is(true));
        assertThat(serverHandshake.peerCertificates(), is((X509Certificate[]) null));
    }

    @Test
    void shouldHonorServerAliasRefusalForRequestedServerName() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));

        NullChoosingKeyManager keyManager =
                new NullChoosingKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                           QuicTlsRfc8448Vectors.rsaPrivateKey());
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      false,
                                                                      false,
                                                                      null,
                                                                      keyManager);

        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> serverHandshake.consumeClientHello(ByteBuffer.wrap(clientHello)));

        assertThat(thrown.reason(), containsString("No supported server certificate available"));
        assertThat(keyManager.requestedServerName, is("example.com"));
        assertThat(keyManager.serverAliasesCalls, is(0));
    }

    @Test
    void shouldRejectEmptyClientCertificateWhenClientAuthIsRequired() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));

        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      true,
                                                                      false,
                                                                      new AcceptAllTrustManager());
        QuicTls13ServerHandshake.ServerFlight flight =
                (QuicTls13ServerHandshake.ServerFlight) serverHandshake.consumeClientHello(ByteBuffer.wrap(clientHello));

        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(flight.serverHello()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.encryptedExtensions()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateRequest()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificate()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.certificateVerify()));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(flight.finished()));

        byte[] clientCertificate = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        QuicTransportException thrown = assertThrows(QuicTransportException.class,
                                                     () -> serverHandshake.consumeClientCertificate(
                                                             ByteBuffer.wrap(clientCertificate)));

        assertThat(thrown.reason(), containsString("Empty client certificate chain"));
        assertThat(thrown.errorCode(), is(QuicTransportErrors.CRYPTO_ERROR.from() + 116));
    }

    @Test
    void shouldTranslateAcceptedIssuerProviderFailure() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));
        X509ExtendedTrustManager trustManager = mock(X509ExtendedTrustManager.class);
        ProviderException failure = new ProviderException("accepted issuer provider failure");
        when(trustManager.getAcceptedIssuers()).thenThrow(failure);
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      true,
                                                                      false,
                                                                      trustManager);

        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> serverHandshake.consumeClientHello(ByteBuffer.wrap(clientHello)));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(thrown.getCause(), sameInstance(failure));
    }

    @Test
    void shouldTranslateServerAliasProviderFailure() throws Exception {
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));
        X509ExtendedKeyManager keyManager = mock(X509ExtendedKeyManager.class);
        ProviderException failure = new ProviderException("server alias provider failure");
        when(keyManager.chooseEngineServerAlias(eq("RSA"), isNull(), any(SSLEngine.class))).thenReturn("server");
        when(keyManager.getServerAliases("RSA", null)).thenThrow(failure);
        QuicTls13ServerHandshake serverHandshake = newServerHandshake("h3",
                                                                      null,
                                                                      false,
                                                                      false,
                                                                      null,
                                                                      keyManager);

        QuicTransportException thrown = assertThrows(
                QuicTransportException.class,
                () -> serverHandshake.consumeClientHello(ByteBuffer.wrap(clientHello)));

        assertThat(thrown.errorCode(), is(QuicTransportErrors.INTERNAL_ERROR.code()));
        assertThat(thrown.getCause(), sameInstance(failure));
    }

    private static List<List<SNIServerName>> requestedServerNames() {
        return List.of(List.of(new SNIHostName("example.com")),
                       List.of(),
                       List.of(new SNIServerName(1, bytes("010203")) { }));
    }

    private static void configureServerNames(HelidonClientQuicTLSEngine client, List<SNIServerName> serverNames) {
        SSLParameters parameters = client.sslParameters();
        parameters.setServerNames(serverNames);
        client.sslParameters(parameters);
    }

    private static QuicTlsResumptionTicket newResumptionTicket() throws Exception {
        return newResumptionTicket(List.of(new SNIHostName("example.com")));
    }

    private static QuicTlsResumptionTicket newResumptionTicket(List<SNIServerName> serverNames) throws Exception {
        HelidonClientQuicTLSEngine initialClient = newClientEngine("example.com");
        configureServerNames(initialClient, serverNames);
        byte[] initialClientHello = copy(packetEngine(initialClient).handshakeBytesBuffer(INITIAL));
        QuicTls13ServerHandshake initialServerHandshake = newServerHandshake("h3");
        QuicTls13ServerHandshake.ServerFlight initialFlight =
                (QuicTls13ServerHandshake.ServerFlight) initialServerHandshake.consumeClientHello(
                        ByteBuffer.wrap(initialClientHello));

        initialClient.versionNegotiated(VERSION);
        packetEngine(initialClient).consumeHandshakeBytesBuffer(INITIAL,
                                                                ByteBuffer.wrap(initialFlight.serverHello()));
        packetEngine(initialClient).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                                ByteBuffer.wrap(initialFlight.encryptedExtensions()));
        packetEngine(initialClient).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                                ByteBuffer.wrap(initialFlight.certificate()));
        packetEngine(initialClient).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                                ByteBuffer.wrap(initialFlight.certificateVerify()));
        packetEngine(initialClient).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                                ByteBuffer.wrap(initialFlight.finished()));
        initialServerHandshake.consumeClientFinished(
                ByteBuffer.wrap(copy(packetEngine(initialClient).handshakeBytesBuffer(HANDSHAKE))));

        return initialServerHandshake.newResumptionTicket(3600L,
                                                          0x01020304L,
                                                          bytes("010203040506"),
                                                          bytes("0a0b0c0d0e0f"));
    }

    private static ForeignClientHello foreignClientHello(QuicTlsResumptionTicket resumptionTicket,
                                                          byte[] earlyData,
                                                          byte[] pskKeyExchangeModes)
            throws Exception {
        return foreignClientHello(resumptionTicket, earlyData, pskKeyExchangeModes, true);
    }

    private static ForeignClientHello foreignClientHello(QuicTlsResumptionTicket resumptionTicket,
                                                          byte[] earlyData,
                                                          byte[] pskKeyExchangeModes,
                                                          boolean includeSignatureAlgorithms)
            throws Exception {
        HelidonClientQuicTLSEngine templateClient = newClientEngine("example.com", resumptionTicket);
        QuicTlsClientHelloMessage template = QuicTlsClientHelloMessage.decode(
                ByteBuffer.wrap(copy(packetEngine(templateClient).handshakeBytesBuffer(INITIAL))));
        QuicTlsLocalKeyShares localKeyShares =
                QuicTlsLocalKeyShares.create(List.of(QuicTlsNamedGroup.X25519), new SecureRandom());
        QuicTlsPreSharedKeys.PskIdentity identity =
                template.offeredPreSharedKeys().orElseThrow().identities().getFirst();
        QuicTlsPreSharedKeys.OfferedPsks placeholder =
                new QuicTlsPreSharedKeys.OfferedPsks(
                        List.of(identity),
                        List.of(new byte[resumptionTicket.cipherSuite().hashLength()]));
        List<QuicTlsExtension> extensions = new ArrayList<>(template.extensions().size() + 1);
        for (QuicTlsExtension extension : template.extensions()) {
            switch (extension.type()) {
            case QuicTlsExtensions.KEY_SHARE ->
                    extensions.add(QuicTlsExtension.create(
                            QuicTlsExtensions.KEY_SHARE,
                            QuicTlsKeyShares.encodeClientHello(localKeyShares.keyShareEntries())));
            case QuicTlsExtensions.PSK_KEY_EXCHANGE_MODES ->
                    extensions.add(QuicTlsExtension.create(QuicTlsExtensions.PSK_KEY_EXCHANGE_MODES,
                                                          pskKeyExchangeModes));
            case QuicTlsExtensions.SIGNATURE_ALGORITHMS,
                 QuicTlsExtensions.SIGNATURE_ALGORITHMS_CERT -> {
                if (includeSignatureAlgorithms) {
                    extensions.add(extension);
                }
            }
            case QuicTlsExtensions.PRE_SHARED_KEY -> {
                extensions.add(QuicTlsExtension.create(QuicTlsExtensions.EARLY_DATA, earlyData));
                extensions.add(QuicTlsExtension.create(QuicTlsExtensions.PRE_SHARED_KEY,
                                                       QuicTlsPreSharedKeys.encodeClientHello(placeholder)));
            }
            default -> extensions.add(extension);
            }
        }

        QuicTlsClientHelloMessage placeholderClientHello =
                QuicTlsClientHelloMessage.create(template.legacyVersion(),
                                                 template.random(),
                                                 template.legacySessionId(),
                                                 template.cipherSuites(),
                                                 template.legacyCompressionMethods(),
                                                 extensions);
        byte[] binder = QuicTlsPreSharedKeys.computeBinder(resumptionTicket,
                                                           null,
                                                           null,
                                                           copy(placeholderClientHello.encode()));
        QuicTlsClientHelloMessage clientHello =
                QuicTlsClientHelloMessage.create(
                        template.legacyVersion(),
                        template.random(),
                        template.legacySessionId(),
                        template.cipherSuites(),
                        template.legacyCompressionMethods(),
                        replaceExtension(
                                placeholderClientHello.extensions(),
                                QuicTlsExtensions.PRE_SHARED_KEY,
                                copy(QuicTlsPreSharedKeys.encodeClientHello(
                                        new QuicTlsPreSharedKeys.OfferedPsks(List.of(identity), List.of(binder))))));
        return new ForeignClientHello(copy(clientHello.encode()),
                                      localKeyShares,
                                      resumptionTicket,
                                      null,
                                      null);
    }

    private static ForeignClientHello retriedForeignClientHello(ForeignClientHello initialClientHello,
                                                                 byte[] helloRetryRequest,
                                                                 boolean includeEarlyData,
                                                                 byte[] padding)
            throws Exception {
        QuicTlsClientHelloMessage initial =
                QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(initialClientHello.encoded()));
        QuicTlsServerHelloMessage retry =
                QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(helloRetryRequest));
        QuicTlsNamedGroup selectedGroup = retry.helloRetryRequestSelectedGroup().orElseThrow();
        QuicTlsLocalKeyShares localKeyShares =
                QuicTlsLocalKeyShares.create(List.of(selectedGroup), new SecureRandom());
        QuicTlsPreSharedKeys.PskIdentity identity =
                initial.offeredPreSharedKeys().orElseThrow().identities().getFirst();
        QuicTlsPreSharedKeys.OfferedPsks placeholder =
                new QuicTlsPreSharedKeys.OfferedPsks(
                        List.of(identity),
                        List.of(new byte[initialClientHello.resumptionTicket().cipherSuite().hashLength()]));
        List<QuicTlsExtension> extensions = new ArrayList<>(initial.extensions().size() + 1);
        for (QuicTlsExtension extension : initial.extensions()) {
            switch (extension.type()) {
            case QuicTlsExtensions.KEY_SHARE ->
                    extensions.add(QuicTlsExtension.create(
                            QuicTlsExtensions.KEY_SHARE,
                            QuicTlsKeyShares.encodeClientHello(localKeyShares.keyShareEntries())));
            case QuicTlsExtensions.EARLY_DATA -> {
                if (includeEarlyData) {
                    extensions.add(extension);
                }
            }
            case QuicTlsExtensions.PRE_SHARED_KEY -> {
                if (padding != null) {
                    extensions.add(QuicTlsExtension.create(QuicTlsExtensions.PADDING, padding));
                }
                extensions.add(QuicTlsExtension.create(QuicTlsExtensions.PRE_SHARED_KEY,
                                                      QuicTlsPreSharedKeys.encodeClientHello(placeholder)));
            }
            default -> extensions.add(extension);
            }
        }

        QuicTlsClientHelloMessage placeholderClientHello =
                QuicTlsClientHelloMessage.create(initial.legacyVersion(),
                                                 initial.random(),
                                                 initial.legacySessionId(),
                                                 initial.cipherSuites(),
                                                 initial.legacyCompressionMethods(),
                                                 extensions);
        byte[] initialBytes = initialClientHello.encoded();
        byte[] binder = QuicTlsPreSharedKeys.computeBinder(initialClientHello.resumptionTicket(),
                                                           initialBytes,
                                                           helloRetryRequest,
                                                           copy(placeholderClientHello.encode()));
        QuicTlsClientHelloMessage retriedClientHello =
                QuicTlsClientHelloMessage.create(
                        initial.legacyVersion(),
                        initial.random(),
                        initial.legacySessionId(),
                        initial.cipherSuites(),
                        initial.legacyCompressionMethods(),
                        replaceExtension(
                                placeholderClientHello.extensions(),
                                QuicTlsExtensions.PRE_SHARED_KEY,
                                copy(QuicTlsPreSharedKeys.encodeClientHello(
                                        new QuicTlsPreSharedKeys.OfferedPsks(List.of(identity), List.of(binder))))));
        return new ForeignClientHello(copy(retriedClientHello.encode()),
                                      localKeyShares,
                                      initialClientHello.resumptionTicket(),
                                      initialBytes,
                                      helloRetryRequest);
    }

    private static List<QuicTlsExtension> encryptedExtensions(QuicTls13ServerHandshake.ServerFlight flight)
            throws QuicTransportException {
        ByteBuffer body =
                QuicTlsCodecSupport.handshakeBody(ByteBuffer.wrap(flight.encryptedExtensions()),
                                                  "EncryptedExtensions");
        List<QuicTlsExtension> extensions = QuicTlsExtensions.decode(body, "EncryptedExtensions");
        QuicTlsCodecSupport.ensureConsumed(body, "EncryptedExtensions");
        return extensions;
    }

    private static void assertServerNameAcknowledgment(QuicTls13ServerHandshake.ServerFlight flight,
                                                      List<SNIServerName> serverNames) throws QuicTransportException {
        List<QuicTlsExtension> acknowledgments = encryptedExtensions(flight).stream()
                .filter(extension -> extension.type() == QuicTlsExtensions.SERVER_NAME)
                .toList();
        List<QuicTlsExtension> expected = serverNames.stream().anyMatch(SNIHostName.class::isInstance)
                ? List.of(QuicTlsExtension.create(QuicTlsExtensions.SERVER_NAME, new byte[0]))
                : List.of();
        assertThat("EncryptedExtensions server_name acknowledgment", acknowledgments, equalTo(expected));
    }

    private static void completeForeignClientHandshake(ForeignClientHello clientHello,
                                                       QuicTls13ServerHandshake serverHandshake,
                                                       QuicTls13ServerHandshake.ServerFlight flight)
            throws Exception {
        QuicTlsServerHelloMessage serverHello =
                QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(flight.serverHello()));
        QuicTls13ConnectionSecrets clientSecrets = serverHello.selectedIdentity().isPresent()
                ? QuicTls13ConnectionSecrets.forServerHello(VERSION,
                                                            clientHello.localKeyShares(),
                                                            serverHello,
                                                            clientHello.resumptionTicket().resumptionPsk(),
                                                            true)
                : QuicTls13ConnectionSecrets.forServerHello(VERSION,
                                                            clientHello.localKeyShares(),
                                                            serverHello,
                                                            true);
        QuicTls13CipherSuite cipherSuite = clientSecrets.cipherSuite();
        QuicTlsHandshakeTranscript transcript = new QuicTlsHandshakeTranscript();
        if (clientHello.previousClientHello() != null) {
            transcript.add(ByteBuffer.wrap(clientHello.previousClientHello()));
            transcript.add(ByteBuffer.wrap(clientHello.helloRetryRequest()));
        }
        transcript.add(ByteBuffer.wrap(clientHello.encoded()));
        transcript.add(ByteBuffer.wrap(flight.serverHello()));
        byte[] serverHelloTranscriptHash = transcript.hash(cipherSuite);
        assertThat(serverHelloTranscriptHash, equalTo(flight.serverHelloTranscriptHash()));
        transcript.add(ByteBuffer.wrap(flight.encryptedExtensions()));
        if (flight.certificateRequest() != null) {
            transcript.add(ByteBuffer.wrap(flight.certificateRequest()));
        }
        if (flight.certificate() != null) {
            transcript.add(ByteBuffer.wrap(flight.certificate()));
        }
        if (flight.certificateVerify() != null) {
            transcript.add(ByteBuffer.wrap(flight.certificateVerify()));
        }

        QuicTls13SecretSchedule secretSchedule = new QuicTls13SecretSchedule(cipherSuite);
        SecretKey serverFinishedKey =
                secretSchedule.deriveFinishedKey(
                        clientSecrets.serverHandshakeTrafficSecret(serverHelloTranscriptHash));
        assertThat(QuicTlsFinishedMessage.decode(ByteBuffer.wrap(flight.finished())).verifyData(),
                   equalTo(secretSchedule.computeVerifyData(serverFinishedKey, transcript.hash(cipherSuite))));
        transcript.add(ByteBuffer.wrap(flight.finished()));

        SecretKey clientFinishedKey =
                secretSchedule.deriveFinishedKey(
                        clientSecrets.clientHandshakeTrafficSecret(serverHelloTranscriptHash));
        serverHandshake.consumeClientFinished(
                QuicTlsFinishedMessage.create(
                                secretSchedule.computeVerifyData(clientFinishedKey, transcript.hash(cipherSuite)))
                        .encode());
        assertThat(serverHandshake.complete(), is(true));
    }

    private static HelidonClientQuicTLSEngine newClientEngine(String peerHost) throws Exception {
        return newClientEngine(peerHost, null, false, null);
    }

    private static HelidonClientQuicTLSEngine newClientEngine(String peerHost, boolean withClientCertificate)
            throws Exception {
        return newClientEngine(peerHost, null, withClientCertificate, null);
    }

    private static HelidonClientQuicTLSEngine newClientEngine(String peerHost, String[] namedGroups) throws Exception {
        return newClientEngine(peerHost, null, false, namedGroups);
    }

    private static HelidonClientQuicTLSEngine newClientEngine(String peerHost,
                                                              boolean withClientCertificate,
                                                              String[] namedGroups) throws Exception {
        return newClientEngine(peerHost, null, withClientCertificate, namedGroups);
    }

    private static HelidonClientQuicTLSEngine newClientEngine(String peerHost,
                                                              QuicTlsResumptionTicket resumptionTicket)
            throws Exception {
        QuicTlsSessionCache sessionCache = new QuicTlsSessionCache();
        sessionCache.cache(peerHost, 443, resumptionTicket);
        return newClientEngine(peerHost, sessionCache, false, null);
    }

    private static HelidonClientQuicTLSEngine newClientEngine(String peerHost,
                                                              QuicTlsSessionCache sessionCache,
                                                              boolean withClientCertificate,
                                                              String[] namedGroups) throws Exception {
        var keyManager = withClientCertificate
                ? new StaticKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                       QuicTlsRfc8448Vectors.rsaPrivateKey())
                : null;
        var tls = QuicTlsTestSupport.tls(keyManager, new AcceptAllTrustManager());
        HelidonClientQuicTLSEngine engine = new HelidonClientQuicTLSEngine(
                QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create())),
                peerHost,
                443,
                sessionCache);
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        sslParameters.setApplicationProtocols(new String[] {"h3"});
        sslParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
        if (namedGroups != null) {
            sslParameters.setNamedGroups(namedGroups);
        }
        engine.clientMode(true);
        engine.sslParameters(sslParameters);
        packetEngine(engine).deriveInitialKeysBuffer(VERSION, ByteBuffer.wrap(bytes("8394c8f03e515708")));
        packetEngine(engine).localQuicTransportParametersBuffer(ByteBuffer.wrap(CLIENT_TRANSPORT_PARAMETERS));
        return engine;
    }

    private static QuicTls13ServerHandshake newServerHandshake(String applicationProtocol) throws Exception {
        return newServerHandshake(applicationProtocol,
                                  null,
                                  false,
                                  false,
                                  null,
                                  null,
                                  new QuicTlsServerSessionCache());
    }

    private static QuicTls13ServerHandshake newServerHandshake(String applicationProtocol,
                                                               String[] namedGroups) throws Exception {
        return newServerHandshake(applicationProtocol,
                                  namedGroups,
                                  false,
                                  false,
                                  null,
                                  null,
                                  new QuicTlsServerSessionCache());
    }

    private static QuicTls13ServerHandshake newServerHandshake(String applicationProtocol,
                                                               String[] namedGroups,
                                                               boolean needClientAuth,
                                                               boolean wantClientAuth,
                                                               X509ExtendedTrustManager trustManager) throws Exception {
        return newServerHandshake(applicationProtocol,
                                  namedGroups,
                                  needClientAuth,
                                  wantClientAuth,
                                  trustManager,
                                  null,
                                  new QuicTlsServerSessionCache());
    }

    private static QuicTls13ServerHandshake newServerHandshake(String applicationProtocol,
                                                               String[] namedGroups,
                                                               boolean needClientAuth,
                                                               boolean wantClientAuth,
                                                               X509ExtendedTrustManager trustManager,
                                                               X509ExtendedKeyManager keyManager) throws Exception {
        return newServerHandshake(applicationProtocol,
                                  namedGroups,
                                  needClientAuth,
                                  wantClientAuth,
                                  trustManager,
                                  keyManager,
                                  new QuicTlsServerSessionCache());
    }

    private static QuicTls13ServerHandshake newServerHandshake(String applicationProtocol,
                                                               String[] namedGroups,
                                                               boolean needClientAuth,
                                                               boolean wantClientAuth,
                                                               X509ExtendedTrustManager trustManager,
                                                               X509ExtendedKeyManager keyManager,
                                                               QuicTlsServerSessionCache serverSessionCache)
            throws Exception {
        X509ExtendedKeyManager effectiveKeyManager = keyManager == null
                ? new StaticKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                       QuicTlsRfc8448Vectors.rsaPrivateKey())
                : keyManager;
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(new KeyManager[] {effectiveKeyManager}, null, new SecureRandom());

        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        sslParameters.setApplicationProtocols(new String[] {applicationProtocol});
        sslParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
        if (needClientAuth) {
            sslParameters.setNeedClientAuth(true);
        } else if (wantClientAuth) {
            sslParameters.setWantClientAuth(true);
        }
        if (namedGroups != null) {
            sslParameters.setNamedGroups(namedGroups);
        }
        return QuicTls13ServerHandshake.start(
                VERSION,
                new QuicTls13ServerHandshake.StartParameters(sslContext,
                                                             sslParameters,
                                                             effectiveKeyManager,
                                                             trustManager,
                                                             SERVER_TRANSPORT_PARAMETERS,
                                                             new SecureRandom(),
                                                             null,
                                                             -1,
                                                             serverSessionCache,
                                                             QuicAeadLimits.Confidentiality.defaults()));
    }

    private static byte[] bytes(String hex) {
        return QuicTlsRfc8448Vectors.bytes(hex);
    }

    private static QuicPacketTLSEngine packetEngine(QuicTLSEngine engine) {
        return QuicPacketTLSEngine.internal(engine);
    }

    private static byte[] copy(Optional<ByteBuffer> buffer) {
        return copy(required(buffer));
    }

    private static byte[] copy(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private static <T> T required(Optional<T> optional) {
        return optional.orElseThrow();
    }

    private static List<QuicTlsExtension> replaceExtension(List<QuicTlsExtension> extensions, int type, byte[] data) {
        List<QuicTlsExtension> replaced = new ArrayList<>(extensions.size());
        boolean updated = false;
        for (QuicTlsExtension extension : extensions) {
            if (extension.type() == type) {
                replaced.add(QuicTlsExtension.create(type, data));
                updated = true;
            } else {
                replaced.add(extension);
            }
        }
        if (!updated) {
            throw new IllegalArgumentException(String.format("Missing extension 0x%04x", type));
        }
        return List.copyOf(replaced);
    }

    private static final class ForeignClientHello {
        private final byte[] encoded;
        private final QuicTlsLocalKeyShares localKeyShares;
        private final QuicTlsResumptionTicket resumptionTicket;
        private final byte[] previousClientHello;
        private final byte[] helloRetryRequest;

        private ForeignClientHello(byte[] encoded,
                                   QuicTlsLocalKeyShares localKeyShares,
                                   QuicTlsResumptionTicket resumptionTicket,
                                   byte[] previousClientHello,
                                   byte[] helloRetryRequest) {
            this.encoded = encoded.clone();
            this.localKeyShares = localKeyShares;
            this.resumptionTicket = resumptionTicket;
            this.previousClientHello = previousClientHello == null ? null : previousClientHello.clone();
            this.helloRetryRequest = helloRetryRequest == null ? null : helloRetryRequest.clone();
        }

        private byte[] encoded() {
            return encoded.clone();
        }

        private QuicTlsLocalKeyShares localKeyShares() {
            return localKeyShares;
        }

        private QuicTlsResumptionTicket resumptionTicket() {
            return resumptionTicket;
        }

        private byte[] previousClientHello() {
            return previousClientHello == null ? null : previousClientHello.clone();
        }

        private byte[] helloRetryRequest() {
            return helloRetryRequest == null ? null : helloRetryRequest.clone();
        }
    }

    private static class StaticKeyManager extends X509ExtendedKeyManager {
        private static final String ALIAS = "server";

        private final X509Certificate[] certificateChain;
        private final PrivateKey privateKey;

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
            return supports(keyType) ? new String[] {ALIAS} : new String[0];
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return supports(keyType) ? ALIAS : null;
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
            return firstSupported(keyType);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return supports(keyType) ? ALIAS : null;
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

    private static final class NullChoosingKeyManager extends StaticKeyManager {
        private int serverAliasesCalls;
        private String requestedServerName;

        private NullChoosingKeyManager(X509Certificate certificate, PrivateKey privateKey) {
            super(certificate, privateKey);
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            serverAliasesCalls++;
            return super.getServerAliases(keyType, issuers);
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            List<SNIServerName> serverNames = engine.getSSLParameters().getServerNames();
            if (serverNames != null) {
                for (var serverName : serverNames) {
                    if (serverName instanceof SNIHostName hostName) {
                        requestedServerName = hostName.getAsciiName();
                    }
                }
            }
            return null;
        }
    }

    private static final class RecordingKeyManager extends StaticKeyManager {
        private final boolean chooseAlias;
        private String[] localSupportedSignatureAlgorithms;
        private String[] peerSupportedSignatureAlgorithms;
        private List<SNIServerName> requestedServerNames;

        private RecordingKeyManager(X509Certificate certificate, PrivateKey privateKey, boolean chooseAlias) {
            super(certificate, privateKey);
            this.chooseAlias = chooseAlias;
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            ExtendedSSLSession session = (ExtendedSSLSession) engine.getHandshakeSession();
            localSupportedSignatureAlgorithms = session.getLocalSupportedSignatureAlgorithms();
            peerSupportedSignatureAlgorithms = session.getPeerSupportedSignatureAlgorithms();
            List<SNIServerName> serverNames = engine.getSSLParameters().getServerNames();
            requestedServerNames = serverNames == null ? List.of() : List.copyOf(serverNames);
            return chooseAlias ? super.chooseEngineServerAlias(keyType, issuers, engine) : null;
        }
    }

    private static final class AcceptAllTrustManager extends X509ExtendedTrustManager {
        private String[] localSupportedSignatureAlgorithms;
        private String[] peerSupportedSignatureAlgorithms;

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            ExtendedSSLSession session = (ExtendedSSLSession) engine.getHandshakeSession();
            localSupportedSignatureAlgorithms = session.getLocalSupportedSignatureAlgorithms();
            peerSupportedSignatureAlgorithms = session.getPeerSupportedSignatureAlgorithms();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
