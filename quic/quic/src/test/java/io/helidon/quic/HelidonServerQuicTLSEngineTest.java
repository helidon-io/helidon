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
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import static io.helidon.quic.QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO;
import static io.helidon.quic.QuicTLSEngine.HandshakeState.NEED_SEND_HANDSHAKE_DONE;
import static io.helidon.quic.QuicTLSEngine.KeySpace.HANDSHAKE;
import static io.helidon.quic.QuicTLSEngine.KeySpace.INITIAL;
import static io.helidon.quic.QuicTLSEngine.KeySpace.ONE_RTT;
import static io.helidon.quic.QuicTLSEngine.KeySpace.ZERO_RTT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HelidonServerQuicTLSEngineTest {
    private static final QuicVersion VERSION = QuicVersion.QUIC_V1;
    // Distinct transport-parameter payloads keep the server-engine test honest about which side supplied each value.
    private static final byte[] CLIENT_TRANSPORT_PARAMETERS = bytes("010203040506");
    private static final byte[] SERVER_TRANSPORT_PARAMETERS = bytes("0a0b0c0d0e0f");
    private static final byte[] HEADER_PROTECTION_SAMPLE = bytes("0102030405060708090a0b0c0d0e0f10");

    @Test
    void shouldMatchPackedAndLegacyInitialHeaderProtectionMasks() throws Exception {
        HelidonServerQuicTLSEngine engine = newServerEngine("example.com");
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
    void shouldCompleteServerHandshakeAndExposeKeys() throws Exception {
        HelidonServerQuicTLSEngine server = newServerEngine("example.com");
        AtomicReference<byte[]> remoteTransportParameters = new AtomicReference<>();
        server.remoteQuicTransportParametersConsumer(buffer -> remoteTransportParameters.set(copy(buffer)));

        HelidonClientQuicTLSEngine client = newClientEngine("example.com");
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));

        packetEngine(server).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(clientHello));

        assertThat(server.handshakeState(), is(NEED_SEND_CRYPTO));
        assertThat(server.currentSendKeySpace(), is(INITIAL));
        assertThat(required(server.handshakeSession()), notNullValue());
        assertThat(remoteTransportParameters.get(), equalTo(CLIENT_TRANSPORT_PARAMETERS));
        assertThat(required(server.applicationProtocol()), is("h3"));
        assertThat(server.session().getLocalCertificates(), notNullValue());

        byte[] serverHello = copy(packetEngine(server).handshakeBytesBuffer(INITIAL));
        byte[] encryptedExtensions = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));
        byte[] certificate = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));
        byte[] certificateVerify = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));
        byte[] finished = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));

        assertThat(server.handshakeState(), is(NEED_RECV_CRYPTO));
        assertThat(server.currentSendKeySpace(), is(ONE_RTT));
        assertThat(server.keysAvailable(HANDSHAKE), is(true));
        assertThat(server.keysAvailable(ONE_RTT), is(true));
        assertThat(server.keysAvailable(ZERO_RTT), is(false));

        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(serverHello));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(encryptedExtensions));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(certificate));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(certificateVerify));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(finished));

        assertHeaderProtectionMaskMatchesPeer(server, client, HANDSHAKE, false);
        assertHeaderProtectionMaskMatchesPeer(server, client, HANDSHAKE, true);
        assertHeaderProtectionMaskMatchesPeer(server, client, ONE_RTT, false);
        assertHeaderProtectionMaskMatchesPeer(server, client, ONE_RTT, true);

        byte[] clientFinished = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        packetEngine(server).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(clientFinished));

        assertThat(server.handshakeState(), is(NEED_SEND_HANDSHAKE_DONE));
        assertThat(server.isTLSHandshakeComplete(), is(true));
        assertThat(server.tryMarkHandshakeDone(), is(true));
        assertThat(server.handshakeState(), is(HANDSHAKE_CONFIRMED));
        assertThat(server.tryMarkHandshakeDone(), is(false));
        assertThat(((X509Certificate) server.session().getLocalCertificates()[0]).getSubjectX500Principal().getName(),
                   containsString("CN=rsa"));
    }

    @Test
    void shouldCompleteServerHandshakeAfterHelloRetryRequest() throws Exception {
        HelidonServerQuicTLSEngine server =
                newServerEngine("example.com", new String[] {"secp256r1"});
        AtomicReference<byte[]> remoteTransportParameters = new AtomicReference<>();
        server.remoteQuicTransportParametersConsumer(buffer -> remoteTransportParameters.set(copy(buffer)));

        HelidonClientQuicTLSEngine client =
                newClientEngine("example.com", new String[] {"x25519", "secp256r1"});
        byte[] clientHello1 = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));

        packetEngine(server).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(clientHello1));

        assertThat(server.handshakeState(), is(NEED_SEND_CRYPTO));
        assertThat(server.currentSendKeySpace(), is(INITIAL));

        byte[] helloRetryRequest = copy(packetEngine(server).handshakeBytesBuffer(INITIAL));
        QuicTlsServerHelloMessage helloRetryRequestMessage =
                QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(helloRetryRequest));
        assertThat(helloRetryRequestMessage.helloRetryRequest(), is(true));
        assertThat(helloRetryRequestMessage.helloRetryRequestSelectedGroup().orElseThrow(),
                   is(QuicTlsNamedGroup.SECP256_R1));
        assertThat(server.handshakeState(), is(NEED_RECV_CRYPTO));
        assertThat(server.currentSendKeySpace(), is(INITIAL));

        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(helloRetryRequest));
        byte[] clientHello2 = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));
        QuicTlsClientHelloMessage retriedClientHello = QuicTlsClientHelloMessage.decode(ByteBuffer.wrap(clientHello2));
        assertThat(retriedClientHello.keyShares().stream().map(QuicTlsKeyShareEntry::namedGroup).toList(),
                   equalTo(List.of(QuicTlsNamedGroup.SECP256_R1)));

        packetEngine(server).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(clientHello2));

        byte[] serverHello = copy(packetEngine(server).handshakeBytesBuffer(INITIAL));
        byte[] encryptedExtensions = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));
        byte[] certificate = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));
        byte[] certificateVerify = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));
        byte[] finished = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));

        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(serverHello));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(encryptedExtensions));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(certificate));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(certificateVerify));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(finished));

        byte[] clientFinished = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        packetEngine(server).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(clientFinished));

        assertThat(server.handshakeState(), is(NEED_SEND_HANDSHAKE_DONE));
        assertThat(server.tryMarkHandshakeDone(), is(true));
        assertThat(server.handshakeState(), is(HANDSHAKE_CONFIRMED));
        assertThat(remoteTransportParameters.get(), equalTo(CLIENT_TRANSPORT_PARAMETERS));
        assertThat(required(server.applicationProtocol()), is("h3"));
    }

    @Test
    void shouldSelectTlsWithoutChangingServerName() throws Exception {
        SSLParameters selectedParameters = new SSLParameters();
        selectedParameters.setProtocols(new String[] {"TLSv1.3"});
        selectedParameters.setApplicationProtocols(new String[] {"h3-selected"});
        selectedParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
        selectedParameters.setNeedClientAuth(true);
        QuicTlsServerSelector.Selection selection =
                serverSelection(selectedParameters, new AcceptAllTrustManager());
        AtomicReference<Optional<String>> requestedServerName = new AtomicReference<>();
        HelidonServerQuicTLSEngine server = newServerEngine("peer.example", requested -> {
            requestedServerName.set(requested);
            return selection;
        });

        HelidonClientQuicTLSEngine client = newClientEngine("EXAMPLE.COM");
        SSLParameters clientParameters = client.sslParameters();
        clientParameters.setApplicationProtocols(new String[] {"h3-selected"});
        client.sslParameters(clientParameters);

        packetEngine(server).consumeHandshakeBytesBuffer(
                INITIAL,
                ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));

        assertThat(requestedServerName.get(), equalTo(Optional.of("EXAMPLE.COM")));
        assertThat(required(server.applicationProtocol()), is("h3-selected"));
        assertThat(server.sslParameters().getNeedClientAuth(), is(true));
    }

    @Test
    void shouldRejectUnsupportedSslPoliciesAtomically() throws Exception {
        HelidonServerQuicTLSEngine engine = newServerEngine("example.com");

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
    void shouldRejectUnsupportedSelectedSslPoliciesAtomically() throws Exception {
        SSLParameters constrained = new SSLParameters();
        constrained.setProtocols(new String[] {"TLSv1.3"});
        constrained.setApplicationProtocols(new String[] {"h3-selected"});
        constrained.setAlgorithmConstraints(QuicTlsTestSupport.allPermittingAlgorithmConstraints());
        assertUnsupportedSelectionRejected(constrained, "AlgorithmConstraints");

        SSLParameters matched = new SSLParameters();
        matched.setProtocols(new String[] {"TLSv1.3"});
        matched.setApplicationProtocols(new String[] {"h3-selected"});
        matched.setSNIMatchers(List.of(SNIHostName.createSNIMatcher("example\\.com")));
        assertUnsupportedSelectionRejected(matched, "SNIMatchers");
    }

    @Test
    void shouldRejectLegacySessionIdBeforeServerSelection() throws Exception {
        AtomicInteger selectionCount = new AtomicInteger();
        HelidonServerQuicTLSEngine server = newServerEngine("peer.example", requested -> {
            selectionCount.incrementAndGet();
            throw new AssertionError("TLS selection must not run for a prohibited legacy_session_id");
        });
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

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> packetEngine(server).consumeHandshakeBytesBuffer(INITIAL,
                                                                        compatibilityModeClientHello.encode()));

        assertThat(failure.errorCode(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
        assertThat(selectionCount.get(), is(0));
    }

    @Test
    void shouldSelectTlsWithoutServerName() throws Exception {
        SSLParameters selectedParameters = new SSLParameters();
        selectedParameters.setProtocols(new String[] {"TLSv1.3"});
        selectedParameters.setApplicationProtocols(new String[] {"h3"});
        selectedParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
        QuicTlsServerSelector.Selection selection = serverSelection(selectedParameters, null);
        AtomicReference<Optional<String>> requestedServerName = new AtomicReference<>();
        HelidonServerQuicTLSEngine server = newServerEngine("peer.example", requested -> {
            requestedServerName.set(requested);
            return selection;
        });
        HelidonClientQuicTLSEngine client = newClientEngine((String) null);

        packetEngine(server).consumeHandshakeBytesBuffer(
                INITIAL,
                ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));

        assertThat(requestedServerName.get(), equalTo(Optional.empty()));
        assertThat(required(server.applicationProtocol()), is("h3"));
    }

    @Test
    void shouldNotDeriveServerNameFromIpLiteralPeerHost() throws Exception {
        for (String peerHost : List.of("127.0.0.1", "::1")) {
            SSLParameters selectedParameters = new SSLParameters();
            selectedParameters.setProtocols(new String[] {"TLSv1.3"});
            selectedParameters.setApplicationProtocols(new String[] {"h3"});
            selectedParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
            QuicTlsServerSelector.Selection selection = serverSelection(selectedParameters, null);
            AtomicReference<Optional<String>> requestedServerName = new AtomicReference<>();
            HelidonServerQuicTLSEngine server = newServerEngine("peer.example", requested -> {
                requestedServerName.set(requested);
                return selection;
            });
            HelidonClientQuicTLSEngine client = newClientEngine(peerHost);

            packetEngine(server).consumeHandshakeBytesBuffer(
                    INITIAL,
                    ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));

            assertThat(requestedServerName.get(), equalTo(Optional.empty()));
            assertThat(required(server.applicationProtocol()), is("h3"));
        }
    }

    @Test
    void shouldSelectTlsOnlyOnceAcrossHelloRetryRequest() throws Exception {
        SSLParameters selectedParameters = new SSLParameters();
        selectedParameters.setProtocols(new String[] {"TLSv1.3"});
        selectedParameters.setApplicationProtocols(new String[] {"h3"});
        selectedParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
        selectedParameters.setNamedGroups(new String[] {"secp256r1"});
        QuicTlsServerSelector.Selection selection = serverSelection(selectedParameters, null);
        AtomicInteger selectionCount = new AtomicInteger();
        HelidonServerQuicTLSEngine server = newServerEngine("peer.example", requested -> {
            selectionCount.incrementAndGet();
            return selection;
        });
        HelidonClientQuicTLSEngine client =
                newClientEngine("example.com", new String[] {"x25519", "secp256r1"});

        packetEngine(server).consumeHandshakeBytesBuffer(
                INITIAL,
                ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));

        byte[] helloRetryRequest = copy(packetEngine(server).handshakeBytesBuffer(INITIAL));
        assertThat(QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(helloRetryRequest)).helloRetryRequest(), is(true));
        assertThat(selectionCount.get(), is(1));

        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(helloRetryRequest));
        packetEngine(server).consumeHandshakeBytesBuffer(
                INITIAL,
                ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));

        assertThat(selectionCount.get(), is(1));
        assertThat(required(server.applicationProtocol()), is("h3"));
    }

    @Test
    void shouldQueueNewSessionTicketAfterHandshakeConfirmation() throws Exception {
        HelidonServerQuicTLSEngine server = newServerEngine("example.com");
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");

        completeHandshake(server, client);

        QuicTlsNewSessionTicket ticket =
                QuicTlsNewSessionTicket.decode(required(packetEngine(server).handshakeBytesBuffer(ONE_RTT)));

        assertThat(server.handshakeState(), is(HANDSHAKE_CONFIRMED));
        assertThat(server.currentSendKeySpace(), is(ONE_RTT));
        assertThat(ticket.ticketNonce().length > 0, is(true));
        assertThat(ticket.ticket().length > 0, is(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void defaultServerCacheDoesNotIssueTicketsWithUnlimitedTlsPolicy(boolean peerInformation) throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        sslParameters.setApplicationProtocols(new String[] {"h3"});
        sslParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
        var keyManager = new StaticKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                              QuicTlsRfc8448Vectors.rsaPrivateKey());
        var tls = QuicTlsTestSupport.tlsBuilder(keyManager, null)
                .sslParameters(sslParameters)
                .sessionCacheSize(0)
                .build();
        QuicTlsConfigSnapshot config = QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create()));
        HelidonServerQuicTLSEngine server = initializeServerEngine(peerInformation
                                                                         ? new HelidonServerQuicTLSEngine(config,
                                                                                                         "example.com",
                                                                                                         443)
                                                                         : new HelidonServerQuicTLSEngine(config));

        completeHandshake(server, newClientEngine("example.com"));

        assertThat(server.handshakeState(), is(HANDSHAKE_CONFIRMED));
        assertThat(server.currentSendKeySpace(), is(ONE_RTT));
        assertThat(packetEngine(server).handshakeBytesBuffer(ONE_RTT), is(Optional.empty()));
    }

    @Test
    void shouldResumeWithCachedPskAndShorterServerFlight() throws Exception {
        QuicTlsSessionCache clientSessionCache = new QuicTlsSessionCache();
        QuicTlsServerSessionCache serverSessionCache = new QuicTlsServerSessionCache();

        HelidonServerQuicTLSEngine server1 = newServerEngine("example.com", serverSessionCache);
        HelidonClientQuicTLSEngine client1 = newClientEngine("example.com", clientSessionCache);

        packetEngine(server1).consumeHandshakeBytesBuffer(INITIAL,
                                                          ByteBuffer.wrap(copy(packetEngine(client1).handshakeBytesBuffer(
                                                                  INITIAL))));

        client1.versionNegotiated(VERSION);
        packetEngine(client1).consumeHandshakeBytesBuffer(INITIAL,
                                                          ByteBuffer.wrap(copy(packetEngine(server1).handshakeBytesBuffer(
                                                                  INITIAL))));
        packetEngine(client1).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                          ByteBuffer.wrap(copy(packetEngine(server1).handshakeBytesBuffer(
                                                                  HANDSHAKE))));
        packetEngine(client1).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                          ByteBuffer.wrap(copy(packetEngine(server1).handshakeBytesBuffer(
                                                                  HANDSHAKE))));
        packetEngine(client1).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                          ByteBuffer.wrap(copy(packetEngine(server1).handshakeBytesBuffer(
                                                                  HANDSHAKE))));
        packetEngine(client1).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                          ByteBuffer.wrap(copy(packetEngine(server1).handshakeBytesBuffer(
                                                                  HANDSHAKE))));

        packetEngine(server1).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                          ByteBuffer.wrap(copy(packetEngine(client1).handshakeBytesBuffer(
                                                                  HANDSHAKE))));
        assertThat(server1.tryMarkHandshakeDone(), is(true));
        client1.tryReceiveHandshakeDone();
        packetEngine(client1).consumeHandshakeBytesBuffer(ONE_RTT,
                                                          ByteBuffer.wrap(copy(packetEngine(server1).handshakeBytesBuffer(
                                                                  ONE_RTT))));

        HelidonServerQuicTLSEngine server2 = newServerEngine("example.com", serverSessionCache);
        HelidonClientQuicTLSEngine client2 = newClientEngine("example.com", clientSessionCache);

        byte[] resumedClientHello = copy(packetEngine(client2).handshakeBytesBuffer(INITIAL));
        packetEngine(server2).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(resumedClientHello));

        byte[] resumedServerHello = copy(packetEngine(server2).handshakeBytesBuffer(INITIAL));
        QuicTlsServerHelloMessage serverHelloMessage =
                QuicTlsServerHelloMessage.decode(ByteBuffer.wrap(resumedServerHello));
        assertThat(serverHelloMessage.selectedIdentity().orElseThrow(), is(0));

        byte[] resumedEncryptedExtensions = copy(packetEngine(server2).handshakeBytesBuffer(HANDSHAKE));
        byte[] resumedFinished = copy(packetEngine(server2).handshakeBytesBuffer(HANDSHAKE));

        assertThat(server2.handshakeState(), is(NEED_RECV_CRYPTO));
        assertThat(server2.currentSendKeySpace(), is(ONE_RTT));

        client2.versionNegotiated(VERSION);
        packetEngine(client2).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(resumedServerHello));
        packetEngine(client2).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(resumedEncryptedExtensions));
        packetEngine(client2).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(resumedFinished));

        packetEngine(server2).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                          ByteBuffer.wrap(copy(packetEngine(client2).handshakeBytesBuffer(
                                                                  HANDSHAKE))));

        assertThat(server2.handshakeState(), is(NEED_SEND_HANDSHAKE_DONE));
    }

    @Test
    void shouldCompleteServerHandshakeWithRequiredClientAuthentication() throws Exception {
        HelidonServerQuicTLSEngine server = newServerEngine("example.com",
                                                            null,
                                                            true,
                                                            false,
                                                            new AcceptAllTrustManager());
        HelidonClientQuicTLSEngine client = newClientEngine("example.com", true, null);
        byte[] clientHello = copy(packetEngine(client).handshakeBytesBuffer(INITIAL));

        packetEngine(server).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(clientHello));

        byte[] serverHello = copy(packetEngine(server).handshakeBytesBuffer(INITIAL));
        byte[] encryptedExtensions = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));
        byte[] certificateRequest = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));
        byte[] certificate = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));
        byte[] certificateVerify = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));
        byte[] finished = copy(packetEngine(server).handshakeBytesBuffer(HANDSHAKE));

        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL, ByteBuffer.wrap(serverHello));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(encryptedExtensions));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(certificateRequest));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(certificate));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(certificateVerify));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(finished));

        byte[] clientCertificate = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        byte[] clientCertificateVerify = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        byte[] clientFinished = copy(packetEngine(client).handshakeBytesBuffer(HANDSHAKE));
        packetEngine(server).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(clientCertificate));
        packetEngine(server).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(clientCertificateVerify));
        packetEngine(server).consumeHandshakeBytesBuffer(HANDSHAKE, ByteBuffer.wrap(clientFinished));

        assertThat(server.handshakeState(), is(NEED_SEND_HANDSHAKE_DONE));
        assertThat(((X509Certificate) server.session().getPeerCertificates()[0]).getSubjectX500Principal().getName(),
                   containsString("CN=rsa"));
        assertThat(server.tryMarkHandshakeDone(), is(true));
        assertThat(server.handshakeState(), is(HANDSHAKE_CONFIRMED));
    }

    private static void completeHandshake(HelidonServerQuicTLSEngine server, HelidonClientQuicTLSEngine client) {
        packetEngine(server).consumeHandshakeBytesBuffer(INITIAL,
                                                         ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL))));

        client.versionNegotiated(VERSION);
        packetEngine(client).consumeHandshakeBytesBuffer(INITIAL,
                                                         ByteBuffer.wrap(copy(packetEngine(server).handshakeBytesBuffer(INITIAL))));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                         ByteBuffer.wrap(copy(packetEngine(server).handshakeBytesBuffer(
                                                                 HANDSHAKE))));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                         ByteBuffer.wrap(copy(packetEngine(server).handshakeBytesBuffer(
                                                                 HANDSHAKE))));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                         ByteBuffer.wrap(copy(packetEngine(server).handshakeBytesBuffer(
                                                                 HANDSHAKE))));
        packetEngine(client).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                         ByteBuffer.wrap(copy(packetEngine(server).handshakeBytesBuffer(
                                                                 HANDSHAKE))));

        packetEngine(server).consumeHandshakeBytesBuffer(HANDSHAKE,
                                                         ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(
                                                                 HANDSHAKE))));
        assertThat(server.tryMarkHandshakeDone(), is(true));
    }

    private static HelidonServerQuicTLSEngine newServerEngine(String peerHost) throws Exception {
        return newServerEngine(peerHost, null, false, false, null, new QuicTlsServerSessionCache());
    }

    private static HelidonServerQuicTLSEngine newServerEngine(String peerHost, String[] namedGroups) throws Exception {
        return newServerEngine(peerHost, namedGroups, false, false, null, new QuicTlsServerSessionCache());
    }

    private static HelidonServerQuicTLSEngine newServerEngine(String peerHost,
                                                              QuicTlsServerSelector serverTlsSelector)
            throws Exception {
        return newServerEngine(peerHost,
                               null,
                               false,
                               false,
                               null,
                               new QuicTlsServerSessionCache(),
                               serverTlsSelector);
    }

    private static HelidonServerQuicTLSEngine newServerEngine(String peerHost,
                                                              QuicTlsServerSessionCache serverSessionCache)
            throws Exception {
        return newServerEngine(peerHost, null, false, false, null, serverSessionCache);
    }

    private static HelidonServerQuicTLSEngine newServerEngine(String peerHost,
                                                              String[] namedGroups,
                                                              boolean needClientAuth,
                                                              boolean wantClientAuth,
                                                              X509ExtendedTrustManager trustManager) throws Exception {
        return newServerEngine(peerHost,
                               namedGroups,
                               needClientAuth,
                               wantClientAuth,
                               trustManager,
                               new QuicTlsServerSessionCache());
    }

    private static HelidonServerQuicTLSEngine newServerEngine(String peerHost,
                                                              String[] namedGroups,
                                                              boolean needClientAuth,
                                                              boolean wantClientAuth,
                                                              X509ExtendedTrustManager trustManager,
                                                              QuicTlsServerSessionCache serverSessionCache)
            throws Exception {
        return newServerEngine(peerHost,
                               namedGroups,
                               needClientAuth,
                               wantClientAuth,
                               trustManager,
                               serverSessionCache,
                               null);
    }

    private static HelidonServerQuicTLSEngine newServerEngine(String peerHost,
                                                              String[] namedGroups,
                                                              boolean needClientAuth,
                                                              boolean wantClientAuth,
                                                              X509ExtendedTrustManager trustManager,
                                                              QuicTlsServerSessionCache serverSessionCache,
                                                              QuicTlsServerSelector serverTlsSelector)
            throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setProtocols(new String[] {"TLSv1.3"});
        sslParameters.setApplicationProtocols(new String[] {"h3"});
        sslParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
        if (needClientAuth) {
            sslParameters.setNeedClientAuth(true);
        } else if (wantClientAuth) {
            sslParameters.setWantClientAuth(true);
        }
        if (namedGroups != null) {
            sslParameters.setNamedGroups(namedGroups);
        }

        var keyManager = new StaticKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                              QuicTlsRfc8448Vectors.rsaPrivateKey());
        var tls = QuicTlsTestSupport.tlsBuilder(keyManager, trustManager)
                .sslParameters(sslParameters)
                .build();
        HelidonServerQuicTLSEngine engine = new HelidonServerQuicTLSEngine(
                QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create())),
                peerHost,
                443,
                serverSessionCache,
                serverTlsSelector);
        return initializeServerEngine(engine);
    }

    private static HelidonServerQuicTLSEngine initializeServerEngine(HelidonServerQuicTLSEngine engine) {
        engine.clientMode(false);
        packetEngine(engine).deriveInitialKeysBuffer(VERSION, ByteBuffer.wrap(bytes("8394c8f03e515708")));
        engine.versionNegotiated(VERSION);
        packetEngine(engine).localQuicTransportParametersBuffer(ByteBuffer.wrap(SERVER_TRANSPORT_PARAMETERS));
        return engine;
    }

    private static QuicTlsServerSelector.Selection serverSelection(SSLParameters sslParameters,
                                                                   X509ExtendedTrustManager trustManager)
            throws Exception {
        return serverSelection(sslParameters, sslParameters, trustManager);
    }

    private static QuicTlsServerSelector.Selection serverSelection(SSLParameters configParameters,
                                                                   SSLParameters selectedParameters,
                                                                   X509ExtendedTrustManager trustManager)
            throws Exception {
        var keyManager = new StaticKeyManager(QuicTlsRfc8448Vectors.rsaCertificate(),
                                              QuicTlsRfc8448Vectors.rsaPrivateKey());
        var tls = QuicTlsTestSupport.tlsBuilder(keyManager, trustManager)
                .sslParameters(configParameters)
                .build();
        QuicTlsConfigSnapshot config =
                QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create()));
        return new QuicTlsServerSelector.Selection(config,
                                                   selectedParameters,
                                                   QuicTlsServerSessionCache.disabled());
    }

    private static void assertUnsupportedSelectionRejected(SSLParameters selectedParameters,
                                                           String expectedMessage)
            throws Exception {
        SSLParameters configParameters = new SSLParameters();
        configParameters.setProtocols(new String[] {"TLSv1.3"});
        configParameters.setApplicationProtocols(new String[] {"h3"});
        configParameters.setSignatureSchemes(new String[] {"rsa_pss_rsae_sha256", "rsa_pkcs1_sha256"});
        QuicTlsServerSelector.Selection selection = serverSelection(configParameters, selectedParameters, null);
        HelidonServerQuicTLSEngine server = newServerEngine("peer.example", requested -> selection);
        HelidonClientQuicTLSEngine client = newClientEngine("example.com");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> packetEngine(server).consumeHandshakeBytesBuffer(
                        INITIAL,
                        ByteBuffer.wrap(copy(packetEngine(client).handshakeBytesBuffer(INITIAL)))));

        assertThat(failure.getMessage(), containsString(expectedMessage));
        assertThat(List.of(server.sslParameters().getApplicationProtocols()), equalTo(List.of("h3")));
    }

    private static HelidonClientQuicTLSEngine newClientEngine(String peerHost) throws Exception {
        return newClientEngine(peerHost, null, false, null);
    }

    private static HelidonClientQuicTLSEngine newClientEngine(String peerHost, String[] namedGroups) throws Exception {
        return newClientEngine(peerHost, null, false, namedGroups);
    }

    private static HelidonClientQuicTLSEngine newClientEngine(String peerHost, QuicTlsSessionCache sessionCache)
            throws Exception {
        return newClientEngine(peerHost, sessionCache, false, null);
    }

    private static HelidonClientQuicTLSEngine newClientEngine(String peerHost,
                                                              boolean withClientCertificate,
                                                              String[] namedGroups) throws Exception {
        return newClientEngine(peerHost, null, withClientCertificate, namedGroups);
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
        QuicTlsConfigSnapshot config =
                QuicTlsConfigSnapshot.create(tls, QuicRuntimeConfig.create(QuicConfig.create()));
        HelidonClientQuicTLSEngine engine = peerHost == null
                ? new HelidonClientQuicTLSEngine(config, sessionCache)
                : new HelidonClientQuicTLSEngine(config, peerHost, 443, sessionCache);
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

    private static void assertHeaderProtectionMaskMatchesPeer(QuicTLSEngine server,
                                                               QuicTLSEngine client,
                                                               QuicTLSEngine.KeySpace keySpace,
                                                               boolean incoming) throws Exception {
        ByteBuffer peerMask = packetEngine(client).computeHeaderProtectionMaskBuffer(
                keySpace,
                !incoming,
                ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE));
        assertThat(packetEngine(server).computeHeaderProtectionMaskBits(
                           keySpace,
                           incoming,
                           ByteBuffer.wrap(HEADER_PROTECTION_SAMPLE)),
                   is(packedHeaderProtectionMask(peerMask)));
    }

    private static long packedHeaderProtectionMask(ByteBuffer mask) {
        int offset = mask.position();
        return ((long) mask.get(offset) & 0xff) << 32
                | ((long) mask.get(offset + 1) & 0xff) << 24
                | ((long) mask.get(offset + 2) & 0xff) << 16
                | ((long) mask.get(offset + 3) & 0xff) << 8
                | (long) mask.get(offset + 4) & 0xff;
    }

    private static <T> T required(Optional<T> optional) {
        return optional.orElseThrow();
    }

    private static final class StaticKeyManager extends X509ExtendedKeyManager {
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

    private static final class AcceptAllTrustManager extends X509ExtendedTrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
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
